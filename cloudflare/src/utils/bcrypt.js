const encoder = new TextEncoder()

export async function hashPassword(password) {
  const passwordData = encoder.encode(password)

  // 随机盐 Random salt
  const salt = crypto.getRandomValues(new Uint8Array(16))

  // PBKDF2 派生 key PBKDF2 derived key
  const key = await crypto.subtle.importKey("raw", passwordData, { name: "PBKDF2" }, false, ["deriveBits"])

  const derivedBits = await crypto.subtle.deriveBits(
    {
      name: "PBKDF2",
      salt,
      iterations: 100000,
      hash: "SHA-256",
    },
    key,
    256
  )

  const hashArray = new Uint8Array(derivedBits)

  return `${toBase64(salt)}:${toBase64(hashArray)}`
}

export async function verifyPassword(password, stored) {
  const [saltB64, hashB64] = stored.split(":")

  const salt = fromBase64(saltB64)
  const storedHash = fromBase64(hashB64)

  const passwordData = encoder.encode(password)

  const key = await crypto.subtle.importKey("raw", passwordData, { name: "PBKDF2" }, false, ["deriveBits"])

  const derivedBits = await crypto.subtle.deriveBits(
    {
      name: "PBKDF2",
      salt,
      iterations: 100000,
      hash: "SHA-256",
    },
    key,
    256
  )

  const derivedHash = new Uint8Array(derivedBits)

  if (derivedHash.length !== storedHash.length) return false

  // constant-time compare
  let diff = 0
  for (let i = 0; i < derivedHash.length; i++) {
    diff |= derivedHash[i] ^ storedHash[i]
  }

  return diff === 0
}

function toBase64(arr) {
  let binary = ""
  for (let i = 0; i < arr.length; i++) binary += String.fromCharCode(arr[i])
  return btoa(binary)
}

function fromBase64(str) {
  const binary = atob(str)
  const arr = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i++) arr[i] = binary.charCodeAt(i)
  return arr
}
