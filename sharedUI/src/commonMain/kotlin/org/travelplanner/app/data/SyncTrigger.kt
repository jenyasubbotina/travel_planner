package org.travelplanner.app.data

import kotlinx.coroutines.channels.Channel

class SyncTrigger {
    val signal: Channel<Unit> = Channel(Channel.CONFLATED)

    fun requestSync() {
        signal.trySend(Unit)
    }
}
