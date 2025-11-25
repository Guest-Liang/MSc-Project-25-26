export function jsonResponse(data, error = null, status = 200) {
  const body = {
    success: !error,
    data: error ? null : data,
    error: error
      ? {
          code: error.code || "UNKNOWN_ERROR",
          message: error.message || error
        }
      : null
  }

  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" }
  })
}
