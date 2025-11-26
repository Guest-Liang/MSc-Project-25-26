export async function sign(payload, secret) {
  const enc = new TextEncoder()
  const key = await crypto.subtle.importKey(
    "raw",
    enc.encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"]
  )

  const header = btoa(JSON.stringify({ alg: "HS256", typ: "JWT" }))
  const exp = Math.floor(Date.now() / 1000) + 60 * 20  // 20分钟 20 mins
  const body = btoa(JSON.stringify({ ...payload, exp }))

  const sig = await crypto.subtle.sign(
    "HMAC",
    key,
    enc.encode(`${header}.${body}`)
  )

  const signature = btoa(String.fromCharCode(...new Uint8Array(sig)))
  return `${header}.${body}.${signature}`
}

export async function verifyToken(token, secret) {
  try {
    const enc = new TextEncoder()
    const [header, body, signature] = token.split(".")

    const key = await crypto.subtle.importKey(
      "raw",
      enc.encode(secret),
      { name: "HMAC", hash: "SHA-256" },
      false,
      ["verify"]
    )

    const valid = await crypto.subtle.verify(
      "HMAC",
      key,
      Uint8Array.from(atob(signature), c => c.charCodeAt(0)),
      enc.encode(`${header}.${body}`)
    )

    if (!valid) return null
    const data = JSON.parse(atob(body))

    if (data.exp && data.exp < Math.floor(Date.now() / 1000))
      return null
    return data
  } catch {
    return null
  }
}
