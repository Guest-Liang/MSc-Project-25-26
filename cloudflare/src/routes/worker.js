import { Hono } from "hono"
import { ERR } from "../utils/errors.js"
import { verifyToken } from "../utils/jwt.js"
import { jsonResponse } from "../utils/response.js"

export const workerRoutes = new Hono()

workerRoutes.use("*", async (c, next) => {
  const auth = c.req.header("Authorization")
  if (!auth)
    return jsonResponse(null, ERR.TOKEN_MISSING)

  const token = auth.replace("Bearer ", "")
  const payload = await verifyToken(token, c.env.JWT_SECRET)

  if (!payload)
    return jsonResponse(null, ERR.TOKEN_INVALID)

  if (payload.role !== "worker")
    return jsonResponse(null, ERR.NO_PERMISSION)

  c.set("user", payload)

  await next()
})


// 查看工人自己的工单 View the worker's own work order
workerRoutes.get("/orders", async (c) => {
  const user = c.get("user")

  const list = await c.env.MScPJ_DB.prepare(
    "SELECT * FROM orders WHERE assigned_to = ?"
  ).bind(user.id).all()

  return c.json({ success: true, data: list.results })
})


// 设置工单完成状态 Set work order completion status
workerRoutes.post("/orders/complete", async (c) => {
  const user = c.get("user")
  const { orderId } = await c.req.json()

  const order = await c.env.MScPJ_DB.prepare(
    "SELECT * FROM orders WHERE id = ?"
  ).bind(orderId).first()

  if (!order)
    return jsonResponse(null, ERR.ORDER_NOT_FOUND)

  if (order.assigned_to !== user.id)
    return jsonResponse(null, ERR.ORDER_NOT_OWNED)

  if (order.status !== "assigned")
    return jsonResponse(null, ERR.ORDER_NOT_COMPLETABLE)

  await c.env.MScPJ_DB.prepare(
    "UPDATE orders SET status = 'completed', updated_at = datetime('now') WHERE id = ?"
  ).bind(orderId).run()

  await c.env.MScPJ_DB.prepare(
    "INSERT INTO order_logs (order_id, action, operator_id, timestamp) VALUES (?, 'completed', ?, datetime('now'))"
  ).bind(orderId, user.id).run()

  return jsonResponse({ completed: true })
})
