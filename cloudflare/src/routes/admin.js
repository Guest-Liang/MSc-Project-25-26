import { Hono } from "hono"
import { requireAdmin } from "../middleware/auth.js"
import { ERR, INFO } from "../utils/status.js"
import { jsonResponse } from "../utils/response.js"
import {
  ORDER_TYPES,
  UID_HEX_LENGTHS,
  countOrderSteps,
  getOrderById,
  normalizeUidHex,
  parsePositiveInteger,
  resolveOrderType,
  toOptionalTrimmedString,
  writeOrderLog
} from "../utils/order.js"

export const adminRoutes = new Hono()

adminRoutes.use("*", requireAdmin)

function buildInvalidUidHexError(field, receivedValue, extraData = {}) {
  return {
    ...ERR.INVALID_UID_HEX,
    data: {
      field,
      receivedValue,
      supportedUidHexLengths: UID_HEX_LENGTHS,
      ...extraData
    }
  }
}

function validateSequenceSteps(rawSteps) {
  if (!Array.isArray(rawSteps) || rawSteps.length === 0) {
    return { error: ERR.INVALID_ORDER_STEPS_PAYLOAD }
  }

  const normalizedSteps = []
  const stepIndexes = new Set()

  for (const rawStep of rawSteps) {
    if (!rawStep || typeof rawStep !== "object") {
      return { error: ERR.INVALID_ORDER_STEPS_PAYLOAD }
    }

    const stepIndex = parsePositiveInteger(rawStep.stepIndex)
    const targetUidHex = normalizeUidHex(rawStep.targetUidHex)

    if (!stepIndex || stepIndexes.has(stepIndex)) {
      return { error: ERR.INVALID_ORDER_STEPS_PAYLOAD }
    }

    if (!targetUidHex) {
      return {
        error: buildInvalidUidHexError("steps.targetUidHex", rawStep.targetUidHex ?? null, {
          stepIndex
        })
      }
    }

    stepIndexes.add(stepIndex)
    normalizedSteps.push({
      stepIndex,
      targetUidHex,
      locationCode: toOptionalTrimmedString(rawStep.locationCode),
      displayName: toOptionalTrimmedString(rawStep.displayName)
    })
  }

  normalizedSteps.sort((left, right) => left.stepIndex - right.stepIndex)

  for (let index = 0; index < normalizedSteps.length; index += 1) {
    if (normalizedSteps[index].stepIndex !== index + 1) {
      return { error: ERR.INVALID_ORDER_STEPS_PAYLOAD }
    }
  }

  return { steps: normalizedSteps }
}

adminRoutes.get("/workers", async (c) => {
  const list = await c.env.MScPJ_DB.prepare(
    "SELECT id, username, role, created_at FROM users WHERE role = 'worker' ORDER BY created_at DESC"
  ).all()

  return jsonResponse({
    ...INFO.SQL_QUERY_SUCCESS,
    data: {
      WorkerList: list.results
    }
  })
})

adminRoutes.post("/orders/create", async (c) => {
  let body

  try {
    body = await c.req.json()
  } catch {
    return jsonResponse(null, ERR.INVALID_ORDER_PAYLOAD)
  }

  const user = c.get("user")
  const title = toOptionalTrimmedString(body?.title)
  const description = toOptionalTrimmedString(body?.description)
  const orderType = resolveOrderType(body?.orderType, ORDER_TYPES.STANDARD)
  const rawTargetUidHex = body?.targetUidHex ?? body?.tag ?? null
  const targetUidHex = rawTargetUidHex == null ? null : normalizeUidHex(rawTargetUidHex)
  const locationCode = toOptionalTrimmedString(body?.locationCode)
  const displayName = toOptionalTrimmedString(body?.displayName)

  if (!title || !description) return jsonResponse(null, ERR.INVALID_ORDER_PAYLOAD)
  if (!orderType) return jsonResponse(null, ERR.INVALID_ORDER_TYPE)
  if (rawTargetUidHex != null && !targetUidHex) {
    return jsonResponse(null, buildInvalidUidHexError("targetUidHex", rawTargetUidHex))
  }
  if (orderType === ORDER_TYPES.STANDARD && !targetUidHex) {
    return jsonResponse(null, ERR.ORDER_TARGET_UID_REQUIRED)
  }

  const exists = await c.env.MScPJ_DB.prepare(
    "SELECT id FROM orders WHERE title = ?"
  ).bind(title).first()
  if (exists) return jsonResponse(null, ERR.ORDER_TITLE_EXISTS)

  const finalTargetUidHex = orderType === ORDER_TYPES.STANDARD ? targetUidHex : null

  const result = await c.env.MScPJ_DB.prepare(
    `INSERT INTO orders (
      title,
      description,
      nfc_tag,
      status,
      assigned_to,
      created_at,
      updated_at,
      order_type,
      target_uid_hex,
      location_code,
      display_name,
      assigned_at,
      completed_at,
      sequence_total_steps,
      sequence_completed_steps
    ) VALUES (?, ?, ?, 'created', NULL, datetime('now'), datetime('now'), ?, ?, ?, ?, NULL, NULL, 0, 0)`
  ).bind(
    title,
    description,
    finalTargetUidHex,
    orderType,
    finalTargetUidHex,
    locationCode,
    displayName
  ).run()

  const orderId = result.meta.last_row_id

  await writeOrderLog(c.env.MScPJ_DB, {
    orderId,
    action: "created",
    operatorId: user.id,
    locationCode,
    displayName,
    expectedUidHex: finalTargetUidHex,
    details: {
      orderType,
      targetUidHex: finalTargetUidHex
    }
  })

  return jsonResponse({
    ...INFO.ORDER_CREATED_SUCCESS,
    data: { orderId }
  })
})

