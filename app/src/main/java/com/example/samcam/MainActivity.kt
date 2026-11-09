@file:OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)

package com.example.samcam

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas as GCanvas
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.MediaActionSound
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Range
import android.util.Rational
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
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.OutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt

class Attempt(val still: Boolean, val video: Boolean, val analysis: Boolean, val fpsReq: Boolean = true)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Integrity.ok(this)) {
            Toast.makeText(this, "This copy is not the original app. Please install the official version.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        ProcessCameraProvider.getInstance(applicationContext)
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
fun Root() {
    val ctx = LocalContext.current
    val need = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    val ask = if (Build.VERSION.SDK_INT >= 33) {
        arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    fun granted() = need.all { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }
    var ok by remember { mutableStateOf(granted()) }
    var splash by remember { mutableStateOf(true) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { ok = granted() }
    LaunchedEffect(Unit) { if (!ok) launcher.launch(ask) }
    val rootScope = rememberCoroutineScope()
    val t0 = remember { System.currentTimeMillis() }
    LaunchedEffect(Unit) { delay(2200); splash = false }
    if (ok) {
        Box(Modifier.fillMaxSize()) {
            CameraScreen {
                rootScope.launch {
                    val el = System.currentTimeMillis() - t0
                    if (el < 500) delay(500 - el)
                    splash = false
                }
            }
            AnimatedVisibility(visible = splash, enter = fadeIn(), exit = fadeOut(tween(250))) { Splash() }
        }
    } else {
        Box(Modifier.fillMaxSize().background(Color.Black), Alignment.Center) {
            Text("Camera and microphone permission needed", color = Color.White)
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun CameraScreen(onReady: () -> Unit) {
    val ctx = LocalContext.current
    val owner = ctx as ComponentActivity
    val scope = rememberCoroutineScope()
    val exec = remember { ContextCompat.getMainExecutor(ctx) }
    val bgExec = remember { Executors.newSingleThreadExecutor() }
    val analysisExec = remember { Executors.newSingleThreadExecutor() }
    val haptic = LocalHapticFeedback.current
    val sound = remember { MediaActionSound() }
    val sm = remember { ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val prefs = remember { ctx.getSharedPreferences("samcam", Context.MODE_PRIVATE) }
    val s = remember { AppSettings(prefs) }
    val previewView = remember {
        PreviewView(ctx).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        }
    }
    val scannerLazy = remember { lazy { BarcodeScanning.getClient() } }
    val labelerLazy = remember { lazy { ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS) } }
    val recLazy = remember { lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) } }
    val segLazy = remember {
        lazy { Segmentation.getClient(SelfieSegmenterOptions.Builder().setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE).build()) }
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
    val teleState = remember { mutableStateOf<TeleInfo?>(null) }
    val tele = teleState.value
    LaunchedEffect(Unit) { teleState.value = withContext(Dispatchers.IO) { detectTele(ctx) } }
    val modeOrder = remember { Mode.values().toList() }
    val itemW = 104.dp
    val itemPx = with(androidx.compose.ui.platform.LocalDensity.current) { itemW.toPx() }

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
        mutableStateOf(if (s.restoreMode) modeOrder.getOrElse(s.lastMode) { Mode.PHOTO } else Mode.PHOTO)
    }
    val modeNow = rememberUpdatedState(mode)
    val modePos = remember { mutableFloatStateOf(modeOrder.indexOf(mode).toFloat()) }
    var modeAnim by remember { mutableStateOf<Job?>(null) }
    var front by remember { mutableStateOf(s.defFront) }
    var lastFront by remember { mutableStateOf(front) }
    var camChoice by remember { mutableIntStateOf(0) }
    var lastCamChoice by remember { mutableIntStateOf(0) }
    var flash by remember { mutableIntStateOf(0) }
    var torch by remember { mutableStateOf(false) }
    var timer by remember { mutableIntStateOf(0) }
    var countdown by remember { mutableIntStateOf(0) }
    var holdStill by remember { mutableStateOf(false) }
    var qr by remember { mutableStateOf<QrResult?>(null) }
    var qrDismissed by remember { mutableStateOf<String?>(null) }
    var qrSeen by remember { mutableLongStateOf(0L) }
    var scene by remember { mutableStateOf<String?>(null) }
    var sceneSeen by remember { mutableLongStateOf(0L) }
    var docFound by remember { mutableStateOf(false) }
    var docSeen by remember { mutableLongStateOf(0L) }
    var scanCounter by remember { mutableIntStateOf(0) }
    val analyzeBusy = remember { AtomicBoolean(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var showGallery by remember { mutableStateOf(false) }
    var tlOn by remember { mutableStateOf(false) }
    var tlJob by remember { mutableStateOf<Job?>(null) }
    var tlCount by remember { mutableIntStateOf(0) }
    var panoOn by remember { mutableStateOf(false) }
    var panoStrips by remember { mutableIntStateOf(0) }
    var panoFov by remember { mutableFloatStateOf(0.9f) }
    var panoRight by remember { mutableStateOf(true) }
    var panoDrift by remember { mutableFloatStateOf(0f) }
    var panoThumb by remember { mutableStateOf<Bitmap?>(null) }
    var panoMsg by remember { mutableStateOf("") }
    var processing by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var paused by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var liveReady by remember { mutableStateOf(false) }
    var useTele by remember { mutableStateOf(false) }
    var teleBroken by remember { mutableStateOf(false) }
    var hdrDriver by remember { mutableStateOf(false) }
    var oisAvail by remember { mutableStateOf(false) }
    var eisAvail by remember { mutableStateOf(false) }
    var fpsSup by remember { mutableStateOf(booleanArrayOf(true, true, false)) }
    var aeLocked by remember { mutableStateOf(false) }
    var focusUiOn by remember { mutableStateOf(false) }
    var evTouch by remember { mutableIntStateOf(0) }
    var showGalleryPicker by remember { mutableStateOf(false) }
    var galleryApps by remember { mutableStateOf<List<GalleryApp>?>(null) }
    val zoomTarget = remember { mutableFloatStateOf(1f) }
    val zoomShown = remember { mutableFloatStateOf(1f) }
    val rulerBump = remember { mutableIntStateOf(0) }
    var zoomFast by remember { mutableStateOf(false) }
    var rulerShown by remember { mutableStateOf(false) }
    var minZoom by remember { mutableFloatStateOf(1f) }
    var maxZoom by remember { mutableFloatStateOf(1f) }
    var camMin by remember { mutableFloatStateOf(1f) }
    var camMax by remember { mutableFloatStateOf(1f) }
    var camBase by remember { mutableFloatStateOf(1f) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var accMs by remember { mutableLongStateOf(0L) }
    var lastUri by remember { mutableStateOf<Uri?>(prefs.getString("last_uri", null)?.let { Uri.parse(it) }) }
    var lastMime by remember { mutableStateOf(prefs.getString("last_mime", "image/jpeg") ?: "image/jpeg") }
    var thumb by remember { mutableStateOf<Bitmap?>(null) }
    var focusPos by remember { mutableStateOf<Offset?>(null) }
    var focusTick by remember { mutableIntStateOf(0) }
    val focusAnimV = remember { Animatable(1f) }
    val shutterAnim = remember { Animatable(0f) }
    var screenFlash by remember { mutableStateOf(false) }
    var camPaused by remember { mutableStateOf(false) }
    var probeRunning by remember { mutableStateOf(false) }
    var probeResult by remember { mutableStateOf("") }
    var nightFps by remember { mutableStateOf<Range<Int>?>(null) }
    var streaming by remember { mutableStateOf(false) }
    var screenW by remember { mutableIntStateOf(0) }
    var screenH by remember { mutableIntStateOf(0) }

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

    val isRec = recording != null
    val capturing = isRec || tlOn || panoOn
    val capturingNow = rememberUpdatedState(capturing)
    val bindCat = when (mode) {
        Mode.VIDEO -> "v"
        Mode.SLOMO -> "s"
        Mode.TIMELAPSE -> "t"
        Mode.PANO -> "p"
        Mode.ULTRA -> "u"
        else -> "i"
    }
    val isStillCat = bindCat == "i" || bindCat == "u"
    val usesRatio = isStillCat || bindCat == "t" || bindCat == "v" || bindCat == "s"
    val ratioKey = if (isStillCat) s.ratioIdx else if (bindCat == "v" || bindCat == "s") s.vRatioIdx else -1
    val capSize = if (bindCat == "u") 0 else s.sizeIdx
    val maxQ = mode == Mode.NIGHT || mode == Mode.ULTRA || s.prioIdx == 0 || s.jpegIdx == 0
    val liveWanted = s.live && mode == Mode.PHOTO
    val analysisWanted = isStillCat && ((mode == Mode.PHOTO && s.qrScan) || s.scenePill || mode == Mode.DOC)
    val fullAr = if (screenH > 0) screenW.toFloat() / screenH else 0.5f
    val ratioIdxNow = if (bindCat == "v" || bindCat == "s") s.vRatioIdx else s.ratioIdx
    val arTarget = if (usesRatio) ratioAr(ratioIdxNow, fullAr) else 0.5625f
    val isFull = usesRatio && ratios[ratioIdxNow].w == 0
    val arAnim by animateFloatAsState(targetValue = arTarget, animationSpec = tween(280), label = "ar")

    val ir = isoRange
    val xr = expRange
    val isoVal = if (ir != null) (ir.lower + proIsoF * (ir.upper - ir.lower)).toInt() else 0
    val shutNs: Long = if (xr != null) {
        val lo = xr.lower.toDouble().coerceAtLeast(1.0)
        val hi = minOf(xr.upper, 500_000_000L).toDouble().coerceAtLeast(lo)
        (lo * Math.pow(hi / lo, proShutF.toDouble())).toLong()
    } else 0L

    fun toast(m: String) {
        Toast.makeText(ctx, m, Toast.LENGTH_LONG).show()
    }

    fun buzz() {
        if (s.vibe) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun snd(kind: Int) {
        if (s.sound) {
            try {
                sound.play(kind)
            } catch (e: Exception) {
            }
        }
    }

    fun setLast(u: Uri?, m: String) {
        if (u != null) {
            lastUri = u
            lastMime = m
            prefs.edit().putString("last_uri", u.toString()).putString("last_mime", m).apply()
        }
    }

    fun bump() {
        rulerBump.intValue = rulerBump.intValue + 1
    }

    fun setZoom(z: Float, fast: Boolean = false) {
        zoomFast = fast
        zoomTarget.floatValue = z.coerceIn(minZoom, maxOf(minZoom, maxZoom))
    }

    DisposableEffect(Unit) {
        val obs = Observer<PreviewView.StreamState> { st ->
            streaming = st == PreviewView.StreamState.STREAMING
            if (streaming) onReady()
        }
        previewView.previewStreamState.observe(owner, obs)
        onDispose { previewView.previewStreamState.removeObserver(obs) }
    }
    val coverAlpha by animateFloatAsState(
        targetValue = if (streaming) 0f else 1f,
        animationSpec = tween(if (streaming) 260 else 60), label = "cover"
    )

    SideEffect { Bus.volMode = s.volMode }

    LaunchedEffect(mode) { s.lastMode = modeOrder.indexOf(mode) }

    LaunchedEffect(s.keepOn) {
        if (s.keepOn) owner.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
            focusAnimV.animateTo(1f, tween(260))
        }
    }

    LaunchedEffect(focusTick, evTouch, aeLocked) {
        if (focusTick > 0) {
            focusUiOn = true
            if (!aeLocked) {
                delay(3500)
                focusUiOn = false
            }
        }
    }

    LaunchedEffect(showGalleryPicker) {
        if (showGalleryPicker && galleryApps == null) {
            galleryApps = withContext(Dispatchers.IO) { listGalleryApps(ctx) }
        }
    }

    LaunchedEffect(rulerBump.intValue) {
        if (rulerBump.intValue > 0) {
            rulerShown = true
            delay(1700)
            rulerShown = false
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(400)
            val now = System.currentTimeMillis()
            if (qr != null && now - qrSeen > 2200) qr = null
            if (now - qrSeen > 3000) qrDismissed = null
            if (scene != null && now - sceneSeen > 3500) scene = null
            if (docFound && now - docSeen > 2500) docFound = false
        }
    }

    LaunchedEffect(Unit) {
        var lastIdx = modePos.floatValue.roundToInt()
        snapshotFlow { modePos.floatValue.roundToInt() }.collect { i ->
            if (i != lastIdx) {
                lastIdx = i
                if (s.vibe) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }

    LaunchedEffect(camera, camBase) {
        val cam = camera ?: return@LaunchedEffect
        fun applyZoom(z: Float) {
            try {
                cam.cameraControl.setZoomRatio((z / camBase).coerceIn(camMin, maxOf(camMin, camMax)))
            } catch (e: Exception) {
            }
        }
        applyZoom(zoomShown.floatValue)
        snapshotFlow { zoomTarget.floatValue }.collectLatest { target ->
            var last = 0L
            while (true) {
                val cur = zoomShown.floatValue
                if (abs(ln(target) - ln(cur)) < 0.0015f) {
                    zoomShown.floatValue = target
                    applyZoom(target)
                    break
                }
                val t = androidx.compose.runtime.withFrameNanos { it }
                val dt = if (last == 0L) 0.016f else ((t - last) / 1e9f).coerceIn(0.004f, 0.08f)
                last = t
                val k = 1f - exp(-dt / (if (zoomFast) 0.03f else 0.075f))
                val next = exp(ln(cur) + (ln(target) - ln(cur)) * k)
                zoomShown.floatValue = next
                applyZoom(next)
            }
        }
    }

    LaunchedEffect(tele, s.tele, front, camChoice, teleBroken) {
        val t = tele ?: return@LaunchedEffect
        if (!s.tele || front || camChoice > 0 || teleBroken) {
            if (useTele) useTele = false
            return@LaunchedEffect
        }
        snapshotFlow { zoomTarget.floatValue }.collect { z ->
            if (!capturingNow.value) {
                if (!useTele && z >= t.factor) useTele = true
                else if (useTele && z < t.factor * 0.94f) useTele = false
            }
        }
    }

    LaunchedEffect(Bus.zoomTick) {
        if (Bus.zoomTick > 0) {
            val f = if (Bus.zoomUp) 1.15f else 1f / 1.15f
            setZoom(zoomTarget.floatValue * f)
            bump()
        }
    }

    fun runAnalysis(b: Bitmap, rot: Int) {
        val m = modeNow.value
        analyzeBusy.set(true)
        val img = InputImage.fromBitmap(b, rot)
        if (m == Mode.DOC) {
            recLazy.value.process(img)
                .addOnSuccessListener { t ->
                    var lines = 0
                    for (blk in t.textBlocks) lines += blk.lines.size
                    if (lines >= 4) {
                        docFound = true
                        docSeen = System.currentTimeMillis()
                    }
                }
                .addOnCompleteListener { analyzeBusy.set(false) }
            return
        }
        scanCounter = scanCounter + 1
        val qrOn = m == Mode.PHOTO && s.qrScan
        val sceneOn = s.scenePill
        val doQr = qrOn && (!sceneOn || scanCounter % 2 == 0)
        if (doQr) {
            scannerLazy.value.process(img)
                .addOnSuccessListener { list ->
                    val bc = list.firstOrNull()
                    if (bc != null) {
                        val raw = bc.rawValue ?: bc.displayValue ?: ""
                        if (raw.isNotEmpty()) {
                            qrSeen = System.currentTimeMillis()
                            if (raw != qrDismissed && qr?.text != raw) {
                                qr = makeQr(bc, raw)
                                buzz()
                            }
                        }
                    }
                }
                .addOnCompleteListener { analyzeBusy.set(false) }
        } else if (sceneOn) {
            labelerLazy.value.process(img)
                .addOnSuccessListener { labels ->
                    val best = labels.filter { it.confidence > 0.62f }.maxByOrNull { it.confidence }
                    if (best != null) {
                        scene = best.text
                        sceneSeen = System.currentTimeMillis()
                    }
                }
                .addOnCompleteListener { analyzeBusy.set(false) }
        } else {
            analyzeBusy.set(false)
        }
    }

    LaunchedEffect(
        bindCat, front, camChoice, useTele, ratioKey, capSize, maxQ, liveWanted, analysisWanted,
        s.vqIdx, s.fpsIdx, s.brIdx, camPaused, s.engine, screenW
    ) {
        if (screenW == 0) return@LaunchedEffect
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
            if (bindCat == "p" || bindCat == "t" || s.engine == 1) PreviewView.ImplementationMode.COMPATIBLE
            else PreviewView.ImplementationMode.PERFORMANCE

        val stillCat = bindCat == "i" || bindCat == "u"
        val ratioNow = if (ratioKey >= 0) ratioAr(ratioKey, screenW.toFloat() / screenH) else 0.5625f
        val sensor169 = !stillCat || ratioNow < 0.62f
        val aspect = if (sensor169) AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY else AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
        val previewRs = ResolutionSelector.Builder().setAspectRatioStrategy(aspect).build()
        val longSide = sizeLong[capSize]
        val irsb = ResolutionSelector.Builder().setAspectRatioStrategy(aspect)
        if (longSide > 0) {
            irsb.setResolutionStrategy(
                ResolutionStrategy(
                    Size(longSide, if (sensor169) longSide * 9 / 16 else longSide * 3 / 4),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                )
            )
        }
        val imageRs = irsb.build()
        val anaRs = ResolutionSelector.Builder().setAspectRatioStrategy(aspect)
            .setResolutionStrategy(
                ResolutionStrategy(
                    if (sensor169) Size(1280, 720) else Size(960, 720),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                )
            ).build()

        val wantId: String? = when {
            camChoice > 0 && camChoice <= camIds.size -> camIds[camChoice - 1]
            !front && tele != null && s.tele && !teleBroken -> if (useTele) tele.teleId else tele.wideId
            else -> null
        }
        val selector: CameraSelector = if (wantId != null) {
            CameraSelector.Builder().addCameraFilter { infos ->
                infos.filter { Camera2CameraInfo.from(it).cameraId == wantId }
            }.build()
        } else if (front) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }

        val attempts = ArrayList<Attempt>()
        when (bindCat) {
            "i", "u" -> {
                attempts.add(Attempt(true, liveWanted, analysisWanted))
                if (analysisWanted) attempts.add(Attempt(true, liveWanted, false))
                if (liveWanted) attempts.add(Attempt(true, false, analysisWanted))
                if (liveWanted && analysisWanted) attempts.add(Attempt(true, false, false))
            }
            "v", "s" -> {
                attempts.add(Attempt(true, true, false))
                attempts.add(Attempt(false, true, false))
                if (bindCat == "s" || fpsVals[s.fpsIdx] != 30) {
                    attempts.add(Attempt(true, true, false, false))
                    attempts.add(Attempt(false, true, false, false))
                }
            }
            else -> attempts.add(Attempt(false, false, false))
        }

        var bound: Camera? = null
        var boundAt: Attempt? = null
        for (at in attempts) {
            try {
                provider.unbindAll()
                imageCapture = null
                videoCapture = null
                val preview = Preview.Builder().setResolutionSelector(previewRs).build()
                preview.setSurfaceProvider(previewView.surfaceProvider)
                val group = UseCaseGroup.Builder()
                group.addUseCase(preview)
                var ic: ImageCapture? = null
                var vc: VideoCapture<Recorder>? = null
                if (at.video) {
                    val q = if (bindCat == "v") vqVals[s.vqIdx] else Quality.HD
                    val rb = Recorder.Builder().setQualitySelector(QualitySelector.from(q, FallbackStrategy.lowerQualityOrHigherThan(q)))
                    val vb = brVals[s.brIdx]
                    if (vb > 0 && bindCat == "v") rb.setTargetVideoEncodingBitRate(vb)
                    val vcb = VideoCapture.Builder(rb.build())
                    if (bindCat == "s" && at.fpsReq) {
                        vcb.setTargetFrameRate(Range(120, 240))
                    } else if (bindCat == "v" && at.fpsReq && fpsVals[s.fpsIdx] != 30) {
                        vcb.setTargetFrameRate(Range(fpsVals[s.fpsIdx], fpsVals[s.fpsIdx]))
                    }
                    val built = vcb.build()
                    group.addUseCase(built)
                    vc = built
                }
                if (at.analysis) {
                    val analysis = ImageAnalysis.Builder()
                        .setResolutionSelector(anaRs)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                    var lastScan = 0L
                    analysis.setAnalyzer(analysisExec) { proxy ->
                        val now = System.currentTimeMillis()
                        if (now - lastScan < 650 || analyzeBusy.get()) {
                            proxy.close()
                        } else {
                            lastScan = now
                            var bmp: Bitmap? = null
                            var rot = 0
                            try {
                                bmp = proxy.toBitmap()
                                rot = proxy.imageInfo.rotationDegrees
                            } catch (e: Exception) {
                            }
                            proxy.close()
                            val b = bmp
                            if (b != null) runAnalysis(b, rot)
                        }
                    }
                    group.addUseCase(analysis)
                }
                if (at.still) {
                    val built = ImageCapture.Builder()
                        .setResolutionSelector(imageRs)
                        .setCaptureMode(if (maxQ) ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY else ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setJpegQuality(if (bindCat == "u") 100 else jpegVals[s.jpegIdx])
                        .build()
                    group.addUseCase(built)
                    ic = built
                }
                if (ratioKey >= 0) {
                    val rat = if (ratios[ratioKey].w == 0) Rational(screenW, screenH) else Rational(ratios[ratioKey].w, ratios[ratioKey].h)
                    group.setViewPort(ViewPort.Builder(rat, Surface.ROTATION_0).build())
                }
                bound = provider.bindToLifecycle(owner, selector, group.build())
                boundAt = at
                imageCapture = ic
                videoCapture = vc
                break
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }

        val cam = bound
        if (cam == null) {
            if (useTele) {
                teleBroken = true
                useTele = false
                toast("The telephoto lens isn't available to apps on this ROM. Using digital zoom.")
                return@LaunchedEffect
            }
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
        aeLocked = false
        focusUiOn = false
        liveReady = boundAt?.video == true && stillCat
        if (liveWanted && !liveReady) toast("Live photo isn't supported by this camera driver (it needs photo and video at once).")
        if ((bindCat == "v" || bindCat == "s") && boundAt?.still == false) {
            toast("Taking photos while recording isn't supported by this camera driver")
        }
        if ((bindCat == "v" || bindCat == "s") && boundAt?.fpsReq == false) {
            toast(if (bindCat == "v") "That frame rate isn't available with these settings. Recording at the default rate." else "Real slow motion isn't available on this camera. The video is stretched instead.")
        }
        ev = 0
        delay(250)
        val zst = cam.cameraInfo.zoomState.value
        camMin = zst?.minZoomRatio ?: 1f
        camMax = zst?.maxZoomRatio ?: 1f
        camBase = if (useTele && tele != null) tele.factor else 1f
        if (front != lastFront || camChoice != lastCamChoice) {
            lastFront = front
            lastCamChoice = camChoice
            zoomTarget.floatValue = 1f
            zoomShown.floatValue = 1f
        }
        zoomTarget.floatValue = zoomTarget.floatValue.coerceIn(minZoom, maxOf(minZoom, maxZoom))
        zoomShown.floatValue = zoomShown.floatValue.coerceIn(minZoom, maxOf(minZoom, maxZoom))
        try {
            val c2i = Camera2CameraInfo.from(cam.cameraInfo)
            isoRange = c2i.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
            expRange = c2i.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
            minFocus = c2i.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
            evRange = cam.cameraInfo.exposureState.exposureCompensationRange
            val ranges = c2i.getCameraCharacteristic(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
            nightFps = ranges?.filter { it.upper >= 8 }?.minByOrNull { it.upper * 1000 + it.lower }
            val scenes = c2i.getCameraCharacteristic(CameraCharacteristics.CONTROL_AVAILABLE_SCENE_MODES)
            hdrDriver = scenes != null && scenes.contains(CaptureRequest.CONTROL_SCENE_MODE_HDR)
            val oisModes = c2i.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)
            oisAvail = oisModes != null && oisModes.contains(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON)
            val eisModes = c2i.getCameraCharacteristic(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES)
            eisAvail = eisModes != null && eisModes.contains(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON)
            val ps = c2i.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
            val fl = c2i.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull()
            if (ps != null && fl != null && fl > 0f) {
                panoFov = (2.0 * atan(minOf(ps.width, ps.height) / (2.0 * fl))).toFloat()
            }
            val has24 = ranges == null || ranges.any { it.lower <= 24 && it.upper >= 24 }
            val has60 = ranges != null && ranges.any { it.upper >= 60 }
            fpsSup = booleanArrayOf(has24, true, has60)
            if (bindCat == "v" && !fpsSup[s.fpsIdx] && !(s.forceFps && s.fpsIdx == 2)) {
                val wantF = fpsVals[s.fpsIdx]
                s.fpsIdx = 1
                toast("${wantF}fps isn't supported by this camera, so 30 fps is used.")
            }
        } catch (e: Exception) {
            isoRange = null
            expRange = null
            minFocus = 0f
            evRange = null
        }
    }

    LaunchedEffect(tele, s.tele, teleBroken, front, camChoice, camMin, camMax, camBase) {
        minZoom = camMin * camBase
        maxZoom = if (tele != null && s.tele && !teleBroken && !front && camChoice == 0) 10f else minOf(camMax * camBase, 10f)
        zoomTarget.floatValue = zoomTarget.floatValue.coerceIn(minZoom, maxOf(minZoom, maxZoom))
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
            val base = if (mode == Mode.NIGHT) ((evRange?.upper ?: 0) * 2 / 3) else 0
            val er2 = evRange
            val raw = base + ev
            val idx = if (er2 != null) raw.coerceIn(er2.lower, er2.upper) else raw
            camera?.cameraControl?.setExposureCompensationIndex(idx)
        } catch (e: Exception) {
        }
    }

    LaunchedEffect(
        mode, camera, proWb, proManualExp, isoVal, shutNs, proManualFocus, proFocusF, minFocus,
        s.forceFps, s.fpsIdx, nightFps, s.hdr, s.denoise, hdrDriver, s.ois, s.eis, oisAvail, eisAvail, s.hdrMode, aeLocked
    ) {
        val cam = camera ?: return@LaunchedEffect
        try {
            val c2 = Camera2CameraControl.from(cam.cameraControl)
            val info = Camera2CameraInfo.from(cam.cameraInfo)
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
                any = true
            }
            if (mode in stillModes && (s.denoise || mode == Mode.NIGHT || mode == Mode.ULTRA)) {
                val nr = info.getCameraCharacteristic(CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES)
                if (nr != null && nr.contains(CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY)) {
                    b.setCaptureRequestOption(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY)
                    any = true
                }
                val ed = info.getCameraCharacteristic(CameraCharacteristics.EDGE_AVAILABLE_EDGE_MODES)
                if (ed != null && ed.contains(CaptureRequest.EDGE_MODE_HIGH_QUALITY)) {
                    b.setCaptureRequestOption(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_HIGH_QUALITY)
                    any = true
                }
            }
            if (s.hdr && s.hdrMode == 1 && hdrDriver && mode in stillModes && mode != Mode.PRO) {
                b.setCaptureRequestOption(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_USE_SCENE_MODE)
                b.setCaptureRequestOption(CaptureRequest.CONTROL_SCENE_MODE, CaptureRequest.CONTROL_SCENE_MODE_HDR)
                any = true
            }
            if (mode == Mode.VIDEO && s.forceFps && fpsVals[s.fpsIdx] > 30) {
                val f = fpsVals[s.fpsIdx]
                b.setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(f, f))
                any = true
            }
            if (aeLocked) {
                b.setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, true)
                any = true
            }
            if (s.ois && oisAvail) {
                b.setCaptureRequestOption(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON)
                any = true
            }
            if (s.eis && eisAvail && (mode == Mode.VIDEO || mode == Mode.SLOMO || mode == Mode.TIMELAPSE)) {
                b.setCaptureRequestOption(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON)
                any = true
            }
            if (any) c2.setCaptureRequestOptions(b.build()) else c2.clearCaptureRequestOptions()
        } catch (e: Exception) {
            toast("Camera options not accepted: ${e.message}")
        }
    }

    fun values(mime: String): android.content.ContentValues {
        val cv = android.content.ContentValues()
        cv.put(MediaStore.MediaColumns.DISPLAY_NAME, stampNow())
        cv.put(MediaStore.MediaColumns.MIME_TYPE, mime)
        cv.put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/SamCam")
        return cv
    }

    fun shoot(tmp: File, cb: (Boolean) -> Unit) {
        val ic = imageCapture
        if (ic == null) {
            cb(false)
            return
        }
        val meta = ImageCapture.Metadata()
        meta.isReversedHorizontal = front && s.mirror
        ic.takePicture(ImageCapture.OutputFileOptions.Builder(tmp).setMetadata(meta).build(), exec, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(r: ImageCapture.OutputFileResults) {
                cb(true)
            }

            override fun onError(e: ImageCaptureException) {
                toast("Error: ${e.message}")
                cb(false)
            }
        })
    }

    fun takePlain() {
        val ic = imageCapture ?: return
        val meta = ImageCapture.Metadata()
        meta.isReversedHorizontal = front && s.mirror
        val tree = sdUri
        val doc: Uri? = if (tree != null) docCreate(ctx, treeRoot(tree), "image/jpeg", "${stampNow()}.jpg") else null
        val os: OutputStream? = if (doc != null) {
            try {
                ctx.contentResolver.openOutputStream(doc)
            } catch (e: Exception) {
                null
            }
        } else null
        if (tree != null && os == null) toast("SD folder not writable, saving to phone")
        val builder = if (os != null) {
            ImageCapture.OutputFileOptions.Builder(os)
        } else {
            ImageCapture.OutputFileOptions.Builder(ctx.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values("image/jpeg"))
        }
        ic.takePicture(builder.setMetadata(meta).build(), exec, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(r: ImageCapture.OutputFileResults) {
                try {
                    os?.close()
                } catch (e: Exception) {
                }
                setLast(r.savedUri ?: doc, "image/jpeg")
            }

            override fun onError(e: ImageCaptureException) {
                try {
                    os?.close()
                } catch (x: Exception) {
                }
                toast("Error: ${e.message}")
            }
        })
    }

    fun softHdrFlag(): Boolean {
        val evOk = evRange?.let { it.upper > it.lower } == true
        return s.hdr && (s.hdrMode == 2 || (s.hdrMode == 1 && !hdrDriver) || (s.hdrMode == 0 && (!evOk || mode != Mode.PHOTO)))
    }

    suspend fun processShot(f: File, m: Mode): Uri? {
        var bmp = decodeFile(f, if (m == Mode.ULTRA) 4200 else if (m == Mode.PORTRAIT) 2400 else 3000) ?: return null
        try {
            if (m == Mode.PORTRAIT) {
                bmp = portraitBlur(bmp, segLazy.value, blurDiv[s.blurIdx])
            } else if (m == Mode.DOC) {
                bmp = docScan(bmp, recLazy.value, s.docBw, s.docAuto)
            }
            val softHdr = softHdrFlag()
            if (m != Mode.DOC && m != Mode.PRO && (softHdr || s.ai)) bmp = toneMap(bmp, softHdr, s.ai)
            if (s.wmOn) bmp = drawWatermark(bmp, s.wmText, s.wmDate, s.wmPos)
        } catch (e: Throwable) {
        }
        val prefix = when (m) {
            Mode.PORTRAIT -> "PORTRAIT_"
            Mode.DOC -> "DOC_"
            Mode.ULTRA -> "UHD_"
            else -> "IMG_"
        }
        return saveBitmap(ctx, bmp, sdUri, prefix)
    }

    fun takePost(m: Mode) {
        if (processing) {
            toast("Still saving the last photo")
            return
        }
        val tmp = File(ctx.cacheDir, "shot_" + stampNow() + ".jpg")
        shoot(tmp) { ok ->
            if (!ok) {
                tmp.delete()
            } else {
                scope.launch {
                    processing = true
                    val uri = withContext(Dispatchers.Default) { processShot(tmp, m) }
                    tmp.delete()
                    processing = false
                    if (uri != null) setLast(uri, "image/jpeg") else toast("Could not save the photo")
                }
            }
        }
    }

    fun takeLive() {
        val vc = videoCapture
        if (vc == null || imageCapture == null) {
            takePlain()
            return
        }
        if (processing) {
            toast("Still saving the last photo")
            return
        }
        scope.launch {
            processing = true
            val id = stampNow()
            val tv = File(ctx.cacheDir, "live_$id.mp4")
            val tj = File(ctx.cacheDir, "live_$id.jpg")
            val fin = CompletableDeferred<Boolean>()
            val r: Recording? = try {
                vc.output.prepareRecording(ctx, FileOutputOptions.Builder(tv).build()).start(exec) { e ->
                    if (e is VideoRecordEvent.Finalize) fin.complete(!e.hasError())
                }
            } catch (e: Exception) {
                null
            }
            if (r == null) {
                processing = false
                takePlain()
                return@launch
            }
            val tLive = System.currentTimeMillis()
            delay(350)
            val jd = CompletableDeferred<Boolean>()
            shoot(tj) { jd.complete(it) }
            val jok = withTimeoutOrNull(8000L) { jd.await() } == true
            val rem = liveMsVals[s.liveIdx] - (System.currentTimeMillis() - tLive)
            if (rem > 0) delay(rem)
            r.stop()
            val vok = withTimeoutOrNull(6000L) { fin.await() } == true
            var uri: Uri? = null
            if (jok) {
                uri = withContext(Dispatchers.IO) {
                    try {
                        val jb = tj.readBytes()
                        val out = if (vok) motionPhotoPack(jb, tv.readBytes(), 350_000L) else jb
                        saveBytes(ctx, out, sdUri, "LIVE_$id", "image/jpeg")
                    } catch (e: Throwable) {
                        null
                    }
                }
            }
            tv.delete()
            tj.delete()
            processing = false
            if (uri != null) setLast(uri, "image/jpeg") else toast("Could not save the live photo")
        }
    }

    suspend fun processHdr(files: List<File>): Uri? {
        var bmp: Bitmap? = null
        try {
            val list = ArrayList<Bitmap>()
            for (f in files) {
                val b = decodeFile(f, 2600)
                if (b != null) list.add(b)
            }
            bmp = if (list.size >= 2) fuseExposures(list) else list.firstOrNull()
        } catch (e: Throwable) {
            bmp = null
        }
        if (bmp == null) bmp = decodeFile(files[0], 3000)
        var out = bmp ?: return null
        try {
            if (s.ai) out = toneMap(out, false, true)
            if (s.wmOn) out = drawWatermark(out, s.wmText, s.wmDate, s.wmPos)
        } catch (e: Throwable) {
        }
        return saveBitmap(ctx, out, sdUri, "HDR_")
    }

    fun takeHdr() {
        val cam = camera
        if (cam == null || imageCapture == null) {
            takePlain()
            return
        }
        if (processing) {
            toast("Still saving the last photo")
            return
        }
        scope.launch {
            processing = true
            holdStill = true
            val id = stampNow()
            val step = try {
                cam.cameraInfo.exposureState.exposureCompensationStep.toFloat()
            } catch (e: Exception) {
                0.33f
            }
            val rng = cam.cameraInfo.exposureState.exposureCompensationRange
            val d = maxOf(1, (1.7f / maxOf(step, 0.05f)).roundToInt())
            val order = listOf(ev, (ev - d).coerceIn(rng.lower, rng.upper), (ev + d).coerceIn(rng.lower, rng.upper))
            val files = ArrayList<File>()
            for ((k, idx) in order.withIndex()) {
                try {
                    withTimeoutOrNull(1500L) {
                        suspendCancellableCoroutine<Unit> { c ->
                            val f = cam.cameraControl.setExposureCompensationIndex(idx)
                            f.addListener({ if (c.isActive) c.resume(Unit) }, exec)
                        }
                    }
                } catch (e: Exception) {
                }
                delay(if (k == 0) 150L else 350L)
                val tmp = File(ctx.cacheDir, "hdr_${id}_$k.jpg")
                val jd = CompletableDeferred<Boolean>()
                shoot(tmp) { jd.complete(it) }
                val ok = withTimeoutOrNull(8000L) { jd.await() } == true
                if (ok) files.add(tmp) else tmp.delete()
            }
            try {
                cam.cameraControl.setExposureCompensationIndex(ev)
            } catch (e: Exception) {
            }
            holdStill = false
            val uri = if (files.isEmpty()) null else withContext(Dispatchers.Default) { processHdr(files) }
            files.forEach { it.delete() }
            processing = false
            if (uri != null) setLast(uri, "image/jpeg") else toast("Could not save the HDR photo")
        }
    }

    fun takePhoto() {
        if (imageCapture == null) return
        snd(MediaActionSound.SHUTTER_CLICK)
        buzz()
        if (s.flashAnim) {
            scope.launch {
                shutterAnim.snapTo(0.6f)
                shutterAnim.animateTo(0f, tween(220))
            }
        }
        val m = mode
        val live = s.live && m == Mode.PHOTO && liveReady
        val softHdr = softHdrFlag()
        val evOk = evRange?.let { it.upper > it.lower } == true
        val hdrMulti = !live && s.hdr && s.hdrMode == 0 && m == Mode.PHOTO && evOk
        val post = !live && (m == Mode.PORTRAIT || m == Mode.DOC || (m != Mode.PRO && (softHdr || s.ai)) || s.wmOn)
        if (live) takeLive() else if (hdrMulti) takeHdr() else if (post) takePost(m) else takePlain()
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
            recording = vc.output.prepareRecording(ctx, FileOutputOptions.Builder(tmp).build()).start(exec) { evt ->
                if (evt is VideoRecordEvent.Finalize) {
                    recording = null
                    paused = false
                    if (evt.hasError()) {
                        toast("Video error ${evt.error}")
                        tmp.delete()
                    } else {
                        scope.launch {
                            processing = true
                            val out = withContext(Dispatchers.IO) { openVideoOut(ctx, sdUri, stampNow() + "_SLO") }
                            var ok = false
                            if (out != null) {
                                ok = withContext(Dispatchers.IO) { remuxSlow(tmp, out.pfd, slowVals[s.slowIdx]) }
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
        val out = openVideoOut(ctx, sdUri, stampNow())
        if (out == null) {
            toast("Could not create the video file")
            return
        }
        snd(MediaActionSound.START_VIDEO_RECORDING)
        var pending = vc.output.prepareRecording(ctx, FileDescriptorOutputOptions.Builder(out.pfd).build())
        if (s.mic) pending = pending.withAudioEnabled()
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

    fun nextFps() {
        val allowed = ArrayList<Int>()
        for (i in fpsVals.indices) if (fpsSup[i] || (s.forceFps && i == 2)) allowed.add(i)
        if (allowed.isEmpty()) allowed.add(1)
        val cur = allowed.indexOf(s.fpsIdx)
        s.fpsIdx = allowed[(cur + 1) % allowed.size]
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
                            val o = openVideoOut(ctx, sdUri, stampNow() + "_TL")
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
                    delay(tlVals[s.tlIdx] * 1000L)
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
        panoDrift = 0f
        panoThumb = null
        panoMsg = ""
        scope.launch {
            var yaw = 0.0
            var pitch = 0.0
            var rate = 0.0
            var lastT = 0L
            val listener = object : SensorEventListener {
                override fun onSensorChanged(e: SensorEvent) {
                    if (lastT != 0L) {
                        val dt = (e.timestamp - lastT) / 1e9
                        pitch += e.values[0] * dt
                        yaw += e.values[1] * dt
                    }
                    rate = e.values[1].toDouble()
                    lastT = e.timestamp
                }

                override fun onAccuracyChanged(sn: Sensor?, a: Int) {}
            }
            sm.registerListener(listener, gyro, SensorManager.SENSOR_DELAY_GAME)
            val strips = ArrayList<Bitmap>()
            val tiny = ArrayList<Bitmap>()
            val thumbH = 72
            var dir = 0
            var lastYaw = 0.0
            var total = 0
            try {
                while (isActive && panoOn) {
                    delay(33)
                    val dg = Math.toDegrees(pitch).toFloat()
                    if (abs(dg - panoDrift) > 0.2f) panoDrift = dg
                    val speed = abs(Math.toDegrees(rate))
                    val vw = previewView.width
                    if (vw <= 0) continue
                    val d = yaw - lastYaw
                    val sgn = if (d > 0) 1 else -1
                    val px = (abs(d) * vw / panoFov).toInt()
                    if (dir == 0 && px >= 20) {
                        dir = sgn
                        panoRight = dir < 0
                    }
                    var wrong = false
                    if (dir != 0 && sgn != dir && px >= 4) {
                        lastYaw = yaw
                        wrong = true
                    } else if (dir != 0 && px >= 10) {
                        val bmp = previewView.bitmap
                        if (bmp != null) {
                            val scale = bmp.width.toFloat() / vw
                            val sw = (px * scale).toInt().coerceIn(4, bmp.width / 2)
                            val x0 = (bmp.width - sw) / 2
                            val strip = Bitmap.createBitmap(bmp, x0, 0, sw, bmp.height)
                            bmp.recycle()
                            val tsc = thumbH.toFloat() / strip.height
                            val small = Bitmap.createScaledBitmap(strip, maxOf(1, (sw * tsc).toInt()), thumbH, true)
                            if (dir > 0) {
                                strips.add(0, strip)
                                tiny.add(0, small)
                            } else {
                                strips.add(strip)
                                tiny.add(small)
                            }
                            total += sw
                            lastYaw += (sgn * (sw / scale) * panoFov / vw).toDouble()
                            panoStrips = strips.size
                            if (strips.size % 3 == 0) {
                                var tw = 0
                                for (x in tiny) tw += x.width
                                val tb = Bitmap.createBitmap(maxOf(1, tw), thumbH, Bitmap.Config.ARGB_8888)
                                val tc = GCanvas(tb)
                                var tx = 0f
                                for (x in tiny) {
                                    tc.drawBitmap(x, tx, 0f, null)
                                    tx += x.width
                                }
                                panoThumb = tb
                            }
                            if (total > 11000) panoOn = false
                        }
                    }
                    panoMsg = when {
                        dir == 0 -> ""
                        wrong -> if (panoRight) "Move to the right" else "Move to the left"
                        speed > 55.0 -> "Slow down"
                        abs(panoDrift) > 5f -> "Keep the line level"
                        else -> "Keep going"
                    }
                }
            } finally {
                sm.unregisterListener(listener)
                panoOn = false
                panoMsg = ""
            }
            tiny.forEach { it.recycle() }
            panoThumb = null
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
                    for (st in strips) {
                        cv.drawBitmap(st, x, 0f, null)
                        x += st.width
                        st.recycle()
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
        val pkg = s.galleryPkg
        val u = lastUri
        if (pkg.isEmpty() || u == null) {
            showGallery = true
            return
        }
        if (!openExternal(ctx, u, lastMime.startsWith("video"), pkg)) showGallery = true
    }

    fun galleryLabel(pkg: String): String = when {
        pkg.isEmpty() -> "In-app viewer"
        pkg == "ask" -> "Ask every time"
        else -> try {
            ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            pkg
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
            scene = null
            docFound = false
            torch = false
            mode = m
            buzz()
        }
        animateModePos(modeOrder.indexOf(m))
    }

    val modeDrag = rememberDraggableState { dx ->
        if (!capturing && countdown == 0) {
            modePos.floatValue = (modePos.floatValue - dx / itemPx).coerceIn(0f, (modeOrder.size - 1).toFloat())
        }
    }

    LaunchedEffect(Bus.tick) { if (Bus.tick > 0) shutter() }

    DisposableEffect(owner) {
        val lo = object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                if (event == Lifecycle.Event.ON_STOP) {
                    try {
                        recording?.stop()
                    } catch (x: Exception) {
                    }
                    recording = null
                    paused = false
                    if (tlOn) {
                        tlOn = false
                        tlJob?.cancel()
                    }
                    panoOn = false
                    torch = false
                    aeLocked = false
                }
            }
        }
        owner.lifecycle.addObserver(lo)
        onDispose { owner.lifecycle.removeObserver(lo) }
    }

    BackHandler(enabled = showSettings && !showGallery) {
        if (showInfo) showInfo = false else showSettings = false
    }

    val pillText: String? = when {
        aeLocked -> null
        mode == Mode.DOC -> if (docFound) "Document detected" else "Align the page inside the frame"
        s.scenePill && mode in stillModes && scene != null -> scene
        else -> null
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black).systemBarsPadding()) {
        SideEffect {
            screenW = constraints.maxWidth
            screenH = constraints.maxHeight
        }
        Box(
            Modifier.align(Alignment.TopCenter).padding(top = if (isFull) 0.dp else 56.dp).fillMaxWidth()
                .aspectRatio(arAnim)
                .pointerInput(s.tapFocus) {
                    detectTapGestures(
                        onLongPress = { o ->
                            if (mode != Mode.PANO && s.tapFocus) {
                                val p = previewView.meteringPointFactory.createPoint(o.x, o.y)
                                camera?.cameraControl?.startFocusAndMetering(
                                    FocusMeteringAction.Builder(p, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                                        .disableAutoCancel().build()
                                )
                                focusPos = o
                                focusTick = focusTick + 1
                                aeLocked = true
                                buzz()
                            }
                        }
                    ) { o ->
                        if (mode == Mode.PANO) {
                            if (!panoOn) {
                                panoRight = !panoRight
                                buzz()
                            }
                        } else if (s.tapFocus) {
                            if (aeLocked) aeLocked = false
                            val p = previewView.meteringPointFactory.createPoint(o.x, o.y)
                            camera?.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(p).build())
                            focusPos = o
                            focusTick = focusTick + 1
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, z, _ ->
                        setZoom(zoomTarget.floatValue * z, true)
                        bump()
                    }
                }
        ) {
            AndroidView({ previewView }, Modifier.fillMaxSize())
            if (s.grid) {
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
                    val c = if (docFound) Color(0xFF4CAF50) else Color.White
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
            if (mode == Mode.PANO) PanoGuide(panoOn, panoRight, panoThumb, { panoDrift }, panoMsg)
            val ringA by animateFloatAsState(targetValue = if (focusUiOn) 1f else 0f, animationSpec = tween(250), label = "ring")
            Canvas(Modifier.fillMaxSize()) {
                val fv = focusAnimV.value
                val fp = focusPos
                if (fp != null && ringA > 0.01f) {
                    val rr = 76f - 36f * fv
                    drawCircle(YELLOW.copy(alpha = ringA), radius = rr, center = fp, style = Stroke(4f))
                }
            }
            val fpv = focusPos
            val erng = evRange
            if (fpv != null && focusUiOn && erng != null && erng.upper > erng.lower && mode != Mode.PANO) {
                ExposureHandle(
                    cx = fpv.x, cy = fpv.y, areaW = screenW.toFloat(),
                    ev = ev, evMin = erng.lower, evMax = erng.upper,
                    onEv = { ev = it },
                    onTouch = { evTouch = evTouch + 1 }
                )
            }
            if (aeLocked) {
                Box(Modifier.align(Alignment.TopCenter).padding(top = 14.dp)) {
                    LockPill {
                        aeLocked = false
                        try {
                            camera?.cameraControl?.cancelFocusAndMetering()
                        } catch (e: Exception) {
                        }
                        focusTick = focusTick + 1
                    }
                }
            }
            if (countdown > 0) {
                Text("$countdown", Modifier.align(Alignment.Center), color = Color.White, fontSize = 96.sp)
            }
            Box(Modifier.align(Alignment.Center)) { BigZoom({ zoomShown.floatValue }, rulerShown) }
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = coverAlpha }.background(Color.Black))
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = shutterAnim.value }.background(Color.Black))
            val qv = qr
            if (qv != null && mode == Mode.PHOTO) {
                Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)) {
                    QrPill(qv.label, {
                        if (qv.kind == 2 && qv.ssid != null) {
                            toast(connectWifi(ctx, qv.ssid, qv.pass, qv.wifiType, qv.hidden, s.wifiManual))
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
        }

        AnimatedVisibility(
            visible = capturing || processing || holdStill,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
            enter = fadeIn(), exit = fadeOut()
        ) {
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(Color(0x66000000)).padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (panoOn) {
                    Text("Pan slowly  ·  $panoStrips", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                } else if (holdStill && !capturing) {
                    Text("Hold still…", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                } else if (processing && !capturing) {
                    Text(if (s.live && mode == Mode.PHOTO) "Live photo…" else "Saving…", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
                        Mode.PHOTO -> {
                            IconBox({ flash = (flash + 1) % 3 }, flash != 0) { FlashIcon(flash) }
                            IconBox({ timer = timerVals[(timerVals.indexOf(timer) + 1) % timerVals.size] }, timer != 0) { TimerIcon(timer) }
                            IconBox({ s.ratioIdx = (s.ratioIdx + 1) % ratios.size }, false) { TextIcon(ratios[s.ratioIdx].label, false) }
                            IconBox({ s.live = !s.live }, s.live) { LiveIcon(s.live) }
                            IconBox({ s.hdr = !s.hdr }, s.hdr) { TextIcon("HDR", s.hdr) }
                        }
                        Mode.PRO, Mode.PORTRAIT, Mode.NIGHT -> {
                            IconBox({ flash = (flash + 1) % 3 }, flash != 0) { FlashIcon(flash) }
                            IconBox({ timer = timerVals[(timerVals.indexOf(timer) + 1) % timerVals.size] }, timer != 0) { TimerIcon(timer) }
                            IconBox({ s.ratioIdx = (s.ratioIdx + 1) % ratios.size }, false) { TextIcon(ratios[s.ratioIdx].label, false) }
                            IconBox({ s.sizeIdx = (s.sizeIdx + 1) % sizeNames.size }, false) { TextIcon(sizeNames[s.sizeIdx], false) }
                        }
                        Mode.ULTRA, Mode.DOC -> {
                            IconBox({ flash = (flash + 1) % 3 }, flash != 0) { FlashIcon(flash) }
                            IconBox({ timer = timerVals[(timerVals.indexOf(timer) + 1) % timerVals.size] }, timer != 0) { TimerIcon(timer) }
                            IconBox({ s.ratioIdx = (s.ratioIdx + 1) % ratios.size }, false) { TextIcon(ratios[s.ratioIdx].label, false) }
                        }
                        Mode.VIDEO -> {
                            IconBox({ torch = !torch }, torch) { FlashIcon(if (torch) 2 else 0) }
                            IconBox({ s.vqIdx = (s.vqIdx + 1) % vqNames.size }, false) { TextIcon(vqNames[s.vqIdx], false) }
                            IconBox({ nextFps() }, s.forceFps && fpsVals[s.fpsIdx] > 30) {
                                TextIcon("${fpsVals[s.fpsIdx]}fps", s.forceFps && fpsVals[s.fpsIdx] > 30)
                            }
                            IconBox({ s.vRatioIdx = (s.vRatioIdx + 1) % ratios.size }, false) { TextIcon(ratios[s.vRatioIdx].label, false) }
                        }
                        Mode.SLOMO -> {
                            IconBox({ torch = !torch }, torch) { FlashIcon(if (torch) 2 else 0) }
                            IconBox({ s.slowIdx = (s.slowIdx + 1) % slowVals.size }, false) { TextIcon(slowNames[s.slowIdx] + " slower", false) }
                            IconBox({ s.vRatioIdx = (s.vRatioIdx + 1) % ratios.size }, false) { TextIcon(ratios[s.vRatioIdx].label, false) }
                        }
                        Mode.TIMELAPSE -> {
                            IconBox({ torch = !torch }, torch) { FlashIcon(if (torch) 2 else 0) }
                            IconBox({ timer = timerVals[(timerVals.indexOf(timer) + 1) % timerVals.size] }, timer != 0) { TimerIcon(timer) }
                            IconBox({ s.tlIdx = (s.tlIdx + 1) % tlVals.size }, false) { TextIcon("${tlVals[s.tlIdx]}s", false) }
                            IconBox({ s.ratioIdx = (s.ratioIdx + 1) % ratios.size }, false) { TextIcon(ratios[s.ratioIdx].label, false) }
                        }
                        Mode.PANO -> {
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = pillText != null && !capturing,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 62.dp),
            enter = fadeIn(), exit = fadeOut()
        ) {
            InfoPill(pillText ?: "", mode == Mode.DOC && docFound)
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 12.dp),
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

            if (s.showZoom && maxZoom - minZoom > 0.05f && mode != Mode.PANO) {
                ZoomBar(
                    zoom = { zoomShown.floatValue },
                    target = { zoomTarget.floatValue },
                    minZ = minZoom, maxZ = maxZoom,
                    tele = if (tele != null && s.tele && !teleBroken && !front) tele.factor else null,
                    rulerShown = rulerShown,
                    onPreset = { setZoom(it, false) },
                    onSet = { setZoom(it, true) },
                    onBump = { bump() }
                )
            } else {
                Spacer(Modifier.height(46.dp))
            }

            AnimatedVisibility(visible = !capturing) {
                Box(
                    Modifier.fillMaxWidth().draggable(
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
                    )
                ) {
                    ModeStrip({ modePos.floatValue }, modeOrder, itemW) { selectMode(it) }
                }
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
                Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp)) {
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
                                        "RESULT: the camera delivers about 60 fps in raw Camera2. Turn on \"Allow 60 fps even if not reported\" in Settings to try it in videos."
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
                        OptionRow("Aspect ratio", ratios[s.ratioIdx].label) { s.ratioIdx = (s.ratioIdx + 1) % ratios.size }
                        OptionRow("Photo size", sizeNames[s.sizeIdx]) { s.sizeIdx = (s.sizeIdx + 1) % sizeNames.size }
                        OptionRow("Photo quality (JPEG)", jpegNames[s.jpegIdx]) { s.jpegIdx = (s.jpegIdx + 1) % jpegNames.size }
                        SwitchRow("Live photo", "Photo mode: saves a short motion clip with the photo (Motion Photo)", s.live) { s.live = it }
                        OptionRow("Live photo length", liveNames[s.liveIdx]) { s.liveIdx = (s.liveIdx + 1) % liveNames.size }
                        SwitchRow("HDR", "Photo mode: takes 3 photos at different brightness and merges them. Hold still.", s.hdr) { s.hdr = it }
                        OptionRow("HDR method", hdrModeNames[s.hdrMode]) { s.hdrMode = (s.hdrMode + 1) % hdrModeNames.size }
                        SwitchRow("AI enhancement", "Boosts colour and contrast after capture", s.ai) { s.ai = it }
                        SwitchRow("Denoiser", "Asks the driver for high-quality noise reduction and sharpening", s.denoise) { s.denoise = it }
                        SwitchRow("Scene recognition pill", "Shows what the camera is looking at", s.scenePill) { s.scenePill = it }
                        SwitchRow("Scan QR codes in Photo mode", "Show a button when a QR code is in view", s.qrScan) { s.qrScan = it }
                        SwitchRow("Wi-Fi QR: add manually", "Opens Wi-Fi settings and copies the password so Android saves the network itself", s.wifiManual) { s.wifiManual = it }
                        SwitchRow("Telephoto lens", if (tele != null) "Switches to the 2x lens when you zoom in (camera ${tele.teleId})" else "Not exposed to apps on this ROM", s.tele) { s.tele = it }
                        OptionRow("Portrait blur strength", blurNames[s.blurIdx]) { s.blurIdx = (s.blurIdx + 1) % blurNames.size }
                        SwitchRow("Document: auto flatten", "Straightens and crops the page using the detected text", s.docAuto) { s.docAuto = it }
                        SwitchRow("Document: black & white", "Off keeps colour with a contrast boost", s.docBw) { s.docBw = it }
                        SwitchRow("Save selfies as previewed", "Keep the mirror image of front camera photos", s.mirror) { s.mirror = it }
                        SwitchRow("Grid lines", "Show a 3x3 grid in the viewfinder", s.grid) { s.grid = it }
                        SwitchRow("Shutter flash animation", "Briefly dim the viewfinder when a photo is taken", s.flashAnim) { s.flashAnim = it }

                        SectionTitle("Watermark")
                        SwitchRow("Add watermark", "Stamp your text on photos", s.wmOn) { s.wmOn = it }
                        TextRow("Watermark text", s.wmText) { s.wmText = it }
                        SwitchRow("Include date and time", "Adds a second line with the time", s.wmDate) { s.wmDate = it }
                        OptionRow("Watermark position", wmPosNames[s.wmPos]) { s.wmPos = (s.wmPos + 1) % wmPosNames.size }

                        SectionTitle("Videos")
                        OptionRow("Video resolution", vqNames[s.vqIdx]) { s.vqIdx = (s.vqIdx + 1) % vqNames.size }
                        OptionRow ("Video aspect ratio", ratios[s.vRatioIdx].label) { s.vRatioIdx = (s.vRatioIdx + 1) % ratios.size }
                        SwitchRow("Optical stabilization (OIS)", if (oisAvail) "Uses the lens stabilizer" else "Not offered by this camera driver", s.ois) { s.ois = it }
                        SwitchRow("Video stabilization (EIS)", if (eisAvail) "Digital stabilization for videos (crops the frame slightly)" else "Not offered by this camera driver", s.eis) { s.eis = it }
                        OptionRow("Video frame rate", "${fpsVals[s.fpsIdx]} fps" + if (fpsSup[2]) "  (60 supported)" else "") { nextFps() }
                        SwitchRow("Allow 60 fps even if not reported (experimental)", "Lets you pick 60 fps if the driver hides it. Turn off if videos freeze", s.forceFps) { s.forceFps = it }
                        OptionRow("Video bitrate", brNames[s.brIdx]) { s.brIdx = (s.brIdx + 1) % brNames.size }
                        SwitchRow("Record audio", "Use the microphone when recording videos", s.mic) { s.mic = it }
                        OptionRow("Slow motion speed", slowNames[s.slowIdx] + " slower") { s.slowIdx = (s.slowIdx + 1) % slowVals.size }
                        OptionRow("Time-lapse interval", "${tlVals[s.tlIdx]} s") { s.tlIdx = (s.tlIdx + 1) % tlVals.size }

                        SectionTitle("Useful features")
                        OptionRow("Volume keys", volNames[s.volMode]) { s.volMode = (s.volMode + 1) % volNames.size }
                        SwitchRow("Tap to focus", "Tap the viewfinder to focus", s.tapFocus) { s.tapFocus = it }
                        SwitchRow("Shutter sound", "Play a sound when taking photos and videos", s.sound) { s.sound = it }
                        SwitchRow("Vibration feedback", "Vibrate when pressing the shutter and changing modes", s.vibe) { s.vibe = it }
                        SwitchRow("Keep screen on", "Prevent the screen from turning off while the camera is open", s.keepOn) { s.keepOn = it }
                        SwitchRow("Show zoom controls", "Show the zoom pill and ruler", s.showZoom) { s.showZoom = it }
                        SwitchRow("Start with front camera", "Open the app using the selfie camera", s.defFront) { s.defFront = it }
                        SwitchRow("Remember last mode", "Open in the mode you used last", s.restoreMode) { s.restoreMode = it }

                        SectionTitle("Storage and gallery")
                        OptionRow("Storage location", if (sdUri == null) "Phone" else "Chosen folder") {
                            Toast.makeText(ctx, "Open your SD card, pick or create a folder named SamCam, then tap Use this folder", Toast.LENGTH_LONG).show()
                            pickFolder.launch(null)
                        }
                        if (sdUri != null) {
                            OptionRow("Back to phone storage", "Reset") {
                                prefs.edit().remove("tree").apply()
                                sdUri = null
                            }
                        }
                        OptionRow("Open pictures with", galleryLabel(s.galleryPkg)) { showGalleryPicker = !showGalleryPicker }
                        if (showGalleryPicker) {
                            val apps = galleryApps
                            if (apps == null) {
                                Text("Looking for gallery apps…", color = Color(0x99FFFFFF), fontSize = 13.sp, modifier = Modifier.padding(8.dp))
                            } else {
                                GalleryChoice("In-app viewer (swipe, share, delete)", s.galleryPkg.isEmpty()) {
                                    s.galleryPkg = ""
                                    showGalleryPicker = false
                                }
                                GalleryChoice("Ask every time", s.galleryPkg == "ask") {
                                    s.galleryPkg = "ask"
                                    showGalleryPicker = false
                                }
                                for (a in apps) {
                                    GalleryChoice(a.label, s.galleryPkg == a.pkg) {
                                        s.galleryPkg = a.pkg
                                        showGalleryPicker = false
                                    }
                                }
                            }
                        }

                        SectionTitle("Camera engine")
                        OptionRow("Preview engine", engineNames[s.engine]) { s.engine = (s.engine + 1) % engineNames.size }
                        OptionRow("Capture priority", prioNames[s.prioIdx]) { s.prioIdx = (s.prioIdx + 1) % prioNames.size }
                        OptionRow("Camera ID", if (camChoice == 0) "Auto" else "ID ${camIds[camChoice - 1]}") {
                            if (!capturing) camChoice = (camChoice + 1) % (camIds.size + 1)
                        }
                        OptionRow("Camera info and 60 fps test", "Open") { showInfo = true }
                        Text(
                            "The camera driver (HAL) belongs to the ROM and can't be switched by an app. Preview engine, capture priority and camera ID change how this app talks to it.",
                            color = Color(0x99FFFFFF), fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp)
                        )

                        SectionTitle("Reset")
                        OptionRow("Reset settings", "Reset") {
                            s.resetAll()
                            sdUri = null
                        }
                        Spacer(Modifier.height(28.dp))
                        Text("Camera 0.6", color = Color(0x88FFFFFF), fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        Text("Developer: Alienluap", color = Color(0xCCFFFFFF), fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                        Text("© Alienluap. All rights reserved.", color = Color(0x88FFFFFF), fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 2.dp))
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }

        AnimatedVisibility(visible = showGallery, enter = fadeIn(), exit = fadeOut()) {
            GalleryViewer(lastUri, lastMime.startsWith("video"), s.galleryPkg) { showGallery = false }
        }
    }
}
