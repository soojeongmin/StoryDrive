package com.soojeongmin.storydrive.speech

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.soojeongmin.storydrive.BuildConfig
import java.util.Locale

class Listener(context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private var stt: SpeechToText? = null
    private var ready = false
    private var pending: ((String?) -> Unit)? = null
    private var best: String? = null

    private val timeout = Runnable { finish(best) }

    private val results = object : ResultListener {
        override fun onUpdated(text: String, isFinal: Boolean) {
            handler.post {
                if (text.isNotBlank()) best = text
                if (BuildConfig.DEBUG) Log.i(TAG, "heard final=$isFinal: $text")
                if (isFinal) finish(best)
            }
        }
        override fun onUpdatedEpdData(on: Long, off: Long) = Unit
        override fun onStartedRecognition() = Unit
        override fun onEndedRecognition() {
            handler.post { finish(best) }
        }
        override fun onError(error: ErrorType) {
            Log.w(TAG, "stt error: $error")
            handler.post { finish(best) }
        }
        override fun onReady() = Unit
        override fun onUpdatedRms(rms: Double) = Unit
    }

    init {
        runCatching {
            SpeechToText(context.applicationContext, Mode.ON_DEVICE).also { engine ->
                stt = engine
                engine.initialize(object : OnInitializeListener {
                    override fun onSuccess() {
                        handler.post {
                            engine.addListener(results)
                            engine.setLocale(Locale.KOREA)
                            ready = true
                            Log.i(TAG, "stt ready")
                        }
                    }
                    override fun onFailure(error: ErrorType) {
                        Log.w(TAG, "stt init failed: $error")
                    }
                })
            }
        }.onFailure { Log.w(TAG, "stt unavailable", it) }
    }

    val available: Boolean get() = ready

    fun listen(windowMs: Long, onResult: (String?) -> Unit) {
        handler.post {
            finish(null)
            if (!ready) {
                onResult(null)
                return@post
            }
            best = null
            pending = onResult
            runCatching { stt?.request() }.onFailure {
                Log.w(TAG, "stt request failed", it)
                finish(null)
                return@post
            }
            handler.postDelayed(timeout, windowMs)
        }
    }

    private fun finish(result: String?) {
        val callback = pending ?: return
        pending = null
        handler.removeCallbacks(timeout)
        runCatching { stt?.stop() }
        callback(result)
    }

    fun deliver(text: String) {
        handler.post {
            if (BuildConfig.DEBUG) Log.i(TAG, "delivered: $text")
            finish(text)
        }
    }

    fun release() {
        finish(null)
        stt?.release()
    }

    private companion object {
        const val TAG = "Listener"
    }
}
