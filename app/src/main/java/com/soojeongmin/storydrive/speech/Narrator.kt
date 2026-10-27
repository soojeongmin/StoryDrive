package com.soojeongmin.storydrive.speech

import ai.pleos.playground.tts.TextToSpeech as PleosTts
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import ai.pleos.playground.tts.listener.OnServerConnectionListener
import com.soojeongmin.storydrive.BuildConfig
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.soojeongmin.storydrive.story.Line
import com.soojeongmin.storydrive.story.Narration
import com.soojeongmin.storydrive.story.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class Narrator(context: Context) : Narration {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val queue = ArrayDeque<Line>()
    private val _current = MutableStateFlow<Line?>(null)
    val current: StateFlow<Line?> = _current.asStateFlow()

    private var engine: Engine = Engine.NONE
    private var pleos: PleosTts? = null
    private var pleosMode = Mode.ON_DEVICE
    private var android: TextToSpeech? = null

    private val audio = appContext.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private var hasFocus = false
    private var resumeOnGain = false
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(attributes)
        .setAcceptsDelayedFocusGain(true)
        .setOnAudioFocusChangeListener(::onFocusChange, handler)
        .build()
    private val releaseFocus = Runnable {
        if (_current.value == null && queue.isEmpty() && hasFocus) {
            audio.abandonAudioFocusRequest(focusRequest)
            hasFocus = false
        }
    }

    private val pleosEvents = object : EventListener {
        override fun onStart() = Unit
        override fun onReady() = Unit
        override fun onDone() = finished()
        override fun onError(error: ErrorType) {
            Log.w(TAG, "pleos tts error: $error")
            if (error == ErrorType.UNAUTHENTICATED) onMain {
                _current.value?.let(queue::addFirst)
                useFallback()
            }
            if (error == ErrorType.ON_DEVICE_ENGINE_ERROR || error == ErrorType.UNAVAILABLE_LANGUAGE) {
                onMain {
                    if (pleosMode == Mode.ON_DEVICE && hasCredentials()) {
                        pleos?.release()
                        engine = Engine.NONE
                        startPleos(Mode.SERVER)
                    } else {
                        useFallback()
                    }
                }
            }
            finished()
        }
        override fun onStop() = Unit
        override fun onUpdatedRms(rms: Double) = Unit
    }

    override val isSpeaking: Boolean get() = _current.value != null || queue.isNotEmpty() || held

    private val _paused = MutableStateFlow(false)
    val paused: StateFlow<Boolean> = _paused.asStateFlow()

    fun pause() = onMain {
        if (_paused.value) return@onMain
        _paused.value = true
        val speaking = _current.value
        if (speaking != null && speaking.priority != Priority.SAFETY) {
            stopEngine()
            _current.value = null
            queue.addFirst(speaking.copy(then = speaking.then))
        }
    }

    fun resume() = onMain {
        if (!_paused.value) return@onMain
        _paused.value = false
        pump()
    }

    init {
        startPleos(Mode.ON_DEVICE)
    }

    private fun hasCredentials() = BuildConfig.PLEOS_CLIENT_ID.isNotBlank() && BuildConfig.PLEOS_CLIENT_SECRET.isNotBlank()

    private fun startPleos(mode: Mode) {
        runCatching {
            PleosTts(appContext, mode).also { tts ->
                pleos = tts
                pleosMode = mode
                Log.i(TAG, "pleos tts initialize $mode")
                tts.initialize(object : OnInitializeListener {
                    override fun onSuccess() = onMain {
                        Log.i(TAG, "pleos tts ready")
                        tts.addEventListener(pleosEvents)
                        tts.setLocale(Locale.KOREA)
                        if (mode == Mode.ON_DEVICE) {
                            handler.postDelayed({ ready() }, WARMUP_MS)
                        } else {
                            tts.registerApp(BuildConfig.PLEOS_CLIENT_ID, BuildConfig.PLEOS_CLIENT_SECRET, object : OnServerConnectionListener {
                                override fun onConnected() = onMain {
                                    Log.i(TAG, "pleos tts server connected")
                                    handler.postDelayed({ ready() }, WARMUP_MS)
                                }
                                override fun onFailed(error: ErrorType) = onMain {
                                    Log.w(TAG, "pleos tts register failed: $error")
                                    ready()
                                }
                            })
                        }
                    }
                    override fun onFailure(error: ErrorType) = onMain {
                        Log.w(TAG, "pleos tts init failed: $error")
                        useFallback()
                    }
                })
            }
        }.onFailure {
            Log.w(TAG, "pleos tts unavailable", it)
            useFallback()
        }
    }

    override fun say(line: Line) = onMain {
        if (line.priority == Priority.SAFETY) {
            queue.removeAll { it.priority != Priority.SAFETY }
            queue.addFirst(line)
        } else {
            val index = queue.indexOfFirst { it.priority < line.priority }
            if (index < 0) queue.addLast(line) else queue.add(index, line)
        }
        pump()
    }

    override fun interrupt() = onMain {
        queue.removeAll { it.priority != Priority.SAFETY }
        stopEngine()
        _current.value = null
    }

    private fun ready() {
        engine = Engine.PLEOS
        pump()
    }

    fun abandonFocus() = onMain {
        handler.removeCallbacks(releaseFocus)
        audio.abandonAudioFocusRequest(focusRequest)
        hasFocus = false
    }

    fun release() {
        stopEngine()
        audio.abandonAudioFocusRequest(focusRequest)
        hasFocus = false
        pleos?.release()
        android?.shutdown()
    }

    private fun pump() {
        if (_current.value != null || engine == Engine.NONE) return
        val blocked = held || _paused.value
        if (blocked && queue.firstOrNull()?.priority != Priority.SAFETY) return
        val upcoming = queue.firstOrNull() ?: run {
            handler.removeCallbacks(releaseFocus)
            handler.postDelayed(releaseFocus, FOCUS_RELEASE_MS)
            return
        }
        if (!acquireFocus() && upcoming.priority != Priority.SAFETY) return
        val next = queue.removeFirst()
        _current.value = next
        if (BuildConfig.DEBUG) Log.i(TAG, "speak[$engine]: ${next.text}")
        if (BuildConfig.DEBUG) Log.i(CAPTION_TAG, "${System.currentTimeMillis()}|${next.text}")
        when (engine) {
            Engine.PLEOS -> pleos?.speak(next.text, true)
            Engine.ANDROID -> android?.apply { setAudioAttributes(attributes) }?.speak(next.text, TextToSpeech.QUEUE_FLUSH, null, next.hashCode().toString())
            Engine.SILENT -> handler.postDelayed(silentDone, estimateMs(next.text))
            Engine.NONE -> Unit
        }
    }

    private var held = false

    private fun acquireFocus(): Boolean {
        handler.removeCallbacks(releaseFocus)
        if (hasFocus) return true
        hasFocus = audio.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return hasFocus
    }

    private fun onFocusChange(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasFocus = true
                if (resumeOnGain) {
                    resumeOnGain = false
                    resume()
                } else {
                    pump()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                hasFocus = false
                if (!_paused.value) {
                    resumeOnGain = true
                    pause()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                hasFocus = false
                resumeOnGain = false
                pause()
            }
        }
    }

    override val isPaused: Boolean get() = _paused.value

    override fun hold(on: Boolean) = onMain {
        held = on
        if (!on) pump()
    }

    private fun finished() = onMain {
        val done = _current.value
        _current.value = null
        done?.then?.invoke()
        handler.postDelayed(::pump, GAP_MS + (done?.pauseAfterMs ?: 0L))
    }

    private val silentDone = Runnable { finished() }

    private fun estimateMs(text: String) = (text.length * MS_PER_CHAR).coerceAtLeast(MIN_SILENT_MS)

    private fun stopEngine() {
        when (engine) {
            Engine.PLEOS -> pleos?.stop()
            Engine.ANDROID -> android?.stop()
            Engine.SILENT -> handler.removeCallbacks(silentDone)
            Engine.NONE -> Unit
        }
    }

    private fun useFallback() {
        if (engine == Engine.ANDROID || engine == Engine.SILENT) return
        engine = Engine.NONE
        android = TextToSpeech(appContext) { status ->
            Log.i(TAG, "android tts status=$status")
            if (status != TextToSpeech.SUCCESS) {
                if (BuildConfig.DEBUG) onMain {
                    Log.i(TAG, "silent caption mode")
                    engine = Engine.SILENT
                    _current.value = null
                    pump()
                }
                return@TextToSpeech
            }
            android?.language = Locale.KOREA
            android?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) = Unit
                override fun onDone(id: String?) = finished()
                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) = finished()
            })
            onMain {
                engine = Engine.ANDROID
                pump()
            }
        }
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else handler.post(block)
    }

    private enum class Engine { NONE, PLEOS, ANDROID, SILENT }

    private companion object {
        const val TAG = "Narrator"
        const val CAPTION_TAG = "StoryCaption"
        const val MS_PER_CHAR = 130L
        const val MIN_SILENT_MS = 1_500L
        const val GAP_MS = 600L
        const val FOCUS_RELEASE_MS = 5_000L
        const val WARMUP_MS = 1_500L
    }
}
