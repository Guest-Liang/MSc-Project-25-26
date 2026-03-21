import { Hono } from "hono"
import { authRoutes } from "./routes/auth.js"
import { adminRoutes } from "./routes/admin.js"
import { workerRoutes } from "./routes/worker.js"
import { orderRoutes } from "./routes/orders.js"
import { ERR } from "./utils/status.js"
import { jsonResponse } from "./utils/response.js"
import { apiLogMiddleware } from "./middleware/apiLog.js"

const app = new Hono()

app.use("*", apiLogMiddleware)

app.get("/healthz", (c) => {
  return jsonResponse({ ciallo: "Ciallo～(∠・ω< )⌒★" })
})

app.route("/auth", authRoutes)
app.route("/admin", adminRoutes)
app.route("/worker", workerRoutes)
app.route("/orders", orderRoutes)

// 默认 404 Default 404 not found
app.notFound(() => jsonResponse(null, ERR.NOT_FOUND))

export default app
