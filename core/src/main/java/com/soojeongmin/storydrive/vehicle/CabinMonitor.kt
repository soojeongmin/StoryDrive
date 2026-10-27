package com.soojeongmin.storydrive.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.HvacAcStateListener
import ai.pleos.playground.vehicle.listener.HvacPowerStateListener
import ai.pleos.playground.vehicle.listener.HvacTemperatureListener
import ai.pleos.playground.vehicle.listener.LightStateListener
import ai.pleos.playground.vehicle.listener.SeatBeltBuckledStateListener
import ai.pleos.playground.vehicle.listener.SeatOccupancyListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import ai.pleos.playground.vehicle.listener.WiperStateListener
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class SeatPosition(val value: SeatValue) {
    DRIVER(SeatValue.ROW_1_LEFT),
    FRONT_PASSENGER(SeatValue.ROW_1_RIGHT),
    REAR_LEFT(SeatValue.ROW_2_LEFT),
    REAR_CENTER(SeatValue.ROW_2_CENTER),
    REAR_RIGHT(SeatValue.ROW_2_RIGHT),
}

data class SeatState(val occupied: Boolean = false, val buckled: Boolean = false)

enum class Turn { OFF, LEFT, RIGHT }

data class CabinState(
    val connected: Boolean = false,
    val parked: Boolean = true,
    val seats: Map<SeatPosition, SeatState> = emptyMap(),
    val headlights: Boolean = false,
    val wiper: Boolean = false,
    val turn: Turn = Turn.OFF,
    val speedKph: Float = 0f,
    val acOn: Boolean = false,
    val hvacOn: Boolean = false,
    val setTemperatureC: Float? = null,
    val uxRestrictions: Int = 0,
    val maxStringLength: Int = 0,
) {
    val setupLocked get() = !parked || uxRestrictions and (CarUxRestrictions.UX_RESTRICTIONS_NO_KEYBOARD or CarUxRestrictions.UX_RESTRICTIONS_NO_SETUP) != 0

    val limitText get() = uxRestrictions and (CarUxRestrictions.UX_RESTRICTIONS_LIMIT_STRING_LENGTH or CarUxRestrictions.UX_RESTRICTIONS_LIMIT_CONTENT) != 0

    val heaterOn get() = hvacOn && !acOn && (setTemperatureC ?: 0f) >= HEATER_MIN_C

    private companion object {
        const val HEATER_MIN_C = 25f
    }
}

class CabinMonitor(private val context: Context) {
    private val _state = MutableStateFlow(CabinState())
    val state: StateFlow<CabinState> = _state.asStateFlow()

    private var vehicle: Vehicle? = null
    private val seatListeners = mutableListOf<Pair<SeatArea, Any>>()

