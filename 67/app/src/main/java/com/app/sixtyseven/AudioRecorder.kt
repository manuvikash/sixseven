package com.app.sixtyseven

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Records audio from the device microphone.
 * When glasses are connected via Bluetooth, this captures audio from the glasses' mic array.
 */
class AudioRecorder(private val context: Context) {
    
    companion object {
        private const val TAG = "AudioRecorder"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }
    
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    
    /**
     * Record audio for specified duration.
     * Returns WAV file bytes.
     */
    suspend fun recordAudio(durationMs: Long): ByteArray? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "RECORD_AUDIO permission not granted")
            return null
        }
        
        return withContext(Dispatchers.IO) {
            try {
                val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
                
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
                )
                
                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "AudioRecord failed to initialize")
                    return@withContext null
                }
                
                val outputStream = ByteArrayOutputStream()
                val buffer = ByteArray(bufferSize)
                
                audioRecord?.startRecording()
                isRecording = true
                
                val startTime = System.currentTimeMillis()
                
                while (isRecording && (System.currentTimeMillis() - startTime) < durationMs) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        outputStream.write(buffer, 0, read)
                    }
                }
                
                audioRecord?.stop()
                audioRecord?.release()
                audioRecord = null
                isRecording = false
                
                val pcmData = outputStream.toByteArray()
                Log.d(TAG, "Recorded ${pcmData.size} bytes of audio")
                
                // Convert PCM to WAV
                pcmToWav(pcmData)
                
            } catch (e: Exception) {
                Log.e(TAG, "Recording failed", e)
                audioRecord?.release()
                audioRecord = null
                isRecording = false
                null
            }
        }
    }
    
    fun stopRecording() {
        isRecording = false
    }
    
    /**
     * Convert raw PCM data to WAV format.
     */
    private fun pcmToWav(pcmData: ByteArray): ByteArray {
        val totalDataLen = pcmData.size + 36
        val byteRate = SAMPLE_RATE * 1 * 16 / 8 // sampleRate * channels * bitsPerSample / 8
        
        val header = ByteArray(44)
        
        // RIFF header
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        
        // File size - 8
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        
        // WAVE
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        
        // fmt chunk
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        
        // Subchunk1 size (16 for PCM)
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0
        
        // Audio format (1 = PCM)
        header[20] = 1
        header[21] = 0
        
        // Channels (1 = mono)
        header[22] = 1
        header[23] = 0
        
        // Sample rate
        header[24] = (SAMPLE_RATE and 0xff).toByte()
        header[25] = ((SAMPLE_RATE shr 8) and 0xff).toByte()
        header[26] = ((SAMPLE_RATE shr 16) and 0xff).toByte()
        header[27] = ((SAMPLE_RATE shr 24) and 0xff).toByte()
        
        // Byte rate
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        
        // Block align
        header[32] = (1 * 16 / 8).toByte()
        header[33] = 0
        
        // Bits per sample
        header[34] = 16
        header[35] = 0
        
        // data chunk
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        
        // Data size
        header[40] = (pcmData.size and 0xff).toByte()
        header[41] = ((pcmData.size shr 8) and 0xff).toByte()
        header[42] = ((pcmData.size shr 16) and 0xff).toByte()
        header[43] = ((pcmData.size shr 24) and 0xff).toByte()
        
        return header + pcmData
    }
}
