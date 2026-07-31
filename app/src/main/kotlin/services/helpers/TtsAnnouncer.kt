package io.github.teslanav.app.services.helpers

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

private const val TAG = "TtsAnnouncer"

class TtsAnnouncer(context: Context) {
    private var tts: TextToSpeech? = null
    private var ready = false

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val locale = Locale.getDefault()
                val result = tts?.setLanguage(locale)
                ready = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
                Log.d(TAG, if (ready) "TTS engine ready ($locale)" else "Language $locale not supported by the TTS engine")
            } else {
                Log.w(TAG, "TTS engine initialization failed (status=$status)")
            }
        }
    }

    fun speak(text: String) {
        if (!ready) {
            Log.w(TAG, "TTS not ready, message dropped: $text")
            return
        }
        Log.d(TAG, "speak: $text")
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, null)
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