    private var car: Car? = null

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) = setParked(gear == VehicleGear.GEAR_PARK)
        override fun onFailed(e: Exception) = Unit
    }

    private val lowBeamListener = object : LightStateListener {
        override fun onLightStateUpdated(state: LightState?) = _state.update { it.copy(headlights = state == LightState.ON) }
        override fun onFailed(e: Exception) = Unit
    }

    private val wiperListener = object : WiperStateListener {
        override fun onWiperStateUpdated(state: WiperState?) = _state.update { it.copy(wiper = state == WiperState.ON) }
        override fun onFailed(e: Exception) = Unit
    }

    private val turnListener = object : TurnSignalStateListener {
        override fun onTurnSignalStateUpdated(value: TurnSignalValue?) = _state.update {
            it.copy(turn = when (value) {
                TurnSignalValue.LEFT -> Turn.LEFT
                TurnSignalValue.RIGHT -> Turn.RIGHT
                else -> Turn.OFF
            })
        }
        override fun onFailed(e: Exception) = Unit
    }

    private val acListener = object : HvacAcStateListener {
        override fun onHvacAcStateUpdated(on: Boolean?) = _state.update { it.copy(acOn = on == true) }
        override fun onFailed(e: Exception) = Unit
    }

    private val powerListener = object : HvacPowerStateListener {
        override fun onHvacPowerStateUpdated(on: Boolean?) = _state.update { it.copy(hvacOn = on == true) }
        override fun onFailed(e: Exception) = Unit
    }

    private val temperatureListener = object : HvacTemperatureListener {
        override fun onHvacTemperatureUpdated(value: Float?) = _state.update { it.copy(setTemperatureC = value) }
        override fun onFailed(e: Exception) = Unit
    }

    private var hvacArea: SeatArea? = null

    private val speedCallback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            when (value.propertyId) {
                VehiclePropertyIds.PERF_VEHICLE_SPEED -> (value.value as? Float)?.let { mps -> _state.update { it.copy(speedKph = mps * 3.6f) } }
                VehiclePropertyIds.GEAR_SELECTION -> (value.value as? Int)?.let { gear -> setParked(gear == GEAR_PARK) }
            }
        }
        override fun onErrorEvent(propertyId: Int, zone: Int) = Unit
    }

    private var uxManager: CarUxRestrictionsManager? = null

    private val uxListener = CarUxRestrictionsManager.OnUxRestrictionsChangedListener(::applyUx)

    fun start() {
        if (vehicle != null) return
        val v = runCatching { Vehicle(context.applicationContext).also { it.initialize() } }.getOrNull() ?: return
        vehicle = v
        _state.update { it.copy(connected = true, parked = true) }
        runCatching {
            v.getGear().registerGearSelection(gearListener)
            v.getGear().getCurrentGearSelectionState({ setParked(it == VehicleGear.GEAR_PARK) }, {})
        }
        watchSeats(v)
        runCatching {
            v.getLight().registerLowBeamLightState(lowBeamListener)
            v.getLight().getCurrentLowBeamLightState(lowBeamListener::onLightStateUpdated, {})
        }
        runCatching {
            val windshield = WindowArea(WindowValue.FRONT_WINDSHIELD)
            v.getWiper().registerWiperState(windshield, wiperListener)
            v.getWiper().getCurrentWiperState(windshield, wiperListener::onWiperStateUpdated, {})
        }
        runCatching {
            v.getTurnSignal().registerTurnSignalState(turnListener)
            v.getTurnSignal().getCurrentTurnSignalState(turnListener::onTurnSignalStateUpdated, {})
        }
        watchCar()
        watchClimate(v)
    }

    fun stop() {
        val v = vehicle ?: return
        runCatching { v.getGear().unregisterGearSelection(gearListener) }
        runCatching { v.getLight().unregisterLowBeamLightState(lowBeamListener) }
        runCatching { v.getWiper().unregisterWiperState(WindowArea(WindowValue.FRONT_WINDSHIELD), wiperListener) }
        runCatching { v.getTurnSignal().unregisterTurnSignalState(turnListener) }
        car?.let { c ->
            runCatching { (c.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager).unregisterCallback(speedCallback) }
            runCatching { uxManager?.unregisterListener() }
            c.disconnect()
        }
        car = null
        uxManager = null
        hvacArea?.let { area ->
            runCatching {
                v.getHVAC().unregisterHvacAcState(area, acListener)
                v.getHVAC().unregisterHvacPowerState(area, powerListener)
                v.getHVAC().unregisterHvacTemperature(area, temperatureListener)
            }
        }
        hvacArea = null
        seatListeners.forEach { (area, listener) ->
            when (listener) {
                is SeatOccupancyListener -> runCatching { v.getSeat().unregisterSeatOccupancy(area, listener) }
                is SeatBeltBuckledStateListener -> runCatching { v.getSeat().unregisterSeatBeltBuckledState(area, listener) }
            }
        }
        seatListeners.clear()
        runCatching { v.release() }
        vehicle = null
        _state.value = CabinState()
    }

    private fun watchClimate(v: Vehicle) {
        runCatching {
            v.getHVAC().checkHvacAcCapabilities({ _, areas: List<SeatArea> ->
                val area = areas.firstOrNull { SeatValue.ROW_1_LEFT in it.values } ?: areas.firstOrNull() ?: return@checkHvacAcCapabilities
                hvacArea = area
                runCatching {
                    v.getHVAC().registerHvacAcState(area, acListener)
                    v.getHVAC().registerHvacPowerState(area, powerListener)
                    v.getHVAC().registerHvacTemperature(area, temperatureListener)
                }
            }, {})
        }
    }

    fun refreshPermissions() {
        val c = car ?: return
        if (c.isConnected) registerProperties(c)
    }

    private fun watchCar() {
        car = runCatching {
            Car.createCar(context.applicationContext, null, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { connected, ready ->
                if (!ready) return@createCar
                registerProperties(connected)
                runCatching {
                    val ux = connected.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
                    uxManager = ux
                    ux.registerListener(uxListener)
                    applyUx(ux.currentCarUxRestrictions)
                }
            }
        }.getOrNull()
    }

    private fun registerProperties(connected: Car) {
        val props = runCatching { connected.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager }.getOrNull() ?: return
        runCatching { props.unregisterCallback(speedCallback) }
        runCatching { props.registerCallback(speedCallback, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_NORMAL) }
        runCatching { props.registerCallback(speedCallback, VehiclePropertyIds.GEAR_SELECTION, CarPropertyManager.SENSOR_RATE_ONCHANGE) }
    }

    private fun applyUx(restrictions: CarUxRestrictions?) {
        val active = restrictions?.takeIf { it.isRequiresDistractionOptimization }?.activeRestrictions ?: 0
        _state.update { it.copy(uxRestrictions = active, maxStringLength = restrictions?.maxRestrictedStringLength ?: 0) }
    }

    private fun watchSeats(v: Vehicle) {
        val all = SeatPosition.entries
        runCatching {
            v.getSeat().checkSeatBeltBuckledCapabilities({ _, areas: List<SeatArea> ->
                val supported = all.filter { p -> areas.any { p.value in it.values } }
                supported.ifEmpty { all }.forEach { watchSeat(v, it) }
            }, { all.forEach { watchSeat(v, it) } })
        }.onFailure { all.forEach { watchSeat(v, it) } }
    }

    private fun watchSeat(v: Vehicle, position: SeatPosition) {
        val area = SeatArea(position.value)
        val occupancy = object : SeatOccupancyListener {
            override fun onSeatOccupancyUpdated(occupied: Boolean?) = updateSeat(position) { it.copy(occupied = occupied == true) }
            override fun onFailed(e: Exception) = Unit
        }
        val belt = object : SeatBeltBuckledStateListener {
            override fun onSeatBeltBuckledStateUpdated(buckled: Boolean?) = updateSeat(position) { it.copy(buckled = buckled == true) }
            override fun onFailed(e: Exception) = Unit
        }
        runCatching {
            v.getSeat().registerSeatOccupancy(area, occupancy)
            v.getSeat().registerSeatBeltBuckledState(area, belt)
            seatListeners += area to occupancy
            seatListeners += area to belt
            v.getSeat().getCurrentSeatOccupancy(area, occupancy::onSeatOccupancyUpdated, {})
            v.getSeat().getCurrentSeatBeltBuckledState(area, belt::onSeatBeltBuckledStateUpdated, {})
        }
    }

    private fun updateSeat(position: SeatPosition, change: (SeatState) -> SeatState) {
        _state.update { cabin ->
            val current = cabin.seats[position] ?: SeatState()
            cabin.copy(seats = cabin.seats + (position to change(current)))
        }
    }

    private fun setParked(parked: Boolean) = _state.update { it.copy(parked = parked) }

    private companion object {
        const val GEAR_PARK = 4
    }
}
