package com.app.sixtyseven

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

/**
 * Text-to-Speech helper for audio feedback.
 * Routes to Bluetooth (Ray-Bans) if connected.
 */
class Speaker(context: Context) : TextToSpeech.OnInitListener {
    
    companion object {
        private const val TAG = "Speaker"
    }
    
    private var tts: TextToSpeech? = null
    private var isReady = false
    private var pendingText: String? = null
    
    init {
        tts = TextToSpeech(context, this)
    }
    
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "Language not supported")
            } else {
                isReady = true
                Log.d(TAG, "TTS initialized")
                
                // Speak any pending text
                pendingText?.let {
                    speak(it)
                    pendingText = null
                }
            }
        } else {
            Log.e(TAG, "TTS init failed: $status")
        }
    }
    
    fun speak(text: String) {
        if (!isReady) {
            pendingText = text
            return
        }
        
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "utterance_${System.currentTimeMillis()}")
        Log.d(TAG, "Speaking: $text")
    }
    
    fun stop() {
        tts?.stop()
    }
    
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }
}
