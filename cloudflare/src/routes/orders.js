import { Hono } from "hono"
import { ERR } from "../utils/errors.js"
import { verifyToken } from "../utils/jwt.js"

export const orderRoutes = new Hono()

// 通过 NFC 标签查工单 Check orders via NFC tags
orderRoutes.get("/byTag/:tagId", async (c) => {
  const tagId = c.req.param("tagId")

  const order = await c.env.MScPJ_DB.prepare(
    "SELECT * FROM orders WHERE nfc_tag = ?"
  ).bind(tagId).first()

  return order
    ? c.json({ success: true, data: order })
    : c.json(ERR.ORDER_NOT_FOUND)
})


// 查询工单日志 Query work order logs (Admin only)
orderRoutes.get("/logs", async (c) => {
  const auth = c.req.header("Authorization")
  if (!auth)
    return c.json(ERR.NO_PERMISSION)

  const token = auth.replace("Bearer ", "")
  const payload = await verifyToken(token, c.env.JWT_SECRET)

  if (!payload || payload.role !== "admin")
    return c.json(ERR.NO_PERMISSION)

  const params = c.req.query()
  let conditions = []
  let values = []

  // orderId: "1,2,3"
  if (params.orderId) {
    const ids = params.orderId.split(",").map(i => i.trim())
    conditions.push(`order_id IN (${ids.map(() => "?").join(",")})`)
    values.push(...ids)
  }

  // status = created/assigned/completed
  if (params.status) {
    const acts = params.status.split(",").map(a => a.trim())
    conditions.push(`action IN (${acts.map(() => "?").join(",")})`)
    values.push(...acts)
  }

  // operator = user ids
  if (params.operator) {
    const ops = params.operator.split(",").map(i => i.trim())
    conditions.push(`operator_id IN (${ops.map(() => "?").join(",")})`)
    values.push(...ops)
  }

  // 时间区间 Time range
  if (params.before) {
    conditions.push("timestamp <= ?")
    values.push(params.before)
  }

  if (params.after) {
    conditions.push("timestamp >= ?")
    values.push(params.after)
  }

  if (params.from && params.to) {
    conditions.push("timestamp BETWEEN ? AND ?")
    values.push(params.from, params.to)
  }

  const where = conditions.length > 0 ? `WHERE ${conditions.join(" AND ")}` : ""
  const sql = `SELECT * FROM order_logs ${where} ORDER BY timestamp DESC`

  const rows = await c.env.MScPJ_DB.prepare(sql).bind(...values).all()

  return c.json({ success: true, data: rows.results })
})
