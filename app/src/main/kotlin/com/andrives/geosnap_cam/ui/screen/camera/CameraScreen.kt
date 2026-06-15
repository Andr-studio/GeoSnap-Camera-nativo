package com.andrives.geosnap_cam.ui.screen.camera

import android.Manifest
import android.content.Context
import android.view.WindowManager
import androidx.camera.core.Camera
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.andrives.geosnap_cam.ui.screen.camera.component.CameraBottomControls
import com.andrives.geosnap_cam.ui.screen.camera.component.CameraTopBar
import com.andrives.geosnap_cam.ui.screen.camera.component.FocusExposureOverlay
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen(
    onNavigateToPreview: (
        path: String,
        isVideo: Boolean,
        sessionPaths: List<String>,
        sessionIsVideo: List<Boolean>,
    ) -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: CameraViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)
    var camera by remember { mutableStateOf<Camera?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var exposureRatio by remember { mutableFloatStateOf(0.5f) }

    DisposableEffect(Unit) {
        val window = (context as? android.app.Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    LaunchedEffect(Unit) {
        if (!cameraPermission.status.isGranted) cameraPermission.launchPermissionRequest()
        viewModel.startLocationUpdates()
    }
    DisposableEffect(Unit) {
        onDispose { viewModel.stopLocationUpdates() }
    }
    DisposableEffect(lifecycleOwner.lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.reloadSettings()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(uiState.flashMode, camera, uiState.mode, uiState.isRecording) {
        val activeCamera = camera ?: return@LaunchedEffect
        val torchEnabled = (uiState.mode == CameraMode.VIDEO || uiState.isRecording) &&
            uiState.flashMode == FlashMode.ON
        activeCamera.cameraControl.enableTorch(torchEnabled)
        imageCapture?.flashMode = when (uiState.flashMode) {
            FlashMode.ON -> ImageCapture.FLASH_MODE_ON
            FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
            FlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (cameraPermission.status.isGranted) {
            CameraPreview(
                lensFacing = uiState.lensFacing,
                flashMode = uiState.flashMode,
                exposureRatio = exposureRatio,
                onExposureRatioChange = { exposureRatio = it },
                onCameraReady = { activeCamera, capture, analysis ->
                    camera = activeCamera
                    imageCapture = capture
                    analysis.setAnalyzer(ContextCompat.getMainExecutor(context)) { proxy ->
                        viewModel.processFrame(proxy)
                    }
                },
                onTapToFocus = { x, y, point ->
                    camera?.cameraControl?.startFocusAndMetering(
                        FocusMeteringAction.Builder(point).build(),
                    )
                    viewModel.onTapToFocus(x, y)
                },
                onLongPressToFocus = { x, y, point ->
                    camera?.cameraControl?.startFocusAndMetering(
                        FocusMeteringAction.Builder(point)
                            .disableAutoCancel()
                            .build(),
                    )
                    viewModel.onLongPressToFocus(x, y)
                },
                onResetFocus = {
                    camera?.cameraControl?.cancelFocusAndMetering()
                    viewModel.resetFocus()
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        FocusExposureOverlay(
            visible = uiState.showFocusRing,
            isLocked = uiState.isFocusLocked,
            isActive = uiState.isFocusIndicatorActive,
            x = uiState.focusX,
            y = uiState.focusY,
            exposureRatio = exposureRatio,
            onExposureChange = { exposureRatio = it },
            onInteraction = viewModel::onFocusIndicatorInteraction,
            modifier = Modifier.fillMaxSize(),
        )

        CameraTopBar(
            location = uiState.location,
            gpsReady = uiState.gpsReady,
            flashMode = uiState.flashMode,
            mode = uiState.mode,
            isFlashMenuOpen = uiState.isFlashMenuOpen,
            isRecording = uiState.isRecording,
            iconRotationDegrees = uiState.iconRotationDegrees,
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

        CameraBottomControls(
            mode = uiState.mode,
            processingState = uiState.processingState,
            isRecording = uiState.isRecording,
            recordingSeconds = uiState.recordingSeconds,
            lastCapturedPath = uiState.lastCapturedPath,
            lastCapturedIsVideo = uiState.lastCapturedIsVideo,
            iconRotationDegrees = uiState.iconRotationDegrees,
            onModeTap = viewModel::setMode,
            onShutterTap = {
                when {
                    uiState.mode == CameraMode.PHOTO -> capturePhoto(
                        context,
                        imageCapture,
                        viewModel,
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

private fun capturePhoto(
    context: Context,
    imageCapture: ImageCapture?,
    viewModel: CameraViewModel,
) {
    val capture = imageCapture ?: return
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val outputFile = File(context.cacheDir, "GeoSnap_temp_$timestamp.jpg")
    val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()

    capture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                viewModel.onPhotoCaptured(outputFile.absolutePath)
            }

            override fun onError(error: ImageCaptureException) {
                android.util.Log.e("CameraScreen", "Photo capture failed", error)
            }
        },
    )
}
