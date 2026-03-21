async function sha256Hex(input) {
  const bytes = new TextEncoder().encode(input)
  const digest = await crypto.subtle.digest("SHA-256", bytes)
  return Array.from(new Uint8Array(digest))
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("")
}

const BUSINESS_ROUTE_PREFIXES = ["/auth", "/admin", "/worker", "/orders"]
const NOISE_PATHS = new Set([
  "/favicon.ico",
  "/robots.txt"
])

function classifyPath(path) {
  if (BUSINESS_ROUTE_PREFIXES.some((prefix) => path.startsWith(prefix))) {
    return { routeGroup: "business", isBusinessRequest: true }
  }

  if (path === "/healthz") {
    return { routeGroup: "system", isBusinessRequest: false }
  }

  if (NOISE_PATHS.has(path)) {
    return { routeGroup: null, isBusinessRequest: false }
  }

  return { routeGroup: "unknown", isBusinessRequest: false }
}

async function resolveUserForLog(c, method, path) {
  const authUser = c.get("user") || null
  if (authUser?.id) {
    return {
      userId: authUser.id,
      userRole: authUser.role ?? null
    }
  }

  return { userId: null, userRole: null }
}

async function resolveRequestMeta(c, method, path, isBusinessRequest) {
  if (!isBusinessRequest) return null

  const url = new URL(c.req.url)
  const queryParams = {}
  for (const [key, value] of url.searchParams.entries()) {
    if (Object.prototype.hasOwnProperty.call(queryParams, key)) {
      if (Array.isArray(queryParams[key])) {
        queryParams[key].push(value)
      } else {
        queryParams[key] = [queryParams[key], value]
      }
    } else {
      queryParams[key] = value
    }
  }
  const hasQueryParams = Object.keys(queryParams).length > 0

  const preloadedMeta = c.get("api_log_request_meta")
  let meta = null

  if (typeof preloadedMeta === "string") {
    try {
      const parsed = JSON.parse(preloadedMeta)
      if (parsed && typeof parsed === "object" && !Array.isArray(parsed)) {
        meta = parsed
      } else {
        meta = { preloaded_meta: preloadedMeta }
      }
    } catch {
      meta = { preloaded_meta: preloadedMeta }
    }
  }

  if (!meta && method === "POST" && path === "/auth/login") {
    try {
      const body = await c.req.raw.clone().json()
      const username = typeof body?.username === "string" ? body.username.trim() : null
      const password = typeof body?.password === "string" ? body.password : null

      if (!username && !password && !hasQueryParams) return null

      const pepper = c.env.LOG_PEPPER || c.env.JWT_SECRET || ""
      const passwordFingerprint = password
        ? await sha256Hex(`${pepper}:${password}`)
        : null

      meta = {
        login_username: username,
        password_fingerprint: passwordFingerprint
      }
    } catch {
      if (!hasQueryParams) return null
    }
  }

  if (hasQueryParams) {
    meta = {
      ...(meta || {}),
      query_params: queryParams
    }
  }

  return meta ? JSON.stringify(meta) : null
}

export async function writeApiLog(c) {
  const method = c.req.method
  const path = new URL(c.req.url).pathname
  const { routeGroup, isBusinessRequest } = classifyPath(path)

  if (!routeGroup) return

  const { userId, userRole } = await resolveUserForLog(c, method, path)
  const requestMeta = await resolveRequestMeta(c, method, path, isBusinessRequest)

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
      request_meta,
      created_at
    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, datetime('now'))`
  )
    .bind(
      method,
      path,
      `${routeGroup}:${method} ${path}`,
      userId,
      userRole,
      success,
      responseCode,
      responseMessage,
      ip,
      userAgent,
      requestMeta
    )
    .run()
}

export async function apiLogMiddleware(c, next) {
  const method = c.req.method
  const path = new URL(c.req.url).pathname

  if (method === "POST" && path === "/auth/login") {
    try {
      const body = await c.req.raw.clone().json()
      const username = typeof body?.username === "string" ? body.username.trim() : null
      const password = typeof body?.password === "string" ? body.password : null

      if (username || password) {
        const pepper = c.env.LOG_PEPPER || c.env.JWT_SECRET || ""
        const passwordFingerprint = password
          ? await sha256Hex(`${pepper}:${password}`)
          : null

        c.set("api_log_request_meta", JSON.stringify({
          login_username: username,
          password_fingerprint: passwordFingerprint
        }))
      }
    } catch {
      // Ignore preloading errors; writeApiLog will fall back safely.
    }
  }

  try {
    await next()
  } finally {
    try {
      await writeApiLog(c)
    } catch (err) {
      // Log write failures should never block API responses.
      console.error("api log write failed:", err)
    }
  }
}
