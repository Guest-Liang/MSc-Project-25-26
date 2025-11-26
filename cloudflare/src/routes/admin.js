import { Hono } from "hono"
import { ERR } from "../utils/status.js"
import { verifyToken } from "../utils/jwt.js"
import { jsonResponse } from "../utils/response.js"

export const adminRoutes = new Hono()

// Admin 权限中间件 Admin Middleware
adminRoutes.use("*", async (c, next) => {
  const auth = c.req.header("Authorization")
  if (!auth)
    return jsonResponse(null, ERR.ADMIN_REQUIRED)

  const token = auth.replace("Bearer ", "")
  const payload = await verifyToken(token, c.env.JWT_SECRET)

  if (!payload || payload.role !== "admin")
    return jsonResponse(null, ERR.NO_PERMISSION)

  c.set("user", payload)

  await next()
})


// 创建工单 Create Orders
adminRoutes.post("/orders/create", async (c) => {
  const { title, description, tag } = await c.req.json()
  const user = c.get("user")

  const result = await c.env.MScPJ_DB.prepare(
    "INSERT INTO orders (title, description, nfc_tag, status, created_at) VALUES (?, ?, ?, 'created', datetime('now'))"
  ).bind(title, description, tag).run()

  const orderId = result.meta.last_row_id

  await c.env.MScPJ_DB.prepare(
    "INSERT INTO order_logs (order_id, action, operator_id, timestamp) VALUES (?, 'created', ?, datetime('now'))"
  ).bind(orderId, user.id).run()

  return jsonResponse({ orderId })
})


// 派工 Assign Orders
adminRoutes.post("/orders/assign", async (c) => {
  const { orderId, userId } = await c.req.json()
  const user = c.get("user")

  const worker = await c.env.MScPJ_DB.prepare(
    "SELECT id, role FROM users WHERE id = ?"
  ).bind(userId).first()

  if (!worker || worker.role !== "worker")
    return jsonResponse(null, ERR.WORKER_NOT_FOUND)

  await c.env.MScPJ_DB.prepare(
    "UPDATE orders SET assigned_to = ?, status = 'assigned', updated_at = datetime('now') WHERE id = ?"
  ).bind(userId, orderId).run()

  await c.env.MScPJ_DB.prepare(
    "INSERT INTO order_logs (order_id, action, operator_id, timestamp) VALUES (?, 'assigned', ?, datetime('now'))"
  ).bind(orderId, user.id).run()

  return jsonResponse({ assigned: true })
})
