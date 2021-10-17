package com.nextgenbroadcast.mobile.middleware.scoreboard

import android.location.Location
import androidx.lifecycle.*
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.entity.TelemetryEvent
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.reader.ErrorData
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.reader.LocationData
import com.nextgenbroadcast.mobile.middleware.scoreboard.entities.*
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.floor

class SharedViewModel : ViewModel() {
    private val _deviceFlowMap = MutableLiveData<Map<String, ChartData>>(emptyMap())
    private val _telemetryDeviceList: MutableLiveData<List<TelemetryDevice>> = MutableLiveData(emptyList())
    private val _chartDeviceIdList = MutableLiveData<List<String>>(emptyList())
    private val _locationData = MutableLiveData<Map<String, LocationDataAndTime>>(emptyMap())
    val currentDeviceLiveData = MutableLiveData<LocationData?>(null)
    val selectedCommandTarget = MutableLiveData<CommandTarget?>(null)
    val targetCommandLiveData: MediatorLiveData<List<CommandTarget>> = MediatorLiveData()
    val telemetryCommands = MutableLiveData(
        listOf(
            TelemetryEntity("acceleration"),
            TelemetryEntity("gyroscope"),
            TelemetryEntity("motion"),
            TelemetryEntity("step_detector"),
            TelemetryEntity("step_counter"),
            TelemetryEntity("rotation"),
            TelemetryEntity(TelemetryEvent.EVENT_TOPIC_BATTERY),
            TelemetryEntity(TelemetryEvent.EVENT_TOPIC_LOCATION),
            TelemetryEntity(TelemetryEvent.EVENT_TOPIC_PHY)
        )
    )

    private val locationCalculationResult = FloatArray(1)
    private val errorData = MutableLiveData<Map<String, ErrorData>>()

    private val _distanceLiveData: LiveData<Map<String, Float?>?> = currentDeviceLiveData
        .mapWithCallback(
            second = _locationData,
            onFirstChanged = { distanceMap, oldLocation, newLocation, devicesLocationMap ->
                calculateDistance(
                    distanceMap = distanceMap?.toMutableMap(),
                    oldMyLocation = oldLocation,
                    newMyLocation = newLocation ?: return@mapWithCallback distanceMap,
                    newDevicesLocation = devicesLocationMap ?: return@mapWithCallback distanceMap,
                    oldDevicesLocation = devicesLocationMap
                )
            },
            onSecondChanged = { distanceMap, oldDevicesLocation, newDevicesLocation, currentLocation ->
                calculateDistance(
                    distanceMap = distanceMap?.toMutableMap(),
                    oldMyLocation = currentLocation ?: return@mapWithCallback distanceMap,
                    newMyLocation = currentLocation,
                    newDevicesLocation = newDevicesLocation ?: return@mapWithCallback distanceMap,
                    oldDevicesLocation = oldDevicesLocation
                )
            },
            onBothChanged = { distanceMap, oldLocation, newLocation, oldDevicesLocation, newDevicesLocation ->
                calculateDistance(
                    distanceMap = distanceMap?.toMutableMap(),
                    oldMyLocation = oldLocation,
                    newMyLocation = newLocation ?: return@mapWithCallback distanceMap,
                    newDevicesLocation = newDevicesLocation ?: return@mapWithCallback distanceMap,
                    oldDevicesLocation = oldDevicesLocation
                )
            }
        )

    val devicesToAdd = _chartDeviceIdList.mapWith(_deviceFlowMap) { (devices, deviceToFlow) ->
        devices?.subtract(deviceToFlow?.keys ?: emptyList())
    }.distinctUntilChanged()

    val devicesToRemove = _chartDeviceIdList.mapWith(_deviceFlowMap) { (devices, deviceFlows) ->
        deviceFlows?.keys?.subtract(devices ?: emptyList())
    }.distinctUntilChanged()

    private val deviceInfoListMediator = MediatorLiveData<List<DeviceScoreboardInfo>>()
    val deviceInfoList = deviceInfoListMediator.distinctUntilChanged()

