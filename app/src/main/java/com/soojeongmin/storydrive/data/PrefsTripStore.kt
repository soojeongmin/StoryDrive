package com.soojeongmin.storydrive.data

import android.content.Context
import com.soojeongmin.storydrive.story.TripState
import com.soojeongmin.storydrive.story.TripStore
import kotlinx.serialization.json.Json

class PrefsTripStore(context: Context) : TripStore {
    private val prefs = context.getSharedPreferences("trip", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    override fun load(): TripState =
        prefs.getString(KEY, null)?.let { runCatching { json.decodeFromString(TripState.serializer(), it) }.getOrNull() } ?: TripState()

    override fun save(state: TripState) {
        prefs.edit().putString(KEY, json.encodeToString(TripState.serializer(), state)).apply()
    }

    private companion object {
        const val KEY = "state"
    }
}
