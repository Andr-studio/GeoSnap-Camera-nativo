package com.andrives.geosnap_cam.ui.screen.camera

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.OrientationEventListener
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.MeteringPoint
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andrives.geosnap_cam.data.model.LocationData
import com.andrives.geosnap_cam.data.model.WatermarkConfig
import com.andrives.geosnap_cam.data.repository.WatermarkSettingsRepository
import com.andrives.geosnap_cam.media.MediaSaver
import com.andrives.geosnap_cam.media.WatermarkService
import com.andrives.geosnap_cam.media.VideoRecorderController
import com.andrives.geosnap_cam.service.AnalyticsService
import com.andrives.geosnap_cam.service.LocationService
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

enum class CameraMode { PHOTO, VIDEO }
enum class FlashMode { OFF, ON, AUTO }
enum class ProcessingState { IDLE, RECORDING, PROCESSING, DONE }

data class CameraUiState(
    val mode: CameraMode = CameraMode.PHOTO,
    val flashMode: FlashMode = FlashMode.OFF,
    val lensFacing: Int = CameraSelector.LENS_FACING_BACK,
    val isRecording: Boolean = false,
    val recordingSeconds: Int = 0,
    val processingState: ProcessingState = ProcessingState.IDLE,
    val location: LocationData? = null,
    val watermarkConfig: WatermarkConfig = WatermarkConfig(),
    val lastCapturedPath: String? = null,
    val lastCapturedIsVideo: Boolean = false,
    val sessionPaths: List<String> = emptyList(),
    val sessionIsVideo: List<Boolean> = emptyList(),
    val zoomRatio: Float = 1f,
    val isFlashMenuOpen: Boolean = false,
    val showFocusRing: Boolean = false,
    val isFocusLocked: Boolean = false,
    val isFocusIndicatorActive: Boolean = false,
    val focusX: Float = 0.5f,
    val focusY: Float = 0.5f,
    val showSecondaryControls: Boolean = true,
    val gpsReady: Boolean = false,
    val isSwitchingCamera: Boolean = false,
    val iconRotationDegrees: Float = 0f,
    val targetRotation: Int = android.view.Surface.ROTATION_0,
)

