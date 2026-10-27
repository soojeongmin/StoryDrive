package com.soojeongmin.storydrive.sim

import com.soojeongmin.storydrive.story.AgeBand
import com.soojeongmin.storydrive.story.KidProfile
import com.soojeongmin.storydrive.story.Role
import com.soojeongmin.storydrive.story.RouteEvent
import com.soojeongmin.storydrive.story.TurnKind
import com.soojeongmin.storydrive.vehicle.CabinState
import com.soojeongmin.storydrive.vehicle.SeatPosition
import com.soojeongmin.storydrive.vehicle.SeatState
import com.soojeongmin.storydrive.vehicle.Turn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

class DriveSimulator(
    private val scope: CoroutineScope,
    private val kids: () -> List<KidProfile>,
    private val emitRoute: (RouteEvent) -> Unit,
    private val decideHideout: (Boolean) -> Unit,
    private val addMember: (KidProfile) -> Unit = {},
    private val beginNewTrip: (Int) -> Unit = {},
) {
    private val _cabin = MutableStateFlow<CabinState?>(null)
    val cabin: StateFlow<CabinState?> = _cabin.asStateFlow()

    private var remainM = TRIP_M
    private var demo: Job? = null
    private var cruise = CRUISE_KPH.toFloat()

    val active get() = _cabin.value != null

    fun enable(on: Boolean) {
        demo?.cancel()
        _cabin.value = if (on) CabinState(connected = true, parked = true, seats = occupiedSeats(false)) else null
        remainM = TRIP_M
    }

    fun toggleBelt(seat: SeatPosition) = edit { cabin ->
        val current = cabin.seats[seat] ?: SeatState(occupied = true)
        cabin.copy(seats = cabin.seats + (seat to current.copy(occupied = true, buckled = !current.buckled)))
    }

    fun buckleAll(buckled: Boolean) = edit { it.copy(seats = occupiedSeats(buckled)) }

    fun setParked(parked: Boolean) = edit { it.copy(parked = parked, speedKph = if (parked) 0f else 60f) }

    fun setSpeed(kph: Float) = edit { it.copy(speedKph = kph) }

    fun setHeadlights(on: Boolean) = edit { it.copy(headlights = on) }

    fun setWiper(on: Boolean) = edit { it.copy(wiper = on) }

    fun setTurn(turn: Turn) = edit { it.copy(turn = turn) }

    fun toggleAc() = edit { it.copy(hvacOn = true, acOn = !it.acOn) }

    fun toggleHeater() = edit { if (it.heaterOn) it.copy(hvacOn = false) else it.copy(hvacOn = true, acOn = false, setTemperatureC = 27f) }

    fun tunnel() = emitRoute(RouteEvent.Turn(TurnKind.TUNNEL, 400.0, ""))

    fun restArea(km: Double) = emitRoute(RouteEvent.Turn(TurnKind.REST_AREA, km * 1000, "행담도휴게소"))

    fun congestion() = emitRoute(RouteEvent.Congestion(900.0, 3_000.0, 600))

    fun region(address: String) = emitRoute(RouteEvent.Location(address))

    fun advance(fraction: Double) {
        if (remainM == TRIP_M) emitRoute(RouteEvent.Trip(TRIP_M, TRIP_ETA_SEC))
        remainM = (remainM - (TRIP_M * fraction).toInt()).coerceAtLeast(0)
        emitRoute(RouteEvent.Trip(remainM, (TRIP_ETA_SEC * remainM.toDouble() / TRIP_M).toInt()))
        if (remainM < 1_500) emitRoute(RouteEvent.Turn(TurnKind.ARRIVE, remainM.toDouble(), ""))
    }

    fun arrive() {
        emitRoute(RouteEvent.Arrived)
        setParked(true)
    }

    fun runCourse(course: DemoCourse) {
        demo?.cancel()
        if (kids().none { it.seat == SeatPosition.DRIVER }) addMember(KidProfile("demo-driver", "아빠", AgeBand.EARLY, SeatPosition.DRIVER, Role.ADULT))
        val random = Random(System.currentTimeMillis())
        beginNewTrip(random.nextInt(1_000))
        enable(true)
        demo = scope.launch {
            val driveSec = course.minutes * 60 - SETUP_SEC - ARRIVAL_SEC
            val totalM = driveSec * CRUISE_KPH * 1000 / 3600
            emitRoute(RouteEvent.Trip(totalM, driveSec + ARRIVAL_SEC))
            region(course.origin)
            pause(6)
            kids().forEach { toggleBelt(it.seat); pause(3) }
            pause(4)
            setParked(false)
            cruise = CRUISE_KPH.toFloat()
            setSpeed(cruise)
            val departedAt = System.currentTimeMillis()
            val meter = launch { meterTrip(departedAt, driveSec, totalM, random) }
            course.timeline(random, driveSec).forEach { (at, cue) ->
                delay((departedAt + at * 1_000L - System.currentTimeMillis()).coerceAtLeast(0))
                play(cue, course, random)
            }
            delay((departedAt + driveSec * 1_000L - System.currentTimeMillis()).coerceAtLeast(0))
            meter.cancel()
            emitRoute(RouteEvent.Trip(800, 60))
            emitRoute(RouteEvent.Turn(TurnKind.ARRIVE, 800.0, ""))
            setSpeed(30f)
            pause(ARRIVAL_SEC)
            emitRoute(RouteEvent.Trip(0, 0))
            arrive()
        }
    }

    private suspend fun meterTrip(departedAt: Long, driveSec: Int, totalM: Int, random: Random) {
        while (true) {
            pause(10)
            val left = 1.0 - (System.currentTimeMillis() - departedAt) / (driveSec * 1_000.0)
            val remain = (totalM * left).toInt().coerceAtLeast(1_600)
            emitRoute(RouteEvent.Trip(remain, (driveSec * left).toInt().coerceAtLeast(90)))
            val speed = _cabin.value?.speedKph ?: 0f
            if (!(_cabin.value?.parked ?: true) && speed == cruise) {
                cruise = CRUISE_KPH + random.nextInt(-8, 9).toFloat()
                setSpeed(cruise)
            }
        }
    }

    private suspend fun play(cue: Cue, course: DemoCourse, random: Random) {
        when (cue) {
            Cue.TUNNEL -> {
                emitRoute(RouteEvent.Turn(TurnKind.TUNNEL, 900.0, ""))
                pause(8)
                tunnel()
            }
            Cue.REST_AREA -> {
                restArea(4.5)
                pause(20)
                val visit = random.nextBoolean()
                decideHideout(visit)
                pause(25)
                restArea(0.4)
                if (visit) {
                    setSpeed(30f)
                    pause(8)
                    setParked(true)
                    pause(35)
                    setParked(false)
                    setSpeed(cruise)
                }
            }
            Cue.CONGESTION -> {
                emitRoute(RouteEvent.Congestion(900.0, 4_000.0, random.nextLong(240, 420)))
                pause(15)
                setSpeed(8f)
                pause(80)
                setSpeed(cruise)
            }
            Cue.SLOW -> hold(random.nextInt(20, 31).toFloat(), 35)
            Cue.FAST -> hold(random.nextInt(105, 116).toFloat(), 35)
            Cue.STOPLIGHT -> {
                setSpeed(25f)
                pause(3)
                setSpeed(0f)
                pause(12)
                setSpeed(25f)
                pause(5)
                setSpeed(cruise)
            }
            Cue.GANGWON -> region(course.destination)
            Cue.HEADLIGHTS -> setHeadlights(true)
            Cue.WIPER -> {
                setWiper(true)
                pause(45)
                setWiper(false)
            }
            Cue.TURN -> {
                setTurn(if (random.nextBoolean()) Turn.LEFT else Turn.RIGHT)
                pause(4)
                setTurn(Turn.OFF)
            }
            Cue.AC -> edit { it.copy(hvacOn = true, acOn = true) }
            Cue.HEATER -> edit { it.copy(hvacOn = true, acOn = false, setTemperatureC = 27f) }
        }
    }

    private suspend fun hold(kph: Float, seconds: Int) {
        setSpeed(kph)
        pause(seconds)
        setSpeed(cruise)
    }

    private suspend fun pause(seconds: Int) = delay(seconds * 1_000L)

    private fun edit(change: (CabinState) -> CabinState) = _cabin.update { it?.let(change) }

    private fun occupiedSeats(buckled: Boolean): Map<SeatPosition, SeatState> =
        kids().associate { it.seat to SeatState(occupied = true, buckled = buckled) }

    private companion object {
        const val TRIP_M = 120_000
        const val TRIP_ETA_SEC = 5_400
        const val CRUISE_KPH = 80
        const val SETUP_SEC = 30
        const val ARRIVAL_SEC = 25
    }
}
