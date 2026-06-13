package com.andrives.geosnap_cam.ui.screen.camera

import android.Manifest
import android.content.Context
import android.view.WindowManager
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.andrives.geosnap_cam.ui.screen.camera.component.*
import com.google.accompanist.permissions.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen(
    onNavigateToPreview: (path: String, isVideo: Boolean, sessionPaths: List<String>, sessionIsVideo: List<Boolean>) -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: CameraViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Keep screen on
    DisposableEffect(Unit) {
        val window = (context as? android.app.Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    // Camera permission
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)
    LaunchedEffect(Unit) {
        if (!cameraPermission.status.isGranted) cameraPermission.launchPermissionRequest()
    }

    // Location updates
    LaunchedEffect(Unit) {
        viewModel.startLocationUpdates()
    }
    DisposableEffect(Unit) {
        onDispose { viewModel.stopLocationUpdates() }
    }

    // Reload settings when returning from settings screen
    val lifecycle = lifecycleOwner.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.reloadSettings()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    // CameraX state
    var camera by remember { mutableStateOf<Camera?>(null) }
    var imageAnalysis by remember { mutableStateOf<ImageAnalysis?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // ── Camera Preview ──────────────────────────────────────────────────
        if (cameraPermission.status.isGranted) {
            CameraPreviewView(
                lensFacing = uiState.lensFacing,
                flashMode = uiState.flashMode,
                onCameraReady = { cam, ic, ia ->
                    camera = cam
                    imageCapture = ic
                    imageAnalysis = ia
                    
                    ia.setAnalyzer(ContextCompat.getMainExecutor(context)) { proxy ->
                        viewModel.processFrame(proxy)
                    }
                },
                onTapToFocus = { x, y, meteringPoint ->
                    camera?.cameraControl?.startFocusAndMetering(
                        FocusMeteringAction.Builder(meteringPoint).build()
                    )
                    viewModel.onTapToFocus(x, y)
                },
                onZoom = { ratio ->
                    camera?.cameraControl?.setLinearZoom(ratio.coerceIn(0f, 1f))
                    viewModel.onZoomChanged(ratio)
                },
                onAnyInteraction = { viewModel.resetControlsTimer() },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // ── Focus ring overlay ──────────────────────────────────────────────
        FocusRingOverlay(
            visible = uiState.showFocusRing,
            x = uiState.focusX,
            y = uiState.focusY,
            modifier = Modifier.fillMaxSize(),
        )

        // ── Top bar ─────────────────────────────────────────────────────────
        CameraTopBar(
            location = uiState.location,
            gpsReady = uiState.gpsReady,
            flashMode = uiState.flashMode,
            isFlashMenuOpen = uiState.isFlashMenuOpen,
            isRecording = uiState.isRecording,
            showControls = uiState.showSecondaryControls,
            onFlashTap = viewModel::toggleFlashMenu,
            onFlashSelect = viewModel::setFlashMode,
            onSettingsTap = {
                viewModel.closeFlashMenu()
                onNavigateToSettings()
            },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding(),
        )

        // ── Bottom controls ──────────────────────────────────────────────────
        CameraBottomControls(
            mode = uiState.mode,
            processingState = uiState.processingState,
            isRecording = uiState.isRecording,
            recordingSeconds = uiState.recordingSeconds,
            lastCapturedPath = uiState.lastCapturedPath,
            lastCapturedIsVideo = uiState.lastCapturedIsVideo,
            showControls = uiState.showSecondaryControls,
            iconRotationDegrees = uiState.iconRotationDegrees,
            onModeTap = viewModel::setMode,
            onShutterTap = {
                viewModel.resetControlsTimer()
                when {
                    uiState.mode == CameraMode.PHOTO -> capturePhoto(
                        context, imageCapture, viewModel
                    )
                    uiState.isRecording -> viewModel.stopRecording()
                    else -> viewModel.startRecording()
                }
            },
            onSwitchCamera = viewModel::switchCamera,
            onGalleryTap = {
                val path = uiState.lastCapturedPath ?: return@CameraBottomControls
                onNavigateToPreview(
                    path,
                    uiState.lastCapturedIsVideo,
                    uiState.sessionPaths,
                    uiState.sessionIsVideo,
                )
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
        )
    }
}

// ── CameraX Preview ───────────────────────────────────────────────────────────

@Composable
private fun CameraPreviewView(
    lensFacing: Int,
    flashMode: FlashMode,
    onCameraReady: (Camera, ImageCapture, ImageAnalysis) -> Unit,
    onTapToFocus: (Float, Float, MeteringPoint) -> Unit,
    onZoom: (Float) -> Unit,
    onAnyInteraction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }

    LaunchedEffect(lensFacing) {
        val cameraProvider = ProcessCameraProvider.getInstance(context).get()

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        val imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setFlashMode(
                when (flashMode) {
                    FlashMode.ON -> ImageCapture.FLASH_MODE_ON
                    FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
                    FlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
                }
            )
            .build()

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .setTargetResolution(android.util.Size(1080, 1920))
            .build()

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        try {
            cameraProvider.unbindAll()
            val camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageCapture,
                imageAnalysis,
            )
            onCameraReady(camera, imageCapture, imageAnalysis)
        } catch (e: Exception) {
            android.util.Log.e("CameraScreen", "Camera bind failed: ${e.message}", e)
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoom, _ ->
                    onZoom(zoom)
                    onAnyInteraction()
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val meteringPoint = previewView.meteringPointFactory.createPoint(
                        offset.x, offset.y
                    )
                    onTapToFocus(offset.x, offset.y, meteringPoint)
                    onAnyInteraction()
                }
            },
    )
}

// ── Capture helpers ───────────────────────────────────────────────────────────

private fun capturePhoto(
    context: Context,
    imageCapture: ImageCapture?,
    viewModel: CameraViewModel,
) {
    val ic = imageCapture ?: return
    val outputFile = createTempFile(context, ".jpg")
    val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()

    ic.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                viewModel.onPhotoCaptured(outputFile.absolutePath)
            }
            override fun onError(exc: ImageCaptureException) {
                android.util.Log.e("CameraScreen", "Photo capture failed: ${exc.message}", exc)
            }
        }
    )
}

// Recording is now handled fully by CameraViewModel via VideoRecorderController.

private fun createTempFile(context: Context, extension: String): File {
    val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    return File(context.cacheDir, "GeoSnap_temp_$ts$extension")
}
