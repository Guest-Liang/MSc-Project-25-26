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

    return jsonResponse(order ?? { error: "Order does not exist" }, order ? 200 : 404)
  }

  return null
}
