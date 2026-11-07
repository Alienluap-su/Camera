package com.example.samcam

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.hardware.camera2.CaptureRequest
import android.net.Uri
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.Settings
import androidx.camera.video.Quality
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.google.mlkit.vision.barcode.common.Barcode
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.reflect.KProperty

enum class Mode(val label: String) {
    PRO("Pro"), PANO("Panorama"), SLOMO("Slow motion"), VIDEO("Video"), PHOTO("Photo"),
    PORTRAIT("Portrait"), NIGHT("Night"), ULTRA("Ultra HD"), DOC("Document"), TIMELAPSE("Time-lapse")
}

val stillModes = setOf(Mode.PHOTO, Mode.PRO, Mode.PORTRAIT, Mode.NIGHT, Mode.ULTRA, Mode.DOC)

class Ratio(val label: String, val w: Int, val h: Int)

val ratios = listOf(Ratio("3:4", 3, 4), Ratio("9:16", 9, 16), Ratio("1:1", 1, 1), Ratio("2:3", 2, 3), Ratio("Full", 0, 0))

fun ratioAr(i: Int, fullAr: Float): Float {
    val r = ratios[i]
    return if (r.w == 0) fullAr else r.w.toFloat() / r.h
}

object Bus {
    var tick by mutableIntStateOf(0)
    var zoomTick by mutableIntStateOf(0)
    var zoomUp = true
    var volMode = 0
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
val wmPosNames = listOf("Bottom left", "Bottom right", "Bottom center")
val galleryNames = listOf("In-app viewer", "Google Photos (this item)", "Google Photos (app)")
val wbNames = listOf("AUTO", "DAY", "CLOUD", "TUNG", "FLUO")
val wbVals = listOf(
    CaptureRequest.CONTROL_AWB_MODE_AUTO, CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT,
    CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT, CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT,
    CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT
)

interface Resettable { fun reset() }

class IntPref(private val p: SharedPreferences, private val k: String, private val d: Int) : Resettable {
    private val st = mutableIntStateOf(p.getInt(k, d))
    operator fun getValue(t: Any?, prop: KProperty<*>): Int = st.intValue
    operator fun setValue(t: Any?, prop: KProperty<*>, v: Int) {
        st.intValue = v
        p.edit().putInt(k, v).apply()
    }
    override fun reset() { st.intValue = d }
}

class BoolPref(private val p: SharedPreferences, private val k: String, private val d: Boolean) : Resettable {
    private val st = mutableStateOf(p.getBoolean(k, d))
    operator fun getValue(t: Any?, prop: KProperty<*>): Boolean = st.value
    operator fun setValue(t: Any?, prop: KProperty<*>, v: Boolean) {
        st.value = v
        p.edit().putBoolean(k, v).apply()
    }
    override fun reset() { st.value = d }
}

class StrPref(private val p: SharedPreferences, private val k: String, private val d: String) : Resettable {
    private val st = mutableStateOf(p.getString(k, d) ?: d)
    operator fun getValue(t: Any?, prop: KProperty<*>): String = st.value
    operator fun setValue(t: Any?, prop: KProperty<*>, v: String) {
        st.value = v
        p.edit().putString(k, v).apply()
    }
    override fun reset() { st.value = d }
}

class AppSettings(private val p: SharedPreferences) {
    private val all = ArrayList<Resettable>()
    private fun int(k: String, d: Int) = IntPref(p, k, d).also { all.add(it) }
    private fun bool(k: String, d: Boolean) = BoolPref(p, k, d).also { all.add(it) }
    private fun str(k: String, d: String) = StrPref(p, k, d).also { all.add(it) }

