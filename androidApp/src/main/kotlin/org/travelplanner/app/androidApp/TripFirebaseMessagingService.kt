package org.travelplanner.app.androidApp

import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.travelplanner.app.core.RegisterDeviceRequest
import org.travelplanner.app.core.TripApiService
import org.travelplanner.app.core.auth.AuthTokenManager
import org.travelplanner.app.data.GlobalSyncManager
import org.travelplanner.app.data.ParticipantRepository

class TripFirebaseMessagingService : FirebaseMessagingService() {
    private val globalSyncManager: GlobalSyncManager by inject()
    private val api: TripApiService by inject()
    private val participantRepo: ParticipantRepository by inject()
    private val authTokenManager: AuthTokenManager by inject()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val data = message.data
        val eventType = data["eventType"]
        val invitationId = data["invitationId"]

        if (invitationId != null) {
            scope.launch {
                try {
                    participantRepo.acceptInvitation(invitationId)
                } catch (_: Exception) {
                }
            }
            showInviteNotification(message, invitationId, data["tripTitle"])
        }

        if (eventType in listOf("PARTICIPANT_ADDED", "PARTICIPANT_REMOVED", "TRIP_CREATED")) {
            globalSyncManager.forceFullRefresh()
        }

        if (eventType in
            listOf(
                "EXPENSE_PENDING_UPDATE_STORED",
                "EXPENSE_MERGED",
                "EXPENSE_PENDING_REVERTED",
                "EXPENSE_PENDING_REJECTED",
            )
        ) {
        }

        globalSyncManager.syncNow()
    }

    private fun showInviteNotification(
        message: RemoteMessage,
        invitationId: String,
        tripTitle: String?,
    ) {
        val title = message.notification?.title ?: "Приглашение в поездку"
        val body =
            tripTitle?.let { "Вас пригласили на планирование \"$it\"" }
                ?: message.notification?.body
                ?: "Вы были приглашены для планирования поездки"

        val launchIntent =
            Intent(this, AppActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        val pendingIntent =
            PendingIntent.getActivity(
                this,
                invitationId.hashCode(),
                launchIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        val notification =
            NotificationCompat
                .Builder(this, "trip_invites")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .build()

        try {
            NotificationManagerCompat.from(this).notify(invitationId.hashCode(), notification)
        } catch (_: SecurityException) {
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)

        if (authTokenManager.accessToken == null) return

        scope.launch {
            try {
                val device =
                    api.registerDevice(
                        RegisterDeviceRequest(
                            fcmToken = token,
                            deviceName = android.os.Build.MODEL,
                        ),
                    )
                api.currentDeviceId = device.id
            } catch (_: Exception) {
            }
        }
    }
}
