import { authRoutes } from "./routes/auth.js"
import { adminRoutes } from "./routes/admin.js"
import { workerRoutes } from "./routes/worker.js"
import { orderRoutes } from "./routes/orders.js"

export default {
  async fetch(request, env, ctx) {
    return (
      (await authRoutes(request, env)) ??
      (await adminRoutes(request, env)) ??
      (await workerRoutes(request, env)) ??
      (await orderRoutes(request, env)) ??
      new Response("Not Found", { status: 404 })
    )
  },
}
