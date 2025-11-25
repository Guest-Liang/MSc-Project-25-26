import { jsonResponse } from "../utils/response.js"
import { hashPassword, verifyPassword } from "../utils/bcrypt.js"
import { sign } from "../utils/jwt.js"

export async function authRoutes(request, env) {
  const url = new URL(request.url)

  // 登录 Login
  if (url.pathname === "/auth/login" && request.method === "POST") {
    const { username, password } = await request.json()

    const user = await env.MScPJ_DB.prepare(
      "SELECT * FROM users WHERE username = ?"
    ).bind(username).first()

    if (!user) return jsonResponse({ error: "User not exist" }, 400)

    if (!await verifyPassword(password, user.password_hash))
      return jsonResponse({ error: "Wrong Password" }, 401)

    const token = await sign({ id: user.id, role: user.role }, env.JWT_SECRET)
    return jsonResponse({ token, role: user.role })
  }

  // 注册管理员（用于初始化） Register Admin for Initialization
  if (url.pathname === "/auth/register-admin" && request.method === "POST") {
    const { username, password } = await request.json()
    const hash = await hashPassword(password)

    await env.MScPJ_DB.prepare(
      "INSERT INTO users (username, password_hash, role, created_at) VALUES (?, ?, 'admin', datetime('now'))"
    ).bind(username, hash).run()

    return jsonResponse({ ok: true })
  }

  // 注册工人 Register Worker (Admin only)
  if (url.pathname === "/auth/register-worker" && request.method === "POST") {
    const auth = request.headers.get("Authorization")
    if (!auth) return jsonResponse({ error: "Admin Permissions needed" }, 403)

    const token = auth.replace("Bearer ", "")
    const payload = await verifyToken(token, env.JWT_SECRET)
    if (!payload || payload.role !== "admin")
      return jsonResponse({ error: "No permission" }, 403)

    const { username, password } = await request.json()
    const hash = await hashPassword(password)

    await env.MScPJ_DB.prepare(
      "INSERT INTO users (username, password_hash, role, created_at) VALUES (?, ?, 'worker', datetime('now'))"
    ).bind(username, hash).run()

    return jsonResponse({ ok: true })
  }

  return null
}
