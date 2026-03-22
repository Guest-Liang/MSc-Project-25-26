import { Hono } from "hono"
import { requireAdmin } from "../middleware/auth.js"
import { jsonResponse } from "../utils/response.js"
import { ERR, INFO } from "../utils/status.js"
import {
  ORDER_TYPES,
  formatOrderLog,
  formatOrderRecord,
  formatOrderStep,
  getNextSequenceStep,
  getOrderById,
  getOrderSteps,
  normalizeUidHex,
  parseCsvParam,
  parsePositiveInteger,
  resolveOrderType
} from "../utils/order.js"

export const orderRoutes = new Hono()

function parsePositiveIntegerList(rawValue, error) {
  const values = parseCsvParam(rawValue)
  const parsed = values.map((value) => parsePositiveInteger(value))

  if (parsed.some((value) => value === null)) {
    return { error }
  }

  return { values: parsed }
}

function parseOrderTypeList(rawValue) {
  const values = parseCsvParam(rawValue)
  const parsed = values.map((value) => resolveOrderType(value))

  if (parsed.some((value) => value === null)) {
    return { error: ERR.INVALID_ORDER_TYPE }
  }

  return { values: parsed }
}

function appendOrderTypeCondition(column, orderTypes, conditions, values) {
  if (orderTypes.length === 0) return

  const uniqueOrderTypes = [...new Set(orderTypes)]
  const includesStandard = uniqueOrderTypes.includes(ORDER_TYPES.STANDARD)
  const orConditions = []

  if (uniqueOrderTypes.length > 0) {
    orConditions.push(`${column} IN (${uniqueOrderTypes.map(() => "?").join(",")})`)
    values.push(...uniqueOrderTypes)
  }

  if (includesStandard) {
    orConditions.push(`${column} IS NULL`)
  }

  conditions.push(`(${orConditions.join(" OR ")})`)
}

function parseUidHexList(rawValue) {
  const values = parseCsvParam(rawValue)
  const parsed = values.map((value) => normalizeUidHex(value))

  if (parsed.some((value) => value === null)) {
    return { error: ERR.INVALID_UID_HEX }
  }

  return { values: parsed }
}

function appendUidHexLogCondition(uidHexValues, conditions, values) {
  if (uidHexValues.length === 0) return

  conditions.push(
    `(${uidHexValues.map(() => "(l.scan_uid_hex = ? OR l.expected_uid_hex = ?)").join(" OR ")})`
  )

  uidHexValues.forEach((uidHex) => {
    values.push(uidHex, uidHex)
  })
}

