package com.streamforge.sdk.event

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

internal object EventBus {

    private val _events = MutableSharedFlow<StreamForgeEvent>(extraBufferCapacity = 64)

    val events: SharedFlow<StreamForgeEvent> = _events.asSharedFlow()

    fun emit(event: StreamForgeEvent) {
        _events.tryEmit(event)
    }
}
