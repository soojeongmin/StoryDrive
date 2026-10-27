package com.soojeongmin.storydrive.story

import com.soojeongmin.storydrive.vehicle.SeatPosition
import kotlinx.serialization.Serializable

@Serializable
enum class AgeBand { TODDLER, EARLY }

@Serializable
enum class Role { CHILD, ADULT }

enum class MixedLevel { TODDLER, EARLY }

@Serializable
data class KidProfile(
    val id: String,
    val name: String,
    val age: AgeBand,
    val seat: SeatPosition,
    val role: Role = Role.CHILD,
) {
    val isAdult get() = role == Role.ADULT
}

enum class TurnKind { TUNNEL, OVERPASS, UNDERPASS, TOLL, REST_AREA, INTERCHANGE, ARRIVE, OTHER }

enum class CautionKind { WILDLIFE, FALLING_ROCK, FOG, SCHOOL_ZONE, DOWNHILL, SLIPPERY, SPEED_BUMP, RAILROAD, OTHER }

sealed interface RouteEvent {
    data class Turn(val kind: TurnKind, val distanceM: Double, val description: String) : RouteEvent
    data class Caution(val kind: CautionKind, val id: String, val remainM: Int) : RouteEvent
    data class Location(val address: String) : RouteEvent
    data class Trip(val remainDistanceM: Int, val etaSec: Int) : RouteEvent
    data class Congestion(val aheadM: Double, val lengthM: Double, val seconds: Long) : RouteEvent
    data class Destination(val key: String) : RouteEvent
    data object RouteStarted : RouteEvent
    data object Arrived : RouteEvent
}

data class Line(
    val text: String,
    val priority: Priority,
    val pauseAfterMs: Long = 0,
    val then: (() -> Unit)? = null,
)

enum class Priority { FILLER, STORY, ROUTE, SAFETY }
