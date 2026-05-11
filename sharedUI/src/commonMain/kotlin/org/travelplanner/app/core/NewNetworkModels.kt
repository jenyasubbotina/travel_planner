package org.travelplanner.app.core

import kotlinx.serialization.Serializable

// -- Trip --

@Serializable
data class V2CreateTripRequest(
    val title: String,
    val description: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val baseCurrency: String = "USD",
    val totalBudget: String? = null,
    val destination: String? = null,
    val imageUrl: String? = null,
    val id: String? = null,
    val clientMutationId: String? = null,
)

@Serializable
data class V2UpdateTripRequest(
    val title: String? = null,
    val description: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val baseCurrency: String? = null,
    val totalBudget: String? = null,
    val destination: String? = null,
    val imageUrl: String? = null,
    val status: String? = null,
    val expectedVersion: Long? = null,
    val clientMutationId: String? = null,
)

@Serializable
data class TripResponse(
    val id: String,
    val title: String,
    val description: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val baseCurrency: String,
    val totalBudget: String = "0",
    val destination: String = "",
    val imageUrl: String? = null,
    val joinCode: String = "",
    val status: String,
    val createdBy: String,
    val createdAt: String,
    val updatedAt: String,
    val version: Long,
    val deletedAt: String? = null,
)

// -- Participant --

@Serializable
data class ParticipantDetailResponse(
    val userId: String,
    val email: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val role: String,
    val joinedAt: String,
)

@Serializable
data class ParticipantResponse(
    val tripId: String,
    val userId: String,
    val role: String,
    val joinedAt: String,
)

@Serializable
data class InviteParticipantRequest(
    val email: String,
    val role: String = "EDITOR",
)

@Serializable
data class InvitationResponse(
    val id: String,
    val tripId: String,
    val email: String,
    val role: String,
    val status: String,
    val createdAt: String,
    val trip: TripSnapshot? = null,
)

@Serializable
data class TripSnapshot(
    val title: String,
    val destination: String,
    val startDate: String? = null,
    val endDate: String? = null,
    val imageUrl: String? = null,
    val baseCurrency: String,
)

@Serializable
data class ChangeRoleRequest(
    val role: String,
    val expectedVersion: Long? = null,
    val clientMutationId: String? = null,
)

// -- Itinerary --

@Serializable
data class V2CreateItineraryPointRequest(
    val title: String,
    val type: String,
    val category: String? = null,
    val description: String? = null,
    val subtitle: String? = null,
    val date: String? = null,
    val dayIndex: Int? = null,
    val startTime: String? = null,
    val endTime: String? = null,
    val duration: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String? = null,
    val cost: Double? = null,
    val actualCost: Double? = null,
    val status: String? = null,
    val participantIds: List<String>? = null,
    val id: String? = null,
    val clientMutationId: String? = null,
)

@Serializable
data class V2UpdateItineraryPointRequest(
    val title: String? = null,
    val description: String? = null,
    val subtitle: String? = null,
    val type: String? = null,
    val category: String? = null,
    val date: String? = null,
    val dayIndex: Int? = null,
    val startTime: String? = null,
    val endTime: String? = null,
    val duration: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String? = null,
    val cost: Double? = null,
    val actualCost: Double? = null,
    val status: String? = null,
    val participantIds: List<String>? = null,
    val expectedVersion: Long? = null,
    val clientMutationId: String? = null,
)

@Serializable
data class ItineraryPointResponse(
    val id: String,
    val tripId: String,
    val title: String,
    val description: String? = null,
    val subtitle: String? = null,
    val type: String? = null,
    val category: String? = null,
    val date: String? = null,
    val dayIndex: Int = 0,
    val startTime: String? = null,
    val endTime: String? = null,
    val duration: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String? = null,
    val cost: Double? = null,
    val actualCost: Double? = null,
    val status: String = "NONE",
    val participantIds: List<String> = emptyList(),
    val sortOrder: Int,
    val createdBy: String,
    val createdAt: String,
    val updatedAt: String,
    val version: Long,
    val deletedAt: String? = null,
)

@Serializable
data class AddPointLinkRequest(
    val title: String,
    val url: String,
)

@Serializable
data class PointLinkResponse(
    val id: String,
    val pointId: String,
    val title: String,
    val url: String,
    val sortOrder: Int,
    val createdAt: String,
)

@Serializable
data class AddPointCommentRequest(
    val text: String,
    val id: String? = null,
    val clientMutationId: String? = null,
)

@Serializable
data class PointCommentResponse(
    val id: String,
    val pointId: String,
    val authorUserId: String,
    val authorDisplayName: String,
    val text: String,
    val createdAt: String,
)

// -- Checklist --

@Serializable
data class V2CreateChecklistItemRequest(
    val title: String,
    val isGroup: Boolean,
    val id: String? = null,
    val clientMutationId: String? = null,
)

@Serializable
data class ChecklistItemResponse(
    val id: String,
    val tripId: String,
    val title: String,
    val isGroup: Boolean,
    val ownerUserId: String,
    val completedBy: List<String>,
    val createdAt: String,
    val updatedAt: String,
)

// -- Join by code --

@Serializable
data class V2JoinByCodeRequest(
    val code: String,
)

@Serializable
data class JoinRequestUserResponse(
    val userId: String,
    val displayName: String,
    val email: String,
)

@Serializable
data class RegenerateCodeResponse(
    val newCode: String,
)

@Serializable
data class ReorderRequest(
    val items: List<ReorderItem>,
)

@Serializable
data class ReorderItem(
    val id: String,
    val sortOrder: Int,
)

// -- Expense --

