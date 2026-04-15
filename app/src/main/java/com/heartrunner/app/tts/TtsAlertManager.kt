package com.heartrunner.app.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * TTS 语音播报管理器
 * 负责心率区间报警和定时心率播报
 */
class TtsAlertManager(context: Context) {

    private var tts: TextToSpeech? = null
    private val isReady = AtomicBoolean(false)
    private var utteranceId = 0

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.CHINESE)
                isReady.set(
                    result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED
                )
                tts?.setSpeechRate(1.1f)
            }
        }
    }

    fun speak(text: String) {
        if (!isReady.get()) return
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "utterance_${utteranceId++}")
    }

    fun speakNow(text: String) {
        if (!isReady.get()) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utterance_${utteranceId++}")
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady.set(false)
    }
}