    val chartDeviceInfoList = _chartDeviceIdList.mapWith(deviceInfoList) { (chartList, deviceList) ->
        deviceList?.filter { info -> chartList?.contains(info.device.id) ?: false }
    }.distinctUntilChanged()

    val chartDeviceInfoWithFlowList = chartDeviceInfoList.mapWith(_deviceFlowMap, _distanceLiveData) { (chartList, flowMap, distance) ->
        chartList?.filter { flowMap?.containsKey(it.device.id) ?: false }?.map { deviceInfo ->
            ChartGeneralInfo(
                deviceId = deviceInfo.device.id,
                isLost = deviceInfo.device.isLost,
                distance = distance?.get(deviceInfo.device.id),
                chartData = flowMap?.get(deviceInfo.device.id),
                errorData = deviceInfo.errorData
            )
        }
    }

    val selectedDeviceId: MutableLiveData<String?> = MutableLiveData()

    val selectionState: LiveData<Pair<Boolean, Boolean>> = deviceInfoList.map { deviceList ->
        val deviceWithFlowCount = deviceList.filter { (_, hasFlow) -> hasFlow }.size
        Pair(deviceWithFlowCount > 0, deviceWithFlowCount == deviceList.size)
    }.distinctUntilChanged()

    private val deviceInfoMap = mutableMapOf<String, DeviceScoreboardInfo>()

    init {
        deviceInfoListMediator.addSource(_telemetryDeviceList) { devices ->
            devices.forEach { device ->
                deviceInfoMap.compute(device.id) { _, deviceInfo ->
                    deviceInfo?.copy(device = device) ?: DeviceScoreboardInfo(device = device, selected = false)
                }
            }
            deviceInfoListMediator.value = deviceInfoMap.values.toList()
        }
        deviceInfoListMediator.addSource(_deviceFlowMap) { flowMap ->
            deviceInfoMap.forEach { (id, deviceInfo) ->
                deviceInfoMap.computeIfPresent(id) { _, _ ->
                    deviceInfo.copy(selected = flowMap?.containsKey(id) ?: false)
                }
            }
            deviceInfoListMediator.value = deviceInfoMap.values.toList()
        }
        deviceInfoListMediator.addSource(_distanceLiveData) { distanceMap ->
            distanceMap?.forEach { (id, distance) ->
                deviceInfoMap.computeIfPresent(id) { _, deviceInfo ->
                    deviceInfo.copy(distance = distance)
                }
            }
            deviceInfoListMediator.value = deviceInfoMap.values.toList()
        }
        deviceInfoListMediator.addSource(errorData){ errorMap ->
            errorMap.forEach{ (id, error) ->
                deviceInfoMap.computeIfPresent(id){ _, deviceInfo ->
                    deviceInfo.copy(errorData = error)
                }
            }
            deviceInfoListMediator.value = deviceInfoMap.values.toList()
        }

        targetCommandLiveData.value = initialTargetSetup()
        targetCommandLiveData.addSource(deviceInfoList) { deviceInfoList ->
            targetCommandLiveData.value = mutableListOf(
                CommandTarget.SelectedDevices(
                    deviceIdList = deviceInfoList
                        .filter { it.selected }
                        .map { it.device.id }
                ),
                CommandTarget.Broadcast
            ).apply {
                deviceInfoList.map { deviceInfo ->
                    CommandTarget.Device(deviceInfo.device.id)
                }.let { commands ->
                    addAll(commands)
                }
            }.also {
                // update value in case devices has been selected
                if (selectedCommandTarget.value is CommandTarget.SelectedDevices) {
                    selectedCommandTarget.value = it.first()
                }
            }
        }
    }

    private fun initialTargetSetup(): List<CommandTarget> = listOf(
        CommandTarget.SelectedDevices(
            deviceIdList = deviceInfoList.value
                ?.filter { it.selected }
                ?.map { it.device.id }
                ?: emptyList()
        ),
        CommandTarget.Broadcast
    )

