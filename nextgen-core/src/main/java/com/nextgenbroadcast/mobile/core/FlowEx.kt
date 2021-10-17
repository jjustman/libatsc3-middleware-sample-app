package com.nextgenbroadcast.mobile.core

import kotlinx.coroutines.flow.*

fun <T> StateFlow<T>.asReadOnly(): StateFlow<T> {
    return if (this is MutableStateFlow<T>) {
        this.asStateFlow()
    } else {
        this
    }
}

fun <T> SharedFlow<T>.asReadOnly(): SharedFlow<T> {
    return if (this is MutableSharedFlow<T>) {
        this.asSharedFlow()
    } else {
        this
    }
}
