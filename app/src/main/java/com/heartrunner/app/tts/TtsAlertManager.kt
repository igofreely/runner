package com.heartrunner.app.tts

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.ArrayDeque
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.ConcurrentHashMap

/**
 * TTS 语音播报管理器
 * 负责心率区间报警和定时心率播报
 */
class TtsAlertManager(context: Context) {

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val pendingUtterances = ArrayDeque<PendingUtterance>()
    private val activeUtteranceCategories = ConcurrentHashMap<String, UtteranceCategory>()
    private val isReady = AtomicBoolean(false)
    private val utteranceCounter = AtomicInteger(0)
    private val activeWorkoutBroadcastCount = AtomicInteger(0)

    private var tts: TextToSpeech? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasRetriedWithDefaultEngine = false

    init {
        initializeTts(resolvePreferredEnginePackage())
    }

    fun speak(text: String, category: UtteranceCategory = UtteranceCategory.GENERAL) {
        enqueueOrSpeak(text, TextToSpeech.QUEUE_ADD, category)
    }

    fun speakNow(text: String, category: UtteranceCategory = UtteranceCategory.GENERAL) {
        enqueueOrSpeak(text, TextToSpeech.QUEUE_FLUSH, category)
    }

    fun hasActiveWorkoutBroadcast(): Boolean {
        return activeWorkoutBroadcastCount.get() > 0
    }

    fun stop() {
        tts?.stop()
        resetActiveUtterances()
        abandonAudioFocus()
    }

    fun shutdown() {
        synchronized(pendingUtterances) {
            pendingUtterances.clear()
        }
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady.set(false)
        resetActiveUtterances()
        abandonAudioFocus()
    }

    private fun enqueueOrSpeak(text: String, queueMode: Int, category: UtteranceCategory) {
        if (text.isBlank()) return

        val utterance = PendingUtterance(text = text, queueMode = queueMode, category = category)
        if (!isReady.get()) {
            synchronized(pendingUtterances) {
                if (queueMode == TextToSpeech.QUEUE_FLUSH) {
                    pendingUtterances.clear()
                }
                pendingUtterances.addLast(utterance)
            }
            Log.d(TAG, "Queued TTS before init completes: $text")
            return
        }

        Log.d(TAG, "Dispatching TTS text=$text queueMode=$queueMode category=$category")
        speakInternal(utterance)
    }

    private fun speakInternal(utterance: PendingUtterance) {
        val engine = tts ?: return
        requestAudioFocus()
        val utteranceId = "utterance_${utteranceCounter.getAndIncrement()}"
        registerUtterance(utteranceId, utterance.category)
        val result = engine.speak(utterance.text, utterance.queueMode, null, utteranceId)
        if (result != TextToSpeech.SUCCESS) {
            Log.w(TAG, "speak() failed: result=$result text=${utterance.text}")
            completeUtterance(utteranceId)
            abandonAudioFocus()
        }
    }

    private fun flushPendingUtterances() {
        val queued = mutableListOf<PendingUtterance>()
        synchronized(pendingUtterances) {
            while (pendingUtterances.isNotEmpty()) {
                queued.add(pendingUtterances.removeFirst())
            }
        }

        queued.forEach(::speakInternal)
    }

    private fun configureLanguage(engine: TextToSpeech): Locale? {
        val preferredLocales = listOf(
            Locale.SIMPLIFIED_CHINESE,
            Locale.CHINA,
            Locale.TRADITIONAL_CHINESE,
            Locale.TAIWAN,
            Locale.CHINESE,
            Locale.getDefault()
        ).distinct()

        for (locale in preferredLocales) {
            val availability = engine.isLanguageAvailable(locale)
            if (!isUsableLanguageResult(availability)) {
                continue
            }

            val result = engine.setLanguage(locale)
            if (isUsableLanguageResult(result)) {
                return locale
            }
        }

        return null
    }

    private fun initializeTts(enginePackage: String?) {
        Log.i(
            TAG,
            "Initializing TTS with engine=${enginePackage ?: "<system-default>"} available=${queryTtsEnginePackages()}"
        )
        tts = if (enginePackage.isNullOrBlank()) {
            TextToSpeech(appContext, { status -> onTtsInitialized(status, enginePackage) })
        } else {
            TextToSpeech(appContext, { status -> onTtsInitialized(status, enginePackage) }, enginePackage)
        }
    }

    private fun onTtsInitialized(status: Int, enginePackage: String?) {
        Log.i(TAG, "TTS init callback: status=$status engine=${enginePackage ?: "<system-default>"}")
        if (status != TextToSpeech.SUCCESS) {
            maybeRetryWithSystemDefault(enginePackage, status)
            return
        }

        val engine = tts ?: run {
            Log.e(TAG, "TTS init callback received but engine is null")
            isReady.set(false)
            return
        }

        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Log.d(TAG, "TTS started: utterance=$utteranceId")
            }

