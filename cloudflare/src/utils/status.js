export const ERR = {
  // Success
  SUCCESS: { code: 0, message: "Success" },

  // User & Auth (1000–1999)
  USER_NOT_EXIST: { code: 1001, message: "User does not exist" },
  WRONG_PASSWORD: { code: 1002, message: "Wrong password" },
  TOKEN_MISSING: { code: 1003, message: "Token missing" },
  TOKEN_INVALID: { code: 1004, message: "Invalid token" },
  USER_ALREADY_EXISTS: { code: 1005, message: "User already exists" },
  TOKEN_REVOKED: { code: 1006, message: "Token has been revoked" },

  // Permission (2000–2999)
  NO_PERMISSION: { code: 2001, message: "No permission" },
  ADMIN_REQUIRED: { code: 2002, message: "Admin permissions required" },

  // Worker (3000–3999)
  WORKER_NOT_FOUND: { code: 3001, message: "Worker does not exist" },
  NOT_A_WORKER: { code: 3002, message: "The specified user is not a worker" },
  ORDER_NOT_OWNED: { code: 3003, message: "Order does not belong to this worker" },

  // Order (4000–4999)
  ORDER_NOT_FOUND: { code: 4001, message: "Order does not exist" },
  ORDER_NOT_COMPLETABLE: { code: 4002, message: "Order cannot be completed" },
  ORDER_TITLE_EXISTS: { code: 4003, message: "Order with this title already exists" },

  // System (5000–5999)
  DB_ERROR: { code: 5001, message: "Database error" },
  INTERNAL_ERROR: { code: 5002, message: "Internal server error" }
}

export const INFO = {
  PASSWORD_UPDATED: { code: 101, message: "Password updated" },
  LOGOUT_SUCCESS: { code: 0, message: "Logout success" }
}
