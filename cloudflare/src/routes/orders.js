import { Hono } from "hono"
import { requireAdmin } from "../middleware/auth.js"
import { jsonResponse } from "../utils/response.js"
import { ERR, INFO } from "../utils/status.js"

export const orderRoutes = new Hono()

// // 通过 NFC 标签查工单 Check orders via NFC tags
// orderRoutes.get("/byTag/:tagId", async (c) => {
//   const tagId = c.req.param("tagId")

//   const order = await c.env.MScPJ_DB.prepare(
//     "SELECT * FROM orders WHERE nfc_tag = ?"
//   ).bind(tagId).first()

//   return order
//     ? jsonResponse(order)
//     : jsonResponse(null, ERR.ORDER_NOT_FOUND)
// })


// 查询工单日志 Query work order logs (Admin only)
orderRoutes.get("/logs", requireAdmin, async (c) => {
  const params = c.req.query()
  let conditions = []
  let values = []

  // 检查时间范围 Precheck time range
  if (params.startTime && params.endTime && params.startTime > params.endTime)
    return jsonResponse(null, ERR.INVALID_TIME_RANGE)

  // orderId: "1,2,3"
  if (params.orderId) {
    const ids = params.orderId.split(",").map(i => i.trim()).filter(i => i.length > 0)
    if (ids.length > 0) {
      conditions.push(`order_id IN (${ids.map(() => "?").join(",")})`)
      values.push(...ids)
    }
  }

  // status = created/assigned/completed
  if (params.status) {
    const acts = params.status.split(",").map(a => a.trim()).filter(a => a.length > 0)
    if (acts.length > 0) {
      conditions.push(`action IN (${acts.map(() => "?").join(",")})`)
      values.push(...acts)
    }
  }

  // operator = user ids
  if (params.operator) {
    const ops = params.operator.split(",").map(i => i.trim()).filter(i => i.length > 0)
    if (ops.length > 0) {
      conditions.push(`operator_id IN (${ops.map(() => "?").join(",")})`)
      values.push(...ops)
    }
  }

  // 时间区间 Time range
  if (params.startTime) {
    conditions.push("timestamp >= ?")
    values.push(params.startTime)
  }

  if (params.endTime) {
    conditions.push("timestamp <= ?")
    values.push(params.endTime)
  }

  const where = conditions.length > 0 ? `WHERE ${conditions.join(" AND ")}` : ""
  const sql = `SELECT * FROM order_logs ${where} ORDER BY timestamp DESC`

  const rows = await c.env.MScPJ_DB.prepare(sql).bind(...values).all()

  return jsonResponse({
    ...INFO.SQL_QUERY_SUCCESS,
    data: rows.results
  })
})

// 查询工单（支持多条件筛选） Search Orders
orderRoutes.get("/search", requireAdmin, async (c) => {
  const params = c.req.query()
  let conditions = []
  let values = []

  // 检查时间范围 Precheck time range
  if (params.createdStart && params.createdEnd && params.createdStart > params.createdEnd) {
    return jsonResponse(null, ERR.INVALID_TIME_RANGE)
  }
  if (params.updatedStart && params.updatedEnd && params.updatedStart > params.updatedEnd) {
    return jsonResponse(null, ERR.INVALID_TIME_RANGE)
  }

  // 模糊匹配字段 Fuzzy matching fields
  if (params.title) {
    conditions.push("title LIKE ?")
    values.push(`%${params.title}%`)
  }

  if (params.description) {
    conditions.push("description LIKE ?")
    values.push(`%${params.description}%`)
  }

  if (params.nfc_tag) {
    conditions.push("nfc_tag LIKE ?")
    values.push(`%${params.nfc_tag}%`)
  }

  // status 精确匹配多个 exact match (multiple)
  if (params.status) {
    const sts = params.status.split(",").map(s => s.trim()).filter(s => s.length > 0)
    if (sts.length > 0) {
      conditions.push(`status IN (${sts.map(() => "?").join(",")})`)
      values.push(...sts)
    }
  }

  // assigned_to 精确匹配多个 exact match (multiple)
  if (params.assigned) {
    if (params.assigned === "NULL") {
      conditions.push("assigned_to IS NULL")
    } else {
      const ids = params.assigned.split(",").map(i => i.trim()).filter(i => i.length > 0)
      if (ids.length > 0) {
        conditions.push(`assigned_to IN (${ids.map(() => "?").join(",")})`)
        values.push(...ids)
      }
    }
  }

  // created_at 时间范围 Time range
  if (params.createdStart) {
    conditions.push("created_at >= ?")
    values.push(params.createdStart)
  }
  if (params.createdEnd) {
    conditions.push("created_at <= ?")
    values.push(params.createdEnd)
  }

  // updated_at 时间范围 Time range
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
  return jsonResponse({
    ...INFO.SQL_QUERY_SUCCESS,
    data: rows.results
  })
})
