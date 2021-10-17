package com.nextgenbroadcast.mobile.middleware.service.init

import android.content.Context
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.util.Log
import com.nextgenbroadcast.mobile.core.model.PhyFrequency
import com.nextgenbroadcast.mobile.middleware.IAtsc3ReceiverCore
import com.nextgenbroadcast.mobile.middleware.atsc3.utils.*
import com.nextgenbroadcast.mobile.core.initialization.IFrequencyLocator
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

internal class FrequencyInitializer(
        private val receiver: IAtsc3ReceiverCore
) : IServiceInitializer {

    @Volatile
    private var isCanceled = false

    override suspend fun initialize(context: Context, components: Map<Class<*>, Pair<Int, String>>): Boolean {
        Log.d(TAG, "FrequencyInitializer::initalize - locator.locateFrequency before method invocation, location_request_day: $LOCATION_REQUEST_DELAY")

        val locators = components.filter { (clazz, _) ->
            IFrequencyLocator::class.java.isAssignableFrom(clazz)
        }.map { (clazz, data) ->
            val (resource) = data
            @Suppress("UNCHECKED_CAST")
            Pair(clazz as Class<IFrequencyLocator>, resource)
        }

        supervisorScope {
            var locationTaken = false
            var frequencyApplied = false

            val defaultTune = async {
                delay(FAST_TUNE_DELAY)

                if (!isActive || isCanceled) return@async

                frequencyApplied = true
                withContext(Dispatchers.Main) {
                    if (!isCanceled) {
                        receiver.tune(PhyFrequency.default(PhyFrequency.Source.AUTO))
                    }
                }
            }

            withTimeout(LOCATION_REQUEST_DELAY) {
                locators.forEach { (component, resource) ->
                    if (!isActive || isCanceled) return@forEach

                    try {
                        val defaultFrequencies: List<Int> = try {
                            readAttributes(context.resources.getXml(resource))
                        } catch (e: Resources.NotFoundException) {
                            emptyList()
                        }

                        val instance: Any = component.getDeclaredConstructor().newInstance()
                        val locator = instance as IFrequencyLocator

                        Log.d(TAG, "locator.locateFrequency before method invocation, location_request_day: $LOCATION_REQUEST_DELAY")

                        val frequencies = (defaultFrequencies + locator.locateFrequency(context)).distinct()
                        if (frequencies.isNotEmpty()) {
                            withContext(Dispatchers.Main) {
                                if (!isCanceled) {
                                    receiver.tune(PhyFrequency.auto(frequencies))
                                }
                            }
                            locationTaken = true
                        }

                        defaultTune.cancel()
                    } catch (e: TimeoutCancellationException) {
                        Log.w(TAG, "Location request timeout")
                    } catch (e: Resources.NotFoundException) {
                        Log.w(OnboardPhyInitializer.TAG, "Frequency resource reading error: ", e)
                    }

                    if (!isActive || locationTaken) return@forEach
                }
            }

            if (!locationTaken && !frequencyApplied) {
                withContext(Dispatchers.Main) {
                    Log.i(TAG, "locationTaken: $locationTaken, frequencyApplied: $frequencyApplied")
                    if (!isCanceled) {
                        receiver.tune(PhyFrequency.default(PhyFrequency.Source.AUTO))
                    }
                }
            }
        }

        return true
    }

    override fun cancel() {
        isCanceled = true
    }

    private fun readAttributes(parser: XmlResourceParser): List<Int> {
        val result = ArrayList<Int>()
        parser.use {
            parser.iterateDocument { name ->
                if (name == "frequencies") {
                    parser.iterateSubTags { subName ->
                        if (subName == "frequency") {
                            parser.readTextTag()?.toIntOrNull()?.let {
                                result.add(it * 1000)
                            }
                        } else {
                            parser.skipTag()
                        }
                    }
                } else {
                    parser.skipTag()
                }
            }
        }
        return result
    }

    companion object {
        val TAG: String = FrequencyInitializer::class.java.simpleName

        private val FAST_TUNE_DELAY = TimeUnit.SECONDS.toMillis(10)
        private val LOCATION_REQUEST_DELAY = TimeUnit.MINUTES.toMillis(5)
    }
}