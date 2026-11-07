package com.example.samcam

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Rect
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.util.Range
import android.view.Surface
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.nio.ByteBuffer

class ProbeRes(val fps: Double, val msg: String)

class TeleInfo(val wideId: String, val teleId: String, val factor: Float)

fun detectTele(ctx: Context): TeleInfo? {
    try {
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val list = ArrayList<Pair<String, Float>>()
        for (id in cm.cameraIdList) {
            val c = cm.getCameraCharacteristics(id)
            if (c.get(CameraCharacteristics.LENS_FACING) != CameraCharacteristics.LENS_FACING_BACK) continue
            val fl = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull() ?: continue
            val sz = c.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) ?: continue
            if (sz.width <= 0f) continue
            list.add(Pair(id, fl / sz.width))
        }
        if (list.size < 2) return null
        val wide = list.minByOrNull { it.second } ?: return null
        val tele = list.maxByOrNull { it.second } ?: return null
        val f = tele.second / wide.second
        if (f < 1.5f || f > 3.5f) return null
        return TeleInfo(wide.first, tele.first, f)
    } catch (e: Exception) {
        return null
    }
}

fun remuxSlow(src: File, dst: ParcelFileDescriptor, factor: Int): Boolean {
    val ex = MediaExtractor()
    var mux: MediaMuxer? = null
    var started = false
    try {
        ex.setDataSource(src.absolutePath)
        var vt = -1
        for (i in 0 until ex.trackCount) {
            val mime = ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("video/")) {
                vt = i
                break
            }
        }
        if (vt < 0) return false
        ex.selectTrack(vt)
        val fmt = ex.getTrackFormat(vt)
        val m = MediaMuxer(dst.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        mux = m
        if (fmt.containsKey(MediaFormat.KEY_ROTATION)) m.setOrientationHint(fmt.getInteger(MediaFormat.KEY_ROTATION))
        val track = m.addTrack(fmt)
        m.start()
        started = true
        val buf = ByteBuffer.allocate(4 * 1024 * 1024)
        val info = MediaCodec.BufferInfo()
        while (true) {
            buf.clear()
            val n = ex.readSampleData(buf, 0)
            if (n < 0) break
            val flags = if ((ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
            info.set(0, n, ex.sampleTime * factor, flags)
            m.writeSampleData(track, buf, info)
            ex.advance()
        }
        return true
    } catch (e: Exception) {
        return false
    } finally {
        try { if (started) mux?.stop() } catch (e: Exception) {}
        try { mux?.release() } catch (e: Exception) {}
        try { ex.release() } catch (e: Exception) {}
    }
}

class TimelapseEncoder(val w: Int, val h: Int, private val pfd: ParcelFileDescriptor) {
    private val dst = Rect(0, 0, w, h)
    private val info = MediaCodec.BufferInfo()
    private val codec: MediaCodec
    private val inputSurface: Surface
    private val muxer: MediaMuxer
    private var track = -1
    private var started = false
    private var written = 0

    init {
        val fmt = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h)
        fmt.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        fmt.setInteger(MediaFormat.KEY_BIT_RATE, 10_000_000)
        fmt.setInteger(MediaFormat.KEY_FRAME_RATE, 30)
        fmt.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        inputSurface = codec.createInputSurface()
        codec.start()
        muxer = MediaMuxer(pfd.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    }

    fun addFrame(bmp: Bitmap) {
        val canvas = inputSurface.lockCanvas(null)
        try {
            canvas.drawBitmap(bmp, null, dst, null)
        } finally {
            inputSurface.unlockCanvasAndPost(canvas)
        }
        drain(false)
    }

    private fun drain(end: Boolean) {
        val deadline = System.currentTimeMillis() + 4000L
        while (true) {
            val idx = try {
                codec.dequeueOutputBuffer(info, if (end) 10_000L else 0L)
            } catch (e: Exception) {
                return
            }
            if (idx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!end) return
                if (System.currentTimeMillis() > deadline) return
            } else if (idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (!started) {
                    track = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    started = true
                }
            } else if (idx >= 0) {
                val buf = codec.getOutputBuffer(idx)
                if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) info.size = 0
                if (info.size > 0 && started && buf != null) {
                    buf.position(info.offset)
                    buf.limit(info.offset + info.size)
                    info.presentationTimeUs = written * 33_333L
                    muxer.writeSampleData(track, buf, info)
                    written++
                }
                codec.releaseOutputBuffer(idx, false)
                if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return
            } else {
                if (!end || System.currentTimeMillis() > deadline) return
            }
        }
    }

    fun finish() {
        try { codec.signalEndOfInputStream() } catch (e: Exception) {}
        try { drain(true) } catch (e: Exception) {}
        try { codec.stop() } catch (e: Exception) {}
        try { codec.release() } catch (e: Exception) {}
        try { inputSurface.release() } catch (e: Exception) {}
        try { if (started) muxer.stop() } catch (e: Exception) {}
        try { muxer.release() } catch (e: Exception) {}
        try { pfd.close() } catch (e: Exception) {}
    }
}

