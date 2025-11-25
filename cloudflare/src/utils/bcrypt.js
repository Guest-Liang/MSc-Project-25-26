import bcrypt from "@fastify/bcrypt"

export async function hashPassword(password) {
  return bcrypt.hash(password)
}

export async function verifyPassword(password, hash) {
  return bcrypt.compare(password, hash)
}
