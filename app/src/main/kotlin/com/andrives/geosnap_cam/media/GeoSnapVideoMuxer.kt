package com.andrives.geosnap_cam.media

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.media.*
import android.util.Log
import androidx.camera.core.ImageProxy
import com.andrives.geosnap_cam.NativeEngine
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * GeoSnapVideoMuxer — Real-time video encoder with GPS watermark burned in.
 * Replaces the old double-buffer architecture with a clean, synchronous pipeline
 * using MediaCodec (H.264) + AudioRecord (PCM) + MediaMuxer (MP4) directly.
 */
class GeoSnapVideoMuxer(
    private val context: Context,
    private val width: Int = 1080,
    private val height: Int = 1920
) {
    companion object {
        private const val TAG = "GeoSnapVideoMuxer"
        private const val VIDEO_MIME = MediaFormat.MIMETYPE_VIDEO_AVC
        private const val AUDIO_MIME = MediaFormat.MIMETYPE_AUDIO_AAC
        private const val VIDEO_BIT_RATE = 5_000_000
        private const val FRAME_RATE = 30
        private const val I_FRAME_INTERVAL = 1
        private const val AUDIO_SAMPLE_RATE = 44100
        private const val AUDIO_BIT_RATE = 128_000
        private const val DRAIN_TIMEOUT_US = 10_000L
    }

    private var videoEncoder: MediaCodec? = null
    private var audioEncoder: MediaCodec? = null
    private var mediaMuxer: MediaMuxer? = null
    private var audioRecord: AudioRecord? = null

    private var videoTrackIndex = -1
    private var audioTrackIndex = -1

    private val muxerLock = Any()
    private val isRecordingStatus = AtomicBoolean(false)

    @Volatile private var muxerStarted = false
    @Volatile private var videoFormatSet = false
    @Volatile private var audioFormatSet = false
    @Volatile private var startTimeNs = 0L

    private var audioThread: Thread? = null
    private var outputFilePath = ""
    private var lockedRotation = -1
    
    private var nv12Buffer: ByteArray? = null

    val isRecording: Boolean get() = isRecordingStatus.get()

    @SuppressLint("MissingPermission")
    fun startRecording(outputFile: File) {
        if (isRecordingStatus.get()) return
        outputFilePath = outputFile.absolutePath
        
        pendingBuffers.clear()
        videoEncoder = null // Will be initialized on first frame

        // Audio encoder
        val audioFmt = MediaFormat.createAudioFormat(AUDIO_MIME, AUDIO_SAMPLE_RATE, 1).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BIT_RATE)
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }
        audioEncoder = MediaCodec.createEncoderByType(AUDIO_MIME).also { c ->
            c.configure(audioFmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            c.start()
        }

        // Muxer
        mediaMuxer = MediaMuxer(outputFilePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        // AudioRecord
        val minBuf = AudioRecord.getMinBufferSize(
            AUDIO_SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                AUDIO_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf * 4
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AudioRecord. Recording video only.", e)
            audioRecord = null
        }

        videoTrackIndex = -1
        audioTrackIndex = -1
        muxerStarted = false
        videoFormatSet = false
        audioFormatSet = false
        startTimeNs = System.nanoTime()
        lockedRotation = -1

        isRecordingStatus.set(true)
        
        if (audioRecord?.state == AudioRecord.STATE_INITIALIZED) {
            audioRecord?.startRecording()
            audioThread = Thread { audioLoop(minBuf) }.also { it.start() }
        } else {
            // Handle audio failure gracefully (e.g. mic in use)
            audioFormatSet = true // pretend we don't need audio
            audioTrackIndex = -1
        }
        
        Log.d(TAG, "Recording started -> $outputFilePath")
    }

    @Volatile private var isEncoderPlanar = false

    private fun initVideoEncoder(rot: Int, frameW: Int, frameH: Int) {
        val outW = if (rot == 90 || rot == 270) frameH else frameW
        val outH = if (rot == 90 || rot == 270) frameW else frameH
        val videoFmt = MediaFormat.createVideoFormat(VIDEO_MIME, outW, outH).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BIT_RATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
        }
        videoEncoder = MediaCodec.createEncoderByType(VIDEO_MIME).also { c ->
            c.configure(videoFmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            try {
                val actualFormat = c.inputFormat.getInteger(MediaFormat.KEY_COLOR_FORMAT)
                isEncoderPlanar = (actualFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar)
                Log.d(TAG, "Encoder format: $actualFormat (isPlanar: $isEncoderPlanar)")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to get actual color format, assuming NV12")
            }
            c.start()
        }
    }

    fun stopRecording(): String? {
        if (!isRecordingStatus.get()) return null
        isRecordingStatus.set(false)

        try {
            val enc = videoEncoder
            if (enc != null) {
                val idx = enc.dequeueInputBuffer(DRAIN_TIMEOUT_US)
                if (idx >= 0) {
                    enc.queueInputBuffer(idx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "EOS video error", e)
        }

        try { audioRecord?.stop() } catch (e: Exception) { }
        audioThread?.join(3000)
        audioThread = null

        drainEncoder(videoEncoder, isVideo = true, endOfStream = true)

        synchronized(muxerLock) {
            if (muxerStarted) {
                try { mediaMuxer?.stop() } catch (e: Exception) { Log.e(TAG, "muxer stop", e) }
            }
        }

        releaseAll()
        val file = File(outputFilePath)
        return if (file.exists() && file.length() > 0) outputFilePath else null
    }

    fun processFrame(
        proxy: ImageProxy,
        overlayBitmap: Bitmap?,
        isFrontCamera: Boolean
    ) {
        if (!isRecordingStatus.get()) { proxy.close(); return }
        try {
            if (lockedRotation == -1) {
                lockedRotation = proxy.imageInfo.rotationDegrees
            }
            val rot = lockedRotation
            
            val frameW = proxy.width
            val frameH = proxy.height
            
            if (videoEncoder == null) {
                initVideoEncoder(rot, frameW, frameH)
            }
            
            if (nv12Buffer == null || nv12Buffer!!.size != frameW * frameH * 3 / 2) {
                nv12Buffer = ByteArray(frameW * frameH * 3 / 2)
            }
            
            val image = proxy.image
            if (image == null || image.planes.size < 3) {
                Log.w(TAG, "Skipping frame: ImageAnalysis must output YUV_420_888 with 3 planes")
                return
            }
            val y = image.planes[0]
            val u = image.planes[1]
            val v = image.planes[2]
            
            val enc = videoEncoder ?: return
            val idx = enc.dequeueInputBuffer(DRAIN_TIMEOUT_US)
            if (idx < 0) {
                // Drop frame if encoder is busy
                return
            }

            // Determine if the hardware prefers Planar (I420) or Semi-Planar (NV12)
            try {
                val inputImage = enc.getInputImage(idx)
                if (inputImage != null && inputImage.planes.size >= 3) {
                    isEncoderPlanar = (inputImage.planes[1].pixelStride == 1)
                }
            } catch (e: Exception) { }

            NativeEngine.processVideoFrame(
                y = y.buffer,
                u = u.buffer,
                v = v.buffer,
                yRowStride = y.rowStride,
                uRowStride = u.rowStride,
                vRowStride = v.rowStride,
                pixelStride = u.pixelStride,
                overlayBitmap = overlayBitmap,
                width = frameW,
                height = frameH,
                rotation = rot,
                targetNv12 = nv12Buffer!!,
                isFrontCamera = isFrontCamera,
                isPlanar = isEncoderPlanar
            )
            
            val buf = enc.getInputBuffer(idx)
            if (buf != null) {
                buf.clear()
                val chunk = Math.min(nv12Buffer!!.size, buf.capacity())
                buf.put(nv12Buffer!!, 0, chunk)
                val pts = (System.nanoTime() - startTimeNs) / 1000L
                enc.queueInputBuffer(idx, 0, chunk, pts, 0)
            }
            drainEncoder(enc, isVideo = true, endOfStream = false)
        } catch (e: Exception) {
            Log.e(TAG, "processFrame error", e)
        } finally {
            proxy.close()
        }
    }

    private fun audioLoop(bufSize: Int) {
        val enc = audioEncoder ?: return
        val record = audioRecord ?: return
        val pcm = ByteArray(bufSize)
        val bufInfo = MediaCodec.BufferInfo()
        var audioTimeUs = -1L
        while (isRecordingStatus.get()) {
            val read = record.read(pcm, 0, bufSize)
            val actualRead = if (read > 0) {
                read
            } else {
                val bytesFor10Ms = 882 // 44100Hz * 16bit * Mono * 10ms
                java.util.Arrays.fill(pcm, 0, bytesFor10Ms, 0.toByte())
                Thread.sleep(10)
                bytesFor10Ms
            }
            
            val idx = enc.dequeueInputBuffer(DRAIN_TIMEOUT_US)
            if (idx >= 0) {
                val buf = enc.getInputBuffer(idx) ?: continue
                buf.clear()
                val chunk = Math.min(actualRead, buf.capacity())
                buf.put(pcm, 0, chunk)
                
                if (audioTimeUs == -1L) {
                    audioTimeUs = (System.nanoTime() - startTimeNs) / 1000L
                }
                val pts = audioTimeUs
                val durationUs = (chunk * 1000000L) / (44100L * 2L)
                audioTimeUs += durationUs
                
                enc.queueInputBuffer(idx, 0, chunk, pts, 0)
            }
            drainEncoder(enc, isVideo = false, endOfStream = false)
        }
        record.stop()
        val idx = enc.dequeueInputBuffer(DRAIN_TIMEOUT_US)
        if (idx >= 0) enc.queueInputBuffer(idx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        drainEncoder(enc, isVideo = false, endOfStream = true, bufInfo = bufInfo)
    }

    private fun drainEncoder(
        encoder: MediaCodec?,
        isVideo: Boolean,
        endOfStream: Boolean,
        bufInfo: MediaCodec.BufferInfo = MediaCodec.BufferInfo()
    ) {
        val enc = encoder ?: return
        val timeoutUs = if (endOfStream) DRAIN_TIMEOUT_US else 0L
        while (true) {
            val idx = enc.dequeueOutputBuffer(bufInfo, timeoutUs)
            when {
                idx == MediaCodec.INFO_TRY_AGAIN_LATER -> break
                idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    synchronized(muxerLock) {
                        val muxer = mediaMuxer ?: return
                        if (isVideo && !videoFormatSet) {
                            videoTrackIndex = muxer.addTrack(enc.outputFormat)
                            videoFormatSet = true
                        } else if (!isVideo && !audioFormatSet) {
                            audioTrackIndex = muxer.addTrack(enc.outputFormat)
                            audioFormatSet = true
                        }
                        if (videoFormatSet && audioFormatSet && !muxerStarted) {
                            muxer.start()
                            muxerStarted = true
                            
                            pendingBuffers.sortBy { it.info.presentationTimeUs }
                            for (pb in pendingBuffers) {
                                val t = if (pb.isVideo) videoTrackIndex else audioTrackIndex
                                if (t >= 0) {
                                    try {
                                        val bb = java.nio.ByteBuffer.wrap(pb.buffer)
                                        muxer.writeSampleData(t, bb, pb.info)
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Muxer flush error", e)
                                    }
                                }
                            }
                            pendingBuffers.clear()
                        }
                    }
                }
                idx >= 0 -> {
                    val out = enc.getOutputBuffer(idx)
                    if (out == null) {
                        enc.releaseOutputBuffer(idx, false)
                        continue
                    }
                    if (bufInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        bufInfo.size = 0
                    }
                    if (bufInfo.size > 0) {
                        synchronized(muxerLock) {
                            if (muxerStarted) {
                                val track = if (isVideo) videoTrackIndex else audioTrackIndex
                                try {
                                    if (track >= 0) mediaMuxer?.writeSampleData(track, out, bufInfo)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Muxer write error", e)
                                }
                            } else {
                                val array = ByteArray(bufInfo.size)
                                out.position(bufInfo.offset)
                                out.limit(bufInfo.offset + bufInfo.size)
                                out.get(array)
                                val infoCopy = MediaCodec.BufferInfo().apply {
                                    set(0, bufInfo.size, bufInfo.presentationTimeUs, bufInfo.flags)
                                }
                                pendingBuffers.add(PendingBuffer(isVideo, array, infoCopy))
                            }
                        }
                    }
                    enc.releaseOutputBuffer(idx, false)
                    if (bufInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
        }
    }

    private fun releaseAll() {
        try { videoEncoder?.stop(); videoEncoder?.release() } catch (_: Exception) {}
        try { audioEncoder?.stop(); audioEncoder?.release() } catch (_: Exception) {}
        try { audioRecord?.release() } catch (_: Exception) {}
        try { mediaMuxer?.release() } catch (_: Exception) {}
        videoEncoder = null; audioEncoder = null; audioRecord = null; mediaMuxer = null
        pendingBuffers.clear()
    }
    
    private class PendingBuffer(
        val isVideo: Boolean,
        val buffer: ByteArray,
        val info: MediaCodec.BufferInfo
    )
    private val pendingBuffers = mutableListOf<PendingBuffer>()
}
