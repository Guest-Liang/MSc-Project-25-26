import { Hono } from "hono"
import { authRoutes } from "./routes/auth.js"
import { adminRoutes } from "./routes/admin.js"
import { workerRoutes } from "./routes/worker.js"
import { orderRoutes } from "./routes/orders.js"
import { jsonResponse } from "./utils/response.js"

const app = new Hono()

app.get("/healthz", (c) => {
  return jsonResponse({ ciallo: "Ciallo～(∠・ω< )⌒★" })
})

app.route("/auth", authRoutes)
app.route("/admin", adminRoutes)
app.route("/worker", workerRoutes)
app.route("/orders", orderRoutes)

// 默认 404 Default 404 not found
app.notFound((c) => c.json({ success: false, error: { code: 404, message: "Not Found" } }))

export default app
