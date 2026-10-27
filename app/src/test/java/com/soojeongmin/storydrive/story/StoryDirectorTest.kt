package com.soojeongmin.storydrive.story

import com.soojeongmin.storydrive.vehicle.CabinState
import com.soojeongmin.storydrive.vehicle.SeatPosition
import com.soojeongmin.storydrive.vehicle.SeatState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.random.Random

class StoryDirectorTest {
    private var clock = 0L
    private val spoken = mutableListOf<Line>()
    private var interrupted = 0
    private val prompts = mutableListOf<String>()
    private var dismissed = 0

    private val narration = object : Narration {
        override fun say(line: Line) { spoken += line }
        override fun interrupt() { interrupted++ }
        override val isSpeaking = false
    }

    private val prompt = object : HideoutPrompt {
        override fun ask(description: String) { prompts += description }
        override fun dismiss() { dismissed++ }
    }

    private val kids = listOf(
        KidProfile("1", "민준", AgeBand.TODDLER, SeatPosition.REAR_LEFT),
        KidProfile("2", "서아", AgeBand.TODDLER, SeatPosition.REAR_RIGHT),
    )

    private lateinit var director: StoryDirector

    @Before
    fun setUp() {
        val json = File("src/main/assets/stories/ko.json").readText()
        director = StoryDirector(StoryLibrary.parse(json, Random(1)), narration, { clock }, { kids }, prompt)
    }

    private fun cabin(parked: Boolean, vararg buckled: SeatPosition, speed: Float = 50f) = CabinState(
        connected = true,
        parked = parked,
        seats = kids.associate { it.seat to SeatState(occupied = true, buckled = it.seat in buckled) },
        speedKph = speed,
    )

    private fun texts() = spoken.map { it.text }

    private fun startDriving() {
        director.onCabin(cabin(true))
        director.onCabin(cabin(true, SeatPosition.REAR_LEFT, SeatPosition.REAR_RIGHT))
        director.onCabin(cabin(false, SeatPosition.REAR_LEFT, SeatPosition.REAR_RIGHT))
        spoken.clear()
    }

    @Test
    fun beltMissionCallsEachKidByName() {
        director.onCabin(cabin(true))
        director.onCabin(cabin(true, SeatPosition.REAR_LEFT))
        director.onCabin(cabin(true, SeatPosition.REAR_LEFT, SeatPosition.REAR_RIGHT))
        val all = texts().joinToString("\n")
        assertTrue(all, all.contains("민준"))
        assertTrue(all, all.contains("서아"))
        assertTrue(all, texts().none { it.contains("{") })
    }

    @Test
    fun unbuckleWhileDrivingInterruptsAndNamesKid() {
        startDriving()
        director.onCabin(cabin(false, SeatPosition.REAR_LEFT))
        assertEquals(1, interrupted)
        assertEquals(Priority.SAFETY, spoken.last().priority)
        assertTrue(spoken.last().text, spoken.last().text.contains("서아"))
        assertTrue(director.progress.storyPaused)
    }

    @Test
    fun routeEventsSilentWhileUnbuckled() {
        startDriving()
        director.onCabin(cabin(false, SeatPosition.REAR_LEFT))
        spoken.clear()
        director.onRoute(RouteEvent.Turn(TurnKind.TUNNEL, 300.0, ""))
        assertTrue(texts().toString(), spoken.isEmpty())
    }

    @Test
    fun hideoutAskStopAndTreasure() {
        startDriving()
        director.onRoute(RouteEvent.Turn(TurnKind.REST_AREA, 3_200.0, "행담도휴게소"))
        assertEquals(listOf("행담도휴게소"), prompts)
        assertTrue(texts().joinToString(), texts().any { it.contains("3") })
        director.decideHideout(stop = true)
        assertEquals(Hideout.PLANNED, director.progress.hideout)
        director.onCabin(cabin(true, SeatPosition.REAR_LEFT, SeatPosition.REAR_RIGHT))
        assertEquals(1, director.progress.treasuresFound.size)
        assertEquals(Hideout.VISITING, director.progress.hideout)
    }

