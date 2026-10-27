package com.soojeongmin.storydrive.story

import com.soojeongmin.storydrive.vehicle.CabinState
import com.soojeongmin.storydrive.vehicle.SeatPosition
import com.soojeongmin.storydrive.vehicle.SeatState
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

class EpisodeTest {
    private var clock = 1_000_000L
    private val spoken = mutableListOf<Line>()
    private var heardQueue = ArrayDeque<String?>()

    private val narration = object : Narration {
        override fun say(line: Line) {
            spoken += line
            line.then?.invoke()
        }
        override fun interrupt() = Unit
        override val isSpeaking = false
    }

    private val kids = listOf(
        KidProfile("1", "민준", AgeBand.TODDLER, SeatPosition.REAR_LEFT),
        KidProfile("2", "서아", AgeBand.TODDLER, SeatPosition.REAR_RIGHT),
        KidProfile("3", "아빠", AgeBand.EARLY, SeatPosition.DRIVER, Role.ADULT),
    )

    private val stories = File("src/main/assets/stories/ko.json").readText()
    private val episodes = File("src/main/assets/stories/episodes_ko.json").readText()

    private fun director(store: TripStore = InMemoryTripStore()) = StoryDirector(
        StoryLibrary.parse(stories, Random(5), episodes), narration, { clock }, { kids },
        memory = TripMemory(store) { clock },
        listen = { _, done -> done(heardQueue.removeFirstOrNull()) },
    )

    private fun cabin(parked: Boolean, speed: Float = 60f) = CabinState(
        connected = true, parked = parked, speedKph = speed,
        seats = kids.associate { it.seat to SeatState(occupied = true, buckled = true) },
    )

    private fun texts() = spoken.map { it.text }

    private fun depart(d: StoryDirector) {
        d.onCabin(cabin(true))
        d.onCabin(cabin(false))
    }

    @Test
    fun departureStartsEpisodeWithRolesByName() {
        val d = director()
        depart(d)
        val all = texts().joinToString("\n")
        assertTrue(all, all.contains("뽀글이"))
        assertTrue(all, all.contains("망원경 대장") && all.contains("지도 대장"))
        assertTrue(all, all.contains("민준아, 망원경"))
        assertTrue(all, texts().none { it.contains("{") })
    }

    @Test
    fun choiceFollowsWhatKidsSaid() {
        heardQueue.addAll(listOf("동굴!"))
        val d = director()
        depart(d)
        repeat(3) { clock += StoryDirector.BEAT_INTERVAL_MS + 1; d.tick() }
        val all = texts().joinToString("\n")
        assertTrue(all, all.contains("산호 동굴로 출발"))
        assertTrue(all, !all.contains("해초 숲으로 출발"))
    }

    @Test
    fun choiceFallsBackWhenNothingHeard() {
        heardQueue.addAll(listOf(null))
        val d = director()
        depart(d)
        repeat(3) { clock += StoryDirector.BEAT_INTERVAL_MS + 1; d.tick() }
        val all = texts().joinToString("\n")
        assertTrue(all, all.contains("반짝이가 골라 볼게") && all.contains("해초 숲으로 출발"))
    }

    @Test
    fun eventsUseEpisodeSkin() {
        val d = director()
        depart(d)
        spoken.clear()
        d.onRoute(RouteEvent.Turn(TurnKind.TUNNEL, 300.0, ""))
        assertTrue(texts().joinToString(), texts().any { it.contains("고래 뱃속") || it.contains("바다 밑 터널") })
    }

    @Test
    fun hideoutDecidedByVoice() {
        heardQueue.addAll(listOf("들러요"))
        val d = director()
        depart(d)
        d.onRoute(RouteEvent.Turn(TurnKind.REST_AREA, 3_000.0, ""))
        assertEquals(Hideout.PLANNED, d.progress.hideout)
        assertTrue(texts().joinToString(), texts().any { it.contains("아빠 대장님, 이번 아지트") })
    }

    @Test
    fun hideoutSkipByVoiceAndRetryOnSilence() {
        heardQueue.addAll(listOf(null, "그냥 지나가요"))
        val d = director()
        depart(d)
        d.onRoute(RouteEvent.Turn(TurnKind.REST_AREA, 3_000.0, ""))
        assertEquals(Hideout.SKIPPED, d.progress.hideout)
    }

    @Test
    fun arrivalPlaysFinaleAndNextTripStartsNextEpisode() {
        val store = InMemoryTripStore()
        val d = director(store)
        d.onRoute(RouteEvent.Destination("강릉"))
        depart(d)
        d.onRoute(RouteEvent.Arrived)
        assertTrue(texts().joinToString(), texts().any { it.contains("진주 찾기 대성공") })
        assertEquals(1, store.load().episodeCursor["TODDLER"])
    }

    @Test
    fun answerParsing() {
        assertEquals(true, HideoutAnswer.parse("네 들러요"))
        assertEquals(false, HideoutAnswer.parse("아니 지나가자"))
        assertEquals(null, HideoutAnswer.parse("음"))
        assertEquals(null, HideoutAnswer.parse(null))
    }

    @Test
    fun everyEpisodeLineRenders() {
        val book = Json.decodeFromString(EpisodeBook.serializer(), episodes)
        val values = mapOf("km" to "3", "treasure" to "무지개 깃털", "count" to "2", "region" to "강원도", "minutes" to "10",
            "driver" to "아빠 대장님", "captains" to "아빠 대장님", "r1" to "민준", "r2" to "서아", "r3" to "민준")
        book.episodes.values.flatten().forEach { ep ->
            val lines = ep.intro + ep.finale + ep.resume + ep.beats.flatMap { b ->
                b.lines + listOfNotNull(b.choice?.prompt, b.choice?.fallback) + b.choice?.options.orEmpty().flatMap { it.lines }
            } + ep.skins.values.flatten() + ep.games.flatten()
            lines.forEach { raw ->
                val out = LineRenderer.render(raw.replace(Regex("""\s*\[\d+\]\s*$"""), ""), null, kids.take(2), values)
                assertTrue("${ep.id}: $out", !out.contains("{") && !out.contains("}"))
            }
            ep.beats.mapNotNull { it.choice }.forEach { c -> assertTrue(ep.id, c.options.size >= 2 && c.options.all { it.keywords.isNotEmpty() }) }
        }
    }

    @Test
    fun tripStartedBeforeEpisodesGetsEpisodeOnResume() {
        val store = InMemoryTripStore(TripState(key = "강릉", started = true, updatedAt = clock))
        val d = director(store)
        d.onRoute(RouteEvent.Destination("강릉"))
        depart(d)
        assertTrue(texts().joinToString(), texts().any { it.contains("뽀글이") })
        assertTrue(store.load().episodeId != null)
    }
}
