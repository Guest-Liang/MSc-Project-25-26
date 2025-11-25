export function jsonResponse(data = null, error = null) {
  const hasError = !!error

  let code = 0
  let message = ""

  if (hasError) {
    if (typeof error === "number") {
      code = error
    } else if (typeof error === "string") {
      code = 1
      message = error
    } else {
      code = typeof error.code === "number" ? error.code : 1
      message = error.message || ""
    }
  }

  const body = {
    success: !hasError,
    data: hasError ? null : data,
    error: hasError ? { code, message } : null
  }

  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { "Content-Type": "application/json" }
  })
}
