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
import android.graphics.Rect
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Range
import android.util.Size
import android.view.KeyEvent
import android.view.Surface
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
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
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileDescriptorOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

enum class Mode(val label: String) {
    PRO("Pro"), PHOTO("Photo"), VIDEO("Video"), TIMELAPSE("Time-lapse"), QR("QR scan")
}

object Bus {
    var tick by mutableIntStateOf(0)
    var enabled = true
}

val YELLOW = Color(0xFFFFC107)
val sizeNames = listOf("Max", "8M", "3M")
val sizeLong = listOf(0, 3264, 2048)
val jpegNames = listOf("Max (100)", "High (95)", "Normal (85)")
val jpegVals = listOf(100, 95, 85)
val vqNames = listOf("4K", "1080p", "720p", "480p")
val vqVals = listOf(Quality.UHD, Quality.FHD, Quality.HD, Quality.SD)
val fpsVals = listOf(24, 30, 60)
val tlVals = listOf(1, 2, 5, 10)
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
        var idle = 0
        while (true) {
            val idx = codec.dequeueOutputBuffer(info, if (end) 10_000L else 0L)
            if (idx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!end) return
                idle++
                if (idle > 300) return
            } else if (idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                track = muxer.addTrack(codec.outputFormat)
                muxer.start()
                started = true
            } else if (idx >= 0) {
                idle = 0
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
        if (Bus.enabled && (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            if (event?.repeatCount == 0) Bus.tick++
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}

@Composable
fun Splash() {
    Box(Modifier.fillMaxSize().background(Color(0xFF12151C)), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(Modifier.size(120.dp)) {
                drawCircle(YELLOW, radius = size.minDimension / 2)
                drawCircle(Color(0xFF12151C), radius = size.minDimension * 0.36f)
                drawCircle(YELLOW, radius = size.minDimension * 0.2f)
            }
            Spacer(Modifier.height(16.dp))
            Text("Camera", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
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
    LaunchedEffect(Unit) { delay(1500); splash = false }
    if (ok) {
        Box(Modifier.fillMaxSize()) {
            CameraScreen()
            if (splash) Splash()
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
    val analysisExec = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember {
        PreviewView(ctx).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val scanner = remember { BarcodeScanning.getClient() }
    val camIds = remember {
        try {
            (ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager).cameraIdList.toList()
        } catch (e: Exception) {
            emptyList<String>()
        }
    }
    val prefs = remember { ctx.getSharedPreferences("samcam", Context.MODE_PRIVATE) }
    val modeOrder = remember { Mode.values().toList() }

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

    var mode by remember { mutableStateOf(Mode.PHOTO) }
    var front by remember { mutableStateOf(false) }
    var camChoice by remember { mutableIntStateOf(0) }
    var ratio43 by remember { mutableStateOf(true) }
    var flash by remember { mutableIntStateOf(0) }
    var timer by remember { mutableIntStateOf(0) }
    var countdown by remember { mutableIntStateOf(0) }
    var sizeIdx by remember { mutableIntStateOf(0) }
    var jpegIdx by remember { mutableIntStateOf(1) }
    var vqIdx by remember { mutableIntStateOf(1) }
    var fpsIdx by remember { mutableIntStateOf(1) }
    var tlIdx by remember { mutableIntStateOf(1) }
    var mic by remember { mutableStateOf(true) }
    var grid by remember { mutableStateOf(false) }
    var volShutter by remember { mutableStateOf(true) }
    var mirror by remember { mutableStateOf(true) }
    var forceFps by remember { mutableStateOf(false) }
    var usePhotos by remember { mutableStateOf(prefs.getBoolean("photos", true)) }
    var showSettings by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var qr by remember { mutableStateOf<String?>(null) }
    var tlJob by remember { mutableStateOf<Job?>(null) }
    var tlCount by remember { mutableIntStateOf(0) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var minZoom by remember { mutableFloatStateOf(1f) }
    var maxZoom by remember { mutableFloatStateOf(1f) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var lastUri by remember { mutableStateOf<Uri?>(prefs.getString("last_uri", null)?.let { Uri.parse(it) }) }
    var lastMime by remember { mutableStateOf(prefs.getString("last_mime", "image/jpeg") ?: "image/jpeg") }
    var thumb by remember { mutableStateOf<Bitmap?>(null) }

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

    val use43 = ratio43 && mode != Mode.VIDEO
    val isRec = recording != null
    val capturing = isRec || tlJob != null
    val ir = isoRange
    val xr = expRange
    val isoVal = if (ir != null) (ir.lower + proIsoF * (ir.upper - ir.lower)).toInt() else 0
    val shutNs: Long = if (xr != null) {
        val lo = xr.lower.toDouble().coerceAtLeast(1.0)
        val hi = minOf(xr.upper, 500_000_000L).toDouble().coerceAtLeast(lo)
        (lo * Math.pow(hi / lo, proShutF.toDouble())).toLong()
    } else 0L

    fun toast(s: String) {
        Toast.makeText(ctx, s, Toast.LENGTH_LONG).show()
    }

    fun setLast(u: Uri?, mime: String) {
        if (u != null) {
            lastUri = u
            lastMime = mime
            prefs.edit().putString("last_uri", u.toString()).putString("last_mime", mime).apply()
        }
    }

    fun stamp(): String = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())

    LaunchedEffect(lastUri) {
        val u = lastUri
        thumb = if (u == null) null else withContext(Dispatchers.IO) { loadThumb(ctx, u, lastMime) }
    }

    LaunchedEffect(capturing) {
        if (capturing) {
            val t0 = System.currentTimeMillis()
            while (true) {
                elapsed = (System.currentTimeMillis() - t0) / 1000
                delay(250)
            }
        } else {
            elapsed = 0L
        }
    }

    LaunchedEffect(mode, front, camChoice, ratio43, sizeIdx, jpegIdx, vqIdx, fpsIdx) {
        val provider = suspendCoroutine<ProcessCameraProvider> { c ->
            val f = ProcessCameraProvider.getInstance(ctx)
            f.addListener({ c.resume(f.get()) }, exec)
        }
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
        val preview = Preview.Builder().setResolutionSelector(previewRs).build()
        preview.setSurfaceProvider(previewView.surfaceProvider)

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
        provider.unbindAll()
        imageCapture = null
        videoCapture = null
        try {
            val cases = mutableListOf<UseCase>(preview)
            when (mode) {
                Mode.VIDEO -> {
                    val q = vqVals[vqIdx]
                    val rec = Recorder.Builder()
                        .setQualitySelector(QualitySelector.from(q, FallbackStrategy.lowerQualityOrHigherThan(q)))
                        .build()
                    val fps = fpsVals[fpsIdx]
                    val vcBuilder = VideoCapture.Builder(rec)
                    if (fps != 30) vcBuilder.setTargetFrameRate(Range(fps, fps))
                    val vc = vcBuilder.build()
                    cases.add(vc)
                    videoCapture = vc
                }
                Mode.QR -> {
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                    analysis.setAnalyzer(analysisExec) { proxy ->
                        try {
                            val bmp = proxy.toBitmap()
                            val img = InputImage.fromBitmap(bmp, proxy.imageInfo.rotationDegrees)
                            scanner.process(img)
                                .addOnSuccessListener { list ->
                                    val v = list.firstOrNull()?.rawValue
                                    if (v != null && qr == null) qr = v
                                }
                                .addOnCompleteListener { proxy.close() }
                        } catch (e: Exception) {
                            proxy.close()
                        }
                    }
                    cases.add(analysis)
                }
                Mode.TIMELAPSE -> {
                }
                Mode.PHOTO, Mode.PRO -> {
                    val ic = ImageCapture.Builder()
                        .setResolutionSelector(imageRs)
                        .setCaptureMode(
                            if (jpegIdx == 0) ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
                            else ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                        )
                        .setJpegQuality(jpegVals[jpegIdx])
                        .build()
                    cases.add(ic)
                    imageCapture = ic
                }
            }
            val cam = provider.bindToLifecycle(owner, selector, *cases.toTypedArray())
            camera = cam
            zoom = 1f
            ev = 0
            try {
                val c2i = Camera2CameraInfo.from(cam.cameraInfo)
                isoRange = c2i.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
                expRange = c2i.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
                minFocus = c2i.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
                evRange = cam.cameraInfo.exposureState.exposureCompensationRange
                if (mode == Mode.VIDEO && fpsVals[fpsIdx] > 30) {
                    val want = fpsVals[fpsIdx]
                    val ranges = c2i.getCameraCharacteristic(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
                    val offered = ranges != null && ranges.any { it.upper >= want }
                    if (!offered) {
                        toast("${want}fps is not offered by this camera driver. Turn on Force 60fps in Settings to try anyway.")
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
            maxZoom = minOf(zst?.maxZoomRatio ?: 1f, 8f)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
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
                toast("Safe mode (photo only): ${e.message}")
            } catch (e2: Exception) {
                toast("Camera error: ${e2.message}")
            }
        }
    }

    LaunchedEffect(flash, camera, imageCapture, mode) {
        val ic = imageCapture
        if (ic != null) {
            ic.flashMode = when (flash) {
                1 -> ImageCapture.FLASH_MODE_AUTO
                2 -> ImageCapture.FLASH_MODE_ON
                else -> ImageCapture.FLASH_MODE_OFF
            }
        }
        camera?.cameraControl?.enableTorch(mode == Mode.VIDEO && flash == 2)
    }
    LaunchedEffect(zoom, camera) { camera?.cameraControl?.setZoomRatio(zoom) }
    LaunchedEffect(ev, camera) {
        try {
            camera?.cameraControl?.setExposureCompensationIndex(ev)
        } catch (e: Exception) {
        }
    }

    LaunchedEffect(mode, camera, proWb, proManualExp, isoVal, shutNs, proManualFocus, proFocusF, minFocus, forceFps, fpsIdx) {
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
        cv.put(MediaStore.MediaColumns.DISPLAY_NAME, stamp())
        cv.put(MediaStore.MediaColumns.MIME_TYPE, mime)
        cv.put(MediaStore.MediaColumns.RELATIVE_PATH, dir)
        return cv
    }

    fun takePhoto() {
        val ic = imageCapture ?: return
        val meta = ImageCapture.Metadata()
        meta.isReversedHorizontal = front && mirror
        val tree = sdUri
        val doc: Uri? = if (tree != null) docCreate(ctx, treeRoot(tree), "image/jpeg", "${stamp()}.jpg") else null
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

    var recOut by remember { mutableStateOf<VideoOut?>(null) }

    fun toggleRecord() {
        val active = recording
        if (active != null) {
            active.stop()
            return
        }
        val vc = videoCapture ?: return
        val out = openVideoOut(ctx, sdUri, stamp(), "DCIM/SamCam")
        if (out == null) {
            toast("Could not create the video file")
            return
        }
        recOut = out
        var pending = vc.output.prepareRecording(ctx, FileDescriptorOutputOptions.Builder(out.pfd).build())
        if (mic) pending = pending.withAudioEnabled()
        recording = pending.start(exec) { evt ->
            if (evt is VideoRecordEvent.Finalize) {
                recording = null
                try {
                    out.pfd.close()
                } catch (e: Exception) {
                }
                if (evt.hasError()) toast("Video error ${evt.error}") else setLast(out.uri, "video/mp4")
            }
        }
    }

    fun startTimelapse() {
        tlCount = 0
        tlJob = scope.launch {
            var enc: TimelapseEncoder? = null
            var out: VideoOut? = null
            try {
                while (isActive) {
                    val bmp = previewView.bitmap
                    if (bmp != null) {
                        if (enc == null) {
                            val sc = minOf(1f, 1920f / maxOf(bmp.width, bmp.height))
                            val w = ((bmp.width * sc).toInt() / 2) * 2
                            val h = ((bmp.height * sc).toInt() / 2) * 2
                            val o = openVideoOut(ctx, sdUri, stamp() + "_TL", "DCIM/SamCam")
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
                val e2 = enc
                val o2 = out
                withContext(NonCancellable + Dispatchers.IO) {
                    if (e2 != null) e2.finish() else try { o2?.pfd?.close() } catch (x: Exception) {}
                }
                if (e2 != null && o2 != null && tlCount > 0) setLast(o2.uri, "video/mp4")
                tlJob = null
            }
        }
    }

    fun shutter() {
        scope.launch {
            if (mode == Mode.QR) return@launch
            if (mode == Mode.TIMELAPSE && tlJob != null) {
                tlJob?.cancel()
                return@launch
            }
            if (timer > 0 && recording == null) {
                for (i in timer downTo 1) {
                    countdown = i
                    delay(1000)
                }
                countdown = 0
            }
            when (mode) {
                Mode.VIDEO -> toggleRecord()
                Mode.TIMELAPSE -> startTimelapse()
                else -> takePhoto()
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

    fun selectMode(m: Mode) {
        if (!capturing) {
            qr = null
            mode = m
        }
    }

    SideEffect { Bus.enabled = volShutter }
    LaunchedEffect(Bus.tick) { if (Bus.tick > 0 && volShutter) shutter() }

    Box(Modifier.fillMaxSize().background(Color.Black).systemBarsPadding()) {
        Box(
            Modifier.align(Alignment.TopCenter).padding(top = 56.dp).fillMaxWidth()
                .aspectRatio(if (use43) 3f / 4f else 9f / 16f)
                .pointerInput(Unit) {
                    detectTapGestures { o ->
                        val p = previewView.meteringPointFactory.createPoint(o.x, o.y)
                        camera?.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(p).build())
                    }
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, z, _ ->
                        zoom = (zoom * z).coerceIn(minZoom, maxOf(minZoom, maxZoom))
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
            if (countdown > 0) {
                Text("$countdown", Modifier.align(Alignment.Center), color = Color.White, fontSize = 96.sp)
            }
            val qrText = qr
            if (qrText != null) {
                Column(
                    Modifier.align(Alignment.BottomCenter).padding(12.dp)
                        .clip(RoundedCornerShape(16.dp)).background(Color(0xDD000000)).padding(14.dp)
                ) {
                    Text(qrText, color = Color.White, fontSize = 14.sp, maxLines = 4)
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        TopBtn("Copy") {
                            (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                .setPrimaryClip(ClipData.newPlainText("qr", qrText))
                            Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
                        }
                        if (qrText.startsWith("http")) {
                            TopBtn("Open") {
                                try {
                                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(qrText)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                } catch (e: Exception) {
                                    Toast.makeText(ctx, "Can't open this link", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        TopBtn("Close") { qr = null }
                    }
                }
            }
        }

        if (capturing) {
            Row(
                Modifier.align(Alignment.TopCenter).padding(top = 12.dp)
                    .clip(RoundedCornerShape(50)).background(Color(0x66000000))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFE53935)))
                Spacer(Modifier.width(8.dp))
                Text(fmtTime(elapsed), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                if (tlJob != null) {
                    Spacer(Modifier.width(10.dp))
                    Text("$tlCount frames", color = Color(0xCCFFFFFF), fontSize = 12.sp)
                }
            }
        } else {
            Row(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically
            ) {
                IconBox({ showSettings = true }) { GearIcon() }
                if (mode != Mode.QR) IconBox({ flash = (flash + 1) % 3 }) { FlashIcon(flash) }
                if (mode != Mode.QR) {
                    IconBox({
                        timer = when (timer) {
                            0 -> 3
                            3 -> 10
                            else -> 0
                        }
                    }) { TimerIcon(timer) }
                }
                if (mode == Mode.PHOTO || mode == Mode.PRO || mode == Mode.TIMELAPSE) {
                    IconBox({ ratio43 = !ratio43 }) { TextIcon(if (ratio43) "3:4" else "9:16", false) }
                }
                if (mode == Mode.PHOTO || mode == Mode.PRO) {
                    IconBox({ sizeIdx = (sizeIdx + 1) % sizeNames.size }) { TextIcon(sizeNames[sizeIdx], false) }
                }
                if (mode == Mode.VIDEO) {
                    IconBox({ vqIdx = (vqIdx + 1) % vqNames.size }) { TextIcon(vqNames[vqIdx], false) }
                    IconBox({ fpsIdx = (fpsIdx + 1) % fpsVals.size }) {
                        TextIcon("${fpsVals[fpsIdx]}fps", forceFps && fpsVals[fpsIdx] > 30)
                    }
                }
                if (mode == Mode.TIMELAPSE) {
                    IconBox({ tlIdx = (tlIdx + 1) % tlVals.size }) { TextIcon("${tlVals[tlIdx]}s", false) }
                }
            }
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (mode == Mode.PRO && !capturing) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(16.dp)).background(Color(0xBB000000)).padding(10.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        wbNames.forEachIndexed { i, n ->
                            Text(
                                n, color = if (proWb == i) YELLOW else Color.White, fontSize = 12.sp,
                                modifier = Modifier.clickable { proWb = i }.padding(6.dp)
                            )
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

            if (maxZoom - minZoom > 0.05f) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 28.dp).padding(bottom = 6.dp)
                        .clip(RoundedCornerShape(50)).background(Color(0x55000000)).padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("%.1fx".format(zoom), color = Color.White, fontSize = 12.sp, modifier = Modifier.width(40.dp))
                    Slider(
                        value = zoom.coerceIn(minZoom, maxZoom),
                        onValueChange = { zoom = it },
                        valueRange = minZoom..maxZoom,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color(0x55FFFFFF)
                        ),
                        modifier = Modifier.weight(1f).height(28.dp)
                    )
                }
            }

            if (!capturing) {
                val idx = modeOrder.indexOf(mode)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    for (o in -1..1) {
                        val j = idx + o
                        Box(Modifier.width(110.dp), Alignment.Center) {
                            if (j >= 0 && j < modeOrder.size) {
                                val m = modeOrder[j]
                                ModeLabel(m.label, o == 0) { selectMode(m) }
                            }
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(36.dp))
            }
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 36.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
            ) {
                if (capturing) {
                    Spacer(Modifier.size(52.dp))
                } else {
                    Box(
                        Modifier.size(52.dp).clip(CircleShape).border(2.dp, Color.White, CircleShape)
                            .background(Color(0x44FFFFFF)).clickable { openGallery() },
                        contentAlignment = Alignment.Center
                    ) {
                        val t = thumb
                        if (t != null) {
                            Image(
                                bitmap = t.asImageBitmap(), contentDescription = null,
                                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text("▣", color = Color.White, fontSize = 22.sp)
                        }
                    }
                }

                if (mode == Mode.QR) {
                    Spacer(Modifier.size(78.dp))
                } else {
                    Box(
                        Modifier.size(78.dp).clip(CircleShape).border(4.dp, Color.White, CircleShape)
                            .clickable { shutter() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (capturing) {
                            Box(Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFFE53935)))
                        } else if (mode == Mode.VIDEO || mode == Mode.TIMELAPSE) {
                            Box(Modifier.size(58.dp).clip(CircleShape).background(Color(0xFFE53935)))
                        } else {
                            Box(Modifier.size(62.dp).clip(CircleShape).background(Color.White))
                        }
                    }
                }

                if (capturing) {
                    Spacer(Modifier.size(52.dp))
                } else {
                    Box(
                        Modifier.size(52.dp).clip(CircleShape).background(Color(0x44FFFFFF)).clickable {
                            camChoice = 0
                            front = !front
                        },
                        contentAlignment = Alignment.Center
                    ) { FlipIcon() }
                }
            }
        }

        if (showSettings) {
            Box(Modifier.fillMaxSize().background(Color(0xF2000000)).clickable { }) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            if (showInfo) "Camera info" else "Settings",
                            color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold
                        )
                        Text(
                            "✕", color = Color.White, fontSize = 24.sp,
                            modifier = Modifier.clickable {
                                if (showInfo) {
                                    showInfo = false
                                } else {
                                    showSettings = false
                                }
                            }.padding(4.dp)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    if (showInfo) {
                        val report = remember { cameraReport(ctx) }
                        Text(report, color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Spacer(Modifier.height(12.dp))
                        TopBtn("Copy report") {
                            (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                .setPrimaryClip(ClipData.newPlainText("camera", report))
                            Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        SettingRow("Storage", if (sdUri == null) "Phone" else "Chosen folder") {
                            Toast.makeText(ctx, "Open your SD card, pick or create a folder, then tap Use this folder", Toast.LENGTH_LONG).show()
                            pickFolder.launch(null)
                        }
                        if (sdUri != null) {
                            SettingRow("Back to phone storage", "Reset") {
                                prefs.edit().remove("tree").apply()
                                sdUri = null
                            }
                        }
                        SettingRow("Gallery button opens", if (usePhotos) "Google Photos" else "Default gallery") {
                            usePhotos = !usePhotos
                            prefs.edit().putBoolean("photos", usePhotos).apply()
                        }
                        SettingRow("Photo size", sizeNames[sizeIdx]) { sizeIdx = (sizeIdx + 1) % sizeNames.size }
                        SettingRow("Photo quality (JPEG)", jpegNames[jpegIdx]) { jpegIdx = (jpegIdx + 1) % jpegNames.size }
                        SettingRow("Video resolution", vqNames[vqIdx]) { vqIdx = (vqIdx + 1) % vqNames.size }
                        SettingRow("Video frame rate", "${fpsVals[fpsIdx]} fps") { fpsIdx = (fpsIdx + 1) % fpsVals.size }
                        SettingRow("Force 60fps (experimental)", if (forceFps) "On" else "Off") { forceFps = !forceFps }
                        SettingRow("Microphone", if (mic) "On" else "Off") { mic = !mic }
                        SettingRow("Time-lapse interval", "${tlVals[tlIdx]} s") { tlIdx = (tlIdx + 1) % tlVals.size }
                        SettingRow("Grid lines", if (grid) "On" else "Off") { grid = !grid }
                        SettingRow("Volume keys = shutter", if (volShutter) "On" else "Off") { volShutter = !volShutter }
                        SettingRow("Save selfies as previewed", if (mirror) "On" else "Off") { mirror = !mirror }
                        SettingRow("Camera", if (camChoice == 0) "Auto" else "ID ${camIds[camChoice - 1]}") {
                            if (!capturing) camChoice = (camChoice + 1) % (camIds.size + 1)
                        }
                        SettingRow("Camera info (driver report)", "Open") { showInfo = true }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Tap a row to change it. Force 60fps sends a raw request that the driver may refuse; if the preview freezes, switch it off and reopen the app. Time-lapse is built from preview frames at the interval you pick and plays back at 30 fps.",
                            color = Color(0x99FFFFFF), fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun IconBox(onClick: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier.defaultMinSize(44.dp, 44.dp).clip(CircleShape).clickable { onClick() },
        contentAlignment = Alignment.Center,
        content = content
    )
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
fun SettingRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.White, fontSize = 16.sp)
        Text(value, color = YELLOW, fontSize = 16.sp)
    }
}

@Composable
fun TopBtn(label: String, onClick: () -> Unit) {
    Text(label, color = Color.White, fontSize = 13.sp, modifier = Modifier.clickable { onClick() }.padding(8.dp))
}

@Composable
fun ModeLabel(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label, color = if (selected) YELLOW else Color(0xCCFFFFFF),
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, fontSize = 14.sp,
        modifier = Modifier.clickable { onClick() }.padding(8.dp)
    )
}
