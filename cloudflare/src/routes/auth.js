import { Hono } from "hono"
import { requireAuth, requireAdmin } from "../middleware/auth.js"
import { ERR, INFO } from "../utils/status.js"
import { hashPassword, verifyPassword } from "../utils/bcrypt.js"
import { sign, verifyToken } from "../utils/jwt.js"
import { jsonResponse } from "../utils/response.js"

export const authRoutes = new Hono()

async function registerUser(c, role) {
  const { username, password } = await c.req.json()

  const existing = await c.env.MScPJ_DB.prepare(
    "SELECT id FROM users WHERE username = ?"
  ).bind(username).first()

  if (existing) return jsonResponse(null, ERR.USER_ALREADY_EXISTS)

  const hash = await hashPassword(password)

  await c.env.MScPJ_DB.prepare(
    "INSERT INTO users (username, password_hash, role, created_at, updated_at) VALUES (?, ?, ?, datetime('now'), datetime('now'))"
  ).bind(username, hash, role).run()

  return jsonResponse(
    role === "admin" ? INFO.ADMIN_CREATED_SUCCESS : INFO.WORKER_CREATED_SUCCESS
  )
}

async function resetUserPassword(c, role) {
  const { username, password } = await c.req.json()

  const existing = await c.env.MScPJ_DB.prepare(
    "SELECT id, role FROM users WHERE username = ?"
  ).bind(username).first()

  if (!existing) return jsonResponse(null, ERR.USER_NOT_EXIST)

  if (existing.role !== role) {
    return jsonResponse(null, role === "admin" ? ERR.NOT_AN_ADMIN : ERR.NOT_A_WORKER)
  }

  const hash = await hashPassword(password)

  await c.env.MScPJ_DB.prepare(
    "UPDATE users SET password_hash = ?, token = NULL, updated_at = datetime('now') WHERE id = ?"
  ).bind(hash, existing.id).run()

  return jsonResponse(INFO.PASSWORD_UPDATED)
}

// 登录 Login
authRoutes.post("/login", async (c) => {
  const { username, password } = await c.req.json()

  const user = await c.env.MScPJ_DB.prepare(
    "SELECT * FROM users WHERE username = ?"
  ).bind(username).first()

  if (!user) return jsonResponse(null, ERR.USER_NOT_EXIST)

  if (!await verifyPassword(password, user.password_hash)) return jsonResponse(null, ERR.WRONG_PASSWORD)

  if (user.token) {
    const payload = await verifyToken(user.token, c.env.JWT_SECRET)
    
    if (payload) {
      const dbUser = await c.env.MScPJ_DB.prepare(
        "SELECT token FROM users WHERE id = ?"
      ).bind(user.id).first()

      if (dbUser && dbUser.token === user.token) {
        return jsonResponse({
          ...INFO.TOKEN_STILL_VALID,
          data: {
            token: user.token, 
            role: user.role
          }
        })
      }
    }
  }

  const token = await sign({ id: user.id, role: user.role }, c.env.JWT_SECRET)

  await c.env.MScPJ_DB.prepare(
    "UPDATE users SET token = ?, updated_at = datetime('now') WHERE id = ?"
  ).bind(token, user.id).run()

  return jsonResponse({
    ...INFO.TOKEN_GENERATED_SUCCESS,
    data:{
      token,
      role: user.role
    }
  })
})

// 登出 Logout
authRoutes.post("/logout", requireAuth(), async (c) => {
  const user = c.get("user")

  await c.env.MScPJ_DB.prepare(
    "UPDATE users SET token = NULL, updated_at = datetime('now') WHERE id = ?"
  ).bind(user.id).run()

  return jsonResponse(INFO.LOGOUT_SUCCESS)
})

// 注册管理员 Register Admin (Admin only)
authRoutes.post("/register-admin", requireAdmin, async (c) => {
  return registerUser(c, "admin")
})

// 注册工人 Register Worker (Admin only)
authRoutes.post("/register-worker", requireAdmin, async (c) => {
  return registerUser(c, "worker")
})

// 重置管理员密码 Reset Admin Password (Admin only)
authRoutes.post("/reset-admin-password", requireAdmin, async (c) => {
  return resetUserPassword(c, "admin")
})

// 重置工人密码 Reset Worker Password (Admin only)
authRoutes.post("/reset-worker-password", requireAdmin, async (c) => {
  return resetUserPassword(c, "worker")
})
