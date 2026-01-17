package com.app.sixtyseven

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Listens for "hey six seven" trigger with continuous listening.
 * Attempts to use Bluetooth headset mic if available.
 */
class VoiceTrigger(private val context: Context) {
    
    companion object {
        private const val TAG = "VoiceTrigger"
        
        // Trigger phrases to detect (lowercase) - including common misheard variants
        val TRIGGER_PHRASES = listOf(
            // Intended
            "hey six seven",
            "hey sixty seven", 
            "hey 67",
            "a six seven",
            "a sixty seven",
            
            // Misheard variants
            "867",
            "8 67",
            "a 67",
            "hey67",
            "six seven",
            "sixty seven",
            "6 7",
            "hey 6 7",
            "a67",
            "hey sick seven",
            "hey sex seven",
            "hey 6 seven",
            "hey six 7",
            "8 six seven",
            "eight six seven",
            "eight sixty seven",
            "hey sixteen",
            "a sixteen seven",
            "hey sixteen seven",
            "367",
            "3 67",
            "three six seven",
            "three sixty seven"
        )
    }
    
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var lastTriggerTime = 0L
    private val triggerCooldownMs = 3000L // Prevent double triggers
    
    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive
    
    private val _lastHeard = MutableStateFlow<String?>(null)
    val lastHeard: StateFlow<String?> = _lastHeard
    
    private var onTriggerCallback: ((command: String) -> Unit)? = null
    private var logCallback: ((String) -> Unit)? = null
    
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var bluetoothScoStarted = false
    
    fun setLogCallback(callback: (String) -> Unit) {
        logCallback = callback
    }
    
    private fun log(msg: String) {
        Log.d(TAG, msg)
        logCallback?.invoke(msg)
    }
    
    private fun startBluetoothSco() {
        try {
            if (audioManager.isBluetoothScoAvailableOffCall) {
                log("🎧 Bluetooth SCO available, starting...")
                audioManager.startBluetoothSco()
                audioManager.isBluetoothScoOn = true
                bluetoothScoStarted = true
                log("✓ Bluetooth mic enabled")
            } else {
                log("⚠ Bluetooth SCO not available")
            }
        } catch (e: Exception) {
            log("⚠ Failed to start Bluetooth SCO: ${e.message}")
        }
    }
    
    private fun stopBluetoothSco() {
        if (bluetoothScoStarted) {
            try {
                audioManager.isBluetoothScoOn = false
                audioManager.stopBluetoothSco()
                bluetoothScoStarted = false
                log("Bluetooth mic disabled")
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
    
    fun startListening(onTrigger: (command: String) -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            log("✗ Speech recognition not available on this device")
            return
        }
        
        if (isListening) {
            log("Already listening")
            return
        }
        
        onTriggerCallback = onTrigger
        isListening = true
        _isActive.value = true
        
        // Try to use Bluetooth mic
        startBluetoothSco()
        
        createRecognizer()
        startRecognition()
    }
    
    private fun createRecognizer() {
        speechRecognizer?.destroy()
        
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    log("🎤 Listening...")
                }
                
                override fun onBeginningOfSpeech() {
                    log("🎤 Speech detected...")
                }
                
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                
                override fun onEndOfSpeech() {
                    // Don't log this, it's noisy
                }
                
                override fun onError(error: Int) {
                    val errorMsg = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Timeout"
                        SpeechRecognizer.ERROR_AUDIO -> "Audio error"
                        SpeechRecognizer.ERROR_CLIENT -> "Client error"
                        SpeechRecognizer.ERROR_NETWORK -> "Network error"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                        else -> "Error $error"
                    }
                    
                    // Only log real errors, not timeouts
                    if (error != SpeechRecognizer.ERROR_NO_MATCH && 
                        error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                        log("⚠ $errorMsg")
                    }
                    
                    // Restart listening - recreate recognizer for serious errors
                    if (isListening) {
                        if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || 
                            error == SpeechRecognizer.ERROR_CLIENT ||
                            error == SpeechRecognizer.ERROR_AUDIO) {
                            // Recreate recognizer for these errors
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                if (isListening) {
                                    createRecognizer()
                                    startRecognition()
                                }
                            }, 1000)
                        } else {
                            restartListening()
                        }
                    }
                }
                
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    matches?.firstOrNull()?.let { heard ->
                        processHeard(heard)
                    }
                    
                    // Always restart listening after results
                    restartListening()
                }
                
                override fun onPartialResults(partialResults: Bundle?) {
                    // Don't process partial results to avoid false triggers
                }
                
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }
    
    private fun processHeard(heard: String) {
        val heardLower = heard.lowercase().trim()
        _lastHeard.value = heard
        
        Log.d(TAG, "Heard: $heard")
        
        // Check cooldown to prevent double triggers
        val now = System.currentTimeMillis()
        if (now - lastTriggerTime < triggerCooldownMs) {
            log("🎤 \"$heard\" (cooldown)")
            return
        }
        
        // Check if any trigger phrase is in what we heard
        var foundTrigger: String? = null
        for (trigger in TRIGGER_PHRASES) {
            if (heardLower.contains(trigger)) {
                foundTrigger = trigger
                break
            }
        }
        
        if (foundTrigger != null) {
            lastTriggerTime = now
            
            // Extract command after trigger phrase
            val command = heardLower
                .substringAfter(foundTrigger)
                .trim()
            
            log("🎯 TRIGGER: \"$foundTrigger\"")
            
            if (command.isNotEmpty()) {
                log("📝 Command: \"$command\"")
                onTriggerCallback?.invoke(command)
            } else {
                log("⚠ No command after trigger. Say 'hey six seven research...' or 'hey six seven imagine...'")
            }
        } else {
            // Just show what was heard without triggering
            log("🎤 \"$heard\"")
        }
    }
    
    private fun startRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false) // Disable partial to reduce false triggers
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        }
        
        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            log("✗ Failed to start: ${e.message}")
        }
    }
    
    private fun restartListening() {
        if (!isListening) return
        
        // Small delay before restarting to avoid rapid loops
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (isListening) {
                try {
                    // Cancel any pending recognition first
                    speechRecognizer?.cancel()
                    startRecognition()
                } catch (e: Exception) {
                    log("⚠ Restart failed, recreating recognizer...")
                    createRecognizer()
                    startRecognition()
                }
            }
        }, 500) // Increased delay for stability
    }
    
    fun stopListening() {
        isListening = false
        _isActive.value = false
        
        // Stop Bluetooth SCO
        stopBluetoothSco()
        
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            // Ignore
        }
        speechRecognizer = null
        
        log("⏹ Voice listening stopped")
    }
}
