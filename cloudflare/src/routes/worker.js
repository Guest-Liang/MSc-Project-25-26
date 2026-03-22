import { Hono } from "hono"
import { requireWorker } from "../middleware/auth.js"
import { ERR, INFO } from "../utils/status.js"
import { jsonResponse } from "../utils/response.js"
import {
  ORDER_TYPES,
  formatOrderLog,
  formatOrderRecord,
  getNextSequenceStep,
  getOrderById,
  getOrderSteps,
  normalizeUidHex,
  parsePositiveInteger,
  parseCsvParam,
  toOptionalTrimmedString,
  writeOrderLog
} from "../utils/order.js"

export const workerRoutes = new Hono()

workerRoutes.use("*", requireWorker)

async function enrichWorkerOrders(db, orders) {
  return Promise.all(
    orders.map(async (order) => {
      const orderType = order.order_type ?? ORDER_TYPES.STANDARD
      const nextStep = orderType === ORDER_TYPES.SEQUENCE && order.status !== "completed"
        ? await getNextSequenceStep(db, order.id, Number(order.sequence_completed_steps ?? 0) + 1)
        : null

      return formatOrderRecord(order, nextStep)
    })
  )
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

  const list = await c.env.MScPJ_DB.prepare(
    "SELECT * FROM orders WHERE assigned_to = ? ORDER BY updated_at DESC"
  ).bind(user.id).all()

  const formattedOrders = await enrichWorkerOrders(c.env.MScPJ_DB, list.results ?? [])

  return jsonResponse({
    ...INFO.SQL_QUERY_SUCCESS,
    data: formattedOrders
  })
})

workerRoutes.get("/history", async (c) => {
  const user = c.get("user")
  const params = c.req.query()
  const conditions = ["l.operator_id = ?"]
  const values = [user.id]

  if (params.startTime && params.endTime && params.startTime > params.endTime) {
    return jsonResponse(null, ERR.INVALID_TIME_RANGE)
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
    ORDER BY l.timestamp DESC
  `

  const rows = await c.env.MScPJ_DB.prepare(sql).bind(...values).all()

  return jsonResponse({
    ...INFO.SQL_QUERY_SUCCESS,
    data: (rows.results ?? []).map(formatOrderLog)
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

    await c.env.MScPJ_DB.prepare(
      "UPDATE orders SET status = 'completed', completed_at = datetime('now'), updated_at = datetime('now') WHERE id = ?"
    ).bind(orderId).run()

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

  const isFinalStep = nextStepIndex >= totalSteps

  if (isFinalStep) {
    await c.env.MScPJ_DB.prepare(
      `UPDATE orders
       SET status = 'completed',
           completed_at = datetime('now'),
           updated_at = datetime('now'),
           sequence_total_steps = ?,
           sequence_completed_steps = ?
       WHERE id = ?`
    ).bind(totalSteps, totalSteps, orderId).run()

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

  await c.env.MScPJ_DB.prepare(
    `UPDATE orders
     SET updated_at = datetime('now'),
         sequence_total_steps = ?,
         sequence_completed_steps = ?
     WHERE id = ?`
  ).bind(totalSteps, nextStepIndex, orderId).run()

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

workerRoutes.post("/orders/complete", async (c) => {
  const user = c.get("user")
  let orderId = null
  let rawOrderId = null

  try {
    const body = await c.req.json()
    rawOrderId = body?.orderId ?? null
    orderId = parsePositiveInteger(body?.orderId)
  } catch {
    rawOrderId = null
  }

  await writeOrderLog(c.env.MScPJ_DB, {
    orderId,
    action: "complete",
    operatorId: user.id,
    result: "deprecated_complete_api",
    details: {
      requestedOrderId: rawOrderId
    }
  })

  return jsonResponse(null, {
    ...ERR.DEPRECATED_COMPLETE_API,
    data: {
      orderId,
      recommendedEndpoint: "/worker/orders/scan"
    }
  })
})
