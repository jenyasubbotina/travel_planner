package org.travelplanner.app.core

import kotlinx.serialization.json.Json
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.travelplanner.app.core.auth.AuthTokenManager
import org.travelplanner.app.data.BackgroundDrainScheduler
import org.travelplanner.app.data.DeltaSyncCoordinator
import org.travelplanner.app.data.EventRepository
import org.travelplanner.app.data.ExpenseRepository
import org.travelplanner.app.data.GlobalSyncManager
import org.travelplanner.app.data.NetworkState
import org.travelplanner.app.data.NetworkStateHolder
import org.travelplanner.app.data.OutboxAttachmentStorage
import org.travelplanner.app.data.OutboxDrainer
import org.travelplanner.app.data.OutboxRepository
import org.travelplanner.app.data.ParticipantRepository
import org.travelplanner.app.data.SyncTrigger
import org.travelplanner.app.data.TripDetailsScreenModel
import org.travelplanner.app.data.TripRepository
import org.travelplanner.app.db.MyDatabase
import org.travelplanner.app.features.profile.ui.ProfileScreenModel
import org.travelplanner.app.features.tripDetails.balance.BalanceScreenModel
import org.travelplanner.app.features.tripDetails.expenses.ExpenseFormScreenModel
import org.travelplanner.app.features.tripDetails.expenses.ExpensesScreenModel
import org.travelplanner.app.features.tripDetails.expenses.details.ExpenseDetailsScreenModel
import org.travelplanner.app.features.tripDetails.history.data.HistoryRepository
import org.travelplanner.app.features.tripDetails.history.ui.HistoryScreenModel
import org.travelplanner.app.features.tripDetails.more.MoreTabScreenModel
import org.travelplanner.app.features.tripDetails.more.checklist.data.ChecklistRepository
import org.travelplanner.app.features.tripDetails.more.checklist.ui.ChecklistScreenModel
import org.travelplanner.app.features.tripDetails.more.files.ui.FilesScreenModel
import org.travelplanner.app.features.tripDetails.more.participants.ui.ParticipantsScreenModel
import org.travelplanner.app.features.tripDetails.route.detailed.ui.EventDetailsScreenModel
import org.travelplanner.app.features.tripDetails.route.ui.ItineraryScreenModel
import org.travelplanner.app.features.tripDetails.summary.TripSummaryScreenModel
import org.travelplanner.app.features.tripList.CreateTripScreenModel
import org.travelplanner.app.features.tripList.TripListScreenModel