    private fun calculateDistance(
        distanceMap: MutableMap<String, Float?>?,
        oldMyLocation: LocationData?,
        newMyLocation: LocationData,
        newDevicesLocation: Map<String, LocationDataAndTime>,
        oldDevicesLocation: Map<String, LocationDataAndTime>?
    ): Map<String, Float?> {
        return if (distanceMap == null || oldMyLocation != newMyLocation) {

            // we should recalculate all records:
            // 1. When it's first calculation (aka. distanceMap is equals to null)
            // 2. When location of the host device is changed, so all data is invalid

            if (distanceMap != null && oldMyLocation != null
                && !isLocationDataChangedSignificantly(newMyLocation, oldMyLocation)
                && newDevicesLocation.keys == oldDevicesLocation?.keys
            ) {
                // if current device location changed not significantly we should
                // check if devices location is still valid
                calculateDevicesLocation(distanceMap, newMyLocation, newDevicesLocation, oldDevicesLocation)
                return distanceMap
            }
            // Current device location is changed and we should recalculate all data
            val map = distanceMap ?: mutableMapOf()
            val devicesLocationMap = newDevicesLocation.toMutableMap()
            invalidateOldLocationDevices(map, devicesLocationMap)
            calculateDistance(map, devicesLocationMap, newMyLocation)
            return map
        } else {
            // means that current device location is not changed, so we should check if
            // devices location is still valid
            calculateDevicesLocation(distanceMap, newMyLocation, newDevicesLocation, oldDevicesLocation)
            distanceMap
        }
    }

    private fun calculateDevicesLocation(
        distanceMap: MutableMap<String, Float?>,
        newMyLocation: LocationData,
        newDevicesLocation: Map<String, LocationDataAndTime>,
        oldDevicesLocation: Map<String, LocationDataAndTime>?
    ) {
        val diff = newDevicesLocation.toMutableMap()
        invalidateOldLocationDevices(distanceMap, diff)
        // finding diff in devices location
        oldDevicesLocation?.forEach { (id, locationData) ->
            // remove devices that has same location as in prev. calculation
            val newLocationData = diff[id]
            if (newLocationData != null && !isLocationDataChangedSignificantly(
                    newLocationData.location,
                    locationData.location
                )
            ) {
                diff.remove(id)
            }
        }

        // now diff contains only locations that should be recalculated
        if (diff.isNotEmpty()) {
            calculateDistance(distanceMap, diff, newMyLocation)
        }
    }

    /**
     * Removes device from the [devicesLocationMap] and its distance form the [distanceMap]
     * in case its last location update was more than [INVALIDATE_LOCATION_TIME_DIFF] before
     */
    private fun invalidateOldLocationDevices(
        distanceMap: MutableMap<String, Float?>,
        devicesLocationMap: MutableMap<String, LocationDataAndTime>
    ) {
        devicesLocationMap
            .filter { (_, locationData) ->
                System.currentTimeMillis() - locationData.timestamp >= INVALIDATE_LOCATION_TIME_DIFF
            }
            .map { it.key }
            .forEach { id ->
                devicesLocationMap.remove(id)
                distanceMap.remove(id)
            }
    }

    /**
     * Compares two coordinates.
     *
     * 1second it is ~ 31m, so if changes is in second and it's less or equals to 0.1 second we could
     * ignore those changes and don't recalculate all distances
     */
    private fun isCoordinateChangedSignificantly(first: Double, second: Double): Boolean {
        var firstCoordinate = abs(first)
        var secondCoordinate = abs(second)
        val firstDegrees = floor(firstCoordinate)
        val secondDegrees = floor(secondCoordinate)

        if (firstDegrees != secondDegrees) return true

        firstCoordinate -= firstDegrees
        firstCoordinate *= 60
        secondCoordinate -= secondDegrees
        secondCoordinate *= 60
        val firstMinutes = floor(firstCoordinate)
        val secondMinutes = floor(secondCoordinate)

        if (firstMinutes != secondMinutes) return true

        firstCoordinate -= firstMinutes
        firstCoordinate *= 60
        secondCoordinate -= secondMinutes
        secondCoordinate *= 60


        return abs(firstCoordinate - secondCoordinate) > SKIP_SECONDS
    }

    private fun isLocationDataChangedSignificantly(first: LocationData, second: LocationData): Boolean {
        return isCoordinateChangedSignificantly(first.lat, second.lat)
            || isCoordinateChangedSignificantly(first.lng, second.lng)
    }

