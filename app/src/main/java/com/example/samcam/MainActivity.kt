@file:OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)

package com.example.samcam

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as GCanvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.media.MediaActionSound
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.net.Uri
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.Settings
import android.util.Range
import android.util.Size
import android.view.KeyEvent
import android.view.Surface
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileDescriptorOutputOptions
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.SegmentationMask
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.Segmenter
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class Mode(val label: String) {
    PRO("Pro"), PANO("Panorama"), SLOMO("Slow motion"), VIDEO("Video"), PHOTO("Photo"),
    PORTRAIT("Portrait"), NIGHT("Night"), DOC("Document"), TIMELAPSE("Time-lapse")
}

val stillModes = setOf(Mode.PHOTO, Mode.PRO, Mode.PORTRAIT, Mode.NIGHT, Mode.DOC)

object Bus {
    var tick by mutableIntStateOf(0)
    var zoomTick by mutableIntStateOf(0)
    var zoomUp = true
    var volMode = 0
}

class QrResult(
    val text: String, val kind: Int, val label: String,
    val ssid: String?, val pass: String?, val wifiType: Int, val hidden: Boolean
)

class Attempt(val still: Boolean, val analysis: Boolean)
class ProbeRes(val fps: Double, val msg: String)
class JpegData(val bytes: ByteArray, val rot: Int)

val YELLOW = Color(0xFFFFC107)
val sizeNames = listOf("Max", "8M", "3M")
val sizeLong = listOf(0, 3264, 2048)
val jpegNames = listOf("Max (100)", "High (95)", "Normal (85)")
val jpegVals = listOf(100, 95, 85)
val vqNames = listOf("4K", "1080p", "720p", "480p")
val vqVals = listOf(Quality.UHD, Quality.FHD, Quality.HD, Quality.SD)
val fpsVals = listOf(24, 30, 60)
val tlVals = listOf(1, 2, 5, 10)
val timerVals = listOf(0, 2, 5, 10)
val brNames = listOf("Auto", "20 Mbps", "35 Mbps", "50 Mbps")
val brVals = listOf(0, 20_000_000, 35_000_000, 50_000_000)
val volNames = listOf("Shutter", "Zoom", "Off")
val slowNames = listOf("2x", "4x", "8x")
val slowVals = listOf(2, 4, 8)
val blurNames = listOf("Low", "Medium", "High")
val blurDiv = listOf(6, 10, 16)
val prioNames = listOf("Quality", "Balanced", "Speed")
val engineNames = listOf("Auto (fast)", "TextureView (compatible)")
val wbNames = listOf("AUTO", "DAY", "CLOUD", "TUNG", "FLUO")
val wbVals = listOf(
    CaptureRequest.CONTROL_AWB_MODE_AUTO, CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT,
    CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT, CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT,
    CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT
)

fun shutLabel(ns: Long): String =
    if (ns <= 0L) "-" else if (ns >= 100_000_000L) "%.1fs".format(ns / 1e9) else "1/${(1e9 / ns).toInt()}"

fun fmtTime(s: Long): String {
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}

fun fmtZoom(z: Float): String =
    if (abs(z - z.roundToInt()) < 0.05f) "${z.roundToInt()}x" else "%.1fx".format(z)

fun fmtPreset(z: Float): String =
    if (abs(z - z.roundToInt()) < 0.01f) "${z.roundToInt()}" else "%.1f".format(z)

fun stampNow(): String = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())

fun copyText(ctx: Context, text: String) {
    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
        .setPrimaryClip(ClipData.newPlainText("camera", text))
}

fun makeQr(b: Barcode, raw: String): QrResult {
    val w = if (b.valueType == Barcode.TYPE_WIFI) b.wifi else null
    val ssid = w?.ssid
    if (!ssid.isNullOrEmpty()) {
        return QrResult(raw, 2, "Join: $ssid", ssid, w?.password, w?.encryptionType ?: 2, raw.contains("H:true", true))
    }
    val url: String? = b.url?.url ?: (if (raw.startsWith("http", true)) raw else null)
    if (url != null) {
        val shown = url.removePrefix("https://").removePrefix("http://").removePrefix("www.")
        return QrResult(url, 1, "Open: $shown", null, null, 0, false)
    }
    return QrResult(raw, 0, "Copy: " + raw.replace("\n", " "), null, null, 0, false)
}

fun connectWifi(ctx: Context, ssid: String, pass: String?, type: Int, hidden: Boolean): String {
    try {
        if (type == 3) {
            if (!pass.isNullOrEmpty()) copyText(ctx, pass)
            ctx.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return "WEP can't be added by apps. Password copied; pick \"$ssid\" in Wi-Fi settings."
        }
        val b = WifiNetworkSuggestion.Builder().setSsid(ssid)
        if (type != 1 && !pass.isNullOrEmpty()) b.setWpa2Passphrase(pass)
        if (hidden) b.setIsHiddenSsid(true)
        val sug = b.build()
        if (Build.VERSION.SDK_INT >= 30) {
            val i = Intent(Settings.ACTION_WIFI_ADD_NETWORKS)
            i.putParcelableArrayListExtra(Settings.EXTRA_WIFI_NETWORK_LIST, arrayListOf(sug))
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
            return "Tap Save in the Wi-Fi prompt to join \"$ssid\""
        }
        val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val st = wm.addNetworkSuggestions(listOf(sug))
        return if (st == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) {
            "Network \"$ssid\" suggested. Android will connect when it is in range."
        } else {
            "Android refused the network suggestion (code $st)."
        }
    } catch (e: Exception) {
        if (!pass.isNullOrEmpty()) copyText(ctx, pass)
        try {
            ctx.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (x: Exception) {
        }
        return "Couldn't add it automatically. Password copied; choose \"$ssid\" in Wi-Fi settings."
    }
}

fun treeRoot(tree: Uri): Uri =
    DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))

fun docCreate(ctx: Context, parent: Uri, mime: String, name: String): Uri? {
    return try {
        DocumentsContract.createDocument(ctx.contentResolver, parent, mime, name)
    } catch (e: Exception) {
        null
    }
}

class VideoOut(val pfd: ParcelFileDescriptor, val uri: Uri)

fun openVideoOut(ctx: Context, tree: Uri?, name: String, relPath: String): VideoOut? {
    try {
        if (tree != null) {
            val doc = docCreate(ctx, treeRoot(tree), "video/mp4", "$name.mp4")
            if (doc != null) {
                val p = ctx.contentResolver.openFileDescriptor(doc, "rw")
                if (p != null) return VideoOut(p, doc)
            }
        }
        val cv = ContentValues()
        cv.put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        cv.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
        cv.put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
        val uri = ctx.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv) ?: return null
        val p = ctx.contentResolver.openFileDescriptor(uri, "rw") ?: return null
        return VideoOut(p, uri)
    } catch (e: Exception) {
        return null
    }
}

fun saveBitmap(ctx: Context, bmp: Bitmap, tree: Uri?, prefix: String): Uri? {
    val name = prefix + stampNow()
    try {
        var uri: Uri? = null
        var out: OutputStream? = null
        if (tree != null) {
            uri = docCreate(ctx, treeRoot(tree), "image/jpeg", "$name.jpg")
            if (uri != null) out = ctx.contentResolver.openOutputStream(uri)
        }
        if (out == null) {
            val cv = ContentValues()
            cv.put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            cv.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            cv.put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/SamCam")
            uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)
            if (uri != null) out = ctx.contentResolver.openOutputStream(uri)
        }
        if (out == null) return null
        out.use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        return uri
    } catch (e: Exception) {
        return null
    }
}

fun loadThumb(ctx: Context, uri: Uri, mime: String): Bitmap? {
    try {
        if (Build.VERSION.SDK_INT >= 29) {
            return ctx.contentResolver.loadThumbnail(uri, Size(200, 200), null)
        }
    } catch (e: Exception) {
    }
    if (mime.startsWith("image")) {
        try {
            val opts = BitmapFactory.Options()
            opts.inSampleSize = 8
            ctx.contentResolver.openInputStream(uri)?.use { return BitmapFactory.decodeStream(it, null, opts) }
        } catch (e: Exception) {
        }
    }
    return null
}

fun decodeJpeg(d: JpegData, mirror: Boolean, maxSide: Int): Bitmap? {
    try {
        val o = BitmapFactory.Options()
        o.inJustDecodeBounds = true
        BitmapFactory.decodeByteArray(d.bytes, 0, d.bytes.size, o)
        var ss = 1
        while (maxOf(o.outWidth, o.outHeight) / ss > maxSide) ss *= 2
        val o2 = BitmapFactory.Options()
        o2.inSampleSize = ss
        val raw = BitmapFactory.decodeByteArray(d.bytes, 0, d.bytes.size, o2) ?: return null
        val m = Matrix()
        m.postRotate(d.rot.toFloat())
        if (mirror) m.postScale(-1f, 1f)
        return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
    } catch (e: Exception) {
        return null
    }
}

suspend fun portraitBlur(src: Bitmap, segmenter: Segmenter, div: Int): Bitmap {
    val w = src.width
    val h = src.height
    val sc = 640f / maxOf(w, h)
    val small = Bitmap.createScaledBitmap(src, maxOf(1, (w * sc).toInt()), maxOf(1, (h * sc).toInt()), true)
    val mask: SegmentationMask? = suspendCoroutine { c ->
        try {
            segmenter.process(InputImage.fromBitmap(small, 0))
                .addOnSuccessListener { c.resume(it) }
                .addOnFailureListener { c.resume(null) }
        } catch (e: Exception) {
            c.resume(null)
        }
    }
    var mw = 64
    var mh = 64
    var conf = FloatArray(mw * mh)
    var cover = 0
    if (mask != null) {
        try {
            mw = mask.width
            mh = mask.height
            conf = FloatArray(mw * mh)
            val bb = mask.buffer
            bb.rewind()
            for (i in conf.indices) {
                val v = bb.getFloat()
                conf[i] = v
                if (v > 0.6f) cover++
            }
        } catch (e: Exception) {
            cover = 0
        }
    }
    if (mask == null || cover < mw * mh * 0.02f) {
        for (y in 0 until mh) {
            for (x in 0 until mw) {
                val dx = ((x + 0.5f) / mw - 0.5f) / 0.38f
                val dy = ((y + 0.5f) / mh - 0.5f) / 0.46f
                val d = sqrt(dx * dx + dy * dy) * 0.75f
                conf[y * mw + x] = ((1.1f - d) / 0.35f).coerceIn(0f, 1f)
            }
        }
    }
    val px = IntArray(mw * mh)
    for (i in px.indices) {
        val a = ((conf[i] - 0.4f) / 0.25f).coerceIn(0f, 1f)
        px[i] = (a * 255f).toInt() shl 24
    }
    val maskBmp = Bitmap.createBitmap(px, mw, mh, Bitmap.Config.ARGB_8888)
    val maskScaled = Bitmap.createScaledBitmap(maskBmp, w, h, true)

    val bw = maxOf(2, w / div)
    val bh = maxOf(2, h / div)
    val b1 = Bitmap.createScaledBitmap(src, bw, bh, true)
    val b2 = Bitmap.createScaledBitmap(b1, maxOf(1, bw / 2), maxOf(1, bh / 2), true)
    val blurred = Bitmap.createScaledBitmap(b2, w, h, true)

    val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val cv = GCanvas(result)
    cv.drawBitmap(blurred, 0f, 0f, null)
    val fg = src.copy(Bitmap.Config.ARGB_8888, true)
    val p = Paint()
    p.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    GCanvas(fg).drawBitmap(maskScaled, 0f, 0f, p)
    cv.drawBitmap(fg, 0f, 0f, null)
    return result
}

