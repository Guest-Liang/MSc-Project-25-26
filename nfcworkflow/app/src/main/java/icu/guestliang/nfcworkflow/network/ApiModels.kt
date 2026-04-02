package icu.guestliang.nfcworkflow.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ApiResponse(
    val success: Boolean = true,
    val code: Int = 0,
    val message: String = "",
    val data: JsonElement? = null
)

@Serializable
data class PaginatedResponse<T>(
    val items: List<T> = emptyList(),
    val nextCursor: String? = null,
    val hasMore: Boolean = false
)

@Serializable
data class LoginRequest(
    val username: String,
    val password: String
)

@Serializable
data class Order(
    val id: Int? = null,
    val title: String = "",
    val description: String = "",
    val orderType: String = "standard", // "standard" or "sequence"
    val targetUidHex: String? = null,
    val status: String = "",
    val assignedTo: Int? = null,
    val locationCode: String? = null,
    val displayName: String? = null,
    val assignedAt: String? = null,
    val createdAt: String? = null,
    val sequenceTotalSteps: Int = 0,
    val sequenceCompletedSteps: Int = 0,
    val nextStepIndex: Int? = null,
    val nextExpectedUidHex: String? = null,
    val nextLocationCode: String? = null,
    val nextDisplayName: String? = null,
    // Add backward compatible fields
    val nfc_tag: String? = null,
    val assigned_to: Int? = null,
    val created_at: String? = null,
    val updated_at: String? = null
)

@Serializable
data class ScanRequest(
    val orderId: Int,
    val uidHex: String,
    val rawText: String? = null,
    val ndefText: String? = null
)

@Serializable
data class ScanResponseData(
    val orderId: Int,
    val orderType: String,
    val matched: Boolean,
    val completed: Boolean,
    val scannedUidHex: String,
    val expectedUidHex: String? = null,
    val locationCode: String? = null,
    val displayName: String? = null,
    val sequenceTotalSteps: Int = 0,
    val sequenceCompletedSteps: Int = 0,
    val nextStepIndex: Int? = null,
    val nextExpectedUidHex: String? = null,
    val nextLocationCode: String? = null,
    val nextDisplayName: String? = null,
    val completedStepIndex: Int? = null
)

@Serializable
data class ScanErrorData(
    val orderId: Int,
    val orderType: String,
    val scannedUidHex: String,
    val expectedStepIndex: Int? = null,
    val expectedUidHex: String? = null,
    val expectedLocationCode: String? = null,
    val expectedDisplayName: String? = null,
    val scannedStepIndex: Int? = null
)

@Serializable
data class CompleteOrderRequest(
    val orderId: Int
)

@Serializable
data class CreateOrderRequest(
    val title: String,
    val description: String,
    val orderType: String = "standard",
    val targetUidHex: String? = null,
    val locationCode: String? = null,
    val displayName: String? = null,
    val tag: String? = null // Backward compat
)

@Serializable
data class CreateOrderResponseData(
    val orderId: Int
)

@Serializable
data class SaveOrderStepsRequest(
    val orderId: Int,
    val steps: List<OrderStep>
)

@Serializable
data class AssignOrderRequest(
    val orderId: Int,
    val userId: Int? = null
)

@Serializable
data class WorkerUser(
    val id: Int,
    val username: String,
    val role: String,
    val created_at: String? = null
)

@Serializable
data class LogEntry(
    val id: Int,
    val orderId: Int? = null,
    val action: String,
    val workerId: Int? = null,
    val timestamp: String? = null,
    val result: String? = null,
    val stepIndex: Int? = null,
    val scanUidHex: String? = null,
    val expectedUidHex: String? = null,
    val locationCode: String? = null,
    val displayName: String? = null,
    val orderType: String? = null,
    val orderTitle: String? = null,
    val details: JsonElement? = null,
    // Compatibility fields
    val order_id: Int? = null,
    val operator_id: Int? = null
)

@Serializable
data class OrderStep(
    val id: Int? = null,
    val order_id: Int? = null,
    val stepIndex: Int,
    val targetUidHex: String,
    val locationCode: String? = null,
    val displayName: String? = null
)

@Serializable
data class OrderStepsResponse(
    val orderId: Int,
    val orderType: String,
    val steps: List<OrderStep>
)

@Serializable
data class WorkerSummary(
    val workerId: Int? = null,
    val totalScanCount: Int = 0,
    val successfulScanCount: Int = 0,
    val mismatchCount: Int = 0,
    val outOfOrderCount: Int = 0,
    val duplicateCount: Int = 0,
    val completedOrderCount: Int = 0,
    val visitedOrderCount: Int = 0,
    val uniqueVisitedUidCount: Int = 0,
    val lastScanAt: String? = null,
    val lastCompletedAt: String? = null
)

@Serializable
data class AdminTotals(
    val totalOrders: Int = 0,
    val completedOrders: Int = 0,
    val standardOrders: Int = 0,
    val sequenceOrders: Int = 0
)

@Serializable
data class AdminScans(
    val totalScanEvidenceCount: Int = 0,
    val standardMatchedCount: Int = 0,
    val sequenceStepCompletedCount: Int = 0,
    val mismatchCount: Int = 0,
    val outOfOrderCount: Int = 0,
    val duplicateCount: Int = 0,
    val uniqueVisitedUidCount: Int = 0
)

@Serializable
data class AdminWorkerSummary(
    val workerId: Int,
    val username: String,
    val currentAssignedOrderCount: Int = 0,
    val totalScanCount: Int = 0,
    val successfulScanCount: Int = 0,
    val mismatchCount: Int = 0,
    val outOfOrderCount: Int = 0,
    val duplicateCount: Int = 0,
    val completedOrderCount: Int = 0,
    val uniqueVisitedUidCount: Int = 0,
    val lastScanAt: String? = null
)

@Serializable
data class AdminAnalysisSummary(
    val totals: AdminTotals? = null,
    val scans: AdminScans? = null,
    val workers: List<AdminWorkerSummary>? = null
)