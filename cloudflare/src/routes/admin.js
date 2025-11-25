import { jsonResponse } from "../utils/response.js"
import { verifyToken } from "../utils/jwt.js"

export async function adminRoutes(request, env) {
  const url = new URL(request.url)

  // 身份验证 Authorization
  const auth = request.headers.get("Authorization")
  if (!auth) return null

  const token = auth.replace("Bearer ", "")
  const payload = await verifyToken(token, env.JWT_SECRET)

  if (!payload || payload.role !== "admin") return null

  // 创建工单 Create Orders
  if (url.pathname === "/admin/orders/create" && request.method === "POST") {
    const { title, description, tag } = await request.json()

    await env.MScPJ_DB.prepare(
      "INSERT INTO orders (title, description, nfc_tag, status, created_at) VALUES (?, ?, ?, 'created', datetime('now'))"
    ).bind(title, description, tag).run()

    return jsonResponse({ ok: true })
  }

  // 派工 Assign Orders
  if (url.pathname === "/admin/orders/assign" && request.method === "POST") {
    const { orderId, userId } = await request.json()

    await env.MScPJ_DB.prepare(
      "UPDATE orders SET assigned_to = ?, status = 'assigned', updated_at = datetime('now') WHERE id = ?"
    ).bind(userId, orderId).run()

    // 写入日志 Write logs
    await env.MScPJ_DB.prepare(
      "INSERT INTO order_logs (order_id, action, operator_id, timestamp) VALUES (?, 'assigned', ?, datetime('now'))"
    ).bind(orderId, payload.id).run()

    return jsonResponse({ ok: true })
  }

  return null
}
