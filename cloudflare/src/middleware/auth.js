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
      "SELECT id, username, role FROM users WHERE id = ? AND token = ?"
    ).bind(payload.id, token).first()

    if (!dbUser) return jsonResponse(null, ERR.TOKEN_REVOKED)

    if (role && dbUser.role !== role) return jsonResponse(null, ERR.NO_PERMISSION)

    c.set("user", {
      id: dbUser.id,
      username: dbUser.username,
      role: dbUser.role
    })

    await next()
  }
}

export const requireAdmin = requireAuth("admin")

export const requireWorker = requireAuth("worker")
