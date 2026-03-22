export function jsonResponse(input = null, error = null) {
  const hasError = !!error

  if (hasError) {
    const { code, message, data = null } = error
    return new Response(JSON.stringify({
      success: false,
      code,
      message,
      data
    }), {
      status: 200,
      headers: { "Content-Type": "application/json" }
    })
  }

  let code = 0
  let message = "Success"
  let data = {}

  // input 的几种情况：
  // 1. INFO.xxx → { code, message }
  // 2. { code, message, data }
  // 3. { data, message }
  // 4. 普通对象/数组
  // 5. null

  if (input === null || input === undefined) { } // 默认: Success + {}
  else if (Array.isArray(input)) { data = input }
  else if (typeof input === "object") {
    // 1: INFO.xxx 或自定义 code + message
    if (typeof input.code === "number" && typeof input.message === "string") {
      code = input.code
      message = input.message
      data = input.data ?? {}
    }
    // 2: 普通对象作为 data Ordinary objects as data
    else { data = input }
  }
  // 其他类型 Other types
  else { data = { value: input } }

  return new Response(JSON.stringify({
    success: true,
    code,
    message,
    data
  }), {
    status: 200,
    headers: { "Content-Type": "application/json" }
  })
}
