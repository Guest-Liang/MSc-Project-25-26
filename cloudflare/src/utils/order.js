export const ORDER_TYPES = {
  STANDARD: "standard",
  SEQUENCE: "sequence"
}

const UID_HEX_ALLOWED_PATTERN = /^[0-9a-fA-F:\-\s]+$/
const HEX_ONLY_PATTERN = /^[0-9A-F]+$/
const SUPPORTED_UID_HEX_LENGTHS = new Set([14, 16, 20])

export const UID_HEX_LENGTHS = [14, 16, 20]

export function toOptionalTrimmedString(value) {
  if (typeof value !== "string") return null

  const trimmed = value.trim()
  return trimmed.length > 0 ? trimmed : null
}

export function parsePositiveInteger(value) {
  if (typeof value === "number" && Number.isInteger(value) && value > 0) return value

  if (typeof value === "string" && /^\d+$/.test(value.trim())) {
    const parsed = Number.parseInt(value.trim(), 10)
    return parsed > 0 ? parsed : null
  }

  return null
}

export function normalizeUidHex(value) {
  if (typeof value !== "string") return null

  const trimmed = value.trim()
  if (!trimmed || !UID_HEX_ALLOWED_PATTERN.test(trimmed)) return null

  const normalized = trimmed.replace(/[^0-9a-fA-F]/g, "").toUpperCase()
  // For this project we only accept canonical NFC UID lengths commonly
  // produced by the deployed Android scanning flow: 7, 8, or 10 bytes.
  if (
    normalized.length % 2 !== 0 ||
    !HEX_ONLY_PATTERN.test(normalized) ||
    !SUPPORTED_UID_HEX_LENGTHS.has(normalized.length)
  ) {
    return null
  }

  return normalized
}

export function resolveOrderType(value, fallback = null) {
  const normalized = toOptionalTrimmedString(value)?.toLowerCase()
  if (!normalized) return fallback

  if (normalized === ORDER_TYPES.STANDARD || normalized === ORDER_TYPES.SEQUENCE) {
    return normalized
  }

  return null
}

export function parseCsvParam(value) {
  if (typeof value !== "string") return []

  return value
    .split(",")
    .map((item) => item.trim())
    .filter((item) => item.length > 0)
}

export function parseJsonDetails(detailsJson) {
  if (typeof detailsJson !== "string" || !detailsJson.trim()) return null

  try {
    return JSON.parse(detailsJson)
  } catch {
    return { raw: detailsJson }
  }
}

function serializeDetails(details) {
  if (!details || typeof details !== "object") return null

  const compact = Object.fromEntries(
    Object.entries(details).filter(([, value]) => value !== undefined)
  )

  return Object.keys(compact).length > 0 ? JSON.stringify(compact) : null
}

export async function getOrderById(db, orderId) {
  return db.prepare("SELECT * FROM orders WHERE id = ?").bind(orderId).first()
}

export async function getOrderSteps(db, orderId) {
  const rows = await db.prepare(
    "SELECT * FROM order_steps WHERE order_id = ? ORDER BY step_index ASC"
  ).bind(orderId).all()

  return rows.results ?? []
}

export async function getNextSequenceStep(db, orderId, nextStepIndex) {
  return db.prepare(
    "SELECT * FROM order_steps WHERE order_id = ? AND step_index = ?"
  ).bind(orderId, nextStepIndex).first()
}

export async function countOrderSteps(db, orderId) {
  const row = await db.prepare(
    "SELECT COUNT(*) AS count FROM order_steps WHERE order_id = ?"
  ).bind(orderId).first()

  return Number(row?.count ?? 0)
}

export async function writeOrderLog(db, log) {
  const detailsJson = serializeDetails(log.details)

  await db.prepare(
    `INSERT INTO order_logs (
      order_id,
      action,
      operator_id,
      timestamp,
      result,
      step_index,
      scan_uid_hex,
      expected_uid_hex,
      location_code,
      display_name,
      details_json
    ) VALUES (?, ?, ?, datetime('now'), ?, ?, ?, ?, ?, ?, ?)`
  )
    .bind(
      log.orderId ?? null,
      log.action,
      log.operatorId ?? null,
      log.result ?? null,
      log.stepIndex ?? null,
      log.scanUidHex ?? null,
      log.expectedUidHex ?? null,
      log.locationCode ?? null,
      log.displayName ?? null,
      detailsJson
    )
    .run()
}

export function formatOrderStep(step) {
  return {
    ...step,
    stepIndex: Number(step.step_index ?? 0),
    targetUidHex: step.target_uid_hex ?? null,
    locationCode: step.location_code ?? null,
    displayName: step.display_name ?? null,
    createdAt: step.created_at ?? null,
    updatedAt: step.updated_at ?? null
  }
}

export function formatOrderLog(log) {
  const parsedDetails = parseJsonDetails(log.details_json)

  return {
    ...log,
    orderType: log.order_type ?? null,
    orderTitle: log.order_title ?? null,
    stepIndex: log.step_index ?? null,
    operatorId: log.operator_id ?? null,
    orderId: log.order_id ?? null,
    scanUidHex: log.scan_uid_hex ?? null,
    expectedUidHex: log.expected_uid_hex ?? null,
    locationCode: log.location_code ?? null,
    displayName: log.display_name ?? null,
    details: parsedDetails,
    detailsJson: parsedDetails
  }
}

export function buildProgressStatus(order) {
  if (order.status === "completed") return "completed"

  const orderType = order.order_type ?? ORDER_TYPES.STANDARD
  const completedSteps = Number(order.sequence_completed_steps ?? 0)

  if (orderType === ORDER_TYPES.SEQUENCE && completedSteps > 0) return "in_progress"

  return "not_started"
}

export function formatOrderRecord(order, nextStep = null) {
  const orderType = order.order_type ?? ORDER_TYPES.STANDARD
  const targetUidHex = order.target_uid_hex ?? order.nfc_tag ?? null
  const locationCode = order.location_code ?? null
  const displayName = order.display_name ?? null
  const sequenceTotalSteps = Number(order.sequence_total_steps ?? 0)
  const sequenceCompletedSteps = Number(order.sequence_completed_steps ?? 0)
  const progressStatus = buildProgressStatus(order)

  return {
    ...order,
    nfc_tag: order.nfc_tag ?? targetUidHex,
    order_type: orderType,
    target_uid_hex: targetUidHex,
    location_code: locationCode,
    display_name: displayName,
    assigned_at: order.assigned_at ?? null,
    completed_at: order.completed_at ?? null,
    sequence_total_steps: sequenceTotalSteps,
    sequence_completed_steps: sequenceCompletedSteps,
    progress_status: progressStatus,
    next_step_index: nextStep?.step_index ?? null,
    next_expected_uid_hex: nextStep?.target_uid_hex ?? null,
    next_location_code: nextStep?.location_code ?? null,
    next_display_name: nextStep?.display_name ?? null,
    orderType,
    targetUidHex,
    locationCode,
    displayName,
    assignedAt: order.assigned_at ?? null,
    completedAt: order.completed_at ?? null,
    sequenceTotalSteps,
    sequenceCompletedSteps,
    progressStatus,
    nextStepIndex: nextStep?.step_index ?? null,
    nextExpectedUidHex: nextStep?.target_uid_hex ?? null,
    nextLocationCode: nextStep?.location_code ?? null,
    nextDisplayName: nextStep?.display_name ?? null
  }
}