adminRoutes.post("/orders/steps/save", async (c) => {
  let body

  try {
    body = await c.req.json()
  } catch {
    return jsonResponse(null, ERR.INVALID_ORDER_STEPS_PAYLOAD)
  }

  const user = c.get("user")
  const orderId = parsePositiveInteger(body?.orderId)
  if (!orderId) return jsonResponse(null, ERR.INVALID_ORDER_ID)

  const { steps, error } = validateSequenceSteps(body?.steps)
  if (error) return jsonResponse(null, error)

  const order = await getOrderById(c.env.MScPJ_DB, orderId)
  if (!order) return jsonResponse(null, ERR.ORDER_NOT_FOUND)
  if ((order.order_type ?? ORDER_TYPES.STANDARD) !== ORDER_TYPES.SEQUENCE) {
    return jsonResponse(null, ERR.ORDER_STEP_NOT_SUPPORTED)
  }
  if (order.status !== "created" || order.assigned_to !== null || Number(order.sequence_completed_steps ?? 0) > 0) {
    return jsonResponse(null, ERR.ORDER_STEP_EDIT_FORBIDDEN)
  }

  const statements = [
    c.env.MScPJ_DB.prepare("DELETE FROM order_steps WHERE order_id = ?").bind(orderId),
    ...steps.map((step) => c.env.MScPJ_DB.prepare(
      `INSERT INTO order_steps (
        order_id,
        step_index,
        target_uid_hex,
        location_code,
        display_name,
        created_at,
        updated_at
      ) VALUES (?, ?, ?, ?, ?, datetime('now'), datetime('now'))`
    ).bind(orderId, step.stepIndex, step.targetUidHex, step.locationCode, step.displayName)),
    c.env.MScPJ_DB.prepare(
      "UPDATE orders SET sequence_total_steps = ?, sequence_completed_steps = 0, updated_at = datetime('now') WHERE id = ?"
    ).bind(steps.length, orderId)
  ]

  await c.env.MScPJ_DB.batch(statements)

  await writeOrderLog(c.env.MScPJ_DB, {
    orderId,
    action: "steps_saved",
    operatorId: user.id,
    result: "sequence_steps_saved",
    stepIndex: steps.length,
    details: {
      stepCount: steps.length
    }
  })

  return jsonResponse({
    ...INFO.ORDER_STEPS_SAVED_SUCCESS,
    data: {
      orderId,
      stepCount: steps.length
    }
  })
})

adminRoutes.post("/orders/assign", async (c) => {
  let body

  try {
    body = await c.req.json()
  } catch {
    return jsonResponse(null, ERR.INVALID_ORDER_PAYLOAD)
  }

  const user = c.get("user")
  const orderId = parsePositiveInteger(body?.orderId)
  if (!orderId) return jsonResponse(null, ERR.INVALID_ORDER_ID)

  const hasExplicitNullUserId = Object.prototype.hasOwnProperty.call(body ?? {}, "userId") && body.userId === null
  const workerId = hasExplicitNullUserId ? null : parsePositiveInteger(body?.userId)
  if (!hasExplicitNullUserId && workerId === null) return jsonResponse(null, ERR.INVALID_WORKER_ID)

  const order = await getOrderById(c.env.MScPJ_DB, orderId)
  if (!order) return jsonResponse(null, ERR.ORDER_NOT_FOUND)
  if (order.status === "completed") return jsonResponse(null, ERR.ORDER_NOT_COMPLETABLE)

  if (workerId === null) {
    if ((order.order_type ?? ORDER_TYPES.STANDARD) === ORDER_TYPES.SEQUENCE && Number(order.sequence_completed_steps ?? 0) > 0) {
      return jsonResponse(null, ERR.ORDER_PROGRESS_LOCKED)
    }

    await c.env.MScPJ_DB.prepare(
      "UPDATE orders SET assigned_to = NULL, status = 'created', assigned_at = NULL, updated_at = datetime('now') WHERE id = ?"
    ).bind(orderId).run()

    await writeOrderLog(c.env.MScPJ_DB, {
      orderId,
      action: "unassigned",
      operatorId: user.id
    })

    return jsonResponse(INFO.ORDER_UNASSIGNED_SUCCESS)
  }

  const worker = await c.env.MScPJ_DB.prepare(
    "SELECT id, role FROM users WHERE id = ?"
  ).bind(workerId).first()
  if (!worker) return jsonResponse(null, ERR.WORKER_NOT_FOUND)
  if (worker.role !== "worker") return jsonResponse(null, ERR.NOT_A_WORKER)

  if ((order.order_type ?? ORDER_TYPES.STANDARD) === ORDER_TYPES.SEQUENCE) {
    const stepCount = await countOrderSteps(c.env.MScPJ_DB, orderId)
    if (stepCount < 1) return jsonResponse(null, ERR.ORDER_STEPS_REQUIRED)

    await c.env.MScPJ_DB.prepare(
      `UPDATE orders
       SET assigned_to = ?, status = 'assigned', assigned_at = datetime('now'), updated_at = datetime('now'), sequence_total_steps = ?
       WHERE id = ?`
    ).bind(workerId, stepCount, orderId).run()
  } else {
    await c.env.MScPJ_DB.prepare(
      "UPDATE orders SET assigned_to = ?, status = 'assigned', assigned_at = datetime('now'), updated_at = datetime('now') WHERE id = ?"
    ).bind(workerId, orderId).run()
  }

  await writeOrderLog(c.env.MScPJ_DB, {
    orderId,
    action: "assigned",
    operatorId: user.id,
    details: {
      assignedTo: workerId
    }
  })

  return jsonResponse(INFO.ORDER_ASSIGNED_SUCCESS)
})