val commonModule =
    module {
        single { MyDatabase(get()) }

        single<Json> {
            Json {
                ignoreUnknownKeys = true
                isLenient = true
                encodeDefaults = true
            }
        }

        single { GlobalNotifier() }

        single { ReverseGeocoder(json = get()) }

        single {
            AuthTokenManager(
                sessionStore = get(),
                accountsStore = get(named("accounts")),
                json = get(),
                baseUrlProvider = { get<GatewayConfigManager>().baseUrl },
            )
        }

        single { UserSession(authTokenManager = get()) }

        single { SyncTrigger() }

        single { NetworkStateHolder() }

        single {
            val networkStateHolder = get<NetworkStateHolder>()
            val syncTrigger = get<SyncTrigger>()
            TripApiService(
                authTokenManager = get(),
                json = get(),
                gateway = get(),
                onConnectionLost = {
                    if (networkStateHolder.value != NetworkState.OFFLINE) {
                        networkStateHolder.value = NetworkState.OFFLINE
                        syncTrigger.requestSync()
                    }
                },
                httpClientConfig = getOrNull(),
            )
        }

        single { DeltaSyncCoordinator() }

        single { OutboxRepository(db = get(), json = get()) }

        single {
            ParticipantRepository(
                get(),
                get(),
                outbox = get(),
                syncTrigger = get(),
                userSession = get(),
                json = get(),
            )
        }

        single {
            TripRepository(
                db = get(),
                api = get(),
                outbox = get(),
                attachmentStorage = get<OutboxAttachmentStorage>(),
                userSession = get(),
                syncTrigger = get(),
                json = get(),
            )
        }
        single {
            EventRepository(
                db = get(),
                api = get(),
                outbox = get(),
                attachmentStorage = get<OutboxAttachmentStorage>(),
                userSession = get(),
                syncTrigger = get(),
                json = get(),
            )
        }

        single {
            ExpenseRepository(
                db = get(),
                api = get(),
                participantRepo = get(),
                outbox = get(),
                attachmentStorage = get<OutboxAttachmentStorage>(),
                userSession = get(),
                syncTrigger = get(),
                json = get(),
            )
        }

        single { HistoryRepository(get(), get()) }

        single {
            ChecklistRepository(
                db = get(),
                api = get(),
                outbox = get(),
                userSession = get(),
                syncTrigger = get(),
                json = get(),
            )
        }

        single {
            OutboxDrainer(
                outbox = get(),
                api = get(),
                tripRepo = get(),
                expenseRepo = get(),
                eventRepo = get(),
                checklistRepo = get(),
                attachmentStorage = get<OutboxAttachmentStorage>(),
                db = get(),
                syncTrigger = get(),
                deltaCoordinator = get(),
                networkState = get<NetworkStateHolder>().state,
                json = get(),
            )
        }

        single {
            GlobalSyncManager(
                userSession = get(),
                api = get(),
                tripRepo = get(),
                participantRepo = get(),
                expenseRepo = get(),
                eventRepo = get(),
                outbox = get(),
                outboxDrainer = get(),
                backgroundDrainScheduler = get<BackgroundDrainScheduler>(),
                networkStateHolder = get(),
                db = get(),
                syncTrigger = get(),
                deltaCoordinator = get(),
                json = get(),
            )
        }

        factory { (tripId: String) ->
            TripDetailsScreenModel(
                tripId,
                get(),
                get(),
                expenseRepo = get(),
                globalSyncManager = get(),
                checklistRepository = get(),
                tripRepo = get(),
                outbox = get(),
                userSession = get(),
            )
        }

        factory<TripSummaryScreenModel> { (tripId: String) ->
            TripSummaryScreenModel(
                tripId,
                get(),
                eventsRepository = get(),
                participantRepository = get(),
                expenseRepository = get(),
            )
        }

        factory { (tripId: String) ->
            ItineraryScreenModel(
                tripId,
                get(),
                eventsRepository = get(),
                participantRepository = get(),
                reverseGeocoder = get(),
            )
        }

        factory { (tripId: String, eventId: String) ->
            EventDetailsScreenModel(
                tripId = tripId,
                eventId = eventId,
                eventRepository = get(),
                participantRepository = get(),
                userSession = get(),
                tripRepository = get(),
                reverseGeocoder = get(),
            )
        }

        factory { (tripId: String) ->
            ExpensesScreenModel(
                tripId,
                get(),
                userSession = get(),
                participantRepository = get(),
                tripRepository = get(),
                globalNotifier = get(),
            )
        }

        factory { (expenseId: String, tripId: String) ->
            ExpenseDetailsScreenModel(
                expenseId,
                tripId,
                get(),
                participantRepository = get(),
                historyRepository = get(),
                userSession = get(),
                tripRepository = get(),
            )
        }

        factory { (tripId: String) ->
            ExpenseFormScreenModel(
                tripId,
                participantRepository = get(),
                expenseRepository = get(),
                userSession = get(),
                tripRepository = get(),
            )
        }

        factory { (tripId: String) ->
            BalanceScreenModel(
                tripId,
                get(),
                participantRepository = get(),
                userSession = get(),
                tripRepository = get(),
            )
        }

        factory { (tripId: String) ->
            MoreTabScreenModel(
                tripId,
                get(),
                userSession = get(),
                participantRepository = get(),
                globalSyncManager = get(),
                expenseRepository = get(),
                eventRepository = get(),
                checklistRepository = get(),
                globalNotifier = get(),
                json = get(),
            )
        }

        factory { (tripId: String) ->
            HistoryScreenModel(
                tripId = tripId,
                historyRepository = get(),
                participantRepository = get(),
            )
        }

        factory {
            TripListScreenModel(
                get(),
                userSession = get(),
                globalSyncManager = get(),
                participantRepository = get(),
                outbox = get(),
            )
        }
        factory {
            CreateTripScreenModel(
                get(),
                participantRepository = get(),
                userSession = get(),
            )
        }
        factory { params -> ChecklistScreenModel(params.get(), get(), get(), get()) }
        factory { params -> FilesScreenModel(params.get(), get(), get(), get(), json = get()) }
        factory { params ->
            ParticipantsScreenModel(
                params.get(),
                get(),
                tripRepository = get(),
                userSession = get(),
            )
        }
        factory { params -> ProfileScreenModel(get()) }
    }
