package com.soojeongmin.storydrive.story

import kotlinx.serialization.Serializable

@Serializable
data class TripState(
    val key: String? = null,
    val started: Boolean = false,
    val chapterIndex: Int = 0,
    val fillerCount: Int = 0,
    val used: Map<String, List<Int>> = emptyMap(),
    val treasures: List<String> = emptyList(),
    val milestones: List<Int> = emptyList(),
    val totalDistanceM: Int = 0,
    val updatedAt: Long = 0,
    val episodeId: String? = null,
    val beatIndex: Int = 0,
    val lastBeatAt: Long = 0,
    val finaleDone: Boolean = false,
    val episodeCursor: Map<String, Int> = emptyMap(),
)

interface TripStore {
    fun load(): TripState
    fun save(state: TripState)
}

class InMemoryTripStore(private var state: TripState = TripState()) : TripStore {
    override fun load() = state
    override fun save(state: TripState) {
        this.state = state
    }
}

class TripMemory(private val store: TripStore, private val wallNow: () -> Long) : UsageTracker {
    var state: TripState = store.load()
        private set

    init {
        if (state.updatedAt > 0 && wallNow() - state.updatedAt > STALE_MS) reset(null)
    }

    fun reset(key: String?) {
        state = TripState(
            key = key,
            chapterIndex = state.chapterIndex,
            fillerCount = state.fillerCount,
            used = state.used,
            episodeCursor = state.episodeCursor,
            updatedAt = wallNow(),
        )
        store.save(state)
    }

    fun update(change: (TripState) -> TripState) {
        state = change(state).copy(updatedAt = wallNow())
        store.save(state)
    }

    override fun used(slot: String): Set<Int> = state.used[slot].orEmpty().toSet()

    override fun mark(slot: String, index: Int) = update { it.copy(used = it.used + (slot to (it.used[slot].orEmpty() + index))) }

    override fun clear(slot: String) = update { it.copy(used = it.used - slot) }

    private companion object {
        const val STALE_MS = 12 * 60 * 60 * 1000L
    }
}
