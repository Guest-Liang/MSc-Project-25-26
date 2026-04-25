import { Hono } from "hono"
import { requireWorker } from "../middleware/auth.js"
import { ERR, INFO } from "../utils/status.js"
import { jsonResponse } from "../utils/response.js"
import {
  ORDER_TYPES,
  decodeCursor,
  encodeCursor,
  formatOrderLog,
  formatOrderRecord,
  getNextSequenceStepsByOrder,
  getOrderById,
  getOrderSteps,
  normalizeUidHex,
  parsePaginationLimit,
  parsePositiveInteger,
  parseCsvParam,
  toOptionalTrimmedString,
  writeOrderLog
} from "../utils/order.js"

export const workerRoutes = new Hono()
const WORKER_ORDERS_DEFAULT_LIMIT = 6
const WORKER_ORDERS_MAX_LIMIT = 100
const WORKER_HISTORY_DEFAULT_LIMIT = 3
const WORKER_HISTORY_MAX_LIMIT = 100

workerRoutes.use("*", requireWorker)

async function enrichWorkerOrders(db, orders) {
  const nextStepsByOrder = await getNextSequenceStepsByOrder(db, orders)
  return orders.map((order) => formatOrderRecord(order, nextStepsByOrder.get(Number(order.id)) ?? null))
}

function buildUidHexCondition(columnA, columnB, uidHexValues, conditions, values) {
  if (uidHexValues.length === 0) return

  conditions.push(
    `(${uidHexValues.map(() => `(${columnA} = ? OR ${columnB} = ?)`).join(" OR ")})`
  )

  uidHexValues.forEach((uidHex) => {
    values.push(uidHex, uidHex)
  })
}

workerRoutes.get("/orders", async (c) => {
  const user = c.get("user")
  const params = c.req.query()
  const conditions = ["assigned_to = ?"]
  const values = [user.id]
  const { value: limit, error: limitError } = parsePaginationLimit(
    params.limit,
    WORKER_ORDERS_DEFAULT_LIMIT,
    WORKER_ORDERS_MAX_LIMIT
  )

  if (limitError) return jsonResponse(null, limitError)

  if (params.cursor) {
    const cursor = decodeCursor(params.cursor)
    const completedBucket = cursor?.completedBucket
    const cursorId = parsePositiveInteger(cursor?.id)

    if ((completedBucket !== 0 && completedBucket !== 1) || !cursorId) {
      return jsonResponse(null, {
        ...ERR.INVALID_CURSOR,
        data: { cursor: params.cursor }
      })
    }

    conditions.push("((CASE WHEN status = 'completed' THEN 1 ELSE 0 END) > ? OR ((CASE WHEN status = 'completed' THEN 1 ELSE 0 END) = ? AND id < ?))")
    values.push(completedBucket, completedBucket, cursorId)
  }

  const where = `WHERE ${conditions.join(" AND ")}`

  const list = await c.env.MScPJ_DB.prepare(
    `SELECT *,
      CASE WHEN status = 'completed' THEN 1 ELSE 0 END AS completed_bucket
     FROM orders
     ${where}
     ORDER BY completed_bucket ASC, id DESC
     LIMIT ?`
  ).bind(...values, limit + 1).all()

  const pageRows = (list.results ?? []).slice(0, limit)
  const hasMore = (list.results ?? []).length > limit

  const formattedOrders = await enrichWorkerOrders(c.env.MScPJ_DB, pageRows)
  const lastRow = hasMore ? pageRows[pageRows.length - 1] : null
  const nextCursor = lastRow
    ? encodeCursor({
      completedBucket: Number(lastRow.completed_bucket ?? 0),
      id: lastRow.id
    })
    : null

  return jsonResponse({
    ...INFO.SQL_QUERY_SUCCESS,
    data: {
      items: formattedOrders,
      nextCursor,
      hasMore
    }
  })
})