    @Test
    fun hideoutUnansweredExpiresAsSkip() {
        startDriving()
        director.onRoute(RouteEvent.Turn(TurnKind.REST_AREA, 4_000.0, ""))
        clock += StoryDirector.HIDEOUT_ASK_MS + 1
        director.tick()
        assertEquals(Hideout.SKIPPED, director.progress.hideout)
        assertEquals(1, dismissed)
    }

    @Test
    fun turtleVillageEntersAndExitsBySpeed() {
        startDriving()
        director.onCabin(cabin(false, SeatPosition.REAR_LEFT, SeatPosition.REAR_RIGHT, speed = 8f))
        repeat(61) { clock += 1_000; director.tick() }
        assertTrue(director.progress.inTurtleVillage)
        director.onCabin(cabin(false, SeatPosition.REAR_LEFT, SeatPosition.REAR_RIGHT, speed = 60f))
        repeat(31) { clock += 1_000; director.tick() }
        assertTrue(!director.progress.inTurtleVillage)
    }

    @Test
    fun progressMilestoneAnnouncedOnce() {
        startDriving()
        director.onRoute(RouteEvent.Trip(100_000, 3_600))
        director.onRoute(RouteEvent.Trip(48_000, 1_800))
        director.onRoute(RouteEvent.Trip(47_000, 1_750))
        assertEquals(1, spoken.size)
    }

    @Test
    fun everyLineRendersWithoutLeftoverTokens() {
        val json = File("src/main/assets/stories/ko.json").readText()
        val book = kotlinx.serialization.json.Json.decodeFromString(StoryBook.serializer(), json)
        val values = mapOf("km" to "3", "minutes" to "12", "count" to "2", "treasure" to "무지개 깃털", "region" to "강원도", "driver" to "아빠 대장님", "captains" to "아빠 대장님과 엄마 부대장님", "rank" to "엄마 부대장님", "role" to "길잡이")
        val all = book.lines.values.flatMap { it.values.flatten() } + book.regions.values.flatMap { it.values.flatten() } +
            book.chapters.values.flatten().flatten() + book.games.values.flatten().flatten().map { it.replace(Regex("""\s*\[\d+\]\s*$"""), "") }
        all.forEach { template ->
            val out = LineRenderer.render(template, kids[0], kids, values)
            assertTrue(out, !out.contains("{") && !out.contains("}"))
        }
    }

    @Test
    fun adultsAreCaptainsAndDoNotPauseStory() {
        val crew = kids + KidProfile("3", "아빠", AgeBand.EARLY, SeatPosition.DRIVER, Role.ADULT)
        val d = StoryDirector(StoryLibrary.parse(File("src/main/assets/stories/ko.json").readText(), Random(2)), narration, { clock }, { crew }, prompt)
        val all = crew.map { it.seat }.toTypedArray()
        fun state(parked: Boolean, vararg buckled: SeatPosition) = CabinState(
            connected = true, parked = parked, speedKph = 50f,
            seats = crew.associate { it.seat to SeatState(occupied = true, buckled = it.seat in buckled) },
        )
        d.onCabin(state(true))
        d.onCabin(state(true, *all))
        assertTrue(texts().joinToString(), texts().any { it.contains("아빠 대장님") })
        d.onCabin(state(false, *all))
        spoken.clear()
        d.onCabin(state(false, SeatPosition.REAR_LEFT, SeatPosition.REAR_RIGHT))
        assertEquals(0, interrupted)
        assertTrue(!d.progress.storyPaused)
        assertTrue(texts().joinToString(), texts().single().contains("아빠 대장님"))
    }

    @Test
    fun storyKeepsGoingAfterShortSilence() {
        startDriving()
        clock += StoryDirector.FILLER_IDLE_MS + 1
        director.tick()
        val afterFirst = spoken.size
        assertTrue(afterFirst > 0)
        clock += StoryDirector.FILLER_IDLE_MS + 1
        director.tick()
        assertTrue(spoken.size > afterFirst)
    }

