package org.travelplanner.app.features.tripDetails.more

import org.travelplanner.app.core.AppUser
import org.travelplanner.app.core.UiEffect
import org.travelplanner.app.core.UiIntent
import org.travelplanner.app.core.UiState
import org.travelplanner.app.domain.ChecklistItem
import org.travelplanner.app.domain.Participant
import org.travelplanner.app.domain.PendingUser
import org.travelplanner.app.domain.Trip
import org.travelplanner.app.domain.TripMediaItem

data class MoreState(
    val trip: Trip? = null,
    val list: List<Participant> = emptyList(),
    val pendingRequests: List<PendingUser> = emptyList(),
    val isAddDialogVisible: Boolean = false,
    val currentUser: AppUser? = null,
    val mediaItems: List<TripMediaItem> = emptyList(),
    val checklist: List<ChecklistItem> = emptyList(),
    val isInviteEmailDialogVisible: Boolean = false,
    val isInviteInFlight: Boolean = false,
    val lastCreatedInvitationId: String? = null,
) : UiState

sealed interface MoreTabIntent : UiIntent {
    data class ResolveRequest(
        val userId: String,
        val approve: Boolean,
    ) : MoreTabIntent

    data object RegenerateCode : MoreTabIntent

    data class ArchiveTrip(
        val isArchived: Boolean,
    ) : MoreTabIntent

    data object DeleteOrLeaveTrip : MoreTabIntent

    data class UploadFile(
        val bytes: ByteArray,
        val fileName: String,
    ) : MoreTabIntent

    data class ToggleChecklistItem(
        val itemId: String,
    ) : MoreTabIntent

    data object ShowAddDialog : MoreTabIntent

    data object HideAddDialog : MoreTabIntent

    data object ShowInviteEmailDialog : MoreTabIntent

    data object HideInviteEmailDialog : MoreTabIntent

    data class InviteByEmail(
        val email: String,
        val role: String,
    ) : MoreTabIntent

    data object AcknowledgeInvitationShared : MoreTabIntent

    data object DismissMessage : MoreTabIntent
}

sealed interface MoreTabEffect : UiEffect {
    data class ShowMessage(
        val message: String,
    ) : MoreTabEffect
}
