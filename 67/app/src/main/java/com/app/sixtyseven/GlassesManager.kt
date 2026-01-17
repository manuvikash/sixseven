package com.app.sixtyseven

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import com.meta.wearable.dat.camera.StreamSession
import com.meta.wearable.dat.camera.startStreamSession
import com.meta.wearable.dat.camera.types.PhotoData
import com.meta.wearable.dat.camera.types.StreamConfiguration
import com.meta.wearable.dat.camera.types.StreamSessionState
import com.meta.wearable.dat.camera.types.VideoQuality
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.selectors.AutoDeviceSelector
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import com.meta.wearable.dat.core.types.RegistrationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

/**
 * Manages connection to Meta glasses via WDA SDK.
 */
class GlassesManager(private val application: Application) {
    
    companion object {
        private const val TAG = "GlassesManager"
    }
    
    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Initializing : ConnectionState()
        object WaitingForRegistration : ConnectionState()
        object Registered : ConnectionState()
        data class Connected(val deviceName: String) : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }
    
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState
    
    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing
    
    private val _lastFrame = MutableStateFlow<Bitmap?>(null)
    val lastFrame: StateFlow<Bitmap?> = _lastFrame
    
    private val scope = CoroutineScope(Dispatchers.Main)
    private val deviceSelector = AutoDeviceSelector()
    
    private var streamSession: StreamSession? = null
    private var videoJob: Job? = null
    private var stateJob: Job? = null
    private var registrationJob: Job? = null
    private var deviceJob: Job? = null
    
    private var isInitialized = false
    
    /**
     * Initialize the WDA SDK. Must be called after permissions are granted.
     */
    fun initialize() {
        if (isInitialized) return
        
        _connectionState.value = ConnectionState.Initializing
        
        try {
            Wearables.initialize(application)
            isInitialized = true
            startMonitoring()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Wearables SDK", e)
            _connectionState.value = ConnectionState.Error("SDK init failed: ${e.message}")
        }
    }
    
    private fun startMonitoring() {
        // Monitor registration state
        registrationJob = scope.launch {
            Wearables.registrationState.collect { state ->
                Log.d(TAG, "Registration state: $state")
                when (state) {
                    is RegistrationState.Unavailable -> {
                        _connectionState.value = ConnectionState.WaitingForRegistration
                    }
                    is RegistrationState.Available -> {
                        _connectionState.value = ConnectionState.WaitingForRegistration
                    }
                    is RegistrationState.Registering -> {
                        _connectionState.value = ConnectionState.Initializing
                    }
                    is RegistrationState.Unregistering -> {
                        _connectionState.value = ConnectionState.Initializing
                    }
                    is RegistrationState.Registered -> {
                        _connectionState.value = ConnectionState.Registered
                    }
                }
            }
        }
        
        // Monitor connected devices
        deviceJob = scope.launch {
            deviceSelector.activeDevice(Wearables.devices).collect { device ->
                if (device != null) {
                    _connectionState.value = ConnectionState.Connected(device.toString())
                } else if (_connectionState.value is ConnectionState.Connected) {
                    _connectionState.value = ConnectionState.Registered
                }
            }
        }
    }
    
    /**
     * Start the registration flow (opens Meta AI app).
     */
    fun startRegistration() {
        Wearables.startRegistration(application)
    }
    
    /**
     * Check if camera permission is granted on the glasses.
     */
    suspend fun checkCameraPermission(): PermissionStatus {
        val result = Wearables.checkPermissionStatus(Permission.CAMERA)
        return result.getOrDefault(PermissionStatus.Denied)
    }
    
    /**
     * Start video streaming from glasses.
     */
    fun startStreaming() {
        if (streamSession != null) {
            Log.d(TAG, "Stream already active, checking state...")
            // Check if existing session is still valid
            val currentState = streamSession?.state?.value
            if (currentState == StreamSessionState.STREAMING) {
                Log.d(TAG, "Stream is active and streaming")
                return
            } else {
                Log.d(TAG, "Stream session exists but state is $currentState, restarting...")
                stopStreaming()
            }
        }
        
        Log.d(TAG, "Starting stream session...")
        
        scope.launch {
            try {
                val session = Wearables.startStreamSession(
                    application,
                    deviceSelector,
                    StreamConfiguration(videoQuality = VideoQuality.MEDIUM, 24)
                )
                streamSession = session
                Log.d(TAG, "Stream session created, waiting for streaming state...")
                
                // Monitor stream state
                stateJob = scope.launch {
                    session.state.collect { state ->
                        Log.d(TAG, "Stream state changed: $state")
                        when (state) {
                            StreamSessionState.STREAMING -> {
                                Log.d(TAG, "✓ Stream is now STREAMING")
                            }
                            StreamSessionState.STOPPED -> {
                                Log.d(TAG, "Stream STOPPED")
                            }
                            else -> {
                                Log.d(TAG, "Stream state: $state")
                            }
                        }
                    }
                }
                
                // Collect video frames
                videoJob = scope.launch {
                    Log.d(TAG, "Starting video frame collection...")
                    var frameCount = 0
                    session.videoStream.collect { frame ->
                        try {
                            val buffer = frame.buffer
                            val dataSize = buffer.remaining()
                            val byteArray = ByteArray(dataSize)
                            val originalPosition = buffer.position()
                            buffer.get(byteArray)
                            buffer.position(originalPosition)
                            
                            val bitmap = convertFrameToBitmap(byteArray, frame.width, frame.height)
                            _lastFrame.value = bitmap
                            frameCount++
                            if (frameCount <= 3 || frameCount % 30 == 0) {
                                Log.d(TAG, "Frame #$frameCount: ${frame.width}x${frame.height}")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing video frame", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start streaming: ${e.message}", e)
                _connectionState.value = ConnectionState.Error("Stream failed: ${e.message}")
            }
        }
    }
    
    /**
     * Stop video streaming.
     */
    fun stopStreaming() {
        Log.d(TAG, "Stopping stream...")
        videoJob?.cancel()
        videoJob = null
        stateJob?.cancel()
        stateJob = null
        try {
            streamSession?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing stream session", e)
        }
        streamSession = null
        _isCapturing.value = false
        Log.d(TAG, "Stream stopped")
    }
    
    /**
     * Capture a photo from glasses camera.
     * Returns JPEG bytes.
     */
    suspend fun capturePhoto(): ByteArray? {
        val session = streamSession
        if (session == null) {
            Log.e(TAG, "capturePhoto: No active stream session")
            return null
        }
        
        _isCapturing.value = true
        Log.d(TAG, "capturePhoto: Starting capture...")
        
        return try {
            val result = session.capturePhoto()
            
            val photoData = result.getOrNull()
            if (photoData == null) {
                Log.e(TAG, "capturePhoto: Result was null or failed")
                return null
            }
            
            Log.d(TAG, "capturePhoto: Got photo data type: ${photoData::class.simpleName}")
            when (photoData) {
                is PhotoData.Bitmap -> {
                    Log.d(TAG, "capturePhoto: Converting Bitmap to JPEG")
                    bitmapToJpeg(photoData.bitmap)
                }
                is PhotoData.HEIC -> {
                    Log.d(TAG, "capturePhoto: Converting HEIC to JPEG")
                    val bytes = ByteArray(photoData.data.remaining())
                    photoData.data.get(bytes)
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bitmap != null) {
                        bitmapToJpeg(bitmap)
                    } else {
                        Log.e(TAG, "capturePhoto: Failed to decode HEIC")
                        null
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "capturePhoto exception", e)
            null
        } finally {
            _isCapturing.value = false
        }
    }
    
    /**
     * Capture photo and return as Bitmap for preview.
     */
    suspend fun capturePhotoBitmap(): Bitmap? {
        val session = streamSession ?: return null
        
        _isCapturing.value = true
        
        return try {
            val result = session.capturePhoto()
            result.getOrNull()?.let { photoData ->
                when (photoData) {
                    is PhotoData.Bitmap -> photoData.bitmap
                    is PhotoData.HEIC -> {
                        val bytes = ByteArray(photoData.data.remaining())
                        photoData.data.get(bytes)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Photo capture failed", e)
            null
        } finally {
            _isCapturing.value = false
        }
    }
    
    /**
     * Get the current video frame as JPEG bytes.
     */
    fun getCurrentFrameAsJpeg(): ByteArray? {
        return _lastFrame.value?.let { bitmapToJpeg(it) }
    }
    
    private fun bitmapToJpeg(bitmap: Bitmap, quality: Int = 90): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        return stream.toByteArray()
    }
    
    // Convert I420 to NV21 then to Bitmap
    private fun convertFrameToBitmap(data: ByteArray, width: Int, height: Int): Bitmap {
        val nv21 = convertI420toNV21(data, width, height)
        val image = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val stream = ByteArrayOutputStream()
        image.compressToJpeg(Rect(0, 0, width, height), 80, stream)
        val jpegBytes = stream.toByteArray()
        return BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
    }
    
    // Convert I420 (YYYYYYYY:UUVV) to NV21 (YYYYYYYY:VUVU)
    private fun convertI420toNV21(input: ByteArray, width: Int, height: Int): ByteArray {
        val output = ByteArray(input.size)
        val size = width * height
        val quarter = size / 4
        
        input.copyInto(output, 0, 0, size) // Y plane
        
        for (n in 0 until quarter) {
            output[size + n * 2] = input[size + quarter + n] // V
            output[size + n * 2 + 1] = input[size + n] // U
        }
        return output
    }
    
    fun disconnect() {
        Log.d(TAG, "Disconnecting...")
        stopStreaming()
        registrationJob?.cancel()
        registrationJob = null
        deviceJob?.cancel()
        deviceJob = null
        _connectionState.value = ConnectionState.Disconnected
        _lastFrame.value = null
        Log.d(TAG, "Disconnected")
    }
    
    /**
     * Call this when the app is being destroyed.
     */
    fun cleanup() {
        Log.d(TAG, "Cleanup...")
        disconnect()
    }
}
