package icu.guestliang.nfcworkflow.network

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object AuthManager {
    private val _forceLogoutEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val forceLogoutEvent = _forceLogoutEvent.asSharedFlow()

    fun emitForceLogout() {
        _forceLogoutEvent.tryEmit(Unit)
    }
}
