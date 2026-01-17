package com.app.sixtyseven

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * HTTP client for the SixSeven (67) API.
 * 
 * Endpoint: POST /v1/command
 * Content-Type: application/json
 */
class ApiClient(private val baseUrl: String) {
    
    companion object {
        private const val TAG = "ApiClient"
    }
    
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .addInterceptor { chain ->
            // Add ngrok header to bypass browser warning
            val request = chain.request().newBuilder()
                .addHeader("ngrok-skip-browser-warning", "true")
                .build()
            chain.proceed(request)
        }
        .build()
    
    data class CommandResponse(
        val intent: String,
        val message: String,
        val sessionId: String,
        val jobId: String?,
        val status: String?,
        val error: String? = null
    )
    
    /**
     * Send a command with optional image to the API.
     * 
     * @param commandText The voice command text (e.g., "research quantum computing")
     * @param imageBytes Optional JPEG image bytes
     * @param sessionId Session identifier
     */
    suspend fun sendCommand(
        commandText: String,
        imageBytes: ByteArray? = null,
        sessionId: String = "android-67-app"
    ): CommandResponse {
        return withContext(Dispatchers.IO) {
            try {
                val jsonBody = JSONObject().apply {
                    put("command_text", commandText)
                    put("session_id", sessionId)
                    
                    // Add base64 image if provided
                    if (imageBytes != null && imageBytes.isNotEmpty()) {
                        val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
                        put("image_base64", base64Image)
                    }
                }
                
                Log.d(TAG, "Sending command: $commandText, image: ${imageBytes?.size ?: 0} bytes")
                
                val requestBody = jsonBody.toString()
                    .toRequestBody("application/json".toMediaType())
                
                val request = Request.Builder()
                    .url("$baseUrl/v1/command")
                    .post(requestBody)
                    .build()
                
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: ""
                    Log.d(TAG, "Response: ${response.code} - $body")
                    
                    if (response.isSuccessful) {
                        parseCommandResponse(body)
                    } else {
                        CommandResponse(
                            intent = "error",
                            message = "Request failed: ${response.code}",
                            sessionId = sessionId,
                            jobId = null,
                            status = null,
                            error = body
                        )
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Network error", e)
                CommandResponse(
                    intent = "error",
                    message = "Network error: ${e.message}",
                    sessionId = sessionId,
                    jobId = null,
                    status = null,
                    error = e.message
                )
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error", e)
                CommandResponse(
                    intent = "error",
                    message = "Error: ${e.message}",
                    sessionId = sessionId,
                    jobId = null,
                    status = null,
                    error = e.message
                )
            }
        }
    }
    
    private fun parseCommandResponse(json: String): CommandResponse {
        return try {
            val obj = JSONObject(json)
            CommandResponse(
                intent = obj.optString("intent", "unknown"),
                message = obj.optString("message", ""),
                sessionId = obj.optString("session_id", ""),
                jobId = obj.optString("job_id", null),
                status = obj.optString("status", null)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse response", e)
            CommandResponse(
                intent = "error",
                message = "Failed to parse response",
                sessionId = "",
                jobId = null,
                status = null,
                error = json
            )
        }
    }
    
    /**
     * Get job status/details.
     */
    suspend fun getJob(jobId: String): JSONObject? {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl/v1/jobs/$jobId")
                    .get()
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        response.body?.string()?.let { body ->
                            Log.d(TAG, "Job $jobId response: $body")
                            JSONObject(body)
                        }
                    } else {
                        Log.e(TAG, "Job $jobId failed: ${response.code}")
                        null
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get job", e)
                null
            }
        }
    }
    
    /**
     * Health check.
     */
    suspend fun healthCheck(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl/healthz")
                    .get()
                    .build()
                
                client.newCall(request).execute().use { response ->
                    response.isSuccessful
                }
            } catch (e: Exception) {
                false
            }
        }
    }
}
