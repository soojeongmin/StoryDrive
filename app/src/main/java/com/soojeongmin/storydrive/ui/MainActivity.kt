package com.soojeongmin.storydrive.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.soojeongmin.storydrive.BuildConfig
import com.soojeongmin.storydrive.StoryDriveApp
import com.soojeongmin.storydrive.service.StoryService
import com.soojeongmin.storydrive.story.KidProfile
import com.soojeongmin.storydrive.vehicle.SeatPosition

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions(VEHICLE_PERMISSIONS, 1)
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val micIntroDue = checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED && !prefs.getBoolean(MIC_INTRO_SEEN, false)
        StoryService.start(this)
        val engine = (application as StoryDriveApp).engine
        setContent {
            StoryTheme {
                val cabin by engine.cabin.collectAsState()
                val kids by engine.kidStore.kids.collectAsState()
                val mixedLevel by engine.kidStore.mixedLevel.collectAsState()
                val consented by engine.kidStore.consented.collectAsState()
                val progress by engine.progress.collectAsState()
                val speaking by engine.narrator.current.collectAsState()
                val paused by engine.narrator.paused.collectAsState()
                var managing by remember { mutableStateOf(kids.isEmpty()) }
                var micIntro by remember { mutableStateOf(micIntroDue) }
                var seatForm by remember { mutableStateOf<Pair<SeatPosition, KidProfile?>?>(null) }
                val form = seatForm
                val locked = cabin.setupLocked
                val closeMicIntro = {
                    prefs.edit().putBoolean(MIC_INTRO_SEEN, true).apply()
                    micIntro = false
                }
                if (micIntro && !locked) {
                    MicIntroScreen(
                        onAllow = {
                            closeMicIntro()
                            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), MIC_REQUEST)
                        },
                        onLater = closeMicIntro,
                    )
                } else if (!consented && !locked && (form != null || managing || kids.isEmpty())) {
                    ConsentScreen(
                        onAgree = engine.kidStore::giveConsent,
                        onDecline = { seatForm = null; managing = false; moveTaskToBack(true) },
                    )
                } else if (locked && (form != null || managing || kids.isEmpty())) {
                    BackHandler { if (form != null || managing) { seatForm = null; managing = false } else moveTaskToBack(true) }
                    SetupLockedScreen()
                } else if (form != null) {
                    KidQuestions(
                        initial = form.second,
                        takenSeats = kids.filter { it.id != form.second?.id }.map { it.seat }.toSet(),
                        presetSeat = form.first,
                        onDone = { engine.kidStore.upsert(it); seatForm = null },
                        onCancel = { seatForm = null },
                    )
                } else if (managing || kids.isEmpty()) {
                    KidsScreen(
                        kids = kids,
                        mixedLevel = mixedLevel,
                        onMixedLevel = engine.kidStore::setMixedLevel,
                        onSave = engine.kidStore::upsert,
                        onDelete = engine.kidStore::remove,
                        onDeleteAll = engine.kidStore::clearAll,
                        onBack = { managing = false },
                    )
                } else {
                    BackHandler { moveTaskToBack(true) }
                    CabinScreen(
                        cabin, kids, progress, speaking,
                        paused = paused,
                        onTogglePause = { if (paused) engine.narrator.resume() else engine.narrator.pause() },
                        onManageKids = { managing = true },
                        onSeatClick = { seat, kid -> seatForm = seat to kid },
                        extra = engine.devBench?.let { bench -> { bench.Panel(cabin, kids) } },
                    )
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        (application as StoryDriveApp).engine.refreshPermissions()
        if (requestCode == MIC_REQUEST) StoryService.start(this)
    }

    private companion object {
        const val PREFS = "app"
        const val MIC_INTRO_SEEN = "mic_intro_seen"
        const val MIC_REQUEST = 2

        val VEHICLE_PERMISSIONS = arrayOf(
            "pleos.car.permission.CAR_DRIVE",
            "pleos.car.permission.CAR_SEATS",
            "pleos.car.permission.CAR_CLIMATE",
            "android.permission.POST_NOTIFICATIONS",
            "pleos.car.permission.TTS_SERVICE",
            "pleos.car.permission.STT_SERVICE",
            "android.car.permission.CAR_SPEED",
            "android.car.permission.CAR_POWERTRAIN",
        )
    }
}
