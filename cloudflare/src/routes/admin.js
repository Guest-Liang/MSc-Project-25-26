import { Hono } from "hono"
import { requireAdmin } from "../middleware/auth.js"
import { ERR } from "../utils/status.js"
import { jsonResponse } from "../utils/response.js"

export const adminRoutes = new Hono()

// Admin 权限中间件 Admin Middleware
adminRoutes.use("*", requireAdmin)

// 创建工单 Create Orders
adminRoutes.post("/orders/create", async (c) => {
  const { title, description, tag } = await c.req.json()
  const user = c.get("user")

  const exists = await c.env.MScPJ_DB.prepare(
    "SELECT id FROM orders WHERE title = ?"
  ).bind(title).first()
  if (exists) return jsonResponse(null, ERR.ORDER_TITLE_EXISTS)

  const result = await c.env.MScPJ_DB.prepare(
    "INSERT INTO orders (title, description, nfc_tag, status, created_at, updated_at) VALUES (?, ?, ?, 'created', datetime('now'), datetime('now'))"
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
  if (!worker) return jsonResponse(null, ERR.WORKER_NOT_FOUND) // 未找到 Worker
  if (worker.role !== "worker") return jsonResponse(null, ERR.NOT_A_WORKER) // 该角色不是 Worker

  const order = await c.env.MScPJ_DB.prepare(
    "SELECT id, status FROM orders WHERE id = ?"
  ).bind(orderId).first()
  if (!order) return jsonResponse(null, ERR.ORDER_NOT_FOUND) // 工单未找到

  await c.env.MScPJ_DB.prepare(
    "UPDATE orders SET assigned_to = ?, status = 'assigned', updated_at = datetime('now') WHERE id = ?"
  ).bind(userId, orderId).run()

  await c.env.MScPJ_DB.prepare(
    "INSERT INTO order_logs (order_id, action, operator_id, timestamp) VALUES (?, 'assigned', ?, datetime('now'))"
  ).bind(orderId, user.id).run()

  return jsonResponse({ assigned: true })
})
