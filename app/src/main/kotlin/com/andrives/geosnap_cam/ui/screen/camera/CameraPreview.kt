package com.andrives.geosnap_cam.ui.screen.camera

import android.util.Log
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.MeteringPoint
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
internal fun CameraPreview(
    lensFacing: Int,
    flashMode: FlashMode,
    exposureRatio: Float,
    onExposureRatioChange: (Float) -> Unit,
    onCameraReady: (Camera, ImageCapture, ImageAnalysis) -> Unit,
    onTapToFocus: (Float, Float, MeteringPoint) -> Unit,
    onLongPressToFocus: (Float, Float, MeteringPoint) -> Unit,
    onResetFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    var camera by remember { mutableStateOf<Camera?>(null) }

    LaunchedEffect(lensFacing) {
        val cameraProvider = ProcessCameraProvider.getInstance(context).get()
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        val imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setFlashMode(flashMode.toImageCaptureMode())
            .build()
        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .setTargetResolution(Size(1080, 1920))
            .build()
        val selector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        try {
            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                selector,
                preview,
                imageCapture,
                imageAnalysis,
            ).also { boundCamera ->
                resetExposure(boundCamera, onExposureRatioChange)
                onCameraReady(boundCamera, imageCapture, imageAnalysis)
            }
        } catch (error: Exception) {
            Log.e("CameraPreview", "Camera bind failed", error)
        }
    }

    LaunchedEffect(exposureRatio, camera) {
        camera?.setExposureRatio(exposureRatio)
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier
            .pointerInput(camera) {
                detectTransformGestures { _, _, zoomMultiplier, _ ->
                    val activeCamera = camera ?: return@detectTransformGestures
                    val zoom = activeCamera.cameraInfo.zoomState.value
                        ?: return@detectTransformGestures
                    val ratio = (zoom.zoomRatio * zoomMultiplier)
                        .coerceIn(zoom.minZoomRatio, zoom.maxZoomRatio)
                    activeCamera.cameraControl.setZoomRatio(ratio)
                }
            }
            .pointerInput(camera) {
                detectTapGestures(
                    onDoubleTap = {
                        val activeCamera = camera ?: return@detectTapGestures
                        val zoom = activeCamera.cameraInfo.zoomState.value
                            ?: return@detectTapGestures
                        activeCamera.cameraControl.setZoomRatio(zoom.minZoomRatio)
                        resetExposure(activeCamera, onExposureRatioChange)
                        onResetFocus()
                    },
                    onLongPress = { offset ->
                        onLongPressToFocus(
                            offset.x,
                            offset.y,
                            previewView.meteringPointFactory.createPoint(offset.x, offset.y),
                        )
                    },
                    onTap = { offset ->
                        onTapToFocus(
                            offset.x,
                            offset.y,
                            previewView.meteringPointFactory.createPoint(offset.x, offset.y),
                        )
                    },
                )
            },
    )
}

private fun FlashMode.toImageCaptureMode(): Int = when (this) {
    FlashMode.ON -> ImageCapture.FLASH_MODE_ON
    FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
    FlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
}

private fun resetExposure(camera: Camera, onRatioChange: (Float) -> Unit) {
    val range = camera.cameraInfo.exposureState.exposureCompensationRange
    if (range.upper <= range.lower) return
    camera.cameraControl.setExposureCompensationIndex(0)
    onRatioChange((0 - range.lower).toFloat() / (range.upper - range.lower).toFloat())
}

private fun Camera.setExposureRatio(ratio: Float) {
    val range = cameraInfo.exposureState.exposureCompensationRange
    if (range.upper <= range.lower) return
    val index = range.lower + ((range.upper - range.lower) * ratio.coerceIn(0f, 1f)).toInt()
    cameraControl.setExposureCompensationIndex(index)
}
