export const ERR = {
  // User & Auth (1000–1999)
  USER_NOT_EXIST: { code: 1001, message: "User does not exist" },
  WRONG_PASSWORD: { code: 1002, message: "Wrong password" },
  TOKEN_MISSING: { code: 1003, message: "Token missing" },
  TOKEN_INVALID: { code: 1004, message: "Invalid token" },
  USER_ALREADY_EXISTS: { code: 1005, message: "User already exists" },
  TOKEN_REVOKED: { code: 1006, message: "Token has been revoked" },
  NOT_AN_ADMIN: { code: 1007, message: "The specified user is not an admin" },

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
  INVALID_TIME_RANGE: { code: 4004, message: "Invalid time range" },
  INVALID_ASSIGNED_FILTER: { code: 4005, message: "Invalid assigned filter" },

  // System (5000–5999)
  DB_ERROR: { code: 5001, message: "Database error" },
  INTERNAL_ERROR: { code: 5002, message: "Internal server error" },

  // Others
  NOT_FOUND: { code: 440044, message: "Not Found" },
}

export const INFO = {
  SUCCESS: { code: 0, message: "Success" },
  LOGIN_SUCCESS: { code: 9000, message: "Login success" },
  LOGOUT_SUCCESS: { code: 9001, message: "Logout success" },
  SQL_QUERY_SUCCESS: { code: 9002, message: "SQL query success" },
  PASSWORD_UPDATED: { code: 9003, message: "Password updated" },
  ORDER_ASSIGNED_SUCCESS: { code: 9004, message: "Order assigned success" },
  ORDER_UNASSIGNED_SUCCESS: { code: 9005, message: "Order unassigned success" },
  ADMIN_CREATED_SUCCESS: { code: 9006, message: "Admin created success" },
  WORKER_CREATED_SUCCESS: { code: 9007, message: "Worker created success" },
  ORDER_SET_STATUS_SUCCESS: { code: 9008, message: "Set work order completion success" },
  TOKEN_STILL_VALID: { code: 9009, message: "Token is still valid" },
  ORDER_CREATED_SUCCESS: { code: 9010, message: "Order created successfully" },
  TOKEN_GENERATED_SUCCESS: { code: 9011, message: "Token generated successfully" },
}
