package com.soojeongmin.storydrive

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.soojeongmin.storydrive.data.KidStore
import com.soojeongmin.storydrive.data.PrefsTripStore
import com.soojeongmin.storydrive.story.TripMemory
import com.soojeongmin.storydrive.navi.RouteFeed
import com.soojeongmin.storydrive.service.HideoutNotifier
import com.soojeongmin.storydrive.dev.DevBenches
import com.soojeongmin.storydrive.dev.DevHooks
import com.soojeongmin.storydrive.speech.Narrator
import com.soojeongmin.storydrive.story.RouteEvent
import com.soojeongmin.storydrive.story.StoryDirector
import com.soojeongmin.storydrive.story.StoryLibrary
import com.soojeongmin.storydrive.story.TripProgress
import com.soojeongmin.storydrive.vehicle.CabinMonitor
import com.soojeongmin.storydrive.vehicle.CabinState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class StoryEngine(context: Context) {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var started = false

    val kidStore = KidStore(appContext)
    private val realCabin = CabinMonitor(appContext)
    val narrator = Narrator(appContext)
    val listener = com.soojeongmin.storydrive.speech.Listener(appContext)
    private val library = StoryLibrary.parse(
        appContext.assets.open("stories/ko.json").bufferedReader().use { it.readText() },
        episodes = appContext.assets.open("stories/episodes_ko.json").bufferedReader().use { it.readText() },
    )
    private val director = StoryDirector(
        library = library,
        narration = narrator,
        now = SystemClock::elapsedRealtime,
        members = { kidStore.kids.value },
        hideoutPrompt = HideoutNotifier(appContext),
        memory = TripMemory(PrefsTripStore(appContext), System::currentTimeMillis),
        mixedLevel = { kidStore.mixedLevel.value },
        listen = { window, done -> listener.listen(window, done) },
    )
    val devBench = DevBenches.create(
        DevHooks(scope, { kidStore.kids.value }, ::onRoute, ::decideHideout, kidStore::upsert, director::beginNewTrip, narrator, listener),
    )
    private val benchCabin: StateFlow<CabinState?> = devBench?.cabin ?: MutableStateFlow(null)

    private val route = RouteFeed(appContext) { if (benchCabin.value == null) onRoute(it) }

    val cabin: StateFlow<CabinState> = combine(realCabin.state, benchCabin) { real, sim -> sim ?: real }
        .stateIn(scope, SharingStarted.Eagerly, CabinState())

    private val _progress = MutableStateFlow(TripProgress())
    val progress: StateFlow<TripProgress> = _progress.asStateFlow()

    private val ticker = object : Runnable {
        override fun run() {
            director.tick()
            publish()
            handler.postDelayed(this, TICK_MS)
        }
    }

    init {
        scope.launch { cabin.collect { director.onCabin(it); publish() } }
    }

    fun start() {
        if (started) return
        started = true
        realCabin.start()
        route.start()
        handler.post(ticker)
    }

    fun stop() {
        if (!started) return
        started = false
        handler.removeCallbacks(ticker)
        route.stop()
        realCabin.stop()
    }

    fun suspend() {
        narrator.interrupt()
        narrator.abandonFocus()
        stop()
    }

    fun refreshPermissions() = realCabin.refreshPermissions()

    fun decideHideout(stop: Boolean) {
        director.decideHideout(stop)
        publish()
    }

    private fun onRoute(event: RouteEvent) {
        director.onRoute(event)
        publish()
    }

    private fun publish() {
        _progress.value = director.progress
    }

    private companion object {
        const val TICK_MS = 1_000L
    }
}