@HiltViewModel
class CameraViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val locationService: LocationService,
    private val watermarkService: WatermarkService,
    private val settingsRepo: WatermarkSettingsRepository,
    private val mediaSaver: MediaSaver,
    private val analyticsService: AnalyticsService,
    private val videoRecorderController: VideoRecorderController,
) : ViewModel() {

    companion object {
        private const val TAG = "CameraViewModel"
        /** Minimum meters moved before re-fetching address + weather */
        private const val LOCATION_DISTANCE_THRESHOLD_M = 150.0
        /** Minimum minutes before re-fetching weather */
        private const val WEATHER_REFRESH_MINUTES = 5L
    }

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    // ── Location tracking ────────────────────────────────────────────────────
    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    private var locationCallback: LocationCallback? = null
    private var lastFetchedLat: Double? = null
    private var lastFetchedLon: Double? = null
    private var lastWeatherFetchAt: Long = 0L
    private var isFetchingDetails = false

    // ── Recording clock ──────────────────────────────────────────────────────
    private var clockJob: Job? = null

    // ── Orientation Tracker ──────────────────────────────────────────────────
    private var orientationListener: OrientationEventListener? = null
    private var lastSnappedOrientation = 0
    private var accumulatedRotation = 0f

    // ── Focus ring ───────────────────────────────────────────────────────────
    private var focusJob: Job? = null

    // ── Wakelock (keep screen on) ─────────────────────────────────────────────
    // Done via FLAG_KEEP_SCREEN_ON in the Activity/Compose

    init {
        loadSettings()
        loadRecentSession()
        setupOrientationListener()
    }

    // ── Settings & Session ───────────────────────────────────────────────────

    private fun loadSettings() {
        viewModelScope.launch {
            val config = settingsRepo.getConfig()
            _uiState.update { it.copy(watermarkConfig = config) }
        }
    }

    fun reloadSettings() = loadSettings()

    fun onCameraBackgrounded() {
        closeFlashMenu()
        resetFocus()
        if (_uiState.value.isRecording) {
            stopRecording()
        }
    }

    private fun loadRecentSession() {
        viewModelScope.launch {
            try {
                val dir = getGeoSnapDir() ?: return@launch
                val files = dir.listFiles()
                    ?.filter { it.isFile && (it.extension == "jpg" || it.extension == "mp4") }
                    ?.sortedBy { it.lastModified() }
                    ?.takeLast(20)
                    ?: return@launch

                val paths = files.map { it.absolutePath }
                val isVideos = files.map { it.extension == "mp4" }

                _uiState.update { state ->
                    state.copy(
                        sessionPaths = paths,
                        sessionIsVideo = isVideos,
                        lastCapturedPath = paths.lastOrNull(),
                        lastCapturedIsVideo = isVideos.lastOrNull() ?: false,
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadRecentSession failed: ${e.message}")
            }
        }
    }

    // ── Location ─────────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        stopLocationUpdates()
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000L)
            .setMinUpdateDistanceMeters(10f)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    handleNewPosition(loc.latitude, loc.longitude)
                }
            }
        }
        fusedClient.requestLocationUpdates(request, locationCallback!!, context.mainLooper)
    }

    fun stopLocationUpdates() {
        locationCallback?.let { fusedClient.removeLocationUpdates(it) }
        locationCallback = null
    }

    private fun handleNewPosition(lat: Double, lon: Double) {
        val current = _uiState.value
        val prevLoc = current.location

        // Calculate distance from last fetched position
        val distance = if (lastFetchedLat != null && lastFetchedLon != null) {
            distanceBetween(lastFetchedLat!!, lastFetchedLon!!, lat, lon)
        } else {
            Double.MAX_VALUE
        }

        val isFirstLock = prevLoc == null
        val movedSignificantly = distance > LOCATION_DISTANCE_THRESHOLD_M
        val timeElapsed = (System.currentTimeMillis() - lastWeatherFetchAt) > WEATHER_REFRESH_MINUTES * 60_000L
        val shouldFetchDetails = isFirstLock || movedSignificantly || timeElapsed

        if (shouldFetchDetails) {
            if (isFetchingDetails) return
            isFetchingDetails = true
            viewModelScope.launch {
                try {
                    val locData = locationService.getCurrentLocation() ?: return@launch
                    lastFetchedLat = lat
                    lastFetchedLon = lon
                    lastWeatherFetchAt = System.currentTimeMillis()

                    val config = _uiState.value.watermarkConfig
                    watermarkService.prewarmWatermarkAssets(locData, config)

                    val wasReady = _uiState.value.gpsReady
                    _uiState.update { it.copy(location = locData, gpsReady = true) }

                    if (!wasReady) vibrateGpsReady()
                } catch (e: Exception) {
                    Log.e(TAG, "fetchDetails failed: ${e.message}")
                } finally {
                    isFetchingDetails = false
                }
            }
        } else {
            // Update coordinates only — keep address, weather, timezone from previous fetch
            prevLoc?.let { prev ->
                _uiState.update { state ->
                    state.copy(
                        location = prev.copy(latitude = lat, longitude = lon)
                    )
                }
            }
        }
    }

    // ── Mode & Flash ─────────────────────────────────────────────────────────

    fun setMode(mode: CameraMode) {
        if (_uiState.value.isRecording) return
        _uiState.update { it.copy(mode = mode) }
    }

    fun toggleFlashMenu() {
        _uiState.update { it.copy(isFlashMenuOpen = !it.isFlashMenuOpen) }
    }

    fun closeFlashMenu() {
        _uiState.update { it.copy(isFlashMenuOpen = false) }
    }

    fun setFlashMode(mode: FlashMode) {
        _uiState.update { it.copy(flashMode = mode, isFlashMenuOpen = false) }
    }

    // ── Zoom ──────────────────────────────────────────────────────────────────

    fun onZoomChanged(ratio: Float) {
        _uiState.update { it.copy(zoomRatio = ratio) }
    }

    // ── Focus ring ────────────────────────────────────────────────────────────

    fun onTapToFocus(x: Float, y: Float) {
        _uiState.update {
            it.copy(
                showFocusRing = true,
                isFocusLocked = false,
                isFocusIndicatorActive = true,
                focusX = x,
                focusY = y,
            )
        }
        scheduleFocusIndicatorIdle()
    }

    fun onLongPressToFocus(x: Float, y: Float) {
        _uiState.update {
            it.copy(
                showFocusRing = true,
                isFocusLocked = true,
                isFocusIndicatorActive = true,
                focusX = x,
                focusY = y,
            )
        }
        scheduleFocusIndicatorIdle()
    }

    fun onFocusIndicatorInteraction() {
        if (!_uiState.value.showFocusRing) return
        _uiState.update { it.copy(isFocusIndicatorActive = true) }
        scheduleFocusIndicatorIdle()
    }

    private fun scheduleFocusIndicatorIdle() {
        focusJob?.cancel()
        focusJob = viewModelScope.launch {
            delay(1_200L)
            _uiState.update { state ->
                if (state.isFocusLocked) {
                    state.copy(isFocusIndicatorActive = false)
                } else {
                    state.copy(showFocusRing = false, isFocusIndicatorActive = false)
                }
            }
        }
    }

    fun resetFocus() {
        focusJob?.cancel()
        _uiState.update {
            it.copy(
                showFocusRing = false,
                isFocusLocked = false,
                isFocusIndicatorActive = false,
            )
        }
    }

    // ── Camera switch ─────────────────────────────────────────────────────────

    fun switchCamera() {
        if (_uiState.value.isSwitchingCamera || _uiState.value.isRecording) return
        _uiState.update {
            it.copy(
                isSwitchingCamera = true,
                isFlashMenuOpen = false,
                lensFacing = if (it.lensFacing == CameraSelector.LENS_FACING_BACK)
                    CameraSelector.LENS_FACING_FRONT
                else CameraSelector.LENS_FACING_BACK,
            )
        }
        viewModelScope.launch {
            delay(400)
            _uiState.update { it.copy(isSwitchingCamera = false) }
        }
    }

    // ── Photo capture ─────────────────────────────────────────────────────────

    fun onPhotoCapturedInMem(imageBytes: ByteArray, rotationDegrees: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(processingState = ProcessingState.PROCESSING) }
            try {
                val location = _uiState.value.location
                if (location == null) {
                    Log.w(TAG, "No GPS location — saving photo without watermark")
                    addToSession(saveRawPhotoFallback(imageBytes), false)
                    _uiState.update { it.copy(processingState = ProcessingState.DONE) }
                    return@launch
                }

                val outputPath = generateOutputPath(isVideo = false)
                val result = watermarkService.applyLiveWatermarkToPhoto(imageBytes, rotationDegrees, location, outputPath)

                if (result is WatermarkService.WatermarkResult.Success) {
                    val savedUri = mediaSaver.saveToGallery(result.outputPath, false)
                    val savedPath = result.outputPath
                    addToSession(savedPath, false)
                    analyticsService.logPhotoCapture()
                    vibrateCapture()
                } else {
                    addToSession(saveRawPhotoFallback(imageBytes), false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "onPhotoCapturedInMem failed: ${e.message}", e)
                addToSession(saveRawPhotoFallback(imageBytes), false)
            } finally {
                _uiState.update { it.copy(processingState = ProcessingState.IDLE) }
            }
        }
    }

    // ── Video recording ───────────────────────────────────────────────────────

    fun startRecording() {
        val outputPath = generateOutputPath(isVideo = true)
        val started = videoRecorderController.startRecording(outputPath)
        if (started) {
            _uiState.update { it.copy(isRecording = true, recordingSeconds = 0, processingState = ProcessingState.RECORDING) }
            analyticsService.logVideoRecordingStart()
            startClock()
        }
    }

    fun stopRecording() {
        stopClock()
        val duration = _uiState.value.recordingSeconds.toLong() * 1000L
        analyticsService.logVideoRecordingStop(duration)
        
        _uiState.update { it.copy(isRecording = false, processingState = ProcessingState.PROCESSING) }
        
        viewModelScope.launch {
            val outputPath = videoRecorderController.stopRecording()
            if (outputPath != null) {
                mediaSaver.saveToGallery(outputPath, true)
                addToSession(outputPath, true)
            }
            _uiState.update { it.copy(processingState = ProcessingState.IDLE) }
        }
    }

    fun processFrame(proxy: androidx.camera.core.ImageProxy) {
        if (!_uiState.value.isRecording) {
            proxy.close()
            return
        }
        val isFrontCamera = _uiState.value.lensFacing == CameraSelector.LENS_FACING_FRONT
        val location = _uiState.value.location
        val config = _uiState.value.watermarkConfig

        viewModelScope.launch {
            val rot = proxy.imageInfo.rotationDegrees
            val outW = if (rot == 90 || rot == 270) proxy.height else proxy.width
            val outH = if (rot == 90 || rot == 270) proxy.width else proxy.height

            val overlayBitmap = watermarkService.getLiveWatermarkBitmap(
                location, config, outW, outH
            )
            videoRecorderController.processFrame(proxy, overlayBitmap, isFrontCamera)
        }
    }

    // ── Orientation Tracking ──────────────────────────────────────────────────

    private fun setupOrientationListener() {
        orientationListener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return

                // Snap to nearest 90 degrees
                val snapped = when (orientation) {
                    in 45..134 -> 90   // Device right
                    in 135..224 -> 180 // Device upside down
                    in 225..314 -> 270 // Device left
                    else -> 0          // Device upright
                }

                if (snapped != lastSnappedOrientation) {
                    var diff = snapped - lastSnappedOrientation
                    // Find shortest path to prevent wrapping jumps
                    if (diff == 270) diff = -90
                    else if (diff == -270) diff = 90

                    // We subtract because the UI must rotate opposite to the device
                    accumulatedRotation -= diff
                    lastSnappedOrientation = snapped

                    val surfaceRot = when (snapped) {
                        90 -> android.view.Surface.ROTATION_270
                        180 -> android.view.Surface.ROTATION_180
                        270 -> android.view.Surface.ROTATION_90
                        else -> android.view.Surface.ROTATION_0
                    }

                    _uiState.update {
                        it.copy(
                            iconRotationDegrees = accumulatedRotation,
                            targetRotation = surfaceRot
                        )
                    }
                }
            }
        }
        orientationListener?.enable()
    }

    // ── Clock ─────────────────────────────────────────────────────────────────

    private fun startClock() {
        clockJob?.cancel()
        clockJob = viewModelScope.launch {
            while (isActive && _uiState.value.isRecording) {
                delay(1_000)
                _uiState.update { it.copy(recordingSeconds = it.recordingSeconds + 1) }
            }
        }
    }

    private fun stopClock() {
        clockJob?.cancel()
        clockJob = null
    }

    // ── Session helpers ───────────────────────────────────────────────────────

    private fun addToSession(path: String, isVideo: Boolean) {
        _uiState.update { state ->
            val paths = state.sessionPaths.toMutableList().also { it.add(path) }
            val videos = state.sessionIsVideo.toMutableList().also { it.add(isVideo) }
            state.copy(
                sessionPaths = paths,
                sessionIsVideo = videos,
                lastCapturedPath = path,
                lastCapturedIsVideo = isVideo,
            )
        }
    }

    // ── File paths ────────────────────────────────────────────────────────────

    private suspend fun saveRawPhotoFallback(imageBytes: ByteArray): String {
        val outputPath = generateOutputPath(isVideo = false)
        return try {
            File(outputPath).writeBytes(imageBytes)
            mediaSaver.saveToGallery(outputPath, false)
            outputPath
        } catch (e: Exception) {
            Log.e(TAG, "saveRawPhotoFallback failed: ${e.message}", e)
            ""
        }
    }

    private fun generateOutputPath(isVideo: Boolean): String {
        val ext = if (isVideo) ".mp4" else ".jpg"
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val dir = getGeoSnapDir() ?: context.cacheDir
        return File(dir, "GeoSnap_$ts$ext").absolutePath
    }

    private fun getGeoSnapDir(): File? {
        val dir = File(context.getExternalFilesDir(null), "GeoSnap Cam")
        return if (dir.mkdirs() || dir.exists()) dir else null
    }

    // ── Haptics ───────────────────────────────────────────────────────────────

    private fun vibrateCapture() {
        vibrate(50L)
    }

    private fun vibrateGpsReady() {
        vibrate(80L)
    }

    @Suppress("DEPRECATION")
    private fun vibrate(ms: Long) {
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                val vm = context.getSystemService(VibratorManager::class.java)
                vm?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            } else if (Build.VERSION.SDK_INT >= 26) {
                val v = context.getSystemService(Vibrator::class.java)
                v?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                v?.vibrate(ms)
            }
        } catch (_: Exception) { }
    }

    // ── Distance calculation ──────────────────────────────────────────────────

    private fun distanceBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    }

    override fun onCleared() {
        super.onCleared()
        stopLocationUpdates()
        stopClock()
        orientationListener?.disable()
    }
}