async function enrichOrdersWithNextSteps(db, orders) {
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

orderRoutes.get("/steps", requireAdmin, async (c) => {
  const orderId = parsePositiveInteger(c.req.query("orderId"))
  if (!orderId) return jsonResponse(null, ERR.INVALID_ORDER_ID)

  const order = await getOrderById(c.env.MScPJ_DB, orderId)
  if (!order) return jsonResponse(null, ERR.ORDER_NOT_FOUND)

  const steps = (order.order_type ?? ORDER_TYPES.STANDARD) === ORDER_TYPES.SEQUENCE
    ? await getOrderSteps(c.env.MScPJ_DB, orderId)
    : []

  return jsonResponse({
    ...INFO.SQL_QUERY_SUCCESS,
    data: {
      orderId,
      orderType: order.order_type ?? ORDER_TYPES.STANDARD,
      steps: steps.map(formatOrderStep)
    }
  })
})

orderRoutes.get("/analysis/summary", requireAdmin, async (c) => {
  const totalOrders = await c.env.MScPJ_DB.prepare(
    `SELECT
      COUNT(*) AS total_orders,
      SUM(CASE WHEN status = 'completed' THEN 1 ELSE 0 END) AS completed_orders,
      SUM(CASE WHEN order_type = 'standard' OR order_type IS NULL THEN 1 ELSE 0 END) AS standard_orders,
      SUM(CASE WHEN order_type = 'sequence' THEN 1 ELSE 0 END) AS sequence_orders
    FROM orders`
  ).first()

  const scanSummary = await c.env.MScPJ_DB.prepare(
    `SELECT
      SUM(CASE WHEN action = 'scan' THEN 1 ELSE 0 END) AS total_scan_evidence_count,
      SUM(CASE WHEN action = 'scan' AND result = 'standard_matched' THEN 1 ELSE 0 END) AS standard_matched_count,
      SUM(CASE WHEN action = 'scan' AND result = 'sequence_step_completed' THEN 1 ELSE 0 END) AS sequence_step_completed_count,
      SUM(CASE WHEN action = 'scan' AND result = 'mismatch' THEN 1 ELSE 0 END) AS mismatch_count,
      SUM(CASE WHEN action = 'scan' AND result = 'out_of_order' THEN 1 ELSE 0 END) AS out_of_order_count,
      SUM(CASE WHEN action = 'scan' AND result = 'duplicate' THEN 1 ELSE 0 END) AS duplicate_count,
      COUNT(DISTINCT CASE WHEN action = 'scan' AND result IN ('standard_matched', 'sequence_step_completed') THEN scan_uid_hex END) AS unique_visited_uid_count
    FROM order_logs`
  ).first()

  const workerStats = await c.env.MScPJ_DB.prepare(
    `SELECT
      u.id,
      u.username,
      COALESCE(active.current_assigned_order_count, 0) AS current_assigned_order_count,
      COALESCE(stats.total_scan_count, 0) AS total_scan_count,
      COALESCE(stats.successful_scan_count, 0) AS successful_scan_count,
      COALESCE(stats.mismatch_count, 0) AS mismatch_count,
      COALESCE(stats.out_of_order_count, 0) AS out_of_order_count,
      COALESCE(stats.duplicate_count, 0) AS duplicate_count,
      COALESCE(stats.completed_order_count, 0) AS completed_order_count,
      COALESCE(stats.unique_visited_uid_count, 0) AS unique_visited_uid_count,
      stats.last_scan_at
    FROM users u
    LEFT JOIN (
      SELECT
        assigned_to,
        COUNT(*) AS current_assigned_order_count
      FROM orders
      WHERE assigned_to IS NOT NULL AND status <> 'completed'
      GROUP BY assigned_to
    ) active ON active.assigned_to = u.id
    LEFT JOIN (
      SELECT
        operator_id,
        SUM(CASE WHEN action = 'scan' THEN 1 ELSE 0 END) AS total_scan_count,
        SUM(CASE WHEN action = 'scan' AND result IN ('standard_matched', 'sequence_step_completed') THEN 1 ELSE 0 END) AS successful_scan_count,
        SUM(CASE WHEN action = 'scan' AND result = 'mismatch' THEN 1 ELSE 0 END) AS mismatch_count,
        SUM(CASE WHEN action = 'scan' AND result = 'out_of_order' THEN 1 ELSE 0 END) AS out_of_order_count,
        SUM(CASE WHEN action = 'scan' AND result = 'duplicate' THEN 1 ELSE 0 END) AS duplicate_count,
        SUM(CASE WHEN action = 'completed' AND result IN ('standard_completed', 'sequence_completed') THEN 1 ELSE 0 END) AS completed_order_count,
        COUNT(DISTINCT CASE WHEN action = 'scan' AND result IN ('standard_matched', 'sequence_step_completed') THEN scan_uid_hex END) AS unique_visited_uid_count,
        MAX(CASE WHEN action = 'scan' THEN timestamp ELSE NULL END) AS last_scan_at
      FROM order_logs
      GROUP BY operator_id
    ) stats ON stats.operator_id = u.id
    WHERE u.role = 'worker'
    ORDER BY u.created_at DESC`
  ).all()

  return jsonResponse({
    ...INFO.SQL_QUERY_SUCCESS,
    data: {
      totals: {
        totalOrders: Number(totalOrders?.total_orders ?? 0),
        completedOrders: Number(totalOrders?.completed_orders ?? 0),
        standardOrders: Number(totalOrders?.standard_orders ?? 0),
        sequenceOrders: Number(totalOrders?.sequence_orders ?? 0)
      },
      scans: {
        totalScanEvidenceCount: Number(scanSummary?.total_scan_evidence_count ?? 0),
        standardMatchedCount: Number(scanSummary?.standard_matched_count ?? 0),
        sequenceStepCompletedCount: Number(scanSummary?.sequence_step_completed_count ?? 0),
        mismatchCount: Number(scanSummary?.mismatch_count ?? 0),
        outOfOrderCount: Number(scanSummary?.out_of_order_count ?? 0),
        duplicateCount: Number(scanSummary?.duplicate_count ?? 0),
        uniqueVisitedUidCount: Number(scanSummary?.unique_visited_uid_count ?? 0)
      },
      workers: (workerStats.results ?? []).map((row) => ({
        workerId: row.id,
        username: row.username,
        currentAssignedOrderCount: Number(row.current_assigned_order_count ?? 0),
        totalScanCount: Number(row.total_scan_count ?? 0),
        successfulScanCount: Number(row.successful_scan_count ?? 0),
        mismatchCount: Number(row.mismatch_count ?? 0),
        outOfOrderCount: Number(row.out_of_order_count ?? 0),
        duplicateCount: Number(row.duplicate_count ?? 0),
        completedOrderCount: Number(row.completed_order_count ?? 0),
        uniqueVisitedUidCount: Number(row.unique_visited_uid_count ?? 0),
        lastScanAt: row.last_scan_at ?? null
      }))
    }
  })
})

orderRoutes.get("/logs", requireAdmin, async (c) => {
  const params = c.req.query()
  const conditions = []
  const values = []

  if (params.startTime && params.endTime && params.startTime > params.endTime) {
    return jsonResponse(null, ERR.INVALID_TIME_RANGE)
  }

  if (params.orderId) {
    const { values: orderIds, error } = parsePositiveIntegerList(params.orderId, ERR.INVALID_ORDER_ID)
    if (error) return jsonResponse(null, error)
    if (orderIds.length > 0) {
      conditions.push(`l.order_id IN (${orderIds.map(() => "?").join(",")})`)
      values.push(...orderIds)
    }
  }

  const actionSource = params.action ?? params.status ?? null
  const actions = parseCsvParam(actionSource)
  if (actions.length > 0) {
    conditions.push(`l.action IN (${actions.map(() => "?").join(",")})`)
    values.push(...actions)
  }

  const results = parseCsvParam(params.result)
  if (results.length > 0) {
    conditions.push(`l.result IN (${results.map(() => "?").join(",")})`)
    values.push(...results)
  }

  const workerSource = params.workerId ?? params.operator ?? null
  if (workerSource) {
    const { values: workerIds, error } = parsePositiveIntegerList(workerSource, ERR.INVALID_WORKER_ID)
    if (error) return jsonResponse(null, error)
    if (workerIds.length > 0) {
      conditions.push(`l.operator_id IN (${workerIds.map(() => "?").join(",")})`)
      values.push(...workerIds)
    }
  }

  if (params.uidHex) {
    const { values: uidHexValues, error } = parseUidHexList(params.uidHex)
    if (error) return jsonResponse(null, error)

    appendUidHexLogCondition(uidHexValues, conditions, values)
  }

  if (params.orderType) {
    const { values: orderTypes, error } = parseOrderTypeList(params.orderType)
    if (error) return jsonResponse(null, error)
    appendOrderTypeCondition("o.order_type", orderTypes, conditions, values)
  }

  if (params.startTime) {
    conditions.push("l.timestamp >= ?")
    values.push(params.startTime)
  }

  if (params.endTime) {
    conditions.push("l.timestamp <= ?")
    values.push(params.endTime)
  }

  const where = conditions.length > 0 ? `WHERE ${conditions.join(" AND ")}` : ""
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

orderRoutes.get("/search", requireAdmin, async (c) => {
  const params = c.req.query()
  const conditions = []
  const values = []

  if (params.createdStart && params.createdEnd && params.createdStart > params.createdEnd) {
    return jsonResponse(null, ERR.INVALID_TIME_RANGE)
  }

  if (params.updatedStart && params.updatedEnd && params.updatedStart > params.updatedEnd) {
    return jsonResponse(null, ERR.INVALID_TIME_RANGE)
  }

  if (params.title) {
    conditions.push("title LIKE ?")
    values.push(`%${params.title}%`)
  }

  if (params.description) {
    conditions.push("description LIKE ?")
    values.push(`%${params.description}%`)
  }

  if (params.targetUidHex) {
    const { values: targetUidHexValues, error } = parseUidHexList(params.targetUidHex)
    if (error) return jsonResponse(null, error)

    if (targetUidHexValues.length > 0) {
      conditions.push(`target_uid_hex IN (${targetUidHexValues.map(() => "?").join(",")})`)
      values.push(...targetUidHexValues)
    }
  } else if (params.nfc_tag) {
    conditions.push("nfc_tag LIKE ?")
    values.push(`%${params.nfc_tag}%`)
  }

  if (params.orderType) {
    const { values: orderTypes, error } = parseOrderTypeList(params.orderType)
    if (error) return jsonResponse(null, error)
    appendOrderTypeCondition("order_type", orderTypes, conditions, values)
  }

  if (params.status) {
    const statuses = parseCsvParam(params.status)
    if (statuses.length > 0) {
      conditions.push(`status IN (${statuses.map(() => "?").join(",")})`)
      values.push(...statuses)
    }
  }

  if (params.assigned) {
    const assignedValues = parseCsvParam(params.assigned)
    const hasNull = assignedValues.includes("NULL")

    if (hasNull && assignedValues.length > 1) {
      return jsonResponse(null, ERR.INVALID_ASSIGNED_FILTER)
    }

    if (hasNull) {
      conditions.push("assigned_to IS NULL")
    } else {
      const parsedAssignedValues = assignedValues.map((value) => parsePositiveInteger(value))
      if (parsedAssignedValues.some((value) => value === null)) {
        return jsonResponse(null, ERR.INVALID_ASSIGNED_FILTER)
      }

      if (parsedAssignedValues.length > 0) {
        conditions.push(`assigned_to IN (${parsedAssignedValues.map(() => "?").join(",")})`)
        values.push(...parsedAssignedValues)
      }
    }
  }

  if (params.progress) {
    const progressFilters = parseCsvParam(params.progress)
    const allowedProgressValues = new Set(["not_started", "in_progress", "completed"])

    if (progressFilters.some((value) => !allowedProgressValues.has(value))) {
      return jsonResponse(null, ERR.INVALID_PROGRESS_FILTER)
    }

    const progressConditions = progressFilters.map((value) => {
      if (value === "completed") return "status = 'completed'"
      if (value === "in_progress") return "(status <> 'completed' AND order_type = 'sequence' AND sequence_completed_steps > 0)"
      return "(status <> 'completed' AND (order_type <> 'sequence' OR order_type IS NULL OR sequence_completed_steps = 0))"
    })

    if (progressConditions.length > 0) {
      conditions.push(`(${progressConditions.join(" OR ")})`)
    }
  }

  if (params.createdStart) {
    conditions.push("created_at >= ?")
    values.push(params.createdStart)
  }

  if (params.createdEnd) {
    conditions.push("created_at <= ?")
    values.push(params.createdEnd)
  }

  if (params.updatedStart) {
    conditions.push("updated_at >= ?")
    values.push(params.updatedStart)
  }

  if (params.updatedEnd) {
    conditions.push("updated_at <= ?")
    values.push(params.updatedEnd)
  }

  const where = conditions.length > 0 ? `WHERE ${conditions.join(" AND ")}` : ""
  const sql = `SELECT * FROM orders ${where} ORDER BY id DESC`

  const rows = await c.env.MScPJ_DB.prepare(sql).bind(...values).all()
  const formattedOrders = await enrichOrdersWithNextSteps(c.env.MScPJ_DB, rows.results ?? [])

  return jsonResponse({
    ...INFO.SQL_QUERY_SUCCESS,
    data: formattedOrders
  })
})