@SuppressLint("MissingPermission")
suspend fun probeOnce(ctx: Context, camId: String, fps: Int): ProbeRes {
    val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    val map = try {
        cm.getCameraCharacteristics(camId).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
    } catch (e: Exception) {
        null
    }
    if (map == null) return ProbeRes(-1.0, "no stream map")
    val sizes = map.getOutputSizes(ImageFormat.YUV_420_888)
    if (sizes == null || sizes.isEmpty()) return ProbeRes(-1.0, "no YUV sizes")
    val size = sizes.firstOrNull { it.width == 1920 && it.height == 1080 }
        ?: sizes.filter { it.width <= 1920 }.maxByOrNull { it.width * it.height }
        ?: sizes[0]
    val thread = HandlerThread("fpsprobe")
    thread.start()
    val handler = Handler(thread.looper)
    val stamps = ArrayList<Long>()
    val reader = ImageReader.newInstance(size.width, size.height, ImageFormat.YUV_420_888, 5)
    reader.setOnImageAvailableListener({ r ->
        try {
            val img = r.acquireNextImage()
            if (img != null) {
                synchronized(stamps) { stamps.add(img.timestamp) }
                img.close()
            }
        } catch (e: Exception) {
        }
    }, handler)
    var device: CameraDevice? = null
    var session: CameraCaptureSession? = null
    var res = ProbeRes(-1.0, "unknown")
    try {
        val opened = CompletableDeferred<CameraDevice?>()
        cm.openCamera(camId, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) { opened.complete(camera) }
            override fun onDisconnected(camera: CameraDevice) { camera.close(); opened.complete(null) }
            override fun onError(camera: CameraDevice, error: Int) { camera.close(); opened.complete(null) }
        }, handler)
        val dev = withTimeoutOrNull(4000L) { opened.await() }
        if (dev == null) {
            res = ProbeRes(-1.0, "camera did not open")
        } else {
            device = dev
            val ready = CompletableDeferred<CameraCaptureSession?>()
            dev.createCaptureSession(listOf(reader.surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(s: CameraCaptureSession) { ready.complete(s) }
                override fun onConfigureFailed(s: CameraCaptureSession) { ready.complete(null) }
            }, handler)
            val sess = withTimeoutOrNull(4000L) { ready.await() }
            if (sess == null) {
                res = ProbeRes(-1.0, "session failed at ${size.width}x${size.height}")
            } else {
                session = sess
                val req = dev.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
                req.addTarget(reader.surface)
                req.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(fps, fps))
                sess.setRepeatingRequest(req.build(), null, handler)
                delay(2600)
                val list = synchronized(stamps) { ArrayList(stamps) }
                if (list.size < 20) {
                    res = ProbeRes(-1.0, "only ${list.size} frames arrived")
                } else {
                    val s = list.subList(8, list.size)
                    val span = (s[s.size - 1] - s[0]) / 1e9
                    val f = (s.size - 1) / span
                    res = ProbeRes(f, "%.1f fps at %dx%d".format(f, size.width, size.height))
                }
            }
        }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        res = ProbeRes(-1.0, "error: ${e.message}")
    } finally {
        try { session?.close() } catch (e: Exception) {}
        try { device?.close() } catch (e: Exception) {}
        try { reader.close() } catch (e: Exception) {}
        try { thread.quitSafely() } catch (e: Exception) {}
    }
    return res
}

fun cameraReport(ctx: Context): String {
    val sb = StringBuilder("Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}\n\n")
    val capNames = mapOf(1 to "MANUAL_SENSOR", 2 to "MANUAL_POST", 3 to "RAW", 6 to "BURST", 9 to "HIGH_SPEED", 11 to "LOGICAL_MULTI")
    try {
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        for (id in cm.cameraIdList) {
            val c = cm.getCameraCharacteristics(id)
            val facing = when (c.get(CameraCharacteristics.LENS_FACING)) { 0 -> "front"; 1 -> "back"; 2 -> "external"; else -> "?" }
            val level = when (c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)) {
                0 -> "LIMITED"; 1 -> "FULL"; 2 -> "LEGACY"; 3 -> "LEVEL_3"; else -> "?"
            }
            sb.append("Camera $id ($facing, $level)\n")
            c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.let { caps ->
                sb.append("  Caps: ${caps.joinToString { capNames[it] ?: "#$it" }}\n")
            }
            c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.let { sb.append("  Focal: ${it.joinToString()}\n") }
            c.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)?.let { sb.append("  Sensor mm: $it\n") }
            c.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)?.let { sb.append("  AE fps: ${it.joinToString()}\n") }
            c.get(CameraCharacteristics.CONTROL_AVAILABLE_SCENE_MODES)?.let { sb.append("  Scene modes: ${it.joinToString()}\n") }
            c.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)?.let { sb.append("  ISO: $it\n") }
            c.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)?.let { sb.append("  Exposure ns: $it\n") }
            c.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)?.let { sb.append("  Min focus dist: $it\n") }
            val map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            map?.getOutputSizes(MediaRecorder::class.java)?.take(6)?.let { sb.append("  Video sizes: ${it.joinToString()}\n") }
            val hs = map?.highSpeedVideoSizes
            if (hs == null || hs.isEmpty()) {
                sb.append("  High-speed: none\n")
            } else {
                for (sz in hs.take(4)) {
                    sb.append("  High-speed $sz: ${map.getHighSpeedVideoFpsRangesFor(sz)?.joinToString()}\n")
                }
            }
            sb.append("\n")
        }
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                sb.append("Concurrent camera sets: ${cm.concurrentCameraIds}\n")
            } catch (e: Exception) {
                sb.append("Concurrent camera sets: unavailable\n")
            }
        }
    } catch (e: Exception) {
        sb.append("Error: ${e.message}")
    }
    return sb.toString()
}