    var sizeIdx by int("size", 0)
    var jpegIdx by int("jpeg", 1)
    var vqIdx by int("vq", 1)
    var fpsIdx by int("fps", 1)
    var brIdx by int("br", 0)
    var tlIdx by int("tl", 1)
    var slowIdx by int("slow", 1)
    var blurIdx by int("blur", 1)
    var prioIdx by int("prio", 0)
    var engine by int("engine", 0)
    var ratioIdx by int("ratio", 0)
    var volMode by int("vol", 0)
    var galleryMode by int("gallery", 0)
    var wmPos by int("wmpos", 0)
    var lastMode by int("lastmode", 4)
    var mic by bool("mic", true)
    var grid by bool("grid", false)
    var mirror by bool("mirror", true)
    var forceFps by bool("force", false)
    var sound by bool("sound", true)
    var vibe by bool("vibe", true)
    var tapFocus by bool("tap", true)
    var keepOn by bool("keep", true)
    var flashAnim by bool("flashanim", true)
    var showZoom by bool("showzoom", true)
    var defFront by bool("deffront", false)
    var restoreMode by bool("restore", false)
    var qrScan by bool("qrscan", true)
    var docBw by bool("docbw", true)
    var docAuto by bool("docauto", true)
    var scenePill by bool("scene", true)
    var tele by bool("tele", true)
    var live by bool("live", false)
    var hdr by bool("hdr", false)
    var ai by bool("ai", false)
    var denoise by bool("denoise", true)
    var wmOn by bool("wmon", false)
    var wmDate by bool("wmdate", true)
    var wmText by str("wmtext", "Shot on Note 8")

    fun resetAll() {
        all.forEach { it.reset() }
        p.edit().clear().apply()
    }
}

fun shutLabel(ns: Long): String =
    if (ns <= 0L) "-" else if (ns >= 100_000_000L) "%.1fs".format(ns / 1e9) else "1/${(1e9 / ns).toInt()}"

fun fmtTime(sec: Long): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

fun fmtZoom(z: Float): String =
    if (abs(z - z.roundToInt()) < 0.05f) "${z.roundToInt()}x" else "%.1fx".format(z)

fun fmtPreset(z: Float): String =
    if (abs(z - z.roundToInt()) < 0.15f) "${z.roundToInt()}" else "%.1f".format(z)

fun stampNow(): String = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())

fun copyText(ctx: Context, text: String) {
    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
        .setPrimaryClip(ClipData.newPlainText("camera", text))
}

class QrResult(
    val text: String, val kind: Int, val label: String,
    val ssid: String?, val pass: String?, val wifiType: Int, val hidden: Boolean
)

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

fun openVideoOut(ctx: Context, tree: Uri?, name: String): VideoOut? {
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
        cv.put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/SamCam")
        val uri = ctx.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv) ?: return null
        val p = ctx.contentResolver.openFileDescriptor(uri, "rw") ?: return null
        return VideoOut(p, uri)
    } catch (e: Exception) {
        return null
    }
}

fun saveBytes(ctx: Context, bytes: ByteArray, tree: Uri?, name: String, mime: String): Uri? {
    try {
        var uri: Uri? = null
        var out: java.io.OutputStream? = null
        if (tree != null) {
            uri = docCreate(ctx, treeRoot(tree), mime, "$name.jpg")
            if (uri != null) out = ctx.contentResolver.openOutputStream(uri)
        }
        if (out == null) {
            val cv = ContentValues()
            cv.put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            cv.put(MediaStore.MediaColumns.MIME_TYPE, mime)
            cv.put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/SamCam")
            uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)
            if (uri != null) out = ctx.contentResolver.openOutputStream(uri)
        }
        val o = out ?: return null
        o.use { it.write(bytes) }
        return uri
    } catch (e: Throwable) {
        return null
    }
}

fun saveBitmap(ctx: Context, bmp: android.graphics.Bitmap, tree: Uri?, prefix: String): Uri? {
    val bos = ByteArrayOutputStream()
    bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, bos)
    return saveBytes(ctx, bos.toByteArray(), tree, prefix + stampNow(), "image/jpeg")
}
