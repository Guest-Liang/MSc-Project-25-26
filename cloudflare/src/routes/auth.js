import { Hono } from "hono"
import { ERR } from "../utils/errors.js"
import { hashPassword, verifyPassword } from "../utils/bcrypt.js"
import { sign, verifyToken } from "../utils/jwt.js"
import { jsonResponse } from "../utils/response.js"

export const authRoutes = new Hono()

// 登录 Login
authRoutes.post("/login", async (c) => {
  const { username, password } = await c.req.json()

  const user = await c.env.MScPJ_DB.prepare(
    "SELECT * FROM users WHERE username = ?"
  ).bind(username).first()

  if (!user)
    return jsonResponse(null, ERR.USER_NOT_EXIST)

  if (!await verifyPassword(password, user.password_hash))
    return jsonResponse(null, ERR.WRONG_PASSWORD)

  const token = await sign({ id: user.id, role: user.role }, c.env.JWT_SECRET)
  return jsonResponse({ token, role: user.role })
})


// 注册管理员 Register Admin (Initialization)
authRoutes.post("/register-admin", async (c) => {
  const { username, password } = await c.req.json()
  const hash = await hashPassword(password)

  const existing = await c.env.MScPJ_DB.prepare(
    "SELECT password_hash FROM users WHERE username = ?"
  ).bind(username).first()

  if (existing) {
    const same = await verifyPassword(password, existing.password_hash)
    if (same)
      return jsonResponse(null, ERR.USER_ALREADY_EXISTS)

    const newHash = await hashPassword(password)
    await c.env.MScPJ_DB.prepare(
      "UPDATE users SET password_hash = ?, updated_at = datetime('now') WHERE username = ?"
    ).bind(newHash, username).run()

    return jsonResponse(null, ERR.PASSWORD_UPDATED)
  }

  await c.env.MScPJ_DB.prepare(
    "INSERT INTO users (username, password_hash, role, created_at) VALUES (?, ?, 'admin', datetime('now'))"
  ).bind(username, hash).run()

  return jsonResponse({ created: true })
})


// 注册工人 Register Worker (Admin only)
authRoutes.post("/register-worker", async (c) => {
  const auth = c.req.header("Authorization")
  if (!auth)
    return jsonResponse(null, ERR.ADMIN_REQUIRED)

  const token = auth.replace("Bearer ", "")
  const payload = await verifyToken(token, c.env.JWT_SECRET)

  if (!payload || payload.role !== "admin")
    return jsonResponse(null, ERR.NO_PERMISSION)

  const { username, password } = await c.req.json()
  const hash = await hashPassword(password)

  const existing = await c.env.MScPJ_DB.prepare(
    "SELECT password_hash FROM users WHERE username = ?"
  ).bind(username).first()

  if (existing) {
    const same = await verifyPassword(password, existing.password_hash)
    if (same)
      return jsonResponse(null, ERR.USER_ALREADY_EXISTS)

    const newHash = await hashPassword(password)
    await c.env.MScPJ_DB.prepare(
      "UPDATE users SET password_hash = ?, updated_at = datetime('now') WHERE username = ?"
    ).bind(newHash, username).run()

    return jsonResponse(null, ERR.PASSWORD_UPDATED)
  }


  await c.env.MScPJ_DB.prepare(
    "INSERT INTO users (username, password_hash, role, created_at) VALUES (?, ?, 'worker', datetime('now'))"
  ).bind(username, hash).run()

  return jsonResponse({ created: true })
})
