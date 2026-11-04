@file:OptIn(
    androidx.camera.camera2.interop.ExperimentalCamera2Interop::class,
    androidx.camera.core.ExperimentalGetImage::class
)

package com.example.samcam

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
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
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

enum class Mode(val label: String) {
    PHOTO("PHOTO"), VIDEO("VIDEO"), PRO("PRO"), TIMELAPSE("TIME-LAPSE"), QR("QR SCAN")
}

object Bus {
    var tick by mutableIntStateOf(0)
    var enabled = true
}

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
val extraModes = listOf(Mode.PRO, Mode.TIMELAPSE, Mode.QR)

fun shutLabel(ns: Long): String =
    if (ns <= 0L) "-" else if (ns >= 100_000_000L) "%.1fs".format(ns / 1e9) else "1/${(1e9 / ns).toInt()}"

fun treeRoot(tree: Uri): Uri =
    DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))

fun docCreate(ctx: Context, parent: Uri, mime: String, name: String): Uri? {
    return try {
        DocumentsContract.createDocument(ctx.contentResolver, parent, mime, name)
    } catch (e: Exception) {
        null
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
                drawCircle(Color(0xFFFFC107), radius = size.minDimension / 2)
                drawCircle(Color(0xFF12151C), radius = size.minDimension * 0.36f)
                drawCircle(Color(0xFFFFC107), radius = size.minDimension * 0.2f)
            }
            Spacer(Modifier.height(16.dp))
            Text("SamCam", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
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
    val previewView = remember { PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val scanner = remember { BarcodeScanning.getClient() }
    val camIds = remember {
        try {
            (ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager).cameraIdList.toList()
        } catch (e: Exception) {
            emptyList<String>()
        }
    }
    val prefs = remember { ctx.getSharedPreferences("samcam", Context.MODE_PRIVATE) }

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
    var showMore by remember { mutableStateOf(false) }
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
    val busy = recording != null || tlJob != null
    val stills = mode == Mode.PHOTO || mode == Mode.PRO || mode == Mode.TIMELAPSE
    val ir = isoRange
    val xr = expRange
    val isoVal = if (ir != null) (ir.lower + proIsoF * (ir.upper - ir.lower)).toInt() else 0
    val shutNs: Long = if (xr != null) {
        val lo = xr.lower.toDouble().coerceAtLeast(1.0)
        val hi = minOf(xr.upper, 500_000_000L).toDouble().coerceAtLeast(lo)
        (lo * Math.pow(hi / lo, proShutF.toDouble())).toLong()
    } else 0L

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
                    analysis.setAnalyzer(exec) { proxy ->
                        val media = proxy.image
                        if (media == null) {
                            proxy.close()
                        } else {
                            val img = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                            scanner.process(img)
                                .addOnSuccessListener { list ->
                                    val v = list.firstOrNull()?.rawValue
                                    if (v != null && qr == null) qr = v
                                }
                                .addOnCompleteListener { proxy.close() }
                        }
                    }
                    cases.add(analysis)
                }
                else -> {
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
                        Toast.makeText(
                            ctx,
                            "${want}fps is not offered by this camera driver. Turn on Force 60fps in Settings to try anyway.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                isoRange = null
                expRange = null
                minFocus = 0f
                evRange = null
            }
        } catch (e: Exception) {
            if (camChoice > 0) {
                camChoice = 0
                Toast.makeText(ctx, "That camera ID did not open. Back to Auto.", Toast.LENGTH_LONG).show()
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
                Toast.makeText(ctx, "Safe mode (photo only): ${e.message}", Toast.LENGTH_LONG).show()
            } catch (e2: Exception) {
                Toast.makeText(ctx, "Camera error: ${e2.message}", Toast.LENGTH_LONG).show()
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
            Toast.makeText(ctx, "Camera options not accepted: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun stamp(): String = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())

    fun values(mime: String, dir: String): ContentValues {
        val cv = ContentValues()
        cv.put(MediaStore.MediaColumns.DISPLAY_NAME, stamp())
        cv.put(MediaStore.MediaColumns.MIME_TYPE, mime)
        cv.put(MediaStore.MediaColumns.RELATIVE_PATH, dir)
        return cv
    }

    fun takePhoto(dir: String, sdParent: Uri?, quiet: Boolean, onDone: () -> Unit) {
        val ic = imageCapture ?: return
        val meta = ImageCapture.Metadata()
        meta.isReversedHorizontal = front && mirror
        var stream: OutputStream? = null
        if (sdParent != null) {
            val doc = docCreate(ctx, sdParent, "image/jpeg", "${stamp()}.jpg")
            if (doc != null) {
                stream = try {
                    ctx.contentResolver.openOutputStream(doc)
                } catch (e: Exception) {
                    null
                }
            }
            if (stream == null && !quiet) {
                Toast.makeText(ctx, "SD folder not writable, saving to phone", Toast.LENGTH_LONG).show()
            }
        }
        val s = stream
        val builder = if (s != null) {
            ImageCapture.OutputFileOptions.Builder(s)
        } else {
            ImageCapture.OutputFileOptions.Builder(
                ctx.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values("image/jpeg", dir)
            )
        }
        ic.takePicture(builder.setMetadata(meta).build(), exec, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(r: ImageCapture.OutputFileResults) {
                try {
                    s?.close()
                } catch (e: Exception) {
                }
                if (!quiet) Toast.makeText(ctx, if (s != null) "Saved to SD folder" else "Saved to $dir", Toast.LENGTH_SHORT).show()
                onDone()
            }

            override fun onError(e: ImageCaptureException) {
                try {
                    s?.close()
                } catch (x: Exception) {
                }
                Toast.makeText(ctx, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        })
    }

    fun toggleRecord() {
        val active = recording
        if (active != null) {
            active.stop()
            recording = null
            return
        }
        val vc = videoCapture ?: return
        var pfd: ParcelFileDescriptor? = null
        val tree = sdUri
        if (tree != null) {
            val doc = docCreate(ctx, treeRoot(tree), "video/mp4", "${stamp()}.mp4")
            if (doc != null) {
                pfd = try {
                    ctx.contentResolver.openFileDescriptor(doc, "rw")
                } catch (e: Exception) {
                    null
                }
            }
            if (pfd == null) Toast.makeText(ctx, "SD folder not writable, saving to phone", Toast.LENGTH_LONG).show()
        }
        val fd = pfd
        var pending = if (fd != null) {
            vc.output.prepareRecording(ctx, FileDescriptorOutputOptions.Builder(fd).build())
        } else {
            vc.output.prepareRecording(
                ctx,
                MediaStoreOutputOptions.Builder(ctx.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
                    .setContentValues(values("video/mp4", "DCIM/SamCam")).build()
            )
        }
        if (mic) pending = pending.withAudioEnabled()
        recording = pending.start(exec) { evt ->
            if (evt is VideoRecordEvent.Finalize) {
                recording = null
                try {
                    fd?.close()
                } catch (e: Exception) {
                }
                Toast.makeText(ctx, if (evt.hasError()) "Video error ${evt.error}" else "Video saved", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun shutter() {
        scope.launch {
            if (mode == Mode.QR) return@launch
            if (mode == Mode.TIMELAPSE && tlJob != null) {
                tlJob?.cancel()
                tlJob = null
                Toast.makeText(ctx, "Time-lapse stopped: $tlCount frames saved", Toast.LENGTH_LONG).show()
                return@launch
            }
            if (timer > 0 && recording == null) {
                for (i in timer downTo 1) {
                    countdown = i
                    delay(1000)
                }
                countdown = 0
            }
            if (mode == Mode.VIDEO) {
                toggleRecord()
            } else if (mode == Mode.TIMELAPSE) {
                val name = "TL_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val tree = sdUri
                val sdDir: Uri? = if (tree != null) {
                    docCreate(ctx, treeRoot(tree), DocumentsContract.Document.MIME_TYPE_DIR, name)
                } else null
                tlCount = 0
                tlJob = scope.launch {
                    while (true) {
                        takePhoto("DCIM/SamCam/$name", sdDir, true) { tlCount = tlCount + 1 }
                        delay(tlVals[tlIdx] * 1000L)
                    }
                }
            } else {
                val tree = sdUri
                takePhoto("DCIM/SamCam", if (tree != null) treeRoot(tree) else null, false) {}
            }
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
                        val s = camera?.cameraInfo?.zoomState?.value
                        zoom = (zoom * z).coerceIn(s?.minZoomRatio ?: 1f, s?.maxZoomRatio ?: 8f)
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
            if (tlJob != null) {
                Text(
                    "● $tlCount frames", Modifier.align(Alignment.TopCenter).padding(8.dp),
                    color = Color(0xFFE53935), fontSize = 14.sp, fontWeight = FontWeight.Bold
                )
            }
            if (qr == null && mode != Mode.PRO) {
                Text(
                    "%.1fx".format(zoom),
                    Modifier.align(Alignment.BottomCenter).padding(8.dp)
                        .clip(RoundedCornerShape(50)).background(Color(0x66000000))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    color = Color.White, fontSize = 13.sp
                )
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

        Row(
            Modifier.align(Alignment.TopCenter).padding(top = 8.dp).clip(RoundedCornerShape(50))
                .background(Color(0x33FFFFFF)).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PillBtn("⚙", false) { showSettings = true }
            if (mode != Mode.QR) PillBtn(listOf("⚡off", "⚡auto", "⚡on")[flash], flash != 0) { flash = (flash + 1) % 3 }
            if (mode != Mode.QR) {
                PillBtn(if (timer == 0) "⏱" else "⏱${timer}s", timer != 0) {
                    timer = when (timer) {
                        0 -> 3
                        3 -> 10
                        else -> 0
                    }
                }
            }
            if (stills) PillBtn(if (ratio43) "3:4" else "9:16", false) { ratio43 = !ratio43 }
            if (stills) PillBtn(sizeNames[sizeIdx], false) { sizeIdx = (sizeIdx + 1) % sizeNames.size }
            if (mode == Mode.VIDEO) PillBtn(vqNames[vqIdx], false) { vqIdx = (vqIdx + 1) % vqNames.size }
            if (mode == Mode.VIDEO) {
                PillBtn("${fpsVals[fpsIdx]}fps", forceFps && fpsVals[fpsIdx] > 30) { fpsIdx = (fpsIdx + 1) % fpsVals.size }
            }
            if (mode == Mode.TIMELAPSE) PillBtn("${tlVals[tlIdx]}s", false) { tlIdx = (tlIdx + 1) % tlVals.size }
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (mode == Mode.PRO) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(16.dp)).background(Color(0xBB000000)).padding(10.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        wbNames.forEachIndexed { i, n ->
                            Text(
                                n, color = if (proWb == i) Color(0xFFFFC107) else Color.White, fontSize = 12.sp,
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
                            color = Color(0xFFFFC107), fontSize = 12.sp,
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
                            color = Color(0xFFFFC107), fontSize = 12.sp,
                            modifier = Modifier.clickable { proManualFocus = !proManualFocus }.padding(vertical = 6.dp)
                        )
                        if (proManualFocus) ProSlider("Far / Near", proFocusF, 0f..1f) { proFocusF = it }
                    } else {
                        Text("Manual focus: not offered by driver", color = Color(0x99FFFFFF), fontSize = 12.sp)
                    }
                }
            }

            if (showMore) {
                Row(
                    Modifier.padding(bottom = 10.dp).clip(RoundedCornerShape(50))
                        .background(Color(0x33FFFFFF)).padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    extraModes.forEach { m ->
                        PillBtn(m.label, mode == m) {
                            if (!busy) {
                                mode = m
                                showMore = false
                            }
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                ModeLabel("PHOTO", mode == Mode.PHOTO) {
                    if (!busy) {
                        mode = Mode.PHOTO
                        showMore = false
                    }
                }
                ModeLabel("VIDEO", mode == Mode.VIDEO) {
                    if (!busy) {
                        mode = Mode.VIDEO
                        showMore = false
                    }
                }
                if (mode in extraModes) ModeLabel(mode.label, true) {}
                ModeLabel("MORE", showMore) { showMore = !showMore }
            }
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 40.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(Color(0x44FFFFFF)).clickable {
                        try {
                            ctx.startActivity(
                                Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } catch (e: Exception) {
                            Toast.makeText(ctx, "No gallery app found", Toast.LENGTH_SHORT).show()
                        }
                    },
                    contentAlignment = Alignment.Center
                ) { Text("▣", color = Color.White, fontSize = 22.sp) }

                if (mode == Mode.QR) {
                    Spacer(Modifier.size(76.dp))
                } else {
                    val active = recording != null || tlJob != null
                    Box(
                        Modifier.size(76.dp).clip(CircleShape).border(4.dp, Color.White, CircleShape)
                            .clickable { shutter() },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier
                                .size(if (active) 30.dp else 60.dp)
                                .clip(if (active) RoundedCornerShape(6.dp) else CircleShape)
                                .background(if (mode == Mode.VIDEO || mode == Mode.TIMELAPSE) Color(0xFFE53935) else Color.White)
                        )
                    }
                }

                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(Color(0x44FFFFFF))
                        .clickable {
                            if (!busy) {
                                camChoice = 0
                                front = !front
                            }
                        },
                    contentAlignment = Alignment.Center
                ) { Text("⟳", color = Color.White, fontSize = 24.sp) }
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
                            if (!busy) camChoice = (camChoice + 1) % (camIds.size + 1)
                        }
                        SettingRow("Camera info (driver report)", "Open") { showInfo = true }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Tap a row to change it. Force 60fps sends a raw request that the driver may refuse; if the preview freezes, switch it off and reopen the app. Files saved to the SD card can take a moment to show in Gallery. Settings other than storage reset when the app closes.",
                            color = Color(0x99FFFFFF), fontSize = 13.sp
                        )
                    }
                }
            }
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
        Text(value, color = Color(0xFFFFC107), fontSize = 16.sp)
    }
}

@Composable
fun PillBtn(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        label, color = if (active) Color(0xFFFFC107) else Color.White, fontSize = 13.sp,
        modifier = Modifier.clickable { onClick() }.padding(horizontal = 9.dp, vertical = 10.dp)
    )
}

@Composable
fun TopBtn(label: String, onClick: () -> Unit) {
    Text(label, color = Color.White, fontSize = 13.sp, modifier = Modifier.clickable { onClick() }.padding(8.dp))
}

@Composable
fun ModeLabel(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label, color = if (selected) Color(0xFFFFC107) else Color(0xAAFFFFFF),
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, fontSize = 12.sp,
        modifier = Modifier.clickable { onClick() }.padding(6.dp)
    )
}
