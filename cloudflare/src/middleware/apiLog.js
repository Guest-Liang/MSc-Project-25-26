export async function writeApiLog(c) {
  const method = c.req.method
  const path = new URL(c.req.url).pathname

  // Only log business APIs under /routes
  if (!["/auth", "/admin", "/worker", "/orders"].some((prefix) => path.startsWith(prefix))) {
    return
  }

  const user = c.get("user") || null
  const userId = user?.id ?? null
  const userRole = user?.role ?? null

  let responseCode = null
  let responseMessage = null
  let success = c.res?.ok ? 1 : 0

  try {
    const contentType = c.res?.headers?.get("content-type") || ""
    if (contentType.includes("application/json")) {
      const payload = await c.res.clone().json()
      responseCode = typeof payload?.code === "number" ? payload.code : null
      responseMessage = typeof payload?.message === "string" ? payload.message : null
      if (typeof payload?.success === "boolean") {
        success = payload.success ? 1 : 0
      }
    }
  } catch {
    // Ignore response parse errors and keep fallback values.
  }

  const ip = c.req.header("CF-Connecting-IP") || c.req.header("X-Forwarded-For") || null
  const userAgent = c.req.header("User-Agent") || null

  await c.env.MScPJ_DB.prepare(
    `INSERT INTO api_logs (
      method,
      path,
      action,
      user_id,
      user_role,
      success,
      response_code,
      response_message,
      ip,
      user_agent,
      created_at
    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, datetime('now'))`
  )
    .bind(
      method,
      path,
      `${method} ${path}`,
      userId,
      userRole,
      success,
      responseCode,
      responseMessage,
      ip,
      userAgent
    )
    .run()
}

export async function apiLogMiddleware(c, next) {
  try {
    await next()
  } finally {
    try {
      await writeApiLog(c)
    } catch {
      // Log write failures should never block API responses.
    }
  }
}
