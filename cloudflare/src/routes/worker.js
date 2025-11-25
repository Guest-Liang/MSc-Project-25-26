import { jsonResponse } from "../utils/response.js"
import { verifyToken } from "../utils/jwt.js"

export async function workerRoutes(request, env) {
  const url = new URL(request.url)

  const auth = request.headers.get("Authorization")
  if (!auth) return null

  const token = auth.replace("Bearer ", "")
  const payload = await verifyToken(token, env.JWT_SECRET)

  if (!payload || payload.role !== "worker") return null

  // 查看自己的工单 See their orders
  if (url.pathname === "/worker/orders" && request.method === "GET") {
    const list = await env.MScPJ_DB.prepare(
      "SELECT * FROM orders WHERE assigned_to = ?"
    ).bind(payload.id).all()

    return jsonResponse(list.results)
  }

  // 完成工单 Complete Orders
  if (url.pathname === "/worker/orders/complete" && request.method === "POST") {
    const { orderId } = await request.json()

    await env.MScPJ_DB.prepare(
      "UPDATE orders SET status = 'completed', updated_at = datetime('now') WHERE id = ?"
    ).bind(orderId).run()

    await env.MScPJ_DB.prepare(
      "INSERT INTO order_logs (order_id, action, operator_id, timestamp) VALUES (?, 'completed', ?, datetime('now'))"
    ).bind(orderId, payload.id).run()

    return jsonResponse({ completed: true })
  }

  return null
}
