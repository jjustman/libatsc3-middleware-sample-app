package com.nextgenbroadcast.mobile.middleware.scoreboard

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
inline fun <X, Y, R> mapPairWithCallbacks(
    sourceA: LiveData<X>,
    sourceB: LiveData<Y>,
    crossinline onFirstChanged: (current: R?,old: X?, new: X?, second: Y?) -> R?,
    crossinline onSecondChanged: (current: R?,old: Y?, new: Y?, first: X?) -> R?,
    crossinline onBothChanged: (current: R?,old1: X?, new1: X?, old2: Y?, new2: Y?) -> R?
): LiveData<R> {
    val diff = Diff<X, Y, R>(sourceA.value, sourceA.value, sourceB.value, sourceB.value)
    return MediatorLiveData<R>().apply {
        addSource(sourceA) { x ->
            diff.changeFirst(x)
            setValue(diff(value, onFirstChanged, onSecondChanged, onBothChanged))
        }
        addSource(sourceB) { y ->
            diff.changeSecond(y)
            setValue(diff(value, onFirstChanged, onSecondChanged, onBothChanged))
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

class Diff<X, Y, R>(var old1Value: X?, var new1Value: X?, var old2Value: Y?, var new2Value: Y?) {

    fun changeFirst(value: X?) {
        old1Value = new1Value
        new1Value = value
    }

    fun changeSecond(value: Y?) {
        old2Value = new2Value
        new2Value = value
    }

    inline operator fun invoke(
        currentValue: R?,
        crossinline onFirstChanged: (current: R?,old: X?, new: X?, second: Y?) -> R?,
        crossinline onSecondChanged: (current: R?,old: Y?, new: Y?, first: X?) -> R?,
        crossinline onBothChanged: (current: R?,old1: X?, new1: X?, old2: Y?, new2: Y?) -> R?
    ): R? {
        return when {
            old1Value != new1Value && old2Value != new2Value -> onBothChanged(currentValue, old1Value, new1Value, old2Value, new2Value)
            old1Value != new1Value -> onFirstChanged(currentValue, old1Value, new1Value, new2Value)
            old2Value != new2Value -> onSecondChanged(currentValue, old2Value, new2Value, new1Value)
            else -> currentValue
        }
    }

}

inline fun <X, Y, R> LiveData<X>.mapWithCallback(
    second: LiveData<Y>,
    crossinline onFirstChanged: (prev: R?, old: X?, new: X?, second: Y?) -> R? = { p, _, _, _ -> p },
    crossinline onSecondChanged: (prev: R?, old: Y?, new: Y?, first: X?) -> R? = { p, _, _, _ -> p },
    crossinline onBothChanged: (prev: R?, old1: X?, new1: X?, old2: Y?, new2: Y?) -> R? = { p, _, _, _, _ -> p }
): LiveData<R> = mapPairWithCallbacks(this, second, onFirstChanged, onSecondChanged, onBothChanged)

inline fun <X, Y, R> LiveData<X>.mapWith(second: LiveData<Y>, crossinline transform: (Pair<X?, Y?>) -> R): LiveData<R> =
        mapPair(this, second) { transform(it) }

inline fun <X, Y, Z, R> LiveData<X>.mapWith(second: LiveData<Y>, third: LiveData<Z>, crossinline transform: (Triple<X?, Y?, Z?>) -> R): LiveData<R> =
        mapTriple(this, second, third) { transform(it) }

fun <X, Y> LiveData<X>.unite(second: LiveData<Y>): LiveData<Pair<X?, Y?>> =
        mapPair(this, second) { it }

fun <X, Y, Z> LiveData<X>.unite(second: LiveData<Y>, third: LiveData<Z>): LiveData<Triple<X?, Y?, Z?>> =
        mapTriple(this, second, third) { it }