fun docEnhance(src: Bitmap, bw: Boolean): Bitmap {
    val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
    val cm = ColorMatrix()
    if (bw) cm.setSaturation(0f)
    val k = 1.55f
    val t = (-0.5f * k + 0.5f) * 255f + 30f
    cm.postConcat(
        ColorMatrix(
            floatArrayOf(
                k, 0f, 0f, 0f, t,
                0f, k, 0f, 0f, t,
                0f, 0f, k, 0f, t,
                0f, 0f, 0f, 1f, 0f
            )
        )
    )
    val p = Paint()
    p.colorFilter = ColorMatrixColorFilter(cm)
    GCanvas(out).drawBitmap(src, 0f, 0f, p)
    return out
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
        val buf = java.nio.ByteBuffer.allocate(4 * 1024 * 1024)
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

class ZoomSender(private val exec: java.util.concurrent.Executor) {
    var cam: Camera? = null
    private var busy = false
    private var pending = Float.NaN

    fun send(z: Float) {
        val c = cam ?: return
        if (busy) {
            pending = z
            return
        }
        busy = true
        try {
            val f = c.cameraControl.setZoomRatio(z)
            f.addListener({
                busy = false
                val p = pending
                if (!p.isNaN()) {
                    pending = Float.NaN
                    send(p)
                }
            }, exec)
        } catch (e: Exception) {
            busy = false
        }
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
            override fun onOpened(camera: CameraDevice) {
                opened.complete(camera)
            }

            override fun onDisconnected(camera: CameraDevice) {
                camera.close()
                opened.complete(null)
            }

            override fun onError(camera: CameraDevice, error: Int) {
                camera.close()
                opened.complete(null)
            }
        }, handler)
        val dev = withTimeoutOrNull(4000L) { opened.await() }
        if (dev == null) {
            res = ProbeRes(-1.0, "camera did not open")
        } else {
            device = dev
            val ready = CompletableDeferred<CameraCaptureSession?>()
            dev.createCaptureSession(listOf(reader.surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(s: CameraCaptureSession) {
                    ready.complete(s)
                }

                override fun onConfigureFailed(s: CameraCaptureSession) {
                    ready.complete(null)
                }
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
            c.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)?.let { sb.append("  AE fps: ${it.joinToString()}\n") }
            c.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)?.let { sb.append("  ISO: $it\n") }
            c.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)?.let { sb.append("  Exposure ns: $it\n") }
            c.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)?.let { sb.append("  Min focus dist: $it\n") }
            val map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            map?.getOutputSizes(MediaRecorder::class.java)?.take(6)?.let { sb.append("  Video sizes: ${it.joinToString()}\n") }
            val hs = map?.highSpeedVideoSizes
            if (hs == null || hs.isEmpty()) {
                sb.append("  High-speed: none\n")
            } else {
                for (s in hs.take(4)) {
                    sb.append("  High-speed $s: ${map.getHighSpeedVideoFpsRangesFor(s)?.joinToString()}\n")
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { Root() } }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (Bus.volMode == 0) {
                if (event?.repeatCount == 0) Bus.tick++
                return true
            }
            if (Bus.volMode == 1) {
                Bus.zoomUp = keyCode == KeyEvent.KEYCODE_VOLUME_UP
                Bus.zoomTick++
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}

@Composable
fun LogoMark(dp: Dp) {
    Canvas(Modifier.size(dp)) {
        val w = this.size.width
        drawRoundRect(Color(0xFFF4F4F4), cornerRadius = CornerRadius(w * 0.24f))
        val c = Offset(w * 0.5f, w * 0.5f)
        drawCircle(Color(0xFF2A2A2C), radius = w * 0.34f, center = c)
        drawCircle(Color(0xFF111113), radius = w * 0.29f, center = c)
        drawCircle(Color(0xFF3A3A3E), radius = w * 0.21f, center = c)
        drawCircle(Color(0xFF050506), radius = w * 0.185f, center = c)
        drawCircle(Color(0xFF2E3A4A), radius = w * 0.09f, center = c)
        drawCircle(Color.White.copy(alpha = 0.9f), radius = w * 0.032f, center = Offset(w * 0.44f, w * 0.43f))
        drawCircle(Color(0xFFE53935), radius = w * 0.046f, center = Offset(w * 0.8f, w * 0.2f))
    }
}

@Composable
fun Splash() {
    Box(Modifier.fillMaxSize().background(Color(0xFF0E1116)), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LogoMark(110.dp)
            Spacer(Modifier.height(16.dp))
            Text("Camera", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun Root() {
    val ctx = LocalContext.current
    val perms = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    fun granted() = perms.all { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }
    var ok by remember { mutableStateOf(granted()) }
    var splash by remember { mutableStateOf(true) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { ok = granted() }
    LaunchedEffect(Unit) { if (!ok) launcher.launch(perms) }
    LaunchedEffect(Unit) { delay(1300); splash = false }
    if (ok) {
        Box(Modifier.fillMaxSize()) {
            CameraScreen()
            AnimatedVisibility(visible = splash, enter = fadeIn(), exit = fadeOut(tween(400))) { Splash() }
        }
    } else {
        Box(Modifier.fillMaxSize().background(Color.Black), Alignment.Center) {
            Text("Camera and microphone permission needed", color = Color.White)
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun CameraScreen() {
    val ctx = LocalContext.current
    val owner = ctx as ComponentActivity
    val scope = rememberCoroutineScope()
    val exec = remember { ContextCompat.getMainExecutor(ctx) }
    val bgExec = remember { Executors.newSingleThreadExecutor() }
    val analysisExec = remember { Executors.newSingleThreadExecutor() }
    val haptic = LocalHapticFeedback.current
    val sound = remember { MediaActionSound() }
    val sm = remember { ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val previewView = remember {
        PreviewView(ctx).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        }
    }
    val scanner = remember { BarcodeScanning.getClient() }
    val segLazy = remember {
        lazy {
            Segmentation.getClient(
                SelfieSegmenterOptions.Builder().setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE).build()
            )
        }
    }
    val camIds = remember {
        try {
            (ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager).cameraIdList.toList()
        } catch (e: Exception) {
            emptyList<String>()
        }
    }
    val rearId = remember {
        var found: String? = null
        try {
            val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            for (c in cm.cameraIdList) {
                if (cm.getCameraCharacteristics(c).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) {
                    found = c
                    break
                }
            }
        } catch (e: Exception) {
        }
        found
    }
    val prefs = remember { ctx.getSharedPreferences("samcam", Context.MODE_PRIVATE) }
    val modeOrder = remember { Mode.values().toList() }
    val itemW = 104.dp
    val itemPx = with(LocalDensity.current) { itemW.toPx() }
    val zoomSender = remember { ZoomSender(exec) }

    var sdUri by remember {
        mutableStateOf<Uri?>(
            prefs.getString("tree", null)?.let { Uri.parse(it) }?.takeIf { u ->
                ctx.contentResolver.persistedUriPermissions.any { it.uri == u && it.isWritePermission }
            }
        )
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                prefs.edit().putString("tree", uri.toString()).apply()
                sdUri = uri
                Toast.makeText(ctx, "Saving to the chosen folder", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(ctx, "Could not use that folder: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    var mode by remember {
        mutableStateOf(
            if (prefs.getBoolean("remember", false)) modeOrder.getOrElse(prefs.getInt("lastmode", 4)) { Mode.PHOTO }
            else Mode.PHOTO
        )
    }
    val modePos = remember { mutableFloatStateOf(modeOrder.indexOf(mode).toFloat()) }
    var modeAnim by remember { mutableStateOf<Job?>(null) }
    var front by remember { mutableStateOf(prefs.getBoolean("deffront", false)) }
    var camChoice by remember { mutableIntStateOf(0) }
    var ratio43 by remember { mutableStateOf(prefs.getBoolean("r43", true)) }
    var flash by remember { mutableIntStateOf(0) }
    var torch by remember { mutableStateOf(false) }
    var timer by remember { mutableIntStateOf(0) }
    var countdown by remember { mutableIntStateOf(0) }
    var holdStill by remember { mutableStateOf(false) }
    var sizeIdx by remember { mutableIntStateOf(prefs.getInt("size", 0)) }
    var jpegIdx by remember { mutableIntStateOf(prefs.getInt("jpeg", 1)) }
    var vqIdx by remember { mutableIntStateOf(prefs.getInt("vq", 1)) }
    var fpsIdx by remember { mutableIntStateOf(prefs.getInt("fps", 1)) }
    var brIdx by remember { mutableIntStateOf(prefs.getInt("br", 0)) }
    var tlIdx by remember { mutableIntStateOf(prefs.getInt("tl", 1)) }
    var slowIdx by remember { mutableIntStateOf(prefs.getInt("slow", 1)) }
    var blurIdx by remember { mutableIntStateOf(prefs.getInt("blur", 1)) }
    var prioIdx by remember { mutableIntStateOf(prefs.getInt("prio", 0)) }
    var enginePref by remember { mutableIntStateOf(prefs.getInt("engine", 0)) }
    var mic by remember { mutableStateOf(prefs.getBoolean("mic", true)) }
    var grid by remember { mutableStateOf(prefs.getBoolean("grid", false)) }
    var volMode by remember { mutableIntStateOf(prefs.getInt("vol", 0)) }
    var mirror by remember { mutableStateOf(prefs.getBoolean("mirror", true)) }
    var forceFps by remember { mutableStateOf(prefs.getBoolean("force", false)) }
    var usePhotos by remember { mutableStateOf(prefs.getBoolean("photos", true)) }
    var shutterSound by remember { mutableStateOf(prefs.getBoolean("sound", true)) }
    var vibrate by remember { mutableStateOf(prefs.getBoolean("vibe", true)) }
    var tapFocus by remember { mutableStateOf(prefs.getBoolean("tap", true)) }
    var keepOn by remember { mutableStateOf(prefs.getBoolean("keep", true)) }
    var flashAnim by remember { mutableStateOf(prefs.getBoolean("flashanim", true)) }
    var showZoom by remember { mutableStateOf(prefs.getBoolean("showzoom", true)) }
    var defFront by remember { mutableStateOf(prefs.getBoolean("deffront", false)) }
    var rememberMode by remember { mutableStateOf(prefs.getBoolean("remember", false)) }
    var qrScan by remember { mutableStateOf(prefs.getBoolean("qrscan", true)) }
    var docBw by remember { mutableStateOf(prefs.getBoolean("docbw", true)) }
    var showSettings by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var qr by remember { mutableStateOf<QrResult?>(null) }
    var qrDismissed by remember { mutableStateOf<String?>(null) }
    var qrSeen by remember { mutableLongStateOf(0L) }
    var tlOn by remember { mutableStateOf(false) }
    var tlJob by remember { mutableStateOf<Job?>(null) }
    var tlCount by remember { mutableIntStateOf(0) }
    var panoOn by remember { mutableStateOf(false) }
    var panoStrips by remember { mutableIntStateOf(0) }
    var panoFov by remember { mutableFloatStateOf(0.9f) }
    var processing by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var paused by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    val zoomTarget = remember { mutableFloatStateOf(1f) }
    val zoomShown = remember { mutableFloatStateOf(1f) }
    val rulerBump = remember { mutableIntStateOf(0) }
    var minZoom by remember { mutableFloatStateOf(1f) }
    var maxZoom by remember { mutableFloatStateOf(1f) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var accMs by remember { mutableLongStateOf(0L) }
    var lastUri by remember { mutableStateOf<Uri?>(prefs.getString("last_uri", null)?.let { Uri.parse(it) }) }
    var lastMime by remember { mutableStateOf(prefs.getString("last_mime", "image/jpeg") ?: "image/jpeg") }
    var thumb by remember { mutableStateOf<Bitmap?>(null) }
    var focusPos by remember { mutableStateOf<Offset?>(null) }
    var focusTick by remember { mutableIntStateOf(0) }
    val focusAnimV = remember { Animatable(1f) }
    val switchAlpha = remember { Animatable(0f) }
    val shutterAnim = remember { Animatable(0f) }
    var screenFlash by remember { mutableStateOf(false) }
    var camPaused by remember { mutableStateOf(false) }
    var probeRunning by remember { mutableStateOf(false) }
    var probeResult by remember { mutableStateOf("") }
    var nightFps by remember { mutableStateOf<Range<Int>?>(null) }

    var proWb by remember { mutableIntStateOf(0) }
    var ev by remember { mutableIntStateOf(0) }
    var proManualExp by remember { mutableStateOf(false) }
    var proIsoF by remember { mutableFloatStateOf(0.2f) }
    var proShutF by remember { mutableFloatStateOf(0.6f) }
    var proManualFocus by remember { mutableStateOf(false) }
    var proFocusF by remember { mutableFloatStateOf(0f) }
    var isoRange by remember { mutableStateOf<Range<Int>?>(null) }
    var expRange by remember { mutableStateOf<Range<Long>?>(null) }
    var evRange by remember { mutableStateOf<Range<Int>?>(null) }
    var minFocus by remember { mutableFloatStateOf(0f) }

    val use43 = ratio43 && mode != Mode.VIDEO && mode != Mode.SLOMO
    val isRec = recording != null
    val capturing = isRec || tlOn || panoOn
    val ir = isoRange
    val xr = expRange
    val isoVal = if (ir != null) (ir.lower + proIsoF * (ir.upper - ir.lower)).toInt() else 0
    val shutNs: Long = if (xr != null) {
        val lo = xr.lower.toDouble().coerceAtLeast(1.0)
        val hi = minOf(xr.upper, 500_000_000L).toDouble().coerceAtLeast(lo)
        (lo * Math.pow(hi / lo, proShutF.toDouble())).toLong()
    } else 0L

    val arAnim by animateFloatAsState(
        targetValue = if (use43) 0.75f else 0.5625f,
        animationSpec = tween(300), label = "ar"
    )

    fun toast(s: String) {
        Toast.makeText(ctx, s, Toast.LENGTH_LONG).show()
    }

    fun toastSafe(s: String) {
        scope.launch { toast(s) }
    }

    fun buzz() {
        if (vibrate) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun snd(kind: Int) {
        if (shutterSound) {
            try {
                sound.play(kind)
            } catch (e: Exception) {
            }
        }
    }

    fun setLast(u: Uri?, mime: String) {
        if (u != null) {
            lastUri = u
            lastMime = mime
            prefs.edit().putString("last_uri", u.toString()).putString("last_mime", mime).apply()
        }
    }

    fun zoomDirect(z: Float) {
        val c = z.coerceIn(minZoom, maxOf(minZoom, maxZoom))
        zoomShown.floatValue = c
        zoomTarget.floatValue = c
        zoomSender.send(c)
        rulerBump.intValue = rulerBump.intValue + 1
    }

    LaunchedEffect(
        sizeIdx, jpegIdx, vqIdx, fpsIdx, brIdx, tlIdx, slowIdx, blurIdx, prioIdx, enginePref, mic, grid, volMode,
        mirror, forceFps, usePhotos, shutterSound, vibrate, tapFocus, keepOn, ratio43, flashAnim, showZoom,
        defFront, rememberMode, qrScan, docBw
    ) {
        prefs.edit()
            .putInt("size", sizeIdx).putInt("jpeg", jpegIdx).putInt("vq", vqIdx).putInt("fps", fpsIdx)
            .putInt("br", brIdx).putInt("tl", tlIdx).putInt("slow", slowIdx).putInt("blur", blurIdx)
            .putInt("prio", prioIdx).putInt("engine", enginePref)
            .putBoolean("mic", mic).putBoolean("grid", grid).putInt("vol", volMode).putBoolean("mirror", mirror)
            .putBoolean("force", forceFps).putBoolean("photos", usePhotos).putBoolean("sound", shutterSound)
            .putBoolean("vibe", vibrate).putBoolean("tap", tapFocus).putBoolean("keep", keepOn).putBoolean("r43", ratio43)
            .putBoolean("flashanim", flashAnim).putBoolean("showzoom", showZoom).putBoolean("deffront", defFront)
            .putBoolean("remember", rememberMode).putBoolean("qrscan", qrScan).putBoolean("docbw", docBw)
            .apply()
    }

    LaunchedEffect(mode) {
        prefs.edit().putInt("lastmode", modeOrder.indexOf(mode)).apply()
    }

    LaunchedEffect(keepOn) {
        if (keepOn) owner.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else owner.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    LaunchedEffect(lastUri) {
        val u = lastUri
        thumb = if (u == null) null else withContext(Dispatchers.IO) { loadThumb(ctx, u, lastMime) }
    }

    LaunchedEffect(capturing) {
        if (capturing) {
            accMs = 0L
            var last = System.currentTimeMillis()
            while (true) {
                delay(200)
                val now = System.currentTimeMillis()
                if (!paused) accMs += now - last
                last = now
                elapsed = accMs / 1000
            }
        } else {
            elapsed = 0L
            accMs = 0L
            paused = false
        }
    }

    LaunchedEffect(focusTick) {
        if (focusTick > 0) {
            focusAnimV.snapTo(0f)
            focusAnimV.animateTo(1f, tween(800))
        }
    }

    LaunchedEffect(mode, front, camChoice, ratio43) {
        switchAlpha.snapTo(0.85f)
        switchAlpha.animateTo(0f, tween(450))
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(350)
            val now = System.currentTimeMillis()
            if (qr != null && now - qrSeen > 2200) qr = null
            if (now - qrSeen > 3000) qrDismissed = null
        }
    }

    LaunchedEffect(camera) {
        zoomSender.cam = camera
    }

    LaunchedEffect(camera) {
        if (camera == null) return@LaunchedEffect
        snapshotFlow { zoomTarget.floatValue }.collectLatest { target ->
            val start = zoomShown.floatValue
            if (abs(target - start) > 0.003f) {
                Animatable(start).animateTo(target, spring(dampingRatio = 1f, stiffness = 420f)) {
                    zoomShown.floatValue = value
                    zoomSender.send(value)
                }
            }
        }
    }

    LaunchedEffect(
        mode, front, camChoice, ratio43, sizeIdx, jpegIdx, vqIdx, fpsIdx, brIdx, camPaused, qrScan, enginePref, prioIdx
    ) {
        val provider = suspendCoroutine<ProcessCameraProvider> { c ->
            val f = ProcessCameraProvider.getInstance(ctx)
            f.addListener({ c.resume(f.get()) }, exec)
        }
        provider.unbindAll()
        if (camPaused) {
            camera = null
            imageCapture = null
            videoCapture = null
            return@LaunchedEffect
        }
        previewView.implementationMode =
            if (mode == Mode.PANO || mode == Mode.TIMELAPSE || enginePref == 1) PreviewView.ImplementationMode.COMPATIBLE
            else PreviewView.ImplementationMode.PERFORMANCE

        val aspect = if (use43) AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
        else AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
        val previewRs = ResolutionSelector.Builder().setAspectRatioStrategy(aspect).build()
        val longSide = sizeLong[sizeIdx]
        val imageRsBuilder = ResolutionSelector.Builder().setAspectRatioStrategy(aspect)
        if (longSide > 0) {
            imageRsBuilder.setResolutionStrategy(
                ResolutionStrategy(
                    Size(longSide, if (use43) longSide * 3 / 4 else longSide * 9 / 16),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                )
            )
        }
        val imageRs = imageRsBuilder.build()

        val selector: CameraSelector = if (camChoice > 0 && camChoice <= camIds.size) {
            val id = camIds[camChoice - 1]
            CameraSelector.Builder().addCameraFilter { infos ->
                infos.filter { Camera2CameraInfo.from(it).cameraId == id }
            }.build()
        } else if (front) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }

        val attempts: List<Attempt> = when {
            mode in stillModes ->
                if (mode == Mode.PHOTO && qrScan) listOf(Attempt(true, true), Attempt(true, false))
                else listOf(Attempt(true, false))
            mode == Mode.VIDEO || mode == Mode.SLOMO || mode == Mode.TIMELAPSE ->
                listOf(Attempt(true, false), Attempt(false, false))
            else -> listOf(Attempt(false, false))
        }

        var bound: Camera? = null
        for (at in attempts) {
            try {
                provider.unbindAll()
                imageCapture = null
                videoCapture = null
                val preview = Preview.Builder().setResolutionSelector(previewRs).build()
                preview.setSurfaceProvider(previewView.surfaceProvider)
                val cases = mutableListOf<UseCase>(preview)
                var ic: ImageCapture? = null
                var vc: VideoCapture<Recorder>? = null
                if (mode == Mode.VIDEO || mode == Mode.SLOMO) {
                    val q = if (mode == Mode.SLOMO) Quality.HD else vqVals[vqIdx]
                    val rb = Recorder.Builder()
                        .setQualitySelector(QualitySelector.from(q, FallbackStrategy.lowerQualityOrHigherThan(q)))
                    val vb = brVals[brIdx]
                    if (vb > 0 && mode == Mode.VIDEO) rb.setTargetVideoEncodingBitRate(vb)
                    val vcb = VideoCapture.Builder(rb.build())
                    if (mode == Mode.SLOMO) {
                        vcb.setTargetFrameRate(Range(120, 240))
                    } else if (fpsVals[fpsIdx] != 30) {
                        vcb.setTargetFrameRate(Range(fpsVals[fpsIdx], fpsVals[fpsIdx]))
                    }
                    val built = vcb.build()
                    cases.add(built)
                    vc = built
                }
                if (at.analysis) {
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                    var lastScan = 0L
                    analysis.setAnalyzer(analysisExec) { proxy ->
                        val now = System.currentTimeMillis()
                        if (now - lastScan < 450) {
                            proxy.close()
                        } else {
                            lastScan = now
                            try {
                                val bmp = proxy.toBitmap()
                                val img = InputImage.fromBitmap(bmp, proxy.imageInfo.rotationDegrees)
                                scanner.process(img)
                                    .addOnSuccessListener { list ->
                                        val b = list.firstOrNull()
                                        if (b != null) {
                                            val raw = b.rawValue ?: b.displayValue ?: ""
                                            if (raw.isNotEmpty()) {
                                                qrSeen = System.currentTimeMillis()
                                                if (raw != qrDismissed && qr?.text != raw) {
                                                    qr = makeQr(b, raw)
                                                    buzz()
                                                }
                                            }
                                        }
                                    }
                                    .addOnCompleteListener { proxy.close() }
                            } catch (e: Exception) {
                                proxy.close()
                            }
                        }
                    }
                    cases.add(analysis)
                }
                if (at.still) {
                    val capMode = if (mode == Mode.NIGHT || prioIdx == 0 || jpegIdx == 0)
                        ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY else ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                    val built = ImageCapture.Builder()
                        .setResolutionSelector(imageRs)
                        .setCaptureMode(capMode)
                        .setJpegQuality(jpegVals[jpegIdx])
                        .build()
                    cases.add(built)
                    ic = built
                }
                bound = provider.bindToLifecycle(owner, selector, *cases.toTypedArray())
                imageCapture = ic
                videoCapture = vc
                if ((mode == Mode.VIDEO || mode == Mode.SLOMO) && !at.still) {
                    toast("Taking photos while recording isn't supported by this camera driver")
                }
                break
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }

        val cam = bound
        if (cam == null) {
            if (camChoice > 0) {
                camChoice = 0
                toast("That camera ID did not open. Back to Auto.")
                return@LaunchedEffect
            }
            try {
                provider.unbindAll()
                val ic = ImageCapture.Builder().build()
                val pv = Preview.Builder().build()
                pv.setSurfaceProvider(previewView.surfaceProvider)
                camera = provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, pv, ic)
                imageCapture = ic
                videoCapture = null
                toast("Safe mode (photo only)")
            } catch (e2: Exception) {
                toast("Camera error: ${e2.message}")
            }
            return@LaunchedEffect
        }

        camera = cam
        zoomShown.floatValue = 1f
        zoomTarget.floatValue = 1f
        ev = 0
        try {
            val c2i = Camera2CameraInfo.from(cam.cameraInfo)
            isoRange = c2i.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
            expRange = c2i.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
            minFocus = c2i.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
            evRange = cam.cameraInfo.exposureState.exposureCompensationRange
            val ranges = c2i.getCameraCharacteristic(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
            nightFps = ranges?.filter { it.upper >= 8 }?.minByOrNull { it.upper * 1000 + it.lower }
            val ps = c2i.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
            val fl = c2i.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull()
            if (ps != null && fl != null && fl > 0f) {
                panoFov = (2.0 * atan(minOf(ps.width, ps.height) / (2.0 * fl))).toFloat()
            }
            if (mode == Mode.VIDEO && fpsVals[fpsIdx] > 30) {
                val want = fpsVals[fpsIdx]
                val offered = ranges != null && ranges.any { it.upper >= want }
                if (!offered) {
                    toast("${want}fps is not offered by this camera driver. Run the 60 fps test in Camera info.")
                }
            }
        } catch (e: Exception) {
            isoRange = null
            expRange = null
            minFocus = 0f
            evRange = null
        }
        delay(300)
        val zst = cam.cameraInfo.zoomState.value
        minZoom = zst?.minZoomRatio ?: 1f
        maxZoom = minOf(zst?.maxZoomRatio ?: 1f, 10f)
    }

    LaunchedEffect(flash, camera, imageCapture, mode) {
        val ic = imageCapture
        if (ic != null) {
            ic.flashMode = if (mode in stillModes) {
                when (flash) {
                    1 -> ImageCapture.FLASH_MODE_AUTO
                    2 -> ImageCapture.FLASH_MODE_ON
                    else -> ImageCapture.FLASH_MODE_OFF
                }
            } else {
                ImageCapture.FLASH_MODE_OFF
            }
        }
    }
    LaunchedEffect(torch, camera, mode) {
        val torchOk = mode == Mode.VIDEO || mode == Mode.SLOMO || mode == Mode.TIMELAPSE
        camera?.cameraControl?.enableTorch(torch && torchOk)
    }
    LaunchedEffect(ev, camera, mode) {
        try {
            val idx = if (mode == Mode.NIGHT) ((evRange?.upper ?: 0) * 2 / 3) else ev
            camera?.cameraControl?.setExposureCompensationIndex(idx)
        } catch (e: Exception) {
        }
    }
    LaunchedEffect(Bus.zoomTick) {
        if (Bus.zoomTick > 0) {
            val f = if (Bus.zoomUp) 1.15f else 1f / 1.15f
            zoomTarget.floatValue = (zoomTarget.floatValue * f).coerceIn(minZoom, maxOf(minZoom, maxZoom))
        }
    }

    LaunchedEffect(mode, camera, proWb, proManualExp, isoVal, shutNs, proManualFocus, proFocusF, minFocus, forceFps, fpsIdx, nightFps) {
        val cam = camera ?: return@LaunchedEffect
        try {
            val c2 = Camera2CameraControl.from(cam.cameraControl)
            val b = CaptureRequestOptions.Builder()
            var any = false
            if (mode == Mode.PRO) {
                b.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, wbVals[proWb])
                if (proManualExp && isoRange != null && expRange != null) {
                    b.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                    b.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, isoVal)
                    b.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, shutNs)
                }
                if (proManualFocus && minFocus > 0f) {
                    b.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
                    b.setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, proFocusF * minFocus)
                }
                any = true
            }
            if (mode == Mode.NIGHT) {
                val nf = nightFps
                if (nf != null) b.setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, nf)
                b.setCaptureRequestOption(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY)
                any = true
            }
            if (mode == Mode.VIDEO && forceFps && fpsVals[fpsIdx] > 30) {
                val f = fpsVals[fpsIdx]
                b.setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(f, f))
                any = true
            }
            if (any) c2.setCaptureRequestOptions(b.build()) else c2.clearCaptureRequestOptions()
        } catch (e: Exception) {
            toast("Camera options not accepted: ${e.message}")
        }
    }

    fun values(mime: String, dir: String): ContentValues {
        val cv = ContentValues()
        cv.put(MediaStore.MediaColumns.DISPLAY_NAME, stampNow())
        cv.put(MediaStore.MediaColumns.MIME_TYPE, mime)
        cv.put(MediaStore.MediaColumns.RELATIVE_PATH, dir)
        return cv
    }

    fun takePhoto() {
        val ic = imageCapture ?: return
        snd(MediaActionSound.SHUTTER_CLICK)
        buzz()
        if (flashAnim) {
            scope.launch {
                shutterAnim.snapTo(0.65f)
                shutterAnim.animateTo(0f, tween(220))
            }
        }
        val processedMode = mode == Mode.PORTRAIT || mode == Mode.DOC
        if (processedMode) {
            if (processing) {
                toast("Still saving the last photo")
                return
            }
            val m = mode
            ic.takePicture(bgExec, object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    var data: JpegData? = null
                    try {
                        val buf = image.planes[0].buffer
                        val bytes = ByteArray(buf.remaining())
                        buf.get(bytes)
                        data = JpegData(bytes, image.imageInfo.rotationDegrees)
                    } catch (e: Exception) {
                    }
                    image.close()
                    val d = data
                    if (d == null) {
                        toastSafe("Could not read the photo")
                        return
                    }
                    scope.launch {
                        processing = true
                        val uri = withContext(Dispatchers.Default) {
                            val bmp = decodeJpeg(d, front && mirror, 2400)
                            if (bmp == null) {
                                null
                            } else {
                                val out = try {
                                    if (m == Mode.PORTRAIT) portraitBlur(bmp, segLazy.value, blurDiv[blurIdx])
                                    else docEnhance(bmp, docBw)
                                } catch (e: Exception) {
                                    bmp
                                }
                                saveBitmap(ctx, out, sdUri, if (m == Mode.PORTRAIT) "PORTRAIT_" else "DOC_")
                            }
                        }
                        processing = false
                        if (uri != null) setLast(uri, "image/jpeg") else toast("Could not save the photo")
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    toastSafe("Error: ${exception.message}")
                }
            })
            return
        }
        val meta = ImageCapture.Metadata()
        meta.isReversedHorizontal = front && mirror
        val tree = sdUri
        val doc: Uri? = if (tree != null) docCreate(ctx, treeRoot(tree), "image/jpeg", "${stampNow()}.jpg") else null
        val s: OutputStream? = if (doc != null) {
            try {
                ctx.contentResolver.openOutputStream(doc)
            } catch (e: Exception) {
                null
            }
        } else null
        if (tree != null && s == null) toast("SD folder not writable, saving to phone")
        val builder = if (s != null) {
            ImageCapture.OutputFileOptions.Builder(s)
        } else {
            ImageCapture.OutputFileOptions.Builder(
                ctx.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values("image/jpeg", "DCIM/SamCam")
            )
        }
        ic.takePicture(builder.setMetadata(meta).build(), exec, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(r: ImageCapture.OutputFileResults) {
                try {
                    s?.close()
                } catch (e: Exception) {
                }
                setLast(r.savedUri ?: doc, "image/jpeg")
            }

            override fun onError(e: ImageCaptureException) {
                try {
                    s?.close()
                } catch (x: Exception) {
                }
                toast("Error: ${e.message}")
            }
        })
    }

    fun togglePause() {
        val r = recording ?: return
        if (paused) {
            r.resume()
            paused = false
        } else {
            r.pause()
            paused = true
        }
    }

    fun toggleRecord() {
        val active = recording
        if (active != null) {
            snd(MediaActionSound.STOP_VIDEO_RECORDING)
            active.stop()
            recording = null
            paused = false
            return
        }
        val vc = videoCapture ?: return
        if (mode == Mode.SLOMO) {
            val tmp = File(ctx.cacheDir, "slomo_" + stampNow() + ".mp4")
            snd(MediaActionSound.START_VIDEO_RECORDING)
            val pendingSlo = vc.output.prepareRecording(ctx, FileOutputOptions.Builder(tmp).build())
            recording = pendingSlo.start(exec) { evt ->
                if (evt is VideoRecordEvent.Finalize) {
                    recording = null
                    paused = false
                    if (evt.hasError()) {
                        toast("Video error ${evt.error}")
                        tmp.delete()
                    } else {
                        scope.launch {
                            processing = true
                            val out = withContext(Dispatchers.IO) { openVideoOut(ctx, sdUri, stampNow() + "_SLO", "DCIM/SamCam") }
                            var ok = false
                            if (out != null) {
                                ok = withContext(Dispatchers.IO) { remuxSlow(tmp, out.pfd, slowVals[slowIdx]) }
                                try {
                                    out.pfd.close()
                                } catch (e: Exception) {
                                }
                            }
                            tmp.delete()
                            processing = false
                            if (ok && out != null) setLast(out.uri, "video/mp4") else toast("Could not make the slow-motion video")
                        }
                    }
                }
            }
            return
        }
        val out = openVideoOut(ctx, sdUri, stampNow(), "DCIM/SamCam")
        if (out == null) {
            toast("Could not create the video file")
            return
        }
        snd(MediaActionSound.START_VIDEO_RECORDING)
        var pending = vc.output.prepareRecording(ctx, FileDescriptorOutputOptions.Builder(out.pfd).build())
        if (mic) pending = pending.withAudioEnabled()
        recording = pending.start(exec) { evt ->
            if (evt is VideoRecordEvent.Finalize) {
                recording = null
                paused = false
                try {
                    out.pfd.close()
                } catch (e: Exception) {
                }
                if (evt.hasError()) toast("Video error ${evt.error}") else setLast(out.uri, "video/mp4")
            }
        }
    }

    fun stopTimelapse() {
        tlOn = false
        tlJob?.cancel()
    }

    fun startTimelapse() {
        tlCount = 0
        tlOn = true
        tlJob = scope.launch {
            var enc: TimelapseEncoder? = null
            var out: VideoOut? = null
            try {
                while (isActive && tlOn) {
                    val bmp = previewView.bitmap
                    if (bmp != null) {
                        if (enc == null) {
                            val sc = minOf(1f, 1920f / maxOf(bmp.width, bmp.height))
                            val w = ((bmp.width * sc).toInt() / 2) * 2
                            val h = ((bmp.height * sc).toInt() / 2) * 2
                            val o = openVideoOut(ctx, sdUri, stampNow() + "_TL", "DCIM/SamCam")
                            if (o == null) {
                                toast("Could not create the video file")
                                break
                            }
                            out = o
                            enc = TimelapseEncoder(w, h, o.pfd)
                        }
                        val e = enc ?: break
                        withContext(Dispatchers.Default) { e.addFrame(bmp) }
                        tlCount = tlCount + 1
                    }
                    delay(tlVals[tlIdx] * 1000L)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                toast("Time-lapse error: ${e.message}")
            } finally {
                tlOn = false
                val e2 = enc
                val o2 = out
                val frames = tlCount
                withContext(NonCancellable + Dispatchers.IO) {
                    if (e2 != null) {
                        e2.finish()
                    } else {
                        try {
                            o2?.pfd?.close()
                        } catch (x: Exception) {
                        }
                    }
                }
                if (e2 != null && o2 != null && frames > 0) setLast(o2.uri, "video/mp4")
            }
        }
    }

    fun startPano() {
        val gyro = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        if (gyro == null) {
            toast("This phone has no gyroscope")
            return
        }
        panoOn = true
        panoStrips = 0
        scope.launch {
            var yaw = 0.0
            var lastT = 0L
            val listener = object : SensorEventListener {
                override fun onSensorChanged(e: SensorEvent) {
                    if (lastT != 0L) yaw += e.values[1] * (e.timestamp - lastT) / 1e9
                    lastT = e.timestamp
                }

                override fun onAccuracyChanged(s: Sensor?, a: Int) {}
            }
            sm.registerListener(listener, gyro, SensorManager.SENSOR_DELAY_GAME)
            val strips = ArrayList<Bitmap>()
            var dir = 0
            var lastYaw = 0.0
            var total = 0
            try {
                while (isActive && panoOn) {
                    delay(70)
                    val bmp = previewView.bitmap
                    if (bmp == null) continue
                    val d = yaw - lastYaw
                    val sgn = if (d > 0) 1 else -1
                    val px = (abs(d) * bmp.width / panoFov).toInt()
                    if (dir == 0 && px >= 20) dir = sgn
                    if (dir != 0 && sgn != dir && px >= 4) {
                        lastYaw = yaw
                    } else if (dir != 0 && px >= 14) {
                        val sw = px.coerceAtMost(bmp.width / 2)
                        val x0 = (bmp.width - sw) / 2
                        val strip = Bitmap.createBitmap(bmp, x0, 0, sw, bmp.height)
                        if (dir > 0) strips.add(0, strip) else strips.add(strip)
                        total += sw
                        lastYaw = yaw
                        panoStrips = strips.size
                        if (total > 11000) panoOn = false
                    }
                    bmp.recycle()
                }
            } finally {
                sm.unregisterListener(listener)
                panoOn = false
            }
            if (strips.size < 3) {
                strips.forEach { it.recycle() }
                toast("Pan slowly across the scene to capture a panorama")
            } else {
                processing = true
                val uri = withContext(Dispatchers.Default) {
                    val totalW = strips.sumOf { it.width }
                    val h = strips[0].height
                    val outBmp = Bitmap.createBitmap(totalW, h, Bitmap.Config.ARGB_8888)
                    val cv = GCanvas(outBmp)
                    var x = 0f
                    for (s in strips) {
                        cv.drawBitmap(s, x, 0f, null)
                        x += s.width
                        s.recycle()
                    }
                    saveBitmap(ctx, outBmp, sdUri, "PANO_")
                }
                processing = false
                if (uri != null) setLast(uri, "image/jpeg") else toast("Could not save the panorama")
            }
        }
    }

    fun shutter() {
        scope.launch {
            if (countdown > 0) return@launch
            if (panoOn) {
                buzz()
                panoOn = false
                return@launch
            }
            if (tlOn) {
                buzz()
                stopTimelapse()
                return@launch
            }
            if (recording != null) {
                buzz()
                toggleRecord()
                return@launch
            }
            if (timer > 0 && mode != Mode.PANO) {
                for (i in timer downTo 1) {
                    countdown = i
                    delay(1000)
                }
                countdown = 0
            }
            when (mode) {
                Mode.VIDEO, Mode.SLOMO -> {
                    buzz()
                    toggleRecord()
                }
                Mode.TIMELAPSE -> {
                    buzz()
                    startTimelapse()
                }
                Mode.PANO -> {
                    buzz()
                    startPano()
                }
                Mode.NIGHT -> {
                    holdStill = true
                    delay(1400)
                    takePhoto()
                    delay(400)
                    holdStill = false
                }
                else -> {
                    if (front && flash != 0) {
                        screenFlash = true
                        delay(350)
                        takePhoto()
                        delay(300)
                        screenFlash = false
                    } else {
                        takePhoto()
                    }
                }
            }
        }
    }

    fun openGallery() {
        val u = lastUri
        if (u != null) {
            val base = Intent(Intent.ACTION_VIEW).setDataAndType(u, lastMime)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (usePhotos) {
                try {
                    ctx.startActivity(Intent(base).setPackage("com.google.android.apps.photos"))
                    return
                } catch (e: Exception) {
                }
            }
            try {
                ctx.startActivity(base)
                return
            } catch (e: Exception) {
            }
        }
        try {
            val li = ctx.packageManager.getLaunchIntentForPackage("com.google.android.apps.photos")
            if (usePhotos && li != null) {
                ctx.startActivity(li)
                return
            }
            ctx.startActivity(
                Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            toast("No gallery app found")
        }
    }

    fun animateModePos(target: Int) {
        modeAnim?.cancel()
        modeAnim = scope.launch {
            Animatable(modePos.floatValue).animateTo(
                target.toFloat(),
                spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
            ) { modePos.floatValue = value }
        }
    }

    fun selectMode(m: Mode) {
        if (capturing || countdown > 0) {
            animateModePos(modeOrder.indexOf(mode))
            return
        }
        if (m != mode) {
            qr = null
            torch = false
            mode = m
            buzz()
        }
        animateModePos(modeOrder.indexOf(m))
    }

    fun resetAll() {
        prefs.edit().clear().apply()
        sizeIdx = 0
        jpegIdx = 1
        vqIdx = 1
        fpsIdx = 1
        brIdx = 0
        tlIdx = 1
        slowIdx = 1
        blurIdx = 1
        prioIdx = 0
        enginePref = 0
        mic = true
        grid = false
        volMode = 0
        mirror = true
        forceFps = false
        usePhotos = true
        shutterSound = true
        vibrate = true
        tapFocus = true
        keepOn = true
        ratio43 = true
        flashAnim = true
        showZoom = true
        defFront = false
        rememberMode = false
        qrScan = true
        docBw = true
        sdUri = null
    }

    val modeDrag = rememberDraggableState { dx ->
        if (!capturing && countdown == 0) {
            modePos.floatValue = (modePos.floatValue - dx / itemPx).coerceIn(0f, (modeOrder.size - 1).toFloat())
        }
    }

    LaunchedEffect(Bus.tick) { if (Bus.tick > 0) shutter() }

    BackHandler(enabled = showSettings) {
        if (showInfo) showInfo = false else showSettings = false
    }

    Box(Modifier.fillMaxSize().background(Color.Black).systemBarsPadding()) {
        Box(
            Modifier.align(Alignment.TopCenter).padding(top = 56.dp).fillMaxWidth()
                .aspectRatio(arAnim)
                .pointerInput(tapFocus) {
                    detectTapGestures { o ->
                        if (tapFocus) {
                            val p = previewView.meteringPointFactory.createPoint(o.x, o.y)
                            camera?.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(p).build())
                            focusPos = o
                            focusTick = focusTick + 1
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, z, _ ->
                        zoomDirect(zoomShown.floatValue * z)
                    }
                }
        ) {
            AndroidView({ previewView }, Modifier.fillMaxSize())
            if (grid) {
                Canvas(Modifier.fillMaxSize()) {
                    val c = Color(0x88FFFFFF)
                    drawLine(c, Offset(size.width / 3, 0f), Offset(size.width / 3, size.height), 1f)
                    drawLine(c, Offset(size.width * 2 / 3, 0f), Offset(size.width * 2 / 3, size.height), 1f)
                    drawLine(c, Offset(0f, size.height / 3), Offset(size.width, size.height / 3), 1f)
                    drawLine(c, Offset(0f, size.height * 2 / 3), Offset(size.width, size.height * 2 / 3), 1f)
                }
            }
            if (mode == Mode.DOC) {
                Canvas(Modifier.fillMaxSize()) {
                    val l = Offset(size.width * 0.08f, size.height * 0.1f)
                    val r = Offset(size.width * 0.92f, size.height * 0.9f)
                    val len = size.width * 0.1f
                    val c = Color.White
                    val sw = 6f
                    drawLine(c, l, Offset(l.x + len, l.y), strokeWidth = sw, cap = StrokeCap.Round)
                    drawLine(c, l, Offset(l.x, l.y + len), strokeWidth = sw, cap = StrokeCap.Round)
                    drawLine(c, Offset(r.x, l.y), Offset(r.x - len, l.y), strokeWidth = sw, cap = StrokeCap.Round)
                    drawLine(c, Offset(r.x, l.y), Offset(r.x, l.y + len), strokeWidth = sw, cap = StrokeCap.Round)
                    drawLine(c, Offset(l.x, r.y), Offset(l.x + len, r.y), strokeWidth = sw, cap = StrokeCap.Round)
                    drawLine(c, Offset(l.x, r.y), Offset(l.x, r.y - len), strokeWidth = sw, cap = StrokeCap.Round)
                    drawLine(c, r, Offset(r.x - len, r.y), strokeWidth = sw, cap = StrokeCap.Round)
                    drawLine(c, r, Offset(r.x, r.y - len), strokeWidth = sw, cap = StrokeCap.Round)
                }
            }
            if (mode == Mode.PANO) {
                Canvas(Modifier.fillMaxSize()) {
                    val y = size.height / 2
                    drawLine(Color.White.copy(alpha = 0.8f), Offset(size.width * 0.1f, y), Offset(size.width * 0.9f, y), strokeWidth = 4f, cap = StrokeCap.Round)
                    drawLine(Color.White.copy(alpha = 0.8f), Offset(size.width * 0.9f, y), Offset(size.width * 0.84f, y - 14f), strokeWidth = 4f, cap = StrokeCap.Round)
                    drawLine(Color.White.copy(alpha = 0.8f), Offset(size.width * 0.9f, y), Offset(size.width * 0.84f, y + 14f), strokeWidth = 4f, cap = StrokeCap.Round)
                }
            }
            Canvas(Modifier.fillMaxSize()) {
                val fv = focusAnimV.value
                val fp = focusPos
                if (fp != null && fv < 1f) {
                    val a = if (fv < 0.7f) 1f else 1f - (fv - 0.7f) / 0.3f
                    val r = 80f - 36f * minOf(1f, fv * 3f)
                    drawCircle(YELLOW.copy(alpha = a), radius = r, center = fp, style = Stroke(4f))
                }
            }
            if (countdown > 0) {
                Text("$countdown", Modifier.align(Alignment.Center), color = Color.White, fontSize = 96.sp)
            }
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = switchAlpha.value }.background(Color.Black))
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = shutterAnim.value }.background(Color.Black))

            val qv = qr
            if (qv != null && mode == Mode.PHOTO) {
                Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 66.dp)) {
                    QrPill(qv.label, {
                        if (qv.kind == 2 && qv.ssid != null) {
                            toast(connectWifi(ctx, qv.ssid, qv.pass, qv.wifiType, qv.hidden))
                        } else if (qv.kind == 1) {
                            try {
                                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(qv.text)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            } catch (e: Exception) {
                                toast("Can't open this link")
                            }
                        } else {
                            copyText(ctx, qv.text)
                            Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
                        }
                    }, {
                        qrDismissed = qv.text
                        qr = null
                    })
                }
            }

            if (showZoom && maxZoom - minZoom > 0.05f && mode != Mode.PANO) {
                ZoomOverlay(
                    zoom = { zoomShown.floatValue },
                    minZ = minZoom,
                    maxZ = maxZoom,
                    rulerTick = { rulerBump.intValue },
                    onDirect = { zoomDirect(it) },
                    onPreset = { zoomTarget.floatValue = it },
                    onBump = { rulerBump.intValue = rulerBump.intValue + 1 },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        AnimatedVisibility(
            visible = capturing || processing || holdStill,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
            enter = fadeIn(), exit = fadeOut()
        ) {
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(Color(0x66000000))
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (panoOn) {
                    Text("Pan slowly  ·  $panoStrips", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                } else if (holdStill && !capturing) {
                    Text("Hold still…", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                } else if (processing && !capturing) {
                    Text("Saving…", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                } else {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(if (paused) YELLOW else Color(0xFFE53935)))
                    Spacer(Modifier.width(8.dp))
                    RecTimer({ elapsed }, paused)
                    if (tlOn) {
                        Spacer(Modifier.width(10.dp))
                        Text("$tlCount frames", color = Color(0xCCFFFFFF), fontSize = 12.sp)
                    }
                    if (isRec && mode == Mode.VIDEO) {
                        Spacer(Modifier.width(8.dp))
                        PressBox({ togglePause() }, Modifier.size(32.dp)) {
                            Text(if (paused) "▶" else "❚❚", color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = !capturing,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn(), exit = fadeOut()
        ) {
            Crossfade(targetState = mode, animationSpec = tween(220), label = "topbar") { m ->
                Row(
                    Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically
                ) {
                    IconBox({ showSettings = true }, false) { GearIcon() }
                    when (m) {
                        Mode.PHOTO, Mode.PRO, Mode.PORTRAIT, Mode.NIGHT, Mode.DOC -> {
                            IconBox({ flash = (flash + 1) % 3 }, flash != 0) { FlashIcon(flash) }
                            IconBox({ timer = timerVals[(timerVals.indexOf(timer) + 1) % timerVals.size] }, timer != 0) { TimerIcon(timer) }
                            IconBox({ ratio43 = !ratio43 }, false) { TextIcon(if (ratio43) "3:4" else "9:16", false) }
                            IconBox({ sizeIdx = (sizeIdx + 1) % sizeNames.size }, false) { TextIcon(sizeNames[sizeIdx], false) }
                        }
                        Mode.VIDEO -> {
                            IconBox({ torch = !torch }, torch) { FlashIcon(if (torch) 2 else 0) }
                            IconBox({ vqIdx = (vqIdx + 1) % vqNames.size }, false) { TextIcon(vqNames[vqIdx], false) }
                            IconBox({ fpsIdx = (fpsIdx + 1) % fpsVals.size }, forceFps && fpsVals[fpsIdx] > 30) {
                                TextIcon("${fpsVals[fpsIdx]}fps", forceFps && fpsVals[fpsIdx] > 30)
                            }
                        }
                        Mode.SLOMO -> {
                            IconBox({ torch = !torch }, torch) { FlashIcon(if (torch) 2 else 0) }
                            IconBox({ slowIdx = (slowIdx + 1) % slowVals.size }, false) { TextIcon(slowNames[slowIdx] + " slower", false) }
                        }
                        Mode.TIMELAPSE -> {
                            IconBox({ torch = !torch }, torch) { FlashIcon(if (torch) 2 else 0) }
                            IconBox({ timer = timerVals[(timerVals.indexOf(timer) + 1) % timerVals.size] }, timer != 0) { TimerIcon(timer) }
                            IconBox({ ratio43 = !ratio43 }, false) { TextIcon(if (ratio43) "3:4" else "9:16", false) }
                            IconBox({ tlIdx = (tlIdx + 1) % tlVals.size }, false) { TextIcon("${tlVals[tlIdx]}s", false) }
                        }
                        Mode.PANO -> {
                        }
                    }
                }
            }
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 12.dp)
                .draggable(
                    state = modeDrag,
                    orientation = Orientation.Horizontal,
                    onDragStarted = { modeAnim?.cancel() },
                    onDragStopped = { v ->
                        if (capturing || countdown > 0) {
                            animateModePos(modeOrder.indexOf(mode))
                        } else {
                            val proj = modePos.floatValue - v / itemPx * 0.18f
                            val t = proj.roundToInt().coerceIn(0, modeOrder.size - 1)
                            selectMode(modeOrder[t])
                        }
                    }
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedVisibility(visible = mode == Mode.PRO && !capturing) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(16.dp)).background(Color(0xBB000000)).padding(10.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        wbNames.forEachIndexed { i, n ->
                            ModePill(n, proWb == i, Modifier.width(60.dp).height(32.dp)) { proWb = i }
                        }
                    }
                    val er = evRange
                    if (er != null && er.upper > er.lower) {
                        ProSlider("EV $ev", ev.toFloat(), er.lower.toFloat()..er.upper.toFloat()) { ev = it.toInt() }
                    }
                    if (ir != null && xr != null) {
                        Text(
                            "Exposure: ${if (proManualExp) "MANUAL" else "AUTO"}  (tap)",
                            color = YELLOW, fontSize = 12.sp,
                            modifier = Modifier.clickable { proManualExp = !proManualExp }.padding(vertical = 6.dp)
                        )
                        if (proManualExp) {
                            ProSlider("ISO $isoVal", proIsoF, 0f..1f) { proIsoF = it }
                            ProSlider(shutLabel(shutNs), proShutF, 0f..1f) { proShutF = it }
                        }
                    } else {
                        Text("Manual exposure: not offered by driver", color = Color(0x99FFFFFF), fontSize = 12.sp)
                    }
                    if (minFocus > 0f) {
                        Text(
                            "Focus: ${if (proManualFocus) "MANUAL" else "AUTO"}  (tap)",
                            color = YELLOW, fontSize = 12.sp,
                            modifier = Modifier.clickable { proManualFocus = !proManualFocus }.padding(vertical = 6.dp)
                        )
                        if (proManualFocus) ProSlider("Far / Near", proFocusF, 0f..1f) { proFocusF = it }
                    } else {
                        Text("Manual focus: not offered by driver", color = Color(0x99FFFFFF), fontSize = 12.sp)
                    }
                }
            }

            AnimatedVisibility(visible = !capturing) {
                ModeStrip({ modePos.floatValue }, modeOrder, itemW) { selectMode(it) }
            }
            Spacer(Modifier.height(10.dp))

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 36.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
            ) {
                if (capturing) {
                    if (mode == Mode.VIDEO || mode == Mode.SLOMO || mode == Mode.TIMELAPSE) {
                        PressBox({ torch = !torch; buzz() }, Modifier.size(52.dp), torch) { FlashIcon(if (torch) 2 else 0) }
                    } else {
                        Spacer(Modifier.size(52.dp))
                    }
                } else {
                    PressBox({ openGallery() }, Modifier.size(52.dp).border(2.dp, Color.White, CircleShape)) {
                        val t = thumb
                        if (t != null) {
                            Image(
                                bitmap = t.asImageBitmap(), contentDescription = null,
                                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(CircleShape)
                            )
                        } else {
                            Text("▣", color = Color.White, fontSize = 22.sp)
                        }
                    }
                }

                ShutterButton(mode == Mode.VIDEO || mode == Mode.SLOMO || mode == Mode.TIMELAPSE, capturing) { shutter() }

                if (capturing) {
                    if (isRec && mode == Mode.VIDEO && imageCapture != null) {
                        PressBox({ takePhoto() }, Modifier.size(52.dp).border(2.dp, Color.White, CircleShape)) {
                            Box(Modifier.size(36.dp).clip(CircleShape).background(Color.White))
                        }
                    } else {
                        Spacer(Modifier.size(52.dp))
                    }
                } else {
                    PressBox({
                        camChoice = 0
                        front = !front
                        buzz()
                    }, Modifier.size(52.dp)) { FlipIcon() }
                }
            }
        }

        if (screenFlash) {
            Box(Modifier.fillMaxSize().background(Color.White))
        }

        AnimatedVisibility(
            visible = showSettings,
            enter = slideInHorizontally(animationSpec = tween(260)) { it } + fadeIn(),
            exit = slideOutHorizontally(animationSpec = tween(220)) { it } + fadeOut()
        ) {
            Box(Modifier.fillMaxSize().background(Color(0xFF0B0B0D)).clickable { }) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        PressBox({ if (showInfo) showInfo = false else showSettings = false }, Modifier.size(44.dp)) {
                            Text("‹", color = Color.White, fontSize = 30.sp)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (showInfo) "Camera info" else "Camera settings",
                            color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold
                        )
                    }
                    if (showInfo) {
                        val report = remember { cameraReport(ctx) }
                        OptionRow("Run 60 fps test (rear camera)", if (probeRunning) "Running…" else "Start") {
                            val id = rearId
                            if (id == null) {
                                toast("No rear camera found")
                            } else if (!probeRunning) {
                                probeRunning = true
                                camPaused = true
                                probeResult = "Testing… keep the app open for about 10 seconds"
                                scope.launch {
                                    delay(800)
                                    val a = probeOnce(ctx, id, 30)
                                    delay(600)
                                    val b = probeOnce(ctx, id, 60)
                                    val verdict = if (b.fps >= 50.0) {
                                        "RESULT: the driver DOES deliver about 60 fps in raw Camera2. Tell me and I will build a direct 60 fps recorder."
                                    } else if (b.fps > 0.0) {
                                        "RESULT: the driver stays near ${b.fps.toInt()} fps even in raw Camera2. This is a ROM / camera HAL limit that an app cannot change."
                                    } else {
                                        "RESULT: the 60 fps test could not run (${b.msg})."
                                    }
                                    probeResult = "Asked for 30 fps: ${a.msg}\nAsked for 60 fps: ${b.msg}\n\n$verdict"
                                    camPaused = false
                                    probeRunning = false
                                }
                            }
                        }
                        if (probeResult.isNotEmpty()) {
                            Text(probeResult, color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
                        }
                        Text(report, color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Spacer(Modifier.height(12.dp))
                        OptionRow("Copy report", "") {
                            copyText(ctx, report + "\n" + probeResult)
                            Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        SectionTitle("Pictures")
                        OptionRow("Photo size", sizeNames[sizeIdx]) { sizeIdx = (sizeIdx + 1) % sizeNames.size }
                        OptionRow("Photo quality (JPEG)", jpegNames[jpegIdx]) { jpegIdx = (jpegIdx + 1) % jpegNames.size }
                        OptionRow("Portrait blur strength", blurNames[blurIdx]) { blurIdx = (blurIdx + 1) % blurNames.size }
                        SwitchRow("Document mode: black & white", "Off keeps color with a contrast boost", docBw) { docBw = it }
                        SwitchRow("Scan QR codes in Photo mode", "Show a button when a QR code is in view", qrScan) { qrScan = it }
                        SwitchRow("Save selfies as previewed", "Keep the mirror image of front camera photos", mirror) { mirror = it }
                        SwitchRow("Grid lines", "Show a 3x3 grid in the viewfinder", grid) { grid = it }
                        SwitchRow("Shutter flash animation", "Briefly dim the viewfinder when a photo is taken", flashAnim) { flashAnim = it }

                        SectionTitle("Videos")
                        OptionRow("Video resolution", vqNames[vqIdx]) { vqIdx = (vqIdx + 1) % vqNames.size }
                        OptionRow("Video frame rate", "${fpsVals[fpsIdx]} fps") { fpsIdx = (fpsIdx + 1) % fpsVals.size }
                        SwitchRow("Force 60 fps (experimental)", "Send a raw 60 fps request. Turn off if the preview freezes", forceFps) { forceFps = it }
                        OptionRow("Video bitrate", brNames[brIdx]) { brIdx = (brIdx + 1) % brNames.size }
                        SwitchRow("Record audio", "Use the microphone when recording videos", mic) { mic = it }
                        OptionRow("Slow motion speed", slowNames[slowIdx] + " slower") { slowIdx = (slowIdx + 1) % slowVals.size }
                        OptionRow("Time-lapse interval", "${tlVals[tlIdx]} s") { tlIdx = (tlIdx + 1) % tlVals.size }

                        SectionTitle("Useful features")
                        OptionRow("Volume keys", volNames[volMode]) { volMode = (volMode + 1) % volNames.size }
                        SwitchRow("Tap to focus", "Tap the viewfinder to focus", tapFocus) { tapFocus = it }
                        SwitchRow("Shutter sound", "Play a sound when taking photos and videos", shutterSound) { shutterSound = it }
                        SwitchRow("Vibration feedback", "Vibrate when pressing the shutter and changing modes", vibrate) { vibrate = it }
                        SwitchRow("Keep screen on", "Prevent the screen from turning off while the camera is open", keepOn) { keepOn = it }
                        SwitchRow("Show zoom controls", "Show the zoom pill and ruler in the viewfinder", showZoom) { showZoom = it }
                        SwitchRow("Start with front camera", "Open the app using the selfie camera", defFront) { defFront = it }
                        SwitchRow("Remember last mode", "Open in the mode you used last", rememberMode) { rememberMode = it }

                        SectionTitle("Storage")
                        OptionRow("Storage location", if (sdUri == null) "Phone" else "Chosen folder") {
                            Toast.makeText(ctx, "Open your SD card, pick or create a folder, then tap Use this folder", Toast.LENGTH_LONG).show()
                            pickFolder.launch(null)
                        }
                        if (sdUri != null) {
                            OptionRow("Back to phone storage", "Reset") {
                                prefs.edit().remove("tree").apply()
                                sdUri = null
                            }
                        }
                        OptionRow("Gallery button opens", if (usePhotos) "Google Photos" else "Default gallery") { usePhotos = !usePhotos }

                        SectionTitle("Camera engine")
                        OptionRow("Preview engine", engineNames[enginePref]) { enginePref = (enginePref + 1) % engineNames.size }
                        OptionRow("Capture priority", prioNames[prioIdx]) { prioIdx = (prioIdx + 1) % prioNames.size }
                        OptionRow("Camera ID", if (camChoice == 0) "Auto" else "ID ${camIds[camChoice - 1]}") {
                            if (!capturing) camChoice = (camChoice + 1) % (camIds.size + 1)
                        }
                        OptionRow("Camera info and 60 fps test", "Open") { showInfo = true }
                        Text(
                            "The camera driver (HAL) belongs to the ROM and can't be switched by an app. Preview engine, capture priority and camera ID change how this app talks to it.",
                            color = Color(0x99FFFFFF), fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp)
                        )

                        SectionTitle("Reset")
                        OptionRow("Reset settings", "Reset") { resetAll() }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun RecTimer(elapsed: () -> Long, paused: Boolean) {
    Text(
        fmtTime(elapsed()), color = if (paused) YELLOW else Color.White,
        fontSize = 18.sp, fontWeight = FontWeight.Bold
    )
}

@Composable
fun QrPill(label: String, onTap: () -> Unit, onClose: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(YELLOW).padding(start = 18.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label, color = Color.Black, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 210.dp).clickable { onTap() }.padding(vertical = 11.dp)
        )
        PressBox(onClose, Modifier.size(34.dp)) { Text("✕", color = Color.Black, fontSize = 15.sp) }
    }
}

@Composable
fun ZoomOverlay(
    zoom: () -> Float,
    minZ: Float,
    maxZ: Float,
    rulerTick: () -> Int,
    onDirect: (Float) -> Unit,
    onPreset: (Float) -> Unit,
    onBump: () -> Unit,
    modifier: Modifier = Modifier
) {
    var rulerVisible by remember { mutableStateOf(false) }
    val tick = rulerTick()
    LaunchedEffect(tick) {
        if (tick > 0) {
            rulerVisible = true
            delay(1700)
            rulerVisible = false
        }
    }
    val presets = remember(minZ, maxZ) {
        val l = ArrayList<Float>()
        if (minZ < 0.95f) l.add(minZ)
        l.add(1f)
        if (maxZ >= 1.95f) l.add(2f)
        if (maxZ >= 4.9f) l.add(5f)
        if (maxZ >= 9.9f) l.add(10f)
        l
    }
    val z = zoom()
    Box(modifier) {
        AnimatedVisibility(
            visible = rulerVisible,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(tween(120)), exit = fadeOut(tween(450))
        ) {
            Text(fmtZoom(z), color = Color.White, fontSize = 46.sp, fontWeight = FontWeight.Light)
        }
        Column(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (rulerVisible) {
                ZoomRuler(zoom, minZ, maxZ, onDirect, onBump)
            } else {
                var active = 0
                presets.forEachIndexed { i, p -> if (z >= p - 0.02f) active = i }
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(Color(0x66000000))
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(onDragStart = { onBump() }) { _, _ -> onBump() }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    presets.forEachIndexed { i, p ->
                        val sel = i == active
                        PressBox({ onPreset(p) }, Modifier.defaultMinSize(minWidth = 38.dp, minHeight = 38.dp), sel) {
                            Text(
                                if (sel) fmtZoom(z) else fmtPreset(p),
                                color = if (sel) YELLOW else Color.White, fontSize = 13.sp,
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ZoomRuler(zoom: () -> Float, minZ: Float, maxZ: Float, onDirect: (Float) -> Unit, onBump: () -> Unit) {
    val lnMin = ln(minZ)
    val lnMax = ln(maxZ)
    var widthPx by remember { mutableFloatStateOf(1f) }
    Canvas(
        Modifier.fillMaxWidth(0.7f).height(44.dp)
            .onSizeChanged { widthPx = it.width.toFloat() }
            .pointerInput(minZ, maxZ) {
                detectHorizontalDragGestures(onDragStart = { onBump() }) { change, dx ->
                    change.consume()
                    val next = ln(zoom()) + dx / widthPx * (lnMax - lnMin)
                    onDirect(exp(next).coerceIn(minZ, maxZ))
                }
            }
    ) {
        val w = size.width
        val h = size.height
        val n = 41
        for (i in 0 until n) {
            val x = w * i / (n - 1)
            val major = i % 5 == 0
            val half = if (major) 14f else 9f
            drawLine(
                Color.White.copy(alpha = if (major) 0.95f else 0.6f),
                Offset(x, h * 0.5f - half), Offset(x, h * 0.5f + half), strokeWidth = 3f
            )
        }
        val f = ((ln(zoom()) - lnMin) / (lnMax - lnMin)).coerceIn(0f, 1f)
        drawLine(YELLOW, Offset(w * f, h * 0.5f - 22f), Offset(w * f, h * 0.5f + 22f), strokeWidth = 7f, cap = StrokeCap.Round)
    }
}

@Composable
fun PressBox(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.86f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium), label = "press"
    )
    val bg by animateColorAsState(
        targetValue = if (pressed) Color(0x55FFFFFF) else if (selected) Color(0x38FFFFFF) else Color.Transparent,
        animationSpec = tween(150), label = "pillbg"
    )
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
fun IconBox(onClick: () -> Unit, selected: Boolean, content: @Composable BoxScope.() -> Unit) {
    PressBox(onClick, Modifier.defaultMinSize(minWidth = 44.dp, minHeight = 44.dp), selected, content)
}

@Composable
fun ModePill(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val bg by animateColorAsState(
        targetValue = if (selected) Color(0x40FFFFFF) else if (pressed) Color(0x22FFFFFF) else Color.Transparent,
        animationSpec = tween(220), label = "modebg"
    )
    val tc by animateColorAsState(
        targetValue = if (selected) YELLOW else Color(0xCCFFFFFF),
        animationSpec = tween(220), label = "modetxt"
    )
    val sc by animateFloatAsState(targetValue = if (pressed) 0.9f else 1f, label = "modescale")
    Box(
        modifier
            .graphicsLayer { scaleX = sc; scaleY = sc }
            .padding(vertical = 4.dp, horizontal = 4.dp)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label, color = tc, fontSize = 14.sp, maxLines = 1,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun ModeStrip(posProvider: () -> Float, order: List<Mode>, itemW: Dp, onTap: (Mode) -> Unit) {
    val pos = posProvider()
    val itemPx = with(LocalDensity.current) { itemW.toPx() }
    Box(Modifier.fillMaxWidth().height(44.dp).clipToBounds(), contentAlignment = Alignment.Center) {
        Box(Modifier.width(itemW).height(34.dp).clip(RoundedCornerShape(50)).background(Color(0x40FFFFFF)))
        order.forEachIndexed { i, m ->
            val d = i - pos
            val dist = abs(d)
            if (dist < 3.4f) {
                val sel = (1f - dist).coerceIn(0f, 1f)
                val tc = lerp(Color(0xCCFFFFFF), YELLOW, sel)
                val a = (1.15f - dist * 0.32f).coerceIn(0.25f, 1f)
                val sc = 0.92f + 0.08f * sel
                Box(
                    Modifier.width(itemW).height(44.dp)
                        .offset { IntOffset((d * itemPx).roundToInt(), 0) }
                        .graphicsLayer { alpha = a; scaleX = sc; scaleY = sc }
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onTap(m) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        m.label, color = tc, fontSize = 13.sp, maxLines = 1,
                        fontWeight = if (sel > 0.5f) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
fun ShutterButton(video: Boolean, capturing: Boolean, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val ringScale by animateFloatAsState(targetValue = if (pressed) 0.93f else 1f, label = "ring")
    val inner by animateDpAsState(
        targetValue = if (capturing) 30.dp else if (pressed) 52.dp else 62.dp,
        animationSpec = tween(160), label = "inner"
    )
    val corner by animateDpAsState(
        targetValue = if (capturing) 7.dp else 40.dp,
        animationSpec = tween(200), label = "corner"
    )
    val col by animateColorAsState(
        targetValue = if (video || capturing) Color(0xFFE53935) else Color.White,
        animationSpec = tween(200), label = "shutcol"
    )
    Box(
        Modifier.size(78.dp)
            .graphicsLayer { scaleX = ringScale; scaleY = ringScale }
            .clip(CircleShape)
            .border(4.dp, Color.White, CircleShape)
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.size(inner).clip(RoundedCornerShape(corner)).background(col))
    }
}

@Composable
fun GearIcon() {
    Canvas(Modifier.size(24.dp)) {
        val r = size.minDimension / 2
        val ctr = center
        val sw = r * 0.16f
        drawCircle(Color.White, radius = r * 0.36f, center = ctr, style = Stroke(sw))
        for (i in 0 until 8) {
            val a = Math.toRadians(i * 45.0)
            val dx = Math.cos(a).toFloat()
            val dy = Math.sin(a).toFloat()
            drawLine(
                Color.White,
                Offset(ctr.x + dx * r * 0.58f, ctr.y + dy * r * 0.58f),
                Offset(ctr.x + dx * r * 0.88f, ctr.y + dy * r * 0.88f),
                strokeWidth = sw * 1.6f, cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun FlashIcon(state: Int) {
    val col = if (state == 0) Color.White else YELLOW
    Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(24.dp)) {
            val w = size.width
            val h = size.height
            val p = Path()
            p.moveTo(w * 0.60f, h * 0.05f)
            p.lineTo(w * 0.22f, h * 0.56f)
            p.lineTo(w * 0.46f, h * 0.56f)
            p.lineTo(w * 0.38f, h * 0.95f)
            p.lineTo(w * 0.78f, h * 0.40f)
            p.lineTo(w * 0.54f, h * 0.40f)
            p.close()
            drawPath(p, col, style = Stroke(w * 0.08f))
            if (state == 0) {
                drawLine(Color.White, Offset(w * 0.1f, h * 0.9f), Offset(w * 0.9f, h * 0.1f), strokeWidth = w * 0.08f, cap = StrokeCap.Round)
            }
        }
        if (state == 1) {
            Text("A", color = col, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.BottomEnd))
        }
    }
}

@Composable
fun TimerIcon(seconds: Int) {
    val col = if (seconds == 0) Color.White else YELLOW
    Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(24.dp)) {
            val r = size.minDimension / 2
            val ctr = Offset(size.width / 2, size.height * 0.56f)
            val sw = r * 0.14f
            drawCircle(col, radius = r * 0.78f, center = ctr, style = Stroke(sw))
            drawLine(col, Offset(size.width * 0.4f, size.height * 0.06f), Offset(size.width * 0.6f, size.height * 0.06f), strokeWidth = sw, cap = StrokeCap.Round)
            drawLine(col, ctr, Offset(ctr.x + r * 0.3f, ctr.y - r * 0.35f), strokeWidth = sw, cap = StrokeCap.Round)
        }
        if (seconds > 0) {
            Text("$seconds", color = col, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.BottomEnd))
        }
    }
}

@Composable
fun TextIcon(label: String, active: Boolean) {
    val col = if (active) YELLOW else Color.White
    Box(Modifier.border(1.5.dp, col, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 3.dp)) {
        Text(label, color = col, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun FlipIcon() {
    Canvas(Modifier.size(28.dp)) {
        val r = size.minDimension * 0.34f
        val ctr = center
        val sw = size.minDimension * 0.08f
        val head = size.minDimension * 0.16f
        val topLeft = Offset(ctr.x - r, ctr.y - r)
        val arcSize = androidx.compose.ui.geometry.Size(r * 2, r * 2)
        for (k in 0..1) {
            val start = 200f + 180f * k
            val sweep = 130f
            drawArc(
                color = Color.White, startAngle = start, sweepAngle = sweep, useCenter = false,
                topLeft = topLeft, size = arcSize, style = Stroke(width = sw, cap = StrokeCap.Round)
            )
            val a = Math.toRadians((start + sweep).toDouble())
            val nx = Math.cos(a).toFloat()
            val ny = Math.sin(a).toFloat()
            val px = ctr.x + r * nx
            val py = ctr.y + r * ny
            val tx = -ny
            val ty = nx
            val path = Path()
            path.moveTo(px + tx * head, py + ty * head)
            path.lineTo(px + nx * head * 0.7f, py + ny * head * 0.7f)
            path.lineTo(px - nx * head * 0.7f, py - ny * head * 0.7f)
            path.close()
            drawPath(path, Color.White)
        }
    }
}

@Composable
fun ProSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 12.sp, modifier = Modifier.width(90.dp))
        Slider(value = value, onValueChange = onChange, valueRange = range, modifier = Modifier.weight(1f).height(28.dp))
    }
}

@Composable
fun SectionTitle(t: String) {
    Text(t, color = YELLOW, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 22.dp, bottom = 4.dp))
}

@Composable
fun OptionRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onClick() }.padding(vertical = 14.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Text(value, color = YELLOW, fontSize = 15.sp)
    }
}

@Composable
fun SwitchRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onChange(!checked) }.padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 16.sp)
            Text(sub, color = Color(0x99FFFFFF), fontSize = 12.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = YELLOW)
        )
    }
}