workerRoutes.get("/history", async (c) => {
  const user = c.get("user")
  const params = c.req.query()
  const conditions = ["l.operator_id = ?"]
  const values = [user.id]
  const { value: limit, error: limitError } = parsePaginationLimit(
    params.limit,
    WORKER_HISTORY_DEFAULT_LIMIT,
    WORKER_HISTORY_MAX_LIMIT
  )

  if (limitError) return jsonResponse(null, limitError)

  if (params.startTime && params.endTime && params.startTime > params.endTime) {
    return jsonResponse(null, ERR.INVALID_TIME_RANGE)
  }

  if (params.cursor) {
    const cursor = decodeCursor(params.cursor)
    const cursorId = parsePositiveInteger(cursor?.id)
    const cursorTimestamp = typeof cursor?.timestamp === "string" && cursor.timestamp.trim().length > 0
      ? cursor.timestamp
      : null

    if (!cursorId || !cursorTimestamp) {
      return jsonResponse(null, {
        ...ERR.INVALID_CURSOR,
        data: { cursor: params.cursor }
      })
    }

    conditions.push("(l.timestamp < ? OR (l.timestamp = ? AND l.id < ?))")
    values.push(cursorTimestamp, cursorTimestamp, cursorId)
  }

  if (params.orderId) {
    const orderIds = parseCsvParam(params.orderId).map((value) => parsePositiveInteger(value))
    if (orderIds.some((value) => value === null)) return jsonResponse(null, ERR.INVALID_ORDER_ID)

    if (orderIds.length > 0) {
      conditions.push(`l.order_id IN (${orderIds.map(() => "?").join(",")})`)
      values.push(...orderIds)
    }
  }

  const actionValues = parseCsvParam(params.action)
  if (actionValues.length > 0) {
    conditions.push(`l.action IN (${actionValues.map(() => "?").join(",")})`)
    values.push(...actionValues)
  }

  const resultValues = parseCsvParam(params.result)
  if (resultValues.length > 0) {
    conditions.push(`l.result IN (${resultValues.map(() => "?").join(",")})`)
    values.push(...resultValues)
  }

  const uidHexValues = parseCsvParam(params.uidHex)
  if (uidHexValues.length > 0) {
    const normalizedUidHexValues = uidHexValues.map((value) => normalizeUidHex(value))
    if (normalizedUidHexValues.some((value) => value === null)) {
      return jsonResponse(null, ERR.INVALID_UID_HEX)
    }

    buildUidHexCondition("l.scan_uid_hex", "l.expected_uid_hex", normalizedUidHexValues, conditions, values)
  }

  if (params.startTime) {
    conditions.push("l.timestamp >= ?")
    values.push(params.startTime)
  }

  if (params.endTime) {
    conditions.push("l.timestamp <= ?")
    values.push(params.endTime)
  }

  const where = `WHERE ${conditions.join(" AND ")}`
  const sql = `
    SELECT
      l.*,
      o.order_type,
      o.title AS order_title
    FROM order_logs l
    LEFT JOIN orders o ON o.id = l.order_id
    ${where}
    ORDER BY l.timestamp DESC, l.id DESC
    LIMIT ?
  `

  const rows = await c.env.MScPJ_DB.prepare(sql).bind(...values, limit + 1).all()
  const pageRows = (rows.results ?? []).slice(0, limit)
  const hasMore = (rows.results ?? []).length > limit
  const lastRow = hasMore ? pageRows[pageRows.length - 1] : null
  const nextCursor = lastRow
    ? encodeCursor({ timestamp: lastRow.timestamp, id: lastRow.id })
    : null

  return jsonResponse({
    ...INFO.SQL_QUERY_SUCCESS,
    data: {
      items: pageRows.map(formatOrderLog),
      nextCursor,
      hasMore
    }
  })
})

