package com.app.sixtyseven

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import coil.load
import com.app.sixtyseven.databinding.ActivityMainBinding
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    
    private val glassesManager by lazy { SixtySevenApp.instance.glassesManager }
    private val apiClient by lazy { SixtySevenApp.instance.apiClient }
    private val jobsManager by lazy { SixtySevenApp.instance.jobsManager }
    private val speaker by lazy { SixtySevenApp.instance.speaker }
    
    private lateinit var voiceTrigger: VoiceTrigger
    
    private var isStarted = false
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    
    private val requiredPermissions = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.RECORD_AUDIO)
    } else {
        arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.RECORD_AUDIO)
    }
    
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            log("✓ All permissions granted")
            initializeWearables()
        } else {
            val denied = permissions.filter { !it.value }.keys.map { it.substringAfterLast(".") }
            log("✗ Missing permissions: ${denied.joinToString()}")
            initializeWearables()
        }
    }
    
    private val wearablesPermissionLauncher = registerForActivityResult(
        Wearables.RequestPermissionContract()
    ) { result ->
        val status = result.getOrDefault(PermissionStatus.Denied)
        if (status == PermissionStatus.Granted) {
            log("✓ Glasses camera permission granted")
        } else {
            log("✗ Glasses camera permission denied")
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        voiceTrigger = VoiceTrigger(this)
        
        setupUI()
        observeState()
        
        log("App started")
        checkPermissionsAndInit()
        testApiConnection()
    }
    
    private fun log(message: String) {
        val time = timeFormat.format(Date())
        val logLine = "[$time] $message\n"
        
        runOnUiThread {
            binding.tvLog.append(logLine)
            // Auto-scroll to bottom
            binding.scrollLog.post {
                binding.scrollLog.fullScroll(android.view.View.FOCUS_DOWN)
            }
        }
    }
    
    private fun testApiConnection() {
        lifecycleScope.launch {
            log("Testing API connection...")
            val healthy = apiClient.healthCheck()
            if (healthy) {
                log("✓ API connected")
            } else {
                log("✗ API not reachable")
            }
        }
    }
    
    private fun setupUI() {
        binding.btnConnect.setOnClickListener {
            when (val state = glassesManager.connectionState.value) {
                is GlassesManager.ConnectionState.WaitingForRegistration -> {
                    log("Opening Meta AI app for registration...")
                    glassesManager.startRegistration()
                }
                is GlassesManager.ConnectionState.Connected -> {
                    log("Disconnecting...")
                    stopEverything()
                    glassesManager.disconnect()
                }
                is GlassesManager.ConnectionState.Disconnected,
                is GlassesManager.ConnectionState.Error -> {
                    log("Initializing...")
                    checkPermissionsAndInit()
                }
                else -> {}
            }
        }
        
        binding.btnStart.setOnClickListener {
            if (isStarted) {
                stopEverything()
            } else {
                startListening()
            }
        }
        
        binding.btnClearLog.setOnClickListener {
            binding.tvLog.text = ""
            log("Log cleared")
        }
        
        binding.btnJobs.setOnClickListener {
            showJobsDialog()
        }
        
        // Tap preview to view fullscreen
        binding.cardPreview.setOnClickListener {
            val bitmap = glassesManager.lastFrame.value
            if (bitmap != null) {
                showFullscreenImage(bitmap)
            } else {
                log("No image to display")
            }
        }
    }
    
    private fun showFullscreenImage(bitmap: android.graphics.Bitmap?) {
        if (bitmap == null) {
            log("No image to display")
            return
        }
        
        // Create dialog with custom view
        val imageView = ImageView(this).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(android.graphics.Color.BLACK)
            
            // Copy bitmap to avoid issues
            try {
                val bitmapCopy = bitmap.copy(bitmap.config ?: android.graphics.Bitmap.Config.ARGB_8888, false)
                setImageBitmap(bitmapCopy)
            } catch (e: Exception) {
                setImageBitmap(bitmap)
            }
        }
        
        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.setContentView(imageView)
        dialog.setCancelable(true)
        
        imageView.setOnClickListener { dialog.dismiss() }
        
        dialog.show()
    }
    
    private fun showFullscreenImageUrl(url: String) {
        val imageView = ImageView(this).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(android.graphics.Color.BLACK)
        }
        
        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.setContentView(imageView)
        dialog.setCancelable(true)
        
        // Load with Coil
        imageView.load(url) {
            crossfade(true)
        }
        
        imageView.setOnClickListener { dialog.dismiss() }
        
        dialog.show()
    }
    
    private fun showTextViewer(title: String, content: String) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_text_viewer, null)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvTitle)
        val tvContent = dialogView.findViewById<TextView>(R.id.tvContent)
        val btnClose = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCloseText)
        
        tvTitle.text = title
        tvContent.text = content
        
        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.setContentView(dialogView)
        dialog.setCancelable(true)
        
        btnClose.setOnClickListener { dialog.dismiss() }
        
        dialog.show()
    }
    
    private fun observeState() {
        lifecycleScope.launch {
            glassesManager.connectionState.collectLatest { state ->
                updateUIForState(state)
            }
        }
        
        lifecycleScope.launch {
            glassesManager.isCapturing.collectLatest { capturing ->
                binding.progressCapture.visibility = if (capturing) {
                    android.view.View.VISIBLE
                } else {
                    android.view.View.GONE
                }
            }
        }
        
        lifecycleScope.launch {
            glassesManager.lastFrame.collectLatest { bitmap ->
                bitmap?.let {
                    binding.ivPreview.setImageBitmap(it)
                    binding.tvTapHint.visibility = android.view.View.VISIBLE
                }
            }
        }
        
        lifecycleScope.launch {
            voiceTrigger.lastHeard.collectLatest { text ->
                text?.let {
                    log("🎤 Heard: \"$it\"")
                }
            }
        }
    }
    
    private fun updateUIForState(state: GlassesManager.ConnectionState) {
        when (state) {
            is GlassesManager.ConnectionState.Disconnected -> {
                binding.tvStatus.text = "Disconnected"
                binding.btnConnect.text = "Initialize"
                binding.btnConnect.isEnabled = true
                binding.btnStart.isEnabled = false
            }
            is GlassesManager.ConnectionState.Initializing -> {
                binding.tvStatus.text = "Initializing..."
                binding.btnConnect.isEnabled = false
                log("SDK initializing...")
            }
            is GlassesManager.ConnectionState.WaitingForRegistration -> {
                binding.tvStatus.text = "Tap Connect"
                binding.btnConnect.text = "Connect"
                binding.btnConnect.isEnabled = true
                binding.btnStart.isEnabled = false
                log("Ready to connect glasses")
            }
            is GlassesManager.ConnectionState.Registered -> {
                binding.tvStatus.text = "Waiting for glasses..."
                binding.btnConnect.isEnabled = false
                binding.btnStart.isEnabled = false
                log("Registered, waiting for glasses...")
            }
            is GlassesManager.ConnectionState.Connected -> {
                binding.tvStatus.text = "Connected ✓"
                binding.btnConnect.text = "Disconnect"
                binding.btnConnect.isEnabled = true
                binding.btnStart.isEnabled = true
                log("✓ GLASSES CONNECTED: ${state.deviceName}")
            }
            is GlassesManager.ConnectionState.Error -> {
                binding.tvStatus.text = "Error"
                binding.btnConnect.text = "Retry"
                binding.btnConnect.isEnabled = true
                binding.btnStart.isEnabled = false
                log("✗ Error: ${state.message}")
            }
        }
    }
    
    private fun checkPermissionsAndInit() {
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            initializeWearables()
        } else {
            log("Requesting permissions...")
            permissionLauncher.launch(missing.toTypedArray())
        }
    }
    
    private fun initializeWearables() {
        try {
            glassesManager.initialize()
        } catch (e: Exception) {
            log("✗ Init error: ${e.message}")
        }
    }
    
    private fun startListening() {
        isStarted = true
        binding.btnStart.text = "STOP"
        log("▶ STARTED")
        log("Say: 'hey six seven research [topic]' - voice only")
        log("  or 'hey six seven imagine [description]' - with photo")
        
        lifecycleScope.launch {
            // Check glasses camera permission (this is different from phone camera!)
            log("Checking glasses camera permission...")
            val permStatus = glassesManager.checkCameraPermission()
            log("   Permission status: $permStatus")
            
            if (permStatus != PermissionStatus.Granted) {
                log("📱 Opening Meta AI app for camera permission...")
                wearablesPermissionLauncher.launch(Permission.CAMERA)
                // Wait for user to grant permission
                kotlinx.coroutines.delay(3000)
            } else {
                log("✓ Glasses camera permission OK")
            }
            
            // Start streaming
            log("📹 Starting camera stream...")
            glassesManager.startStreaming()
            
            // Wait for first frame with longer timeout (SDK needs time to initialize)
            log("⏳ Waiting for camera to initialize...")
            var waitTime = 0
            val maxWaitMs = 8000 // 8 seconds total wait
            val checkInterval = 250L
            
            while (waitTime < maxWaitMs && glassesManager.lastFrame.value == null) {
                kotlinx.coroutines.delay(checkInterval)
                waitTime += checkInterval.toInt()
                
                // Log progress every 2 seconds
                if (waitTime % 2000 == 0) {
                    log("   Still waiting... (${waitTime/1000}s)")
                }
            }
            
            if (glassesManager.lastFrame.value != null) {
                log("✓ Camera stream active (took ${waitTime/1000.0}s)")
            } else {
                log("⚠ No frames after ${maxWaitMs/1000}s")
                log("   Try: Restart the app or check Meta AI permissions")
            }
        }
        
        // Pass log callback to voice trigger
        voiceTrigger.setLogCallback { msg -> log(msg) }
        
        voiceTrigger.startListening { command ->
            runOnUiThread {
                handleVoiceCommand(command)
            }
        }
    }
    
    private fun handleVoiceCommand(command: String) {
        log("🎯 TRIGGER DETECTED!")
        log("📝 Command: \"$command\"")
        
        lifecycleScope.launch {
            binding.progressCapture.visibility = android.view.View.VISIBLE
            
            // Determine intent
            val isResearch = command.contains("research", ignoreCase = true)
            val isImagine = command.contains("imagine", ignoreCase = true)
            
            val intent = when {
                isResearch -> "research"
                isImagine -> "imagine"
                else -> "unknown"
            }
            log("🔍 Intent: $intent")
            
            if (isResearch) {
                // RESEARCH: Voice only, no photo needed
                log("📤 Sending research command (no photo)...")
                speaker.speak("Starting research")
                
                val response = apiClient.sendCommand(
                    commandText = command,
                    imageBytes = null
                )
                
                log("📥 Response: ${response.intent}")
                log("   ${response.message}")
                if (response.jobId != null) {
                    log("   Job: ${response.jobId}")
                    // Save job for tracking
                    jobsManager.addJob(
                        jobId = response.jobId,
                        command = command,
                        intent = response.intent,
                        status = response.status ?: "queued",
                        message = response.message
                    )
                }
                
                Toast.makeText(this@MainActivity, response.message, Toast.LENGTH_LONG).show()
                
            } else if (isImagine) {
                // IMAGINE: Need photo
                log("📸 Capturing photo for imagine...")
                
                // Make sure stream is running
                if (glassesManager.lastFrame.value == null) {
                    log("⏳ Waiting for camera stream...")
                    var waitCount = 0
                    while (glassesManager.lastFrame.value == null && waitCount < 20) {
                        kotlinx.coroutines.delay(250)
                        waitCount++
                    }
                }
                
                // Try capture, fallback to current frame
                var imageBytes = glassesManager.capturePhoto()
                
                if (imageBytes == null) {
                    log("⚠ capturePhoto() returned null, trying current frame...")
                    imageBytes = glassesManager.getCurrentFrameAsJpeg()
                }
                
                if (imageBytes != null && imageBytes.isNotEmpty()) {
                    log("✓ Photo ready (${imageBytes.size / 1024} KB)")
                    showCapturedImage(imageBytes)
                    
                    log("📤 Sending imagine command + photo...")
                    speaker.speak("Creating image")
                    val response = apiClient.sendCommand(
                        commandText = command,
                        imageBytes = imageBytes
                    )
                    
                    log("📥 Response: ${response.intent}")
                    log("   ${response.message}")
                    if (response.jobId != null) {
                        log("   Job: ${response.jobId}")
                        // Save job for tracking
                        jobsManager.addJob(
                            jobId = response.jobId,
                            command = command,
                            intent = response.intent,
                            status = response.status ?: "queued",
                            message = response.message
                        )
                    }
                    
                    Toast.makeText(this@MainActivity, response.message, Toast.LENGTH_LONG).show()
                } else {
                    log("✗ No photo available - is camera streaming?")
                    log("   lastFrame: ${glassesManager.lastFrame.value != null}")
                    Toast.makeText(this@MainActivity, "No photo captured - check camera", Toast.LENGTH_SHORT).show()
                }
            } else {
                log("⚠ Unknown command. Use 'research [topic]' or 'imagine [description]'")
                Toast.makeText(this@MainActivity, "Say 'research' or 'imagine'", Toast.LENGTH_SHORT).show()
            }
            
            binding.progressCapture.visibility = android.view.View.GONE
            log("---")
        }
    }
    
    private fun showCapturedImage(imageBytes: ByteArray) {
        try {
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            binding.ivPreview.setImageBitmap(bitmap)
        } catch (e: Exception) {
            log("✗ Failed to display image: ${e.message}")
        }
    }
    
    private fun stopEverything() {
        isStarted = false
        binding.btnStart.text = "START"
        voiceTrigger.stopListening()
        glassesManager.stopStreaming()
        log("⏹ STOPPED")
    }
    
    private fun showJobsDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_jobs, null)
        val llJobsList = dialogView.findViewById<LinearLayout>(R.id.llJobsList)
        val tvEmpty = dialogView.findViewById<TextView>(R.id.tvJobsEmpty)
        val btnRefresh = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnRefreshJobs)
        val btnClose = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCloseJobs)
        
        val dialog = AlertDialog.Builder(this, R.style.Theme_SixtySeven_Dialog)
            .setView(dialogView)
            .create()
        
        btnClose.setOnClickListener { dialog.dismiss() }
        
        fun populateJobs() {
            llJobsList.removeAllViews()
            val jobs = jobsManager.jobs.value
            
            if (jobs.isEmpty()) {
                tvEmpty.visibility = android.view.View.VISIBLE
            } else {
                tvEmpty.visibility = android.view.View.GONE
                jobs.forEach { job ->
                    val itemView = LayoutInflater.from(this).inflate(R.layout.item_job, llJobsList, false)
                    itemView.findViewById<TextView>(R.id.tvJobStatus).text = job.statusEmoji
                    itemView.findViewById<TextView>(R.id.tvJobIntent).text = job.intent
                    itemView.findViewById<TextView>(R.id.tvJobTime).text = job.timeAgo
                    itemView.findViewById<TextView>(R.id.tvJobCommand).text = job.command
                    
                    val tvMessage = itemView.findViewById<TextView>(R.id.tvJobMessage)
                    if (job.message.isNotEmpty()) {
                        tvMessage.text = job.message
                        tvMessage.visibility = android.view.View.VISIBLE
                    }
                    
                    val tvResult = itemView.findViewById<TextView>(R.id.tvJobResult)
                    val llAudioControls = itemView.findViewById<LinearLayout>(R.id.llAudioControls)
                    val btnListen = itemView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnListen)
                    val btnStopAudio = itemView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnStopAudio)
                    
                    if (!job.result.isNullOrEmpty()) {
                        // Show truncated result
                        val truncated = if (job.result.length > 100) job.result.take(100) + "..." else job.result
                        tvResult.text = truncated
                        tvResult.visibility = android.view.View.VISIBLE
                        
                        // Tap to view full result (for research)
                        if (job.imageUrl.isNullOrEmpty()) {
                            tvResult.setOnClickListener {
                                showTextViewer("${job.intent.uppercase()} Result", job.result)
                            }
                            tvResult.setTextColor(resources.getColor(android.R.color.holo_blue_light, null))
                            
                            // Show listen/stop buttons for text results
                            llAudioControls.visibility = android.view.View.VISIBLE
                            btnListen.setOnClickListener {
                                speaker.speak(job.result)
                            }
                            btnStopAudio.setOnClickListener {
                                speaker.stop()
                            }
                        }
                    }
                    
                    val cardImage = itemView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardJobImage)
                    val ivImage = itemView.findViewById<ImageView>(R.id.ivJobImage)
                    if (!job.imageUrl.isNullOrEmpty()) {
                        cardImage.visibility = android.view.View.VISIBLE
                        ivImage.load(job.imageUrl) {
                            crossfade(true)
                            placeholder(android.R.color.darker_gray)
                            error(android.R.color.holo_red_light)
                        }
                        // Tap to view fullscreen
                        cardImage.setOnClickListener {
                            showFullscreenImageUrl(job.imageUrl)
                        }
                    }
                    
                    llJobsList.addView(itemView)
                }
            }
        }
        
        btnRefresh.setOnClickListener {
            lifecycleScope.launch {
                btnRefresh.isEnabled = false
                btnRefresh.text = "Refreshing..."
                
                val jobs = jobsManager.jobs.value
                
                for (job in jobs) {
                    try {
                        val response = apiClient.getJob(job.jobId)
                        if (response != null) {
                            val status = response.optString("status", job.status)
                            
                            // Parse result based on API schema
                            var resultText: String? = null
                            var imageUrl: String? = null
                            val resultObj = response.optJSONObject("result")
                            if (resultObj != null) {
                                // Research result: structured_result.answer or markdown_result
                                val structuredResult = resultObj.optJSONObject("structured_result")
                                if (structuredResult != null) {
                                    resultText = structuredResult.optString("answer", null)
                                    // Also get bullets if available
                                    val bullets = structuredResult.optJSONArray("bullets")
                                    if (bullets != null && bullets.length() > 0) {
                                        val bulletList = (0 until bullets.length()).map { "• ${bullets.getString(it)}" }
                                        resultText = (resultText ?: "") + "\n" + bulletList.joinToString("\n")
                                    }
                                }
                                // Fallback to markdown_result
                                if (resultText.isNullOrEmpty()) {
                                    resultText = resultObj.optString("markdown_result", null)
                                }
                                // Creative result: generated_urls - get first image
                                val urls = resultObj.optJSONArray("generated_urls")
                                if (urls != null && urls.length() > 0) {
                                    imageUrl = urls.getString(0)
                                    resultText = "Generated ${urls.length()} image(s)"
                                }
                            }
                            
                            // Check for error
                            val errorObj = response.optJSONObject("error")
                            if (errorObj != null) {
                                resultText = "Error: ${errorObj.optString("message", "Unknown error")}"
                            }
                            
                            jobsManager.updateJob(job.jobId, status, resultText, imageUrl)
                            log("📋 Job ${job.jobId.take(8)}...: $status")
                            
                            // Announce completion
                            if (status == "succeeded" && job.status != "succeeded") {
                                if (!imageUrl.isNullOrEmpty()) {
                                    speaker.speak("Image ready")
                                } else if (!resultText.isNullOrEmpty()) {
                                    speaker.speak("Research complete")
                                }
                            }
                            
                            if (!resultText.isNullOrEmpty()) {
                                log("   Result: ${resultText.take(100)}...")
                            }
                        }
                    } catch (e: Exception) {
                        log("✗ Failed to refresh job ${job.jobId.take(8)}...")
                    }
                }
                
                populateJobs()
                btnRefresh.isEnabled = true
                btnRefresh.text = "🔄 Refresh All"
            }
        }
        
        populateJobs()
        dialog.show()
    }
    
    override fun onStop() {
        super.onStop()
        if (isStarted) {
            log("App backgrounded, stopping...")
            stopEverything()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopEverything()
        glassesManager.disconnect()
    }
}
