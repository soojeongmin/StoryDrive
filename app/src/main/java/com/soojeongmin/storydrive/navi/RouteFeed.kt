package com.soojeongmin.storydrive.navi

import ai.pleos.playground.navi.constants.CautionPointType
import ai.pleos.playground.navi.constants.TrafficCongestionType
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.constants.TurnType
import ai.pleos.playground.navi.data.CautionPoint
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.DrivingInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.soojeongmin.storydrive.story.CautionKind
import com.soojeongmin.storydrive.story.RouteEvent
import com.soojeongmin.storydrive.story.TurnKind

class RouteFeed(context: Context, private val onEvent: (RouteEvent) -> Unit) {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var navi: NaviHelper? = null
    private var polling = false
    private var ticks = -1

    private val listener = object : NaviHelperEventListener {
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) { emit(RouteEvent.RouteStarted) }
        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) { emit(RouteEvent.Arrived) }
        override fun onRouteEnded() = Unit
        override fun onDestinationInfo(destinationInfo: DestinationInfo) {
            val name = destinationInfo.name.orEmpty()
            if (name.isBlank() && destinationInfo.latitude == 0.0) return
            emit(RouteEvent.Destination(tripKey("%s@%.3f,%.3f".format(name, destinationInfo.latitude, destinationInfo.longitude))))
        }
        override fun onTBTInfo(tbtInfoList: List<TBTInfo>) {
            tbtInfoList.forEach { emit(RouteEvent.Turn(it.type.toKind(), it.distance, it.description.orEmpty())) }
        }
        override fun onCautionPointInfo(cautionPointList: List<CautionPoint>) {
            cautionPointList.forEach { emit(RouteEvent.Caution(it.type.toKind(), it.id.orEmpty(), it.remainDistance)) }
        }
        override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) {
            currentLocationInfo.address?.takeIf { it.isNotBlank() }?.let { emit(RouteEvent.Location(it)) }
        }
        override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) {
            routeStateInfo.trafficStatus?.congestionItemList.orEmpty()
                .filter { it.trafficCongestionType == TrafficCongestionType.HEAVY || it.trafficCongestionType == TrafficCongestionType.SLOW }
                .minByOrNull { it.offset }
                ?.let { emit(RouteEvent.Congestion(it.offset, it.distance, it.trafficCongestionTime)) }
        }
        override fun onDrivingInfo(drivingInfo: DrivingInfo) {
            drivingInfo.destination?.let { emit(RouteEvent.Trip(it.distance, it.duration)) }
        }
    }

    fun start() {
        if (navi != null) return
        val helper = runCatching { NaviHelper(appContext) }.getOrNull() ?: return
        navi = helper
        helper.addListener(listener)
        runCatching { helper.initialize({ startPolling() }, { }) }
    }

    fun stop() {
        polling = false
        handler.removeCallbacksAndMessages(null)
        navi?.let {
            it.removeListener(listener)
            it.release()
        }
        navi = null
    }

    private fun startPolling() {
        if (polling) return
        polling = true
        handler.post(object : Runnable {
            override fun run() {
                if (!polling) return
                navi?.let {
                    runCatching {
                        it.getTBTInfo()
                        it.getCurrentLocationInfo()
                        it.getRouteStateInfo()
                        if (++ticks % DESTINATION_EVERY == 0) it.getDestinationInfo()
                    }
                }
                handler.postDelayed(this, POLL_MS)
            }
        })
    }

    private fun emit(event: RouteEvent) {
        handler.post { onEvent(event) }
    }

    private fun TurnType?.toKind(): TurnKind = when (this) {
        TurnType.TURN_TUNNEL -> TurnKind.TUNNEL
        TurnType.TURN_RESTAREA, TurnType.TURN_ENTER_SERVICEAREA -> TurnKind.REST_AREA
        TurnType.TURN_TOLL -> TurnKind.TOLL
        TurnType.TURN_ENTER_OVER_R, TurnType.TURN_ENTER_OVER_L, TurnType.TURN_ENTER_OVER_C -> TurnKind.OVERPASS
        TurnType.TURN_ENTER_UNDER_R, TurnType.TURN_ENTER_UNDER_L, TurnType.TURN_ENTER_UNDER_C -> TurnKind.UNDERPASS
        TurnType.TURN_ENTER_IC_R, TurnType.TURN_ENTER_IC_L, TurnType.TURN_ENTER_IC_C,
        TurnType.TURN_EXIT_IC_R, TurnType.TURN_EXIT_IC_L, TurnType.TURN_EXIT_IC_C -> TurnKind.INTERCHANGE
        TurnType.TURN_ARRIVE -> TurnKind.ARRIVE
        else -> TurnKind.OTHER
    }

    private fun CautionPointType?.toKind(): CautionKind = when (this) {
        CautionPointType.WILDLIFE_CROSSING -> CautionKind.WILDLIFE
        CautionPointType.FALLING_ROCK_AREA -> CautionKind.FALLING_ROCK
        CautionPointType.FOG_AREA -> CautionKind.FOG
        CautionPointType.SCHOOL_ZONE -> CautionKind.SCHOOL_ZONE
        CautionPointType.DOWNHILL_CAUTION_AREA -> CautionKind.DOWNHILL
        CautionPointType.SLIPPERY_ROAD_ALERT -> CautionKind.SLIPPERY
        CautionPointType.SPEED_BUMP -> CautionKind.SPEED_BUMP
        CautionPointType.RAILROAD_CROSSING -> CautionKind.RAILROAD
        else -> CautionKind.OTHER
    }

    private companion object {
        const val POLL_MS = 3_000L
        const val DESTINATION_EVERY = 5
    }
}

private fun tripKey(raw: String): String =
    java.security.MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }.take(16)