            override fun onDone(utteranceId: String?) {
                completeUtterance(utteranceId)
                Log.d(TAG, "TTS completed: utterance=$utteranceId")
                abandonAudioFocus()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                completeUtterance(utteranceId)
                Log.e(TAG, "TTS playback failed for utterance=$utteranceId")
                abandonAudioFocus()
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                completeUtterance(utteranceId)
                Log.e(TAG, "TTS playback failed for utterance=$utteranceId errorCode=$errorCode")
                abandonAudioFocus()
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                completeUtterance(utteranceId)
                Log.d(TAG, "TTS stopped: utterance=$utteranceId interrupted=$interrupted")
                abandonAudioFocus()
            }
        })

        engine.setAudioAttributes(audioAttributes)
        engine.setSpeechRate(1.1f)

        val selectedLocale = configureLanguage(engine)
        if (selectedLocale == null) {
            Log.e(TAG, "No supported TTS locale found for Chinese prompts")
            isReady.set(false)
            return
        }

        isReady.set(true)
        Log.i(TAG, "TTS ready with locale=${selectedLocale.toLanguageTag()}")
        flushPendingUtterances()
    }

    private fun maybeRetryWithSystemDefault(enginePackage: String?, status: Int) {
        if (!enginePackage.isNullOrBlank() && !hasRetriedWithDefaultEngine) {
            hasRetriedWithDefaultEngine = true
            Log.w(
                TAG,
                "Explicit TTS engine init failed for $enginePackage with status=$status, retrying with system default"
            )
            tts?.shutdown()
            tts = null
            initializeTts(null)
            return
        }

        Log.e(TAG, "TTS init failed: status=$status")
        isReady.set(false)
    }

    private fun resolvePreferredEnginePackage(): String? {
        val configuredDefault = Settings.Secure.getString(
            appContext.contentResolver,
            Settings.Secure.TTS_DEFAULT_SYNTH
        )?.takeIf { it.isNotBlank() }

        val availableEngines = queryTtsEnginePackages()
        val selectedEngine = when {
            configuredDefault != null && availableEngines.contains(configuredDefault) -> configuredDefault
            availableEngines.size == 1 -> availableEngines.first()
            availableEngines.isNotEmpty() -> availableEngines.first()
            else -> null
        }

        Log.i(
            TAG,
            "Resolved TTS engine: configuredDefault=${configuredDefault ?: "<none>"} selected=${selectedEngine ?: "<none>"}"
        )
        return selectedEngine
    }

    @Suppress("DEPRECATION")
    private fun queryTtsEnginePackages(): List<String> {
        val services = appContext.packageManager.queryIntentServices(
            Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE),
            0
        )
        return services.map { it.serviceInfo.packageName }.distinct()
    }

    private fun isUsableLanguageResult(result: Int): Boolean {
        return result != TextToSpeech.LANG_MISSING_DATA &&
            result != TextToSpeech.LANG_NOT_SUPPORTED
    }

    private fun registerUtterance(utteranceId: String, category: UtteranceCategory) {
        activeUtteranceCategories[utteranceId] = category
        if (category == UtteranceCategory.WORKOUT_BROADCAST) {
            activeWorkoutBroadcastCount.incrementAndGet()
        }
    }

    private fun completeUtterance(utteranceId: String?) {
        val category = utteranceId?.let(activeUtteranceCategories::remove) ?: return
        if (category == UtteranceCategory.WORKOUT_BROADCAST) {
            decrementWorkoutBroadcastCount()
        }
    }

    private fun decrementWorkoutBroadcastCount() {
        while (true) {
            val current = activeWorkoutBroadcastCount.get()
            if (current == 0) return
            if (activeWorkoutBroadcastCount.compareAndSet(current, current - 1)) {
                return
            }
        }
    }

    private fun resetActiveUtterances() {
        activeUtteranceCategories.clear()
        activeWorkoutBroadcastCount.set(0)
    }

    private fun requestAudioFocus() {
        if (audioManager == null) return

        val request = audioFocusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(audioAttributes)
            .setAcceptsDelayedFocusGain(false)
            .setWillPauseWhenDucked(false)
            .setOnAudioFocusChangeListener { }
            .build()
            .also { audioFocusRequest = it }

        val result = audioManager.requestAudioFocus(request)
        if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Log.w(TAG, "Audio focus not granted: result=$result")
        }
    }

    private fun abandonAudioFocus() {
        val request = audioFocusRequest ?: return
        audioManager?.abandonAudioFocusRequest(request)
    }

    private data class PendingUtterance(
        val text: String,
        val queueMode: Int,
        val category: UtteranceCategory
    )

    enum class UtteranceCategory {
        GENERAL,
        WORKOUT_BROADCAST
    }

    companion object {
        private const val TAG = "TtsAlertManager"
    }
}
