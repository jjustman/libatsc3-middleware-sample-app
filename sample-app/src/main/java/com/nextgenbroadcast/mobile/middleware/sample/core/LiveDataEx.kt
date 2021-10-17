package com.nextgenbroadcast.mobile.middleware.sample.core

import androidx.annotation.MainThread
import androidx.lifecycle.*

@MainThread
inline fun <X, Y, R> mapPair(sourceA: LiveData<X>, sourceB: LiveData<Y>, crossinline mapFunction: (Pair<X?, Y?>) -> R): LiveData<R> {
    val lastX = Variable<X>()
    val lastY = Variable<Y>()
    return MediatorLiveData<R>().apply {
        addSource(sourceA) { x ->
            lastX.value = x
            setValue(mapFunction(Pair(x, lastY.value)))
        }
        addSource(sourceB) { y ->
            lastY.value = y
            setValue(mapFunction(Pair(lastX.value, y)))
        }
    }
}

@MainThread
inline fun <X, Y, Z, R> mapTriple(sourceA: LiveData<X>, sourceB: LiveData<Y>, sourceC: LiveData<Z>, crossinline mapFunction: (Triple<X?, Y?, Z?>) -> R): LiveData<R> {
    val lastX = Variable<X>()
    val lastY = Variable<Y>()
    val lastZ = Variable<Z>()
    return MediatorLiveData<R>().apply {
        addSource(sourceA) { x ->
            lastX.value = x
            setValue(mapFunction(Triple(x, lastY.value, lastZ.value)))
        }
        addSource(sourceB) { y ->
            lastY.value = y
            setValue(mapFunction(Triple(lastX.value, y, lastZ.value)))
        }
        addSource(sourceC) { z ->
            lastZ.value = z
            setValue(mapFunction(Triple(lastX.value, lastY.value, z)))
        }
    }
}

class Variable<T> {
    var value: T? = null
}

inline fun <X, Y, R> LiveData<X>.mapWith(second: LiveData<Y>, crossinline transform: (Pair<X?, Y?>) -> R): LiveData<R> =
        mapPair(this, second) { transform(it) }

inline fun <X, Y, Z, R> LiveData<X>.mapWith(second: LiveData<Y>, third: LiveData<Z>, crossinline transform: (Triple<X?, Y?, Z?>) -> R): LiveData<R> =
        mapTriple(this, second, third) { transform(it) }

fun <X, Y> LiveData<X>.unite(second: LiveData<Y>): LiveData<Pair<X?, Y?>> =
        mapPair(this, second) { it }

fun <X, Y, Z> LiveData<X>.unite(second: LiveData<Y>, third: LiveData<Z>): LiveData<Triple<X?, Y?, Z?>> =
        mapTriple(this, second, third) { it }
