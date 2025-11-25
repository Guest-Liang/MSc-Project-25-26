import { jsonResponse } from "../utils/response.js"
import { verifyToken } from "../utils/jwt.js"

export async function orderRoutes(request, env) {
  const url = new URL(request.url)

  // 通过 NFC 标签 ID 查工单 Search orders by NFC ID
  if (url.pathname.startsWith("/orders/byTag/")) {
    const tagId = url.pathname.split("/").pop()

    const order = await env.MScPJ_DB.prepare(
      "SELECT * FROM orders WHERE nfc_tag = ?"
    ).bind(tagId).first()

    return order
      ? jsonResponse(order)
      : jsonResponse(null, { code: "ORDER_NOT_FOUND", message: "Order does not exist" }, 404)
  }

  // 查询工单日志 Query Order Logs
  if (url.pathname === "/admin/orderLogs" && request.method === "GET") {
    const auth = request.headers.get("Authorization")
    if (!auth)
      return jsonResponse(null, { code: "NO_PERMISSION", message: "Permission required" }, 403)

    const token = auth.replace("Bearer ", "")
    const payload = await verifyToken(token, env.JWT_SECRET)

    if (!payload || payload.role !== "admin")
      return jsonResponse(null, { code: "NO_PERMISSION", message: "No permission" }, 403)

    const params = url.searchParams

    let conditions = []
    let values = []

    // orderId: "1,2,3"
    if (params.get("orderId")) {
      const ids = params.get("orderId").split(",").map(i => i.trim())
      conditions.push(`order_id IN (${ids.map(() => "?").join(",")})`)
      values.push(...ids)
    }

    // action = created/assigned/completed
    if (params.get("status")) {
      const acts = params.get("status").split(",").map(a => a.trim())
      conditions.push(`action IN (${acts.map(() => "?").join(",")})`)
      values.push(...acts)
    }

    // operator = user ids
    if (params.get("operator")) {
      const ops = params.get("operator").split(",").map(i => i.trim())
      conditions.push(`operator_id IN (${ops.map(() => "?").join(",")})`)
      values.push(...ops)
    }

    if (params.get("before")) {
      conditions.push("timestamp <= ?")
      values.push(params.get("before"))
    }

    if (params.get("after")) {
      conditions.push("timestamp >= ?")
      values.push(params.get("after"))
    }

    if (params.get("from") && params.get("to")) {
      conditions.push("timestamp BETWEEN ? AND ?")
      values.push(params.get("from"), params.get("to"))
    }

    const where = conditions.length > 0 ? `WHERE ${conditions.join(" AND ")}` : ""

    const sql = `SELECT * FROM order_logs ${where} ORDER BY timestamp DESC`

    const rows = await env.MScPJ_DB.prepare(sql).bind(...values).all()

    return jsonResponse(rows.results)
  }


  return null
}
