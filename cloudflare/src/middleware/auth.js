import { ERR } from "../utils/status.js"
import { verifyToken } from "../utils/jwt.js"
import { jsonResponse } from "../utils/response.js"

export function requireAuth(role = null) {
  return async (c, next) => {
    const auth = c.req.header("Authorization")
    if (!auth) return jsonResponse(null, ERR.TOKEN_MISSING)

    const token = auth.replace("Bearer ", "")
    const payload = await verifyToken(token, c.env.JWT_SECRET)

    if (!payload) return jsonResponse(null, ERR.TOKEN_INVALID)

    const dbUser = await c.env.MScPJ_DB.prepare(
      "SELECT token FROM users WHERE id = ?"
    ).bind(payload.id).first()

    if (!dbUser || dbUser.token !== token) return jsonResponse(null, ERR.TOKEN_REVOKED)

    if (role && payload.role !== role) return jsonResponse(null, ERR.NO_PERMISSION)

    c.set("user", payload)

    await next()
  }
}

export const requireAdmin = requireAuth("admin")

export const requireWorker = requireAuth("worker")
