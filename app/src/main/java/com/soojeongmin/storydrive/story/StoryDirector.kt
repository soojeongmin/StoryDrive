package com.soojeongmin.storydrive.story

import com.soojeongmin.storydrive.vehicle.CabinState
import com.soojeongmin.storydrive.vehicle.Turn
import kotlin.math.roundToInt

interface Narration {
    fun say(line: Line)
    fun interrupt()
    val isSpeaking: Boolean
    fun hold(on: Boolean) = Unit
    val isPaused: Boolean get() = false
}

interface HideoutPrompt {
    fun ask(description: String)
    fun dismiss()
}

enum class Hideout { NONE, ASKING, PLANNED, SKIPPED, VISITING }

data class TripProgress(
    val ratio: Float = 0f,
    val treasuresFound: List<String> = emptyList(),
    val storyPaused: Boolean = false,
    val hideout: Hideout = Hideout.NONE,
    val inTurtleVillage: Boolean = false,
)

class StoryDirector(
    private val library: StoryLibrary,
    private val narration: Narration,
    private val now: () -> Long,
    private val members: () -> List<KidProfile>,
    private val hideoutPrompt: HideoutPrompt? = null,
    private val memory: TripMemory = TripMemory(InMemoryTripStore(), now),
    private val mixedLevel: () -> MixedLevel = { MixedLevel.TODDLER },
    private val listen: (Long, (String?) -> Unit) -> Unit = { _, done -> done(null) },
) {
    init {
        library.usage = memory
    }

    private var cabin = CabinState()
    private var tripStarted: Boolean
        get() = memory.state.started
        set(value) = memory.update { it.copy(started = value) }
    private var resumeAnnounced = false
    private var awaitingSince = -1L
    private var missionAnnounced = false
    private var allBuckledAnnounced = false
    private var arrived = false
    private var lastSpokeAt = 0L
    private var lastBeltReminderAt = 0L
    private var chapterIndex: Int
        get() = memory.state.chapterIndex
        set(value) = memory.update { it.copy(chapterIndex = value) }
    private var fillerCount: Int
        get() = memory.state.fillerCount
        set(value) = memory.update { it.copy(fillerCount = value) }
    private var totalDistanceM: Int
        get() = memory.state.totalDistanceM
        set(value) = memory.update { it.copy(totalDistanceM = value) }
    private var remainDistanceM = 0
    private var etaSec = 0
    private val milestonesDone: Set<Int> get() = memory.state.milestones.toSet()
    private val cooldowns = mutableMapOf<String, Long>()
    private val cautionsSeen = mutableSetOf<String>()
    private var currentRegion: String? = null
    private val treasures: List<String> get() = memory.state.treasures

    private var hideout = Hideout.NONE
    private var hideoutAskedAt = 0L

    private var slowSince = -1L
    private var fastSince = -1L
    private var inTurtleVillage = false
    private var lastTurtlePlayAt = 0L
    private var stoppedSince = -1L
    private var stoplightAnnounced = false
    private var pickedEpisode: Int? = null

    var progress = TripProgress()
        private set

    private val age: AgeBand
        get() {
            val ages = kids().map { it.age }.toSet()
            return when {
                ages.isEmpty() -> AgeBand.TODDLER
                ages.size == 1 -> ages.first()
                mixedLevel() == MixedLevel.EARLY -> AgeBand.EARLY
                else -> AgeBand.TODDLER
            }
        }

    private fun kids() = members().filterNot { it.isAdult }

    private fun adults() = members().filter { it.isAdult }

    private val driving get() = !cabin.parked

    fun onCabin(next: CabinState) {
        val previous = cabin
        cabin = next
        if (kids().isEmpty()) return

        if (!driving && !tripStarted && !missionAnnounced) {
            missionAnnounced = true
            speak("mission_start", Priority.STORY)
        }

        members().forEach { member ->
            val before = previous.seats[member.seat]?.buckled == true
            val after = next.seats[member.seat]?.buckled == true
            val adult = member.isAdult
            when {
                !before && after && driving -> speak(if (adult) "belt_rebuckled_adult" else "belt_rebuckled", Priority.SAFETY, member)
                !before && after -> speak(if (adult) "belt_buckled_adult" else "belt_buckled", Priority.STORY, member)
                before && !after && driving && adult -> speak("belt_unbuckled_adult", Priority.ROUTE, member)
                before && !after && driving -> beltAlarm(member)
            }
        }

        if (!driving && !tripStarted && !allBuckledAnnounced && allBuckled()) {
            allBuckledAnnounced = true
            speak("belt_all_ok", Priority.STORY)
        }

        if (previous.parked && driving) onDepart()
        if (!previous.parked && !driving) onStop()
        if (driving && !paused()) onSignals(previous, next)
        updateProgress()
    }

    fun onRoute(event: RouteEvent) {
        when (event) {
            is RouteEvent.Trip -> onTrip(event)
            is RouteEvent.Location -> onLocation(event.address)
            is RouteEvent.Turn -> onTurn(event)
            is RouteEvent.Caution -> onCaution(event)
            is RouteEvent.Congestion -> onCongestion(event)
            is RouteEvent.Destination -> onDestination(event.key)
            RouteEvent.RouteStarted -> Unit
            RouteEvent.Arrived -> onArrived()
        }
        updateProgress()
    }

    fun decideHideout(stop: Boolean) {
        if (hideout != Hideout.ASKING) return
        hideout = if (stop) Hideout.PLANNED else Hideout.SKIPPED
        speak(if (stop) "hideout_visit" else "hideout_skip", Priority.ROUTE)
        updateProgress()
    }

    fun beginNewTrip(episodeIndex: Int? = null) {
        memory.reset(null)
        clearTripCues()
        pickedEpisode = episodeIndex
        updateProgress()
    }

    fun tick() {
        step()
        updateProgress()
    }

    private fun step() {
        if (hideout == Hideout.ASKING && now() - hideoutAskedAt > HIDEOUT_ASK_MS) expireHideoutQuestion()
        if (!driving || kids().isEmpty()) return
        val unbuckled = unbuckledKids()
        if (unbuckled.isNotEmpty()) {
            if (now() - lastBeltReminderAt >= BELT_REMINDER_MS) beltAlarm(unbuckled.first())
            return
        }
        updateTurtle()
        updateStoplight()
        if (narration.isPaused) return
        if (awaitingSince > 0 && now() - awaitingSince > AWAIT_TIMEOUT_MS) {
            awaitingSince = -1
            narration.hold(false)
        }
        if (narration.isSpeaking) return
        if (inTurtleVillage && now() - lastTurtlePlayAt >= TURTLE_PLAY_MS) {
            lastTurtlePlayAt = now()
            speak("turtle_play", Priority.STORY)
        } else if (now() - lastSpokeAt >= FILLER_IDLE_MS) {
            continueStory()
        }
    }

    private fun continueStory() {
        val ep = episode
        if (ep != null && !memory.state.finaleDone) {
            val beats = ep.beats
            val index = memory.state.beatIndex
            if (index < beats.size && beatDue(index, beats.size)) {
                playBeat(ep, index)
                return
            }
            if (index >= beats.size && currentRatio() <= 0.0 && now() - memory.state.lastBeatAt >= BEAT_INTERVAL_MS) {
                playFinale(ep)
                return
            }
            library.themedGame(ep)?.let { game ->
                game.forEach { say(it, Priority.FILLER) }
                return
            }
        }
        if (fillerCount++ % 2 == 1 || library.chapterCount(age) == 0) {
            library.game(age).forEach { say(it, Priority.FILLER) }
        } else {
            library.chapter(chapterIndex++, age).forEach { say(it, Priority.FILLER) }
        }
    }

    private val episode: Episode?
        get() = memory.state.episodeId?.let { library.episodeById(age, it) }

    private fun beatDue(index: Int, total: Int): Boolean {
        val sinceLast = now() - memory.state.lastBeatAt
        if (sinceLast < BEAT_MIN_GAP_MS) return false
        val ratio = currentRatio()
        return if (totalDistanceM > 0 && ratio > 0.0) ratio >= index.toDouble() / total else sinceLast >= BEAT_INTERVAL_MS
    }

    private fun startEpisode(): Boolean {
        val key = age.name
        val cursor = pickedEpisode ?: memory.state.episodeCursor[key] ?: 0
        pickedEpisode = null
        val ep = library.episode(age, cursor) ?: return false
        memory.update { it.copy(episodeId = ep.id, beatIndex = 0, finaleDone = false, lastBeatAt = now()) }
        ep.intro.forEach { say(it, Priority.STORY) }
        if (ep.roles.isNotEmpty()) {
            kids().forEachIndexed { i, kid -> speak("role_assign", Priority.STORY, kid, mapOf("role" to ep.roles[i % ep.roles.size])) }
        }
        if (ep.beats.isNotEmpty()) playBeat(ep, 0)
        return true
    }

    private fun playBeat(ep: Episode, index: Int) {
        val beat = ep.beats[index]
        memory.update { it.copy(beatIndex = index + 1, lastBeatAt = now()) }
        beat.lines.forEach { say(it, Priority.STORY) }
        beat.choice?.let { ask(it) }
    }

    private fun ask(choice: Choice) {
        say(choice.prompt, Priority.STORY) {
            narration.hold(true)
            awaitingSince = now()
            listen(CHOICE_WINDOW_MS) { heard ->
                awaitingSince = -1
                narration.hold(false)
                val picked = heard?.let { text -> choice.options.firstOrNull { option -> option.keywords.any { text.contains(it) } } }
                if (picked == null) say(choice.fallback, Priority.STORY)
                (picked ?: choice.options.first()).lines.forEach { say(it, Priority.STORY) }
            }
        }
    }

    private fun playFinale(ep: Episode) {
        if (memory.state.finaleDone) return
        val key = age.name
        memory.update { it.copy(finaleDone = true, episodeCursor = it.episodeCursor + (key to ((it.episodeCursor[key] ?: 0) + 1))) }
        ep.finale.forEach { say(it, Priority.ROUTE) }
    }

    private fun roleValues(): Map<String, String> {
        val ep = episode ?: return emptyMap()
        val names = kids().map { it.name }
        if (names.isEmpty()) return emptyMap()
        return (1..maxOf(ep.roles.size, 3)).associate { "r$it" to names[(it - 1) % names.size] }
    }

    private fun onDepart() {
        if (hideout == Hideout.VISITING) {
            hideout = Hideout.NONE
            speak("hideout_leave", Priority.STORY)
            return
        }
        if (tripStarted) {
            if (!resumeAnnounced && allBuckled()) {
                resumeAnnounced = true
                speak("trip_resume", Priority.STORY)
                if (memory.state.episodeId == null) startEpisode()
            }
            return
        }
        if (!allBuckled()) return
        tripStarted = true
        resumeAnnounced = true
        speak("departure", Priority.STORY)
        if (!startEpisode()) library.chapter(chapterIndex++, age).forEach { say(it, Priority.STORY) }
    }

    private fun onStop() {
        if (arrived || !tripStarted) return
        if (hideout == Hideout.PLANNED || hideout == Hideout.ASKING) {
            hideoutPrompt?.dismiss()
            hideout = Hideout.VISITING
            val treasure = library.treasure() ?: return
            memory.update { it.copy(treasures = it.treasures + treasure) }
            speak("hideout_arrive", Priority.ROUTE, values = mapOf("treasure" to treasure, "count" to treasures.size.toString()))
        }
    }

    private fun onSignals(previous: CabinState, next: CabinState) {
        if (!previous.headlights && next.headlights) once("night", NIGHT_COOLDOWN_MS) { speak("night", Priority.ROUTE) }
        if (!previous.wiper && next.wiper) once("rain", RAIN_COOLDOWN_MS) { speak("rain", Priority.ROUTE) }
        if (!previous.acOn && next.acOn) once("climate_cold", CLIMATE_COOLDOWN_MS) { speak("climate_cold", Priority.ROUTE) }
        if (!previous.heaterOn && next.heaterOn) once("climate_warm", CLIMATE_COOLDOWN_MS) { speak("climate_warm", Priority.ROUTE) }
        if (previous.turn == Turn.OFF && next.turn != Turn.OFF) {
            val key = if (next.turn == Turn.LEFT) "turn_left" else "turn_right"
            once("turn", TURN_COOLDOWN_MS) { speak(key, Priority.ROUTE) }
        }
    }

    private fun updateStoplight() {
        if (inTurtleVillage) return
        if (cabin.speedKph < STOPPED_KPH) {
            if (stoppedSince < 0) stoppedSince = now()
            if (!stoplightAnnounced && now() - stoppedSince >= STOPLIGHT_MS) {
                once("stoplight", STOPLIGHT_COOLDOWN_MS) {
                    stoplightAnnounced = true
                    speak("stoplight", Priority.ROUTE)
                }
            }
        } else {
            if (stoplightAnnounced && cabin.speedKph > GREEN_KPH) {
                stoplightAnnounced = false
                stoppedSince = -1
                if (!narration.isSpeaking) speak("greenlight", Priority.ROUTE)
            } else if (!stoplightAnnounced) {
                stoppedSince = -1
            }
        }
    }

    private fun updateTurtle() {
        val speed = cabin.speedKph
        if (speed < TURTLE_SLOW_KPH) {
            fastSince = -1
            if (slowSince < 0) slowSince = now()
            if (!inTurtleVillage && now() - slowSince >= TURTLE_ENTER_MS) {
                inTurtleVillage = true
                lastTurtlePlayAt = now()
                speak("turtle_enter", Priority.ROUTE)
            }
        } else {
            slowSince = -1
            if (speed > TURTLE_FAST_KPH) {
                if (fastSince < 0) fastSince = now()
                if (inTurtleVillage && now() - fastSince >= TURTLE_EXIT_MS) {
                    inTurtleVillage = false
                    speak("turtle_exit", Priority.ROUTE)
                }
            }
        }
    }

    private fun onCongestion(event: RouteEvent.Congestion) {
        if (!driving || paused() || inTurtleVillage) return
        if (event.aheadM > TURTLE_AHEAD_M || event.seconds < TURTLE_MIN_SEC) return
        once("turtle_ahead", TURTLE_AHEAD_COOLDOWN_MS) {
            speak("turtle_ahead", Priority.ROUTE, values = mapOf("minutes" to (event.seconds / 60).coerceAtLeast(1).toString()))
        }
    }

    private fun onTrip(trip: RouteEvent.Trip) {
        remainDistanceM = trip.remainDistanceM
        etaSec = trip.etaSec
        if (trip.remainDistanceM > totalDistanceM) totalDistanceM = trip.remainDistanceM
        if (!driving || totalDistanceM <= 0 || paused()) return
        val ratio = 1f - remainDistanceM.toFloat() / totalDistanceM
        MILESTONES.filter { ratio * 100 >= it && it !in milestonesDone }.maxOrNull()?.let { milestone ->
            memory.update { state -> state.copy(milestones = (state.milestones + MILESTONES.filter { it <= milestone }).distinct()) }
            speak("progress_$milestone", Priority.ROUTE, values = progressValues())
        }
    }

    private fun onLocation(address: String) {
        val region = RegionParser.province(address) ?: return
        if (region == currentRegion) return
        val first = currentRegion == null
        currentRegion = region
        if (first || !driving || paused()) return
        speak("region_enter", Priority.ROUTE, values = mapOf("region" to region))
        library.region(region, age)?.let { say(LineRenderer.render(it, null, kids()), Priority.ROUTE) }
    }

    private fun onTurn(turn: RouteEvent.Turn) {
        if (!driving || paused()) return
        val d = turn.distanceM
        when (turn.kind) {
            TurnKind.TUNNEL -> if (d <= 600) once("tunnel", 90_000L) { speak("tunnel", Priority.ROUTE) }
            TurnKind.OVERPASS -> if (d <= 400) once("overpass", 120_000L) { speak("overpass", Priority.ROUTE) }
            TurnKind.UNDERPASS -> if (d <= 400) once("underpass", 120_000L) { speak("underpass", Priority.ROUTE) }
            TurnKind.TOLL -> if (d <= 800) once("toll", 300_000L) { speak("toll", Priority.ROUTE) }
            TurnKind.REST_AREA -> onRestArea(turn)
            TurnKind.ARRIVE -> if (d <= 1_500) once("arrive_soon", 3_600_000L) {
                speak("arrive_soon", Priority.ROUTE)
                episode?.let(::playFinale)
            }
            else -> Unit
        }
    }

    private fun onRestArea(turn: RouteEvent.Turn) {
        when {
            hideout == Hideout.ASKING && turn.distanceM <= HIDEOUT_GIVE_UP_M -> expireHideoutQuestion()
            hideout == Hideout.NONE && turn.distanceM <= HIDEOUT_ASK_M -> once("hideout", HIDEOUT_COOLDOWN_MS) {
                hideout = Hideout.ASKING
                hideoutAskedAt = now()
                val km = (turn.distanceM / 1000.0).roundToInt().coerceAtLeast(1).toString()
                speak("hideout_ahead", Priority.ROUTE, values = mapOf("km" to km))
                hideoutPrompt?.ask(turn.description)
                askHideout("hideout_question")
            }
            hideout == Hideout.SKIPPED && turn.distanceM > HIDEOUT_ASK_M -> hideout = Hideout.NONE
        }
    }

    private fun askHideout(key: String) {
        val template = library.line(key, age) ?: return
        say(LineRenderer.render(template, null, kids(), crewValues()), Priority.ROUTE) {
            if (hideout != Hideout.ASKING) return@say
            narration.hold(true)
            listen(HIDEOUT_LISTEN_MS) { heard ->
                narration.hold(false)
                when (HideoutAnswer.parse(heard)) {
                    true -> decideHideout(true)
                    false -> decideHideout(false)
                    null -> if (key == "hideout_question" && hideout == Hideout.ASKING) askHideout("hideout_question_retry")
                }
            }
        }
    }

    private fun expireHideoutQuestion() {
        hideoutPrompt?.dismiss()
        hideout = Hideout.SKIPPED
        speak("hideout_skip", Priority.ROUTE)
    }

    private fun onCaution(caution: RouteEvent.Caution) {
        if (!driving || paused() || caution.kind == CautionKind.OTHER || caution.remainM > 800) return
        if (!cautionsSeen.add(caution.id)) return
        once("caution_${caution.kind}", 300_000L) { speak("caution_${caution.kind.name.lowercase()}", Priority.ROUTE) }
    }

    private fun onArrived() {
        if (arrived) return
        arrived = true
        episode?.let(::playFinale)
        speak("arrival", Priority.ROUTE, values = mapOf("count" to treasures.size.toString()))
        memory.reset(null)
        resumeAnnounced = false
    }

    private fun onDestination(key: String) {
        val current = memory.state.key
        when {
            current == key -> Unit
            current == null && memory.state.started -> memory.update { it.copy(key = key) }
            else -> {
                memory.reset(key)
                clearTripCues()
            }
        }
    }


    private fun clearTripCues() {
        arrived = false
        resumeAnnounced = false
        missionAnnounced = false
        allBuckledAnnounced = false
        cooldowns.clear()
        cautionsSeen.clear()
        currentRegion = null
        hideout = Hideout.NONE
        inTurtleVillage = false
        slowSince = -1
        fastSince = -1
        stoppedSince = -1
        stoplightAnnounced = false
    }

    private fun beltAlarm(kid: KidProfile) {
        lastBeltReminderAt = now()
        narration.interrupt()
        speak("belt_unbuckled", Priority.SAFETY, kid)
    }

    private fun rank(member: KidProfile): String =
        if (member.seat == com.soojeongmin.storydrive.vehicle.SeatPosition.DRIVER) "${member.name} 대장님" else "${member.name} 부대장님"

    private fun crewValues(): Map<String, String> {
        val crew = adults().sortedBy { it.seat.ordinal }
        val driver = crew.firstOrNull { it.seat == com.soojeongmin.storydrive.vehicle.SeatPosition.DRIVER }
        val ranks = crew.map(::rank)
        val captains = when (ranks.size) {
            0 -> "어른 대장님"
            1 -> ranks[0]
            else -> KoreanName.noun(ranks.dropLast(1).joinToString(", "), "와") + " " + ranks.last()
        }
        return mapOf(
            "driver" to (driver?.let(::rank) ?: "운전 대장님"),
            "captains" to captains,
        )
    }

    private fun progressValues(): Map<String, String> = mapOf(
        "minutes" to (etaSec / 60).coerceAtLeast(1).toString(),
        "count" to treasures.size.toString(),
    )

    private fun currentRatio(): Double =
        if (totalDistanceM <= 0) 0.0 else 1.0 - remainDistanceM.toDouble() / totalDistanceM

    private fun updateProgress() {
        progress = TripProgress(
            ratio = currentRatio().toFloat().coerceIn(0f, 1f),
            treasuresFound = treasures.toList(),
            storyPaused = driving && unbuckledKids().isNotEmpty(),
            hideout = hideout,
            inTurtleVillage = inTurtleVillage,
        )
    }

    private fun paused() = unbuckledKids().isNotEmpty()

    private fun unbuckledKids() = kids().filter { cabin.seats[it.seat]?.buckled != true }

    private fun allBuckled() = kids().isNotEmpty() && members().all { cabin.seats[it.seat]?.buckled == true }

    private inline fun once(key: String, cooldownMs: Long, block: () -> Unit) {
        val last = cooldowns[key]
        if (last != null && now() - last < cooldownMs) return
        cooldowns[key] = now()
        block()
    }

    private fun speak(key: String, priority: Priority, kid: KidProfile? = null, values: Map<String, String> = emptyMap()) {
        val noTreasure = values["count"] == "0"
        val ep = episode
        val storyRunning = priority == Priority.ROUTE && narration.isSpeaking && ep != null && !memory.state.finaleDone && !arrived
        val template = library.skin(ep, key)?.takeIf { !(noTreasure && it.contains("{count")) }
            ?: library.line(key, age) { !(noTreasure && it.contains("{count")) } ?: return
        val memberValues = kid?.takeIf { it.isAdult }?.let { mapOf("rank" to rank(it)) }.orEmpty()
        say(LineRenderer.render(template, kid, kids(), crewValues() + memberValues + values), priority)
        if (storyRunning) (library.resume(ep) ?: library.line("story_resume", age))?.let { say(it, Priority.ROUTE) }
    }

    private fun say(text: String, priority: Priority, then: (() -> Unit)? = null) {
        lastSpokeAt = now()
        val pause = PAUSE.find(text)?.groupValues?.get(1)?.toLong()?.times(1_000) ?: 0L
        val clean = text.replace(PAUSE, "").trim()
        narration.say(Line(LineRenderer.render(clean, null, kids(), crewValues() + roleValues()), priority, pause, then))
    }

    companion object {
        const val BELT_REMINDER_MS = 30_000L
        const val FILLER_IDLE_MS = 60_000L
        const val BEAT_INTERVAL_MS = 300_000L
        const val BEAT_MIN_GAP_MS = 120_000L
        const val CHOICE_WINDOW_MS = 7_000L
        const val HIDEOUT_LISTEN_MS = 7_000L
        const val AWAIT_TIMEOUT_MS = 20_000L
        const val HIDEOUT_ASK_M = 5_000.0
        const val HIDEOUT_GIVE_UP_M = 300.0
        const val HIDEOUT_ASK_MS = 180_000L
        const val HIDEOUT_COOLDOWN_MS = 600_000L
        const val NIGHT_COOLDOWN_MS = 3_600_000L
        const val RAIN_COOLDOWN_MS = 900_000L
        const val TURN_COOLDOWN_MS = 120_000L
        const val CLIMATE_COOLDOWN_MS = 1_200_000L
        const val STOPPED_KPH = 1f
        const val GREEN_KPH = 10f
        const val STOPLIGHT_MS = 4_000L
        const val STOPLIGHT_COOLDOWN_MS = 180_000L
        const val TURTLE_SLOW_KPH = 15f
        const val TURTLE_FAST_KPH = 40f
        const val TURTLE_ENTER_MS = 60_000L
        const val TURTLE_EXIT_MS = 30_000L
        const val TURTLE_PLAY_MS = 180_000L
        const val TURTLE_AHEAD_M = 1_500.0
        const val TURTLE_MIN_SEC = 180L
        const val TURTLE_AHEAD_COOLDOWN_MS = 900_000L
        val MILESTONES = listOf(25, 50, 75, 90)
        private val PAUSE = Regex("""\s*\[(\d+)\]\s*$""")
    }
}