    @Test
    fun gameLinesCarryPauseForAnswers() {
        startDriving()
        clock += StoryDirector.FILLER_IDLE_MS + 1
        director.tick()
        clock += StoryDirector.FILLER_IDLE_MS + 1
        director.tick()
        assertTrue(spoken.any { it.pauseAfterMs > 0 })
        assertTrue(spoken.none { it.text.contains("[") })
    }

    @Test
    fun tripSurvivesRestartWithoutRepeatingChapters() {
        val store = InMemoryTripStore()
        val json = File("src/main/assets/stories/ko.json").readText()
        fun build() = StoryDirector(StoryLibrary.parse(json, Random(3)), narration, { clock }, { kids }, prompt, TripMemory(store) { clock })
        val first = build()
        first.onRoute(RouteEvent.Destination("강릉"))
        first.onCabin(cabin(true))
        first.onCabin(cabin(true, SeatPosition.REAR_LEFT, SeatPosition.REAR_RIGHT))
        first.onCabin(cabin(false, SeatPosition.REAR_LEFT, SeatPosition.REAR_RIGHT))
        val firstChapter = spoken.map { it.text }.filter { it.contains("별조각") || it.contains("반짝이") }.toSet()
        assertEquals(1, store.load().chapterIndex)

        spoken.clear()
        val second = build()
        second.onRoute(RouteEvent.Destination("강릉"))
        second.onCabin(cabin(true, SeatPosition.REAR_LEFT, SeatPosition.REAR_RIGHT))
        second.onCabin(cabin(false, SeatPosition.REAR_LEFT, SeatPosition.REAR_RIGHT))
        clock += StoryDirector.FILLER_IDLE_MS + 1
        second.tick()
        assertTrue(store.load().chapterIndex >= 1)
        assertTrue(spoken.none { it.text in firstChapter && it.priority == Priority.FILLER })

        val progressed = store.load().chapterIndex
        second.onRoute(RouteEvent.Destination("부산"))
        assertEquals(progressed, store.load().chapterIndex)
        assertTrue(!store.load().started)
        assertTrue(store.load().treasures.isEmpty())
    }

    @Test
    fun mixedAgesFollowParentChoice() {
        val mixed = listOf(
            KidProfile("1", "민준", AgeBand.TODDLER, SeatPosition.REAR_LEFT),
            KidProfile("2", "서아", AgeBand.EARLY, SeatPosition.REAR_RIGHT),
        )
        val book = File("src/main/assets/stories/ko.json").readText()
        val earlyStarts = kotlinx.serialization.json.Json.decodeFromString(StoryBook.serializer(), book).lines.getValue("mission_start").getValue("early")
        var level = MixedLevel.EARLY
        val d = StoryDirector(StoryLibrary.parse(book, Random(4)), narration, { clock }, { mixed }, prompt, mixedLevel = { level })
        d.onCabin(CabinState(connected = true, parked = true, seats = mixed.associate { it.seat to SeatState(true, false) }))
        val first = spoken.first().text
        assertTrue(first, earlyStarts.any { template -> LineRenderer.render(template, null, mixed) == first })
    }

    @Test
    fun neverAnnouncesZeroTreasures() {
        repeat(20) { seed ->
            spoken.clear()
            val d = StoryDirector(StoryLibrary.parse(File("src/main/assets/stories/ko.json").readText(), Random(seed)), narration, { clock }, { kids }, prompt)
            d.onCabin(cabin(true, SeatPosition.REAR_LEFT, SeatPosition.REAR_RIGHT))
            d.onCabin(cabin(false, SeatPosition.REAR_LEFT, SeatPosition.REAR_RIGHT))
            d.onRoute(RouteEvent.Trip(100_000, 3_600))
            d.onRoute(RouteEvent.Trip(40_000, 1_500))
            d.onRoute(RouteEvent.Arrived)
            assertTrue(texts().joinToString(), texts().none { Regex("""(보물|0)\D{0,6}0개|0개""").containsMatchIn(it) })
        }
    }
}