workerRoutes.get("/history/summary", async (c) => {
  const user = c.get("user")

  const summary = await c.env.MScPJ_DB.prepare(
    `SELECT
      SUM(CASE WHEN action = 'scan' THEN 1 ELSE 0 END) AS total_scan_count,
      SUM(CASE WHEN action = 'scan' AND result IN ('standard_matched', 'sequence_step_completed') THEN 1 ELSE 0 END) AS successful_scan_count,
      SUM(CASE WHEN action = 'scan' AND result = 'mismatch' THEN 1 ELSE 0 END) AS mismatch_count,
      SUM(CASE WHEN action = 'scan' AND result = 'out_of_order' THEN 1 ELSE 0 END) AS out_of_order_count,
      SUM(CASE WHEN action = 'scan' AND result = 'duplicate' THEN 1 ELSE 0 END) AS duplicate_count,
      SUM(CASE WHEN action = 'completed' AND result IN ('standard_completed', 'sequence_completed') THEN 1 ELSE 0 END) AS completed_order_count,
      COUNT(DISTINCT CASE WHEN action = 'scan' AND result IN ('standard_matched', 'sequence_step_completed') THEN order_id END) AS visited_order_count,
      COUNT(DISTINCT CASE WHEN action = 'scan' AND result IN ('standard_matched', 'sequence_step_completed') THEN scan_uid_hex END) AS unique_visited_uid_count,
      MAX(CASE WHEN action = 'scan' THEN timestamp ELSE NULL END) AS last_scan_at,
      MAX(CASE WHEN action = 'completed' THEN timestamp ELSE NULL END) AS last_completed_at
    FROM order_logs
    WHERE operator_id = ?`
  ).bind(user.id).first()

  return jsonResponse({
    ...INFO.SQL_QUERY_SUCCESS,
    data: {
      workerId: user.id,
      totalScanCount: Number(summary?.total_scan_count ?? 0),
      successfulScanCount: Number(summary?.successful_scan_count ?? 0),
      mismatchCount: Number(summary?.mismatch_count ?? 0),
      outOfOrderCount: Number(summary?.out_of_order_count ?? 0),
      duplicateCount: Number(summary?.duplicate_count ?? 0),
      completedOrderCount: Number(summary?.completed_order_count ?? 0),
      visitedOrderCount: Number(summary?.visited_order_count ?? 0),
      uniqueVisitedUidCount: Number(summary?.unique_visited_uid_count ?? 0),
      lastScanAt: summary?.last_scan_at ?? null,
      lastCompletedAt: summary?.last_completed_at ?? null
    }
  })
})

