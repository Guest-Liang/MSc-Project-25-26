import { Hono } from "hono"
import { requireAdmin } from "../middleware/auth.js"
import { jsonResponse } from "../utils/response.js"

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

  return jsonResponse(rows.results)
})
