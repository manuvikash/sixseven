package com.app.sixtyseven

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Job(
    val jobId: String,
    val command: String,
    val intent: String,
    val status: String,
    val message: String,
    val createdAt: Long = System.currentTimeMillis(),
    val result: String? = null,
    val imageUrl: String? = null
) {
    val timeAgo: String
        get() {
            val diff = System.currentTimeMillis() - createdAt
            return when {
                diff < 60_000 -> "just now"
                diff < 3600_000 -> "${diff / 60_000}m ago"
                else -> "${diff / 3600_000}h ago"
            }
        }
    
    val statusEmoji: String
        get() = when (status) {
            "queued" -> "⏳"
            "running" -> "🔄"
            "succeeded", "completed", "success" -> "✅"
            "failed", "error" -> "❌"
            "cancelled" -> "🚫"
            else -> "❓"
        }
}

class JobsManager {
    private val _jobs = MutableStateFlow<List<Job>>(emptyList())
    val jobs: StateFlow<List<Job>> = _jobs
    
    fun addJob(jobId: String, command: String, intent: String, status: String, message: String) {
        val job = Job(
            jobId = jobId,
            command = command,
            intent = intent,
            status = status,
            message = message
        )
        _jobs.value = listOf(job) + _jobs.value.take(49) // Keep last 50
    }
    
    fun updateJob(jobId: String, status: String, result: String? = null, imageUrl: String? = null) {
        _jobs.value = _jobs.value.map { job ->
            if (job.jobId == jobId) {
                job.copy(status = status, result = result, imageUrl = imageUrl)
            } else {
                job
            }
        }
    }
    
    fun getJob(jobId: String): Job? {
        return _jobs.value.find { it.jobId == jobId }
    }
    
    fun clearAll() {
        _jobs.value = emptyList()
    }
}
