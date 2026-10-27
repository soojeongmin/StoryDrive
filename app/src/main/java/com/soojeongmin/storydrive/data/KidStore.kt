package com.soojeongmin.storydrive.data

import android.content.Context
import com.soojeongmin.storydrive.story.KidProfile
import com.soojeongmin.storydrive.story.MixedLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class KidStore(context: Context) {
    private val prefs = context.getSharedPreferences("kids", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val _kids = MutableStateFlow(load())
    val kids: StateFlow<List<KidProfile>> = _kids.asStateFlow()

    private val _mixedLevel = MutableStateFlow(
        prefs.getString(MIXED_KEY, null)?.let { runCatching { MixedLevel.valueOf(it) }.getOrNull() } ?: MixedLevel.TODDLER,
    )
    val mixedLevel: StateFlow<MixedLevel> = _mixedLevel.asStateFlow()

    private val _consented = MutableStateFlow(prefs.getBoolean(CONSENT_KEY, false))
    val consented: StateFlow<Boolean> = _consented.asStateFlow()

    fun giveConsent() {
        prefs.edit().putBoolean(CONSENT_KEY, true).apply()
        _consented.value = true
    }

    fun clearAll() = save(emptyList())

    fun setMixedLevel(level: MixedLevel) {
        prefs.edit().putString(MIXED_KEY, level.name).apply()
        _mixedLevel.value = level
    }

    fun upsert(kid: KidProfile) = save(_kids.value.filterNot { it.id == kid.id || it.seat == kid.seat } + kid)

    fun remove(id: String) = save(_kids.value.filterNot { it.id == id })

    private fun save(list: List<KidProfile>) {
        val sorted = list.sortedBy { it.seat.ordinal }
        prefs.edit().putString(KEY, SecureBox.seal(json.encodeToString(ListSerializer(KidProfile.serializer()), sorted))).apply()
        _kids.value = sorted
    }

    private fun load(): List<KidProfile> {
        val stored = prefs.getString(KEY, null) ?: return emptyList()
        val opened = SecureBox.open(stored)
        val list = runCatching { json.decodeFromString(ListSerializer(KidProfile.serializer()), opened ?: stored) }.getOrNull().orEmpty()
        if (opened == null && list.isNotEmpty()) {
            prefs.edit().putString(KEY, SecureBox.seal(json.encodeToString(ListSerializer(KidProfile.serializer()), list))).putBoolean(CONSENT_KEY, true).apply()
        }
        return list
    }

    private companion object {
        const val KEY = "profiles"
        const val MIXED_KEY = "mixed_level"
        const val CONSENT_KEY = "guardian_consent"
    }
}