@Serializable
data class V2CreateExpenseRequest(
    val title: String,
    val description: String? = null,
    val amount: String,
    val currency: String,
    val category: String,
    val payerUserId: String,
    val expenseDate: String,
    val splitType: String = "EQUAL",
    val splits: List<V2ExpenseSplitRequest>,
    val id: String? = null,
    val clientMutationId: String? = null,
)

@Serializable
data class V2ExpenseSplitRequest(
    val participantUserId: String,
    val value: String,
)

@Serializable
data class V2UpdateExpenseRequest(
    val title: String? = null,
    val description: String? = null,
    val amount: String? = null,
    val currency: String? = null,
    val category: String? = null,
    val payerUserId: String? = null,
    val expenseDate: String? = null,
    val splitType: String? = null,
    val splits: List<V2ExpenseSplitRequest>? = null,
    val expectedVersion: Long? = null,
    val clientMutationId: String? = null,
)

@Serializable
data class ExpenseResponse(
    val id: String,
    val tripId: String,
    val payerUserId: String,
    val title: String,
    val description: String? = null,
    val amount: String,
    val currency: String,
    val category: String,
    val expenseDate: String,
    val splitType: String,
    val splits: List<ExpenseSplitResponse>,
    val createdBy: String,
    val createdAt: String,
    val updatedAt: String,
    val version: Long,
    val deletedAt: String? = null,
    val pendingUpdate: V2ExpensePendingUpdateResponse? = null,
)

@Serializable
data class V2ExpensePendingUpdateResponse(
    val expenseId: String,
    val proposedByUserId: String,
    val proposedAt: String,
    val baseVersion: Long,
    val payload: String,
    val baseSnapshot: String? = null,
)

@Serializable
data class V2MergeExpenseRequest(
    val title: String? = null,
    val description: String? = null,
    val amount: String? = null,
    val currency: String? = null,
    val category: String? = null,
    val payerUserId: String? = null,
    val expenseDate: String? = null,
    val splitType: String? = null,
    val splits: List<V2ExpenseSplitRequest>? = null,
)

@Serializable
data class ExpenseSplitResponse(
    val id: String,
    val participantUserId: String,
    val shareType: String,
    val value: String,
    val amountInExpenseCurrency: String,
)

// -- Analytics --

@Serializable
data class BalanceResponse(
    val userId: String,
    val totalPaid: String,
    val totalOwed: String,
    val netBalance: String,
)

@Serializable
data class SettlementResponse(
    val fromUserId: String,
    val toUserId: String,
    val amount: String,
    val currency: String,
)

@Serializable
data class StatisticsResponse(
    val totalSpent: String,
    val currency: String,
    val spentByCategory: Map<String, String>,
    val spentByParticipant: Map<String, String>,
    val spentByDay: Map<String, String>,
)

// -- Attachment --

@Serializable
data class PresignUploadRequest(
    val fileName: String,
    val contentType: String,
    val fileSize: Long,
    val tripId: String,
    val attachmentId: String? = null,
)

@Serializable
data class PresignedUploadResponse(
    val uploadUrl: String,
    val s3Key: String,
)

@Serializable
data class CreateAttachmentRequest(
    val fileName: String,
    val fileSize: Long,
    val mimeType: String,
    val s3Key: String,
    val id: String? = null,
    val clientMutationId: String? = null,
)

@Serializable
data class AttachmentResponse(
    val id: String,
    val tripId: String,
    val expenseId: String? = null,
    val pointId: String? = null,
    val uploadedBy: String,
    val fileName: String,
    val fileSize: Long,
    val mimeType: String,
    val s3Key: String,
    val createdAt: String,
    val deletedAt: String? = null,
)

@Serializable
data class PresignDownloadRequest(
    val s3Key: String,
)

@Serializable
data class PresignedDownloadResponse(
    val url: String,
    val expiresInSeconds: Long,
)

// -- History --

@Serializable
data class HistoryEntryResponse(
    val id: String,
    val tripId: String,
    val userId: String,
    val actionType: String,
    val entityType: String,
    val entityId: String,
    val details: String,
    val timestamp: Long,
)

// -- Sync --

@Serializable
data class SnapshotResponse(
    val trip: TripResponse,
    val participants: List<ParticipantResponse>,
    val itineraryPoints: List<ItineraryPointResponse>,
    val expenses: List<ExpenseResponse>,
    val attachments: List<AttachmentResponse>,
    val checklistItems: List<ChecklistItemResponse> = emptyList(),
    val pendingJoinRequests: List<JoinRequestUserResponse> = emptyList(),
    val historyEntries: List<HistoryEntryResponse> = emptyList(),
    val pointLinks: List<PointLinkResponse> = emptyList(),
    val pointComments: List<PointCommentResponse> = emptyList(),
    val cursor: String,
)

@Serializable
data class DeltaResponse(
    val trips: List<TripResponse>,
    val participants: List<ParticipantResponse>,
    val itineraryPoints: List<ItineraryPointResponse>,
    val expenses: List<ExpenseResponse>,
    val attachments: List<AttachmentResponse>,
    val checklistItems: List<ChecklistItemResponse> = emptyList(),
    val pendingJoinRequests: List<JoinRequestUserResponse> = emptyList(),
    val historyEntries: List<HistoryEntryResponse> = emptyList(),
    val pointLinks: List<PointLinkResponse> = emptyList(),
    val pointComments: List<PointCommentResponse> = emptyList(),
    val cursor: String,
)

// -- Device --

@Serializable
data class RegisterDeviceRequest(
    val fcmToken: String,
    val deviceName: String,
)

@Serializable
data class DeviceResponse(
    val id: String,
    val fcmToken: String,
    val deviceName: String? = null,
    val createdAt: String,
)
