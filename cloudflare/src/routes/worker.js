import { Hono } from "hono"
import { requireWorker } from "../middleware/auth.js"
import { ERR, INFO } from "../utils/status.js"
import { jsonResponse } from "../utils/response.js"

export const workerRoutes = new Hono()

// Worker 权限中间件
workerRoutes.use("*", requireWorker)

// 查看工人自己的工单 View the worker's own work order
workerRoutes.get("/orders", async (c) => {
  const user = c.get("user")

  const list = await c.env.MScPJ_DB.prepare(
    "SELECT * FROM orders WHERE assigned_to = ? ORDER BY updated_at DESC"
  ).bind(user.id).all()

  return jsonResponse({
    ...INFO.SQL_QUERY_SUCCESS,
    data: list.results
  })
})

// 设置工单完成状态 Set work order completion status
workerRoutes.post("/orders/complete", async (c) => {
  const user = c.get("user")
  const { orderId } = await c.req.json()

  const order = await c.env.MScPJ_DB.prepare(
    "SELECT * FROM orders WHERE id = ?"
  ).bind(orderId).first()

  if (!order) return jsonResponse(null, ERR.ORDER_NOT_FOUND)

  if (order.assigned_to !== user.id) return jsonResponse(null, ERR.ORDER_NOT_OWNED)

  if (order.status !== "assigned") return jsonResponse(null, ERR.ORDER_NOT_COMPLETABLE)

  await c.env.MScPJ_DB.prepare(
    "UPDATE orders SET status = 'completed', updated_at = datetime('now') WHERE id = ?"
  ).bind(orderId).run()

  await c.env.MScPJ_DB.prepare(
    "INSERT INTO order_logs (order_id, action, operator_id, timestamp) VALUES (?, 'completed', ?, datetime('now'))"
  ).bind(orderId, user.id).run()

  return jsonResponse(INFO.ORDER_SET_STATUS_SUCCESS)
})