    private fun calculateDistance(
        holder: MutableMap<String, Float?>,
        devicesData: Map<String, LocationDataAndTime>,
        currentLocation: LocationData
    ) {
        devicesData.forEach { (id, locationAndTime) ->
            Location.distanceBetween(
                locationAndTime.location.lat,
                locationAndTime.location.lng,
                currentLocation.lat,
                currentLocation.lng,
                locationCalculationResult
            )
            holder[id] = locationCalculationResult.first()
        }
    }

    fun addDeviceLocation(deviceId: String, event: TelemetryEvent) {
        val payload = (event.payload as? LocationData) ?: return
        _locationData.value = _locationData.value?.toMutableMap()?.apply {
            put(deviceId, LocationDataAndTime(payload))
        } ?: mapOf(deviceId to LocationDataAndTime(payload))
    }

    fun addDeviceError(deviceId: String, event: TelemetryEvent) {
        val payload = (event.payload as? ErrorData) ?: return
        errorData.value = errorData.value?.toMutableMap()?.apply {
            put(deviceId, payload)
        } ?: mapOf(deviceId to payload)
    }

    fun setDeviceSelection(deviceId: String?) {
        selectedDeviceId.value = deviceId
    }

    fun setDevicesList(deviceIds: List<TelemetryDevice>) {
        _telemetryDeviceList.value = deviceIds
    }

    fun addDeviceChart(deviceId: String) {
        val list = _chartDeviceIdList.value?.toMutableList() ?: mutableListOf()
        if (!list.contains(deviceId)) {
            list.add(deviceId)
            _chartDeviceIdList.value = list
        }
    }

    fun removeDeviceChart(deviceId: String) {
        val list = _chartDeviceIdList.value?.toMutableList() ?: return
        list.remove(deviceId)
        _chartDeviceIdList.value = list
        synchronizeChartSelection(deviceId)
    }

    private fun synchronizeChartSelection(deviceId: String) {
        if (selectedDeviceId.value == deviceId) {
            selectedDeviceId.value = null
        }
    }

    fun addOrReplaceChartData(list: List<Pair<String, ChartData>>) {
        val map = _deviceFlowMap.value?.toMutableMap() ?: mutableMapOf()
        list.forEach { (deviceId, flow) ->
            map[deviceId] = flow
        }
        _deviceFlowMap.postValue(map)
    }

    fun removeFlows(list: List<String>) {
        val map = _deviceFlowMap.value?.toMutableMap() ?: return
        list.forEach { deviceId ->
            map.remove(deviceId)
        }
        _deviceFlowMap.postValue(map)
    }

    fun removeFlow(deviceId: String) {
        val map = _deviceFlowMap.value?.toMutableMap() ?: return
        map.remove(deviceId)
        _deviceFlowMap.postValue(map)
    }

    fun selectAllDevices(isChecked: Boolean) {
        val list = _chartDeviceIdList.value?.toMutableList() ?: mutableListOf()
        deviceInfoList.value?.forEach { (device) ->
            if (isChecked) {
                if (!list.contains(device.id)) {
                    list.add(device.id)
                }
            } else {
                list.remove(device.id)
                synchronizeChartSelection(device.id)
            }
        }
        _chartDeviceIdList.value = list
    }

    fun selectCommand(position: Int, selected: Boolean) {
        telemetryCommands.value = telemetryCommands.value?.toMutableList()?.apply {
            set(position, get(position).copy(checked = selected))
        }
    }

    fun restoreTelemetryCommands(data: String) {
        val commands = data.split(",")
        telemetryCommands.value = telemetryCommands.value?.toMutableList()?.apply {
            commands.forEach { command ->
                val index = indexOfFirst { it.name.contentEquals(command, true) }
                if (index != -1) {
                    set(index, get(index).copy(checked = true))
                }
            }
        }
    }

    companion object {
        private const val SKIP_SECONDS = 0.1
        private val INVALIDATE_LOCATION_TIME_DIFF = TimeUnit.MINUTES.toMillis(5)
        private const val MAX_ERRORS_LIST_SIZE = 30
    }

}