workerRoutes.post("/orders/scan", async (c) => {
  let body

  try {
    body = await c.req.json()
  } catch {
    return jsonResponse(null, ERR.INVALID_SCAN_PAYLOAD)
  }

  const user = c.get("user")
  const orderId = parsePositiveInteger(body?.orderId)
  const uidHex = normalizeUidHex(body?.uidHex)
  const rawText = toOptionalTrimmedString(body?.rawText)
  const ndefText = toOptionalTrimmedString(body?.ndefText)

  if (!orderId || !uidHex) return jsonResponse(null, ERR.INVALID_SCAN_PAYLOAD)

  const order = await getOrderById(c.env.MScPJ_DB, orderId)
  if (!order) return jsonResponse(null, ERR.ORDER_NOT_FOUND)
  if (order.assigned_to !== user.id) return jsonResponse(null, ERR.ORDER_NOT_OWNED)
  if (order.status !== "assigned") return jsonResponse(null, ERR.ORDER_NOT_COMPLETABLE)

  const orderType = order.order_type ?? ORDER_TYPES.STANDARD

  if (orderType === ORDER_TYPES.STANDARD) {
    const expectedUidHex = normalizeUidHex(order.target_uid_hex ?? order.nfc_tag ?? "")
    const locationCode = order.location_code ?? null
    const displayName = order.display_name ?? null

    if (!expectedUidHex) {
      return jsonResponse(null, {
        ...ERR.ORDER_TARGET_UID_MISSING,
        data: {
          orderId,
          configuredTargetUidHex: order.target_uid_hex ?? order.nfc_tag ?? null
        }
      })
    }

    if (uidHex !== expectedUidHex) {
      await writeOrderLog(c.env.MScPJ_DB, {
        orderId,
        action: "scan",
        operatorId: user.id,
        result: "mismatch",
        scanUidHex: uidHex,
        expectedUidHex,
        locationCode,
        displayName,
        details: {
          orderType,
          rawText,
          ndefText
        }
      })

      return jsonResponse(null, {
        ...ERR.SCAN_UID_MISMATCH,
        data: {
          orderId,
          orderType,
          scannedUidHex: uidHex,
          expectedUidHex,
          locationCode,
          displayName
        }
      })
    }

    const updateResult = await c.env.MScPJ_DB.prepare(
      `UPDATE orders
       SET status = 'completed',
           completed_at = datetime('now'),
           updated_at = datetime('now')
       WHERE id = ?
         AND assigned_to = ?
         AND status = 'assigned'`
    ).bind(orderId, user.id).run()

    if (updateResult.meta.changes !== 1) return jsonResponse(null, ERR.ORDER_STATE_CHANGED)

    await writeOrderLog(c.env.MScPJ_DB, {
      orderId,
      action: "scan",
      operatorId: user.id,
      result: "standard_matched",
      scanUidHex: uidHex,
      expectedUidHex,
      locationCode,
      displayName,
      details: {
        orderType,
        rawText,
        ndefText
      }
    })

    await writeOrderLog(c.env.MScPJ_DB, {
      orderId,
      action: "completed",
      operatorId: user.id,
      result: "standard_completed",
      scanUidHex: uidHex,
      expectedUidHex,
      locationCode,
      displayName,
      details: {
        orderType
      }
    })

    return jsonResponse({
      ...INFO.ORDER_SCAN_SUCCESS,
      data: {
        orderId,
        orderType,
        matched: true,
        completed: true,
        scannedUidHex: uidHex,
        expectedUidHex,
        locationCode,
        displayName,
        sequenceTotalSteps: 0,
        sequenceCompletedSteps: 0,
        nextStepIndex: null,
        nextExpectedUidHex: null
      }
    })
  }

  const steps = await getOrderSteps(c.env.MScPJ_DB, orderId)
  const totalSteps = steps.length
  if (totalSteps < 1) return jsonResponse(null, ERR.ORDER_STEPS_REQUIRED)

  const completedSteps = Number(order.sequence_completed_steps ?? 0)
  const nextStepIndex = completedSteps + 1
  const nextStep = steps.find((step) => Number(step.step_index) === nextStepIndex) ?? null

  if (!nextStep) {
    return jsonResponse(null, {
      ...ERR.ORDER_NEXT_STEP_MISSING,
      data: {
        orderId,
        expectedStepIndex: nextStepIndex
      }
    })
  }

  const matchingSteps = steps.filter((step) => step.target_uid_hex === uidHex)

  if (nextStep.target_uid_hex !== uidHex) {
    const futureMatch = matchingSteps.find((step) => Number(step.step_index) > nextStepIndex) ?? null
    const previousMatches = matchingSteps.filter((step) => Number(step.step_index) < nextStepIndex)
    const duplicateMatch = previousMatches.length > 0 ? previousMatches[previousMatches.length - 1] : null
    const isOutOfOrder = !!futureMatch
    const matchedStep = futureMatch ?? duplicateMatch
    const result = isOutOfOrder ? "out_of_order" : matchingSteps.length > 0 ? "duplicate" : "mismatch"
    const error = isOutOfOrder ? ERR.SCAN_OUT_OF_ORDER : matchingSteps.length > 0 ? ERR.SCAN_DUPLICATE : ERR.SCAN_UID_MISMATCH

    await writeOrderLog(c.env.MScPJ_DB, {
      orderId,
      action: "scan",
      operatorId: user.id,
      result,
      stepIndex: matchedStep ? Number(matchedStep.step_index) : nextStepIndex,
      scanUidHex: uidHex,
      expectedUidHex: nextStep.target_uid_hex,
      locationCode: matchedStep?.location_code ?? nextStep.location_code ?? null,
      displayName: matchedStep?.display_name ?? nextStep.display_name ?? null,
      details: {
        orderType,
        rawText,
        ndefText,
        expectedStepIndex: nextStepIndex,
        scannedStepIndex: matchedStep ? Number(matchedStep.step_index) : null
      }
    })

    return jsonResponse(null, {
      ...error,
      data: {
        orderId,
        orderType,
        scannedUidHex: uidHex,
        expectedStepIndex: nextStepIndex,
        expectedUidHex: nextStep.target_uid_hex,
        expectedLocationCode: nextStep.location_code ?? null,
        expectedDisplayName: nextStep.display_name ?? null,
        scannedStepIndex: matchedStep ? Number(matchedStep.step_index) : null
      }
    })
  }

  const isFinalStep = nextStepIndex >= totalSteps

  if (isFinalStep) {
    const updateResult = await c.env.MScPJ_DB.prepare(
      `UPDATE orders
       SET status = 'completed',
           completed_at = datetime('now'),
           updated_at = datetime('now'),
           sequence_total_steps = ?,
           sequence_completed_steps = ?
       WHERE id = ?
         AND assigned_to = ?
         AND status = 'assigned'
         AND sequence_completed_steps = ?`
    ).bind(totalSteps, totalSteps, orderId, user.id, completedSteps).run()

    if (updateResult.meta.changes !== 1) return jsonResponse(null, ERR.ORDER_STATE_CHANGED)

    await writeOrderLog(c.env.MScPJ_DB, {
      orderId,
      action: "scan",
      operatorId: user.id,
      result: "sequence_step_completed",
      stepIndex: nextStepIndex,
      scanUidHex: uidHex,
      expectedUidHex: uidHex,
      locationCode: nextStep.location_code ?? null,
      displayName: nextStep.display_name ?? null,
      details: {
        orderType,
        rawText,
        ndefText
      }
    })

    await writeOrderLog(c.env.MScPJ_DB, {
      orderId,
      action: "completed",
      operatorId: user.id,
      result: "sequence_completed",
      stepIndex: nextStepIndex,
      scanUidHex: uidHex,
      expectedUidHex: uidHex,
      locationCode: nextStep.location_code ?? null,
      displayName: nextStep.display_name ?? null,
      details: {
        orderType
      }
    })

    return jsonResponse({
      ...INFO.ORDER_SCAN_SUCCESS,
      data: {
        orderId,
        orderType,
        matched: true,
        completed: true,
        scannedUidHex: uidHex,
        completedStepIndex: nextStepIndex,
        sequenceTotalSteps: totalSteps,
        sequenceCompletedSteps: totalSteps,
        nextStepIndex: null,
        nextExpectedUidHex: null,
        nextLocationCode: null,
        nextDisplayName: null
      }
    })
  }

  const nextUpcomingStep = steps.find((step) => Number(step.step_index) === nextStepIndex + 1) ?? null

  const updateResult = await c.env.MScPJ_DB.prepare(
    `UPDATE orders
     SET updated_at = datetime('now'),
         sequence_total_steps = ?,
         sequence_completed_steps = ?
     WHERE id = ?
       AND assigned_to = ?
       AND status = 'assigned'
       AND sequence_completed_steps = ?`
  ).bind(totalSteps, nextStepIndex, orderId, user.id, completedSteps).run()

  if (updateResult.meta.changes !== 1) return jsonResponse(null, ERR.ORDER_STATE_CHANGED)

  await writeOrderLog(c.env.MScPJ_DB, {
    orderId,
    action: "scan",
    operatorId: user.id,
    result: "sequence_step_completed",
    stepIndex: nextStepIndex,
    scanUidHex: uidHex,
    expectedUidHex: uidHex,
    locationCode: nextStep.location_code ?? null,
    displayName: nextStep.display_name ?? null,
    details: {
      orderType,
      rawText,
      ndefText
    }
  })

  return jsonResponse({
    ...INFO.ORDER_SCAN_SUCCESS,
    data: {
      orderId,
      orderType,
      matched: true,
      completed: false,
      scannedUidHex: uidHex,
      completedStepIndex: nextStepIndex,
      sequenceTotalSteps: totalSteps,
      sequenceCompletedSteps: nextStepIndex,
      nextStepIndex: nextUpcomingStep?.step_index ?? null,
      nextExpectedUidHex: nextUpcomingStep?.target_uid_hex ?? null,
      nextLocationCode: nextUpcomingStep?.location_code ?? null,
      nextDisplayName: nextUpcomingStep?.display_name ?? null
    }
  })
})
