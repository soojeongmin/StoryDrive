package com.soojeongmin.storydrive.story

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.random.Random

@Serializable
data class ChoiceOption(val keywords: List<String>, val lines: List<String>)

@Serializable
data class Choice(val prompt: String, val options: List<ChoiceOption>, val fallback: String)

@Serializable
data class Beat(val lines: List<String>, val choice: Choice? = null)

@Serializable
data class Episode(
    val id: String,
    val title: String,
    val roles: List<String> = emptyList(),
    val intro: List<String>,
    val beats: List<Beat>,
    val finale: List<String>,
    val resume: List<String> = emptyList(),
    val skins: Map<String, List<String>> = emptyMap(),
    val games: List<List<String>> = emptyList(),
)

@Serializable
data class EpisodeBook(val episodes: Map<String, List<Episode>> = emptyMap())

@Serializable
data class StoryBook(
    val lines: Map<String, Map<String, List<String>>>,
    val regions: Map<String, Map<String, List<String>>> = emptyMap(),
    val chapters: Map<String, List<List<String>>> = emptyMap(),
    val games: Map<String, List<List<String>>> = emptyMap(),
    val treasures: List<String> = emptyList(),
)

interface UsageTracker {
    fun used(slot: String): Set<Int>
    fun mark(slot: String, index: Int)
    fun clear(slot: String)
}

private class LocalUsage : UsageTracker {
    private val map = mutableMapOf<String, MutableSet<Int>>()
    override fun used(slot: String): Set<Int> = map[slot].orEmpty()
    override fun mark(slot: String, index: Int) {
        map.getOrPut(slot) { mutableSetOf() }.add(index)
    }
    override fun clear(slot: String) {
        map.remove(slot)
    }
}

class StoryLibrary(
    private val book: StoryBook,
    private val random: Random = Random.Default,
    private val episodeBook: EpisodeBook = EpisodeBook(),
) {
    var usage: UsageTracker = LocalUsage()

    fun line(key: String, age: AgeBand, allow: (String) -> Boolean = { true }): String? {
        val all = variants(book.lines[key], age)
        val allowed = all.indices.filter { allow(all[it]) }
        val index = pick("line:$key:$age", allowed.ifEmpty { all.indices.toList() }) ?: return null
        return all[index]
    }

    fun region(region: String, age: AgeBand): String? = pick("region:$region:$age", variants(book.regions[region], age))

    fun hasRegion(region: String) = book.regions.containsKey(region)

    fun chapterCount(age: AgeBand) = book.chapters[age.key()]?.size ?: 0

    fun chapter(index: Int, age: AgeBand): List<String> {
        val chapters = book.chapters[age.key()].orEmpty()
        return if (chapters.isEmpty()) emptyList() else chapters[index % chapters.size]
    }

    fun treasure(): String? = pick("treasure", book.treasures)

    fun episodeCount(age: AgeBand) = episodeBook.episodes[age.key()]?.size ?: 0

    fun episode(age: AgeBand, cursor: Int): Episode? {
        val list = episodeBook.episodes[age.key()].orEmpty()
        return if (list.isEmpty()) null else list[cursor % list.size]
    }

    fun episodeById(age: AgeBand, id: String): Episode? =
        episodeBook.episodes[age.key()]?.firstOrNull { it.id == id } ?: episodeBook.episodes.values.flatten().firstOrNull { it.id == id }

    fun skin(episode: Episode?, key: String): String? {
        val options = episode?.skins?.get(key).orEmpty()
        return pick("skin:${episode?.id}:$key", options)
    }

    fun resume(episode: Episode?): String? = pick("resume:${episode?.id}", episode?.resume.orEmpty())

    fun themedGame(episode: Episode?): List<String>? {
        val games = episode?.games.orEmpty()
        val fresh = games.indices.filterNot { it in usage.used("egame:${episode?.id}") }
        if (fresh.isEmpty()) return null
        val index = fresh[random.nextInt(fresh.size)]
        usage.mark("egame:${episode?.id}", index)
        return games[index]
    }

    fun game(age: AgeBand): List<String> {
        val games = book.games[age.key()].orEmpty()
        val index = pick("game:$age", games.indices.toList()) ?: return emptyList()
        return games[index]
    }

    private fun variants(byAge: Map<String, List<String>>?, age: AgeBand): List<String> =
        byAge?.get(age.key()).orEmpty().ifEmpty { byAge?.values?.firstOrNull().orEmpty() }

    private fun <T> pick(slot: String, options: List<T>): T? {
        if (options.isEmpty()) return null
        var fresh = options.indices.filterNot { it in usage.used(slot) }
        if (fresh.isEmpty()) {
            usage.clear(slot)
            fresh = options.indices.toList()
        }
        val index = fresh[random.nextInt(fresh.size)]
        usage.mark(slot, index)
        return options[index]
    }

    private fun AgeBand.key() = name.lowercase()

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        fun parse(text: String, random: Random = Random.Default, episodes: String? = null) = StoryLibrary(
            json.decodeFromString(text),
            random,
            episodes?.let { json.decodeFromString(EpisodeBook.serializer(), it) } ?: EpisodeBook(),
        )
    }
}
