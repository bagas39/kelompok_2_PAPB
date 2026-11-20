package com.example.cloble.data.remote

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class SupabaseDataInvalidator {

    private val _userTrigger = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 4)
    val userTrigger: SharedFlow<Unit> = _userTrigger.asSharedFlow()

    private val _wardrobeTrigger = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 4)
    val wardrobeTrigger: SharedFlow<Unit> = _wardrobeTrigger.asSharedFlow()

    private val _discoveryTrigger = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 4)
    val discoveryTrigger: SharedFlow<Unit> = _discoveryTrigger.asSharedFlow()

    private val _notificationsTrigger = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 4)
    val notificationsTrigger: SharedFlow<Unit> = _notificationsTrigger.asSharedFlow()

    private val _chatTrigger = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 4)
    val chatTrigger: SharedFlow<Unit> = _chatTrigger.asSharedFlow()

    fun invalidateUser() {
        _userTrigger.tryEmit(Unit)
    }

    fun invalidateWardrobe() {
        _wardrobeTrigger.tryEmit(Unit)
    }

    fun invalidateDiscovery() {
        _discoveryTrigger.tryEmit(Unit)
    }

    fun invalidateNotifications() {
        _notificationsTrigger.tryEmit(Unit)
    }

    fun invalidateChat() {
        _chatTrigger.tryEmit(Unit)
    }

    fun invalidateAll() {
        _userTrigger.tryEmit(Unit)
        _wardrobeTrigger.tryEmit(Unit)
        _discoveryTrigger.tryEmit(Unit)
        _notificationsTrigger.tryEmit(Unit)
        _chatTrigger.tryEmit(Unit)
    }
}
