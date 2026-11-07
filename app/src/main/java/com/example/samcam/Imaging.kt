package com.example.samcam

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as GCanvas
import android.graphics.Color as GColor
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.util.Size
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.SegmentationMask
import com.google.mlkit.vision.segmentation.Segmenter
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognizer
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

fun decodeFile(f: File, maxSide: Int): Bitmap? {
    try {
        val o = BitmapFactory.Options()
        o.inJustDecodeBounds = true
        BitmapFactory.decodeFile(f.absolutePath, o)
        var ss = 1
        while (max(o.outWidth, o.outHeight) / ss > maxSide) ss *= 2
        val o2 = BitmapFactory.Options()
        o2.inSampleSize = ss
        val raw = BitmapFactory.decodeFile(f.absolutePath, o2) ?: return null
        val ori = ExifInterface(f.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)
        val m = Matrix()
        when (ori) {
            2 -> m.setScale(-1f, 1f)
            3 -> m.setRotate(180f)
            4 -> { m.setRotate(180f); m.postScale(-1f, 1f) }
            5 -> { m.setRotate(90f); m.postScale(-1f, 1f) }
            6 -> m.setRotate(90f)
            7 -> { m.setRotate(-90f); m.postScale(-1f, 1f) }
            8 -> m.setRotate(-90f)
            else -> return raw
        }
        val out = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
        if (out !== raw) raw.recycle()
        return out
    } catch (e: Throwable) {
        return null
    }
}

private fun blurPass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horiz: Boolean) {
    val lines = if (horiz) h else w
    val len = if (horiz) w else h
    val step = if (horiz) 1 else w
    val cnt = 2 * r + 1
    for (l in 0 until lines) {
        val start = if (horiz) l * w else l
        var sr = 0
        var sg = 0
        var sb = 0
        for (k in -r..r) {
            val p = src[start + k.coerceIn(0, len - 1) * step]
            sr += (p shr 16) and 255
            sg += (p shr 8) and 255
            sb += p and 255
        }
        for (i in 0 until len) {
            dst[start + i * step] = (0xFF shl 24) or ((sr / cnt) shl 16) or ((sg / cnt) shl 8) or (sb / cnt)
            val a = src[start + min(len - 1, i + r + 1) * step]
            val b = src[start + max(0, i - r) * step]
            sr += ((a shr 16) and 255) - ((b shr 16) and 255)
            sg += ((a shr 8) and 255) - ((b shr 8) and 255)
            sb += (a and 255) - (b and 255)
        }
    }
}

fun boxBlur(px: IntArray, w: Int, h: Int, r: Int) {
    val tmp = IntArray(px.size)
    blurPass(px, tmp, w, h, r, true)
    blurPass(tmp, px, w, h, r, false)
}

suspend fun portraitBlur(src: Bitmap, seg: Segmenter, div: Int): Bitmap {
    val w = src.width
    val h = src.height
    val sc = 512f / max(w, h)
    val small = Bitmap.createScaledBitmap(src, max(1, (w * sc).toInt()), max(1, (h * sc).toInt()), true)
    val mask: SegmentationMask? = suspendCoroutine { c ->
        try {
            seg.process(InputImage.fromBitmap(small, 0))
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
                val d = kotlin.math.sqrt(dx * dx + dy * dy) * 0.75f
                conf[y * mw + x] = ((1.1f - d) / 0.35f).coerceIn(0f, 1f)
            }
        }
    }
    val sp = IntArray(w * h)
    src.getPixels(sp, 0, w, 0, 0, w, h)
    val bw = max(16, w / div)
    val bh = max(16, h / div)
    val tiny = Bitmap.createScaledBitmap(src, bw, bh, true)
    val tp = IntArray(bw * bh)
    tiny.getPixels(tp, 0, bw, 0, 0, bw, bh)
    boxBlur(tp, bw, bh, 3)
    boxBlur(tp, bw, bh, 3)
    val blurSmall = Bitmap.createBitmap(tp, bw, bh, Bitmap.Config.ARGB_8888)
    val blurFull = Bitmap.createScaledBitmap(blurSmall, w, h, true)
    val bp = IntArray(w * h)
    blurFull.getPixels(bp, 0, w, 0, 0, w, h)
    val x0 = IntArray(w)
    val x1 = IntArray(w)
    val tx = FloatArray(w)
    for (x in 0 until w) {
        val fx = (x + 0.5f) * mw / w - 0.5f
        val a = fx.toInt().coerceIn(0, mw - 1)
        x0[x] = a
        x1[x] = min(mw - 1, a + 1)
        tx[x] = (fx - a).coerceIn(0f, 1f)
    }
    val out = IntArray(w * h)
    for (y in 0 until h) {
        val fy = (y + 0.5f) * mh / h - 0.5f
        val y0 = fy.toInt().coerceIn(0, mh - 1)
        val y1 = min(mh - 1, y0 + 1)
        val ty = (fy - y0).coerceIn(0f, 1f)
        for (x in 0 until w) {
            val c00 = conf[y0 * mw + x0[x]]
            val c10 = conf[y0 * mw + x1[x]]
            val c01 = conf[y1 * mw + x0[x]]
            val c11 = conf[y1 * mw + x1[x]]
            val top = c00 + (c10 - c00) * tx[x]
            val bot = c01 + (c11 - c01) * tx[x]
            val cv = top + (bot - top) * ty
            var al = ((cv - 0.35f) / 0.3f).coerceIn(0f, 1f)
            al = al * al * (3f - 2f * al)
            val i = y * w + x
            if (al >= 0.995f) {
                out[i] = sp[i]
            } else if (al <= 0.005f) {
                out[i] = bp[i]
            } else {
                val s = sp[i]
                val b = bp[i]
                val r = (((b shr 16) and 255) + ((((s shr 16) and 255) - ((b shr 16) and 255)) * al)).toInt()
                val g = (((b shr 8) and 255) + ((((s shr 8) and 255) - ((b shr 8) and 255)) * al)).toInt()
                val bl = ((b and 255) + (((s and 255) - (b and 255)) * al)).toInt()
                out[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
            }
        }
    }
    val result = Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
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

private fun dist(a: PointF, b: PointF): Float = hypot(a.x - b.x, a.y - b.y)

suspend fun docScan(src: Bitmap, rec: TextRecognizer, bw: Boolean, auto: Boolean): Bitmap {
    if (!auto) return docEnhance(src, bw)
    val sc = min(1f, 1280f / max(src.width, src.height))
    val small = if (sc < 1f) Bitmap.createScaledBitmap(src, (src.width * sc).toInt(), (src.height * sc).toInt(), true) else src
    val txt: Text? = suspendCoroutine { c ->
        try {
            rec.process(InputImage.fromBitmap(small, 0))
                .addOnSuccessListener { c.resume(it) }
                .addOnFailureListener { c.resume(null) }
        } catch (e: Exception) {
            c.resume(null)
        }
    }
    val pts = ArrayList<PointF>()
    if (txt != null) {
        for (b in txt.textBlocks) {
            for (l in b.lines) {
                val cp = l.cornerPoints
                if (cp != null) for (p in cp) pts.add(PointF(p.x / sc, p.y / sc))
            }
        }
    }
    if (pts.size < 12) return docEnhance(src, bw)
    var tl = pts[0]
    var tr = pts[0]
    var br = pts[0]
    var bl = pts[0]
    for (p in pts) {
        if (p.x + p.y < tl.x + tl.y) tl = p
        if (p.x - p.y > tr.x - tr.y) tr = p
        if (p.x + p.y > br.x + br.y) br = p
        if (p.y - p.x > bl.y - bl.x) bl = p
    }
    val cx = (tl.x + tr.x + br.x + bl.x) / 4f
    val cy = (tl.y + tr.y + br.y + bl.y) / 4f
    val mw = src.width.toFloat()
    val mh = src.height.toFloat()
    fun ex(p: PointF) = PointF((cx + (p.x - cx) * 1.08f).coerceIn(0f, mw), (cy + (p.y - cy) * 1.08f).coerceIn(0f, mh))
    val a = ex(tl)
    val b = ex(tr)
    val c = ex(br)
    val d = ex(bl)
    val wOut = max(dist(a, b), dist(d, c))
    val hOut = max(dist(a, d), dist(b, c))
    if (wOut < 300f || hOut < 300f) return docEnhance(src, bw)
    val k = min(1f, 3200f / max(wOut, hOut))
    val ow = max(2, (wOut * k).toInt())
    val oh = max(2, (hOut * k).toInt())
    val m = Matrix()
    val s = floatArrayOf(a.x, a.y, b.x, b.y, c.x, c.y, d.x, d.y)
    val t = floatArrayOf(0f, 0f, ow.toFloat(), 0f, ow.toFloat(), oh.toFloat(), 0f, oh.toFloat())
    if (!m.setPolyToPoly(s, 0, t, 0, 4)) return docEnhance(src, bw)
    val flat = Bitmap.createBitmap(ow, oh, Bitmap.Config.ARGB_8888)
    GCanvas(flat).drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG))
    return docEnhance(flat, bw)
}

fun toneMap(src: Bitmap, hdr: Boolean, ai: Boolean): Bitmap {
    val w = src.width
    val h = src.height
    val px = IntArray(w * h)
    src.getPixels(px, 0, w, 0, 0, w, h)
    val gain = FloatArray(256) { i ->
        val y = i / 255f
        if (hdr) 1f + 0.6f * Math.pow((1f - y).toDouble(), 2.2).toFloat() - 0.15f * y * y * y * y else 1f
    }
    for (i in px.indices) {
        val p = px[i]
        val r = (p shr 16) and 255
        val g = (p shr 8) and 255
        val b = p and 255
        val y = (r * 77 + g * 150 + b * 29) shr 8
        val k = gain[y]
        var rf = r * k
        var gf = g * k
        var bf = b * k
        if (ai) {
            val yy = 0.299f * rf + 0.587f * gf + 0.114f * bf
            rf = yy + (rf - yy) * 1.18f
            gf = yy + (gf - yy) * 1.18f
            bf = yy + (bf - yy) * 1.18f
            rf = (rf - 128f) * 1.05f + 128f
            gf = (gf - 128f) * 1.05f + 128f
            bf = (bf - 128f) * 1.05f + 128f
        }
        val ri = rf.toInt().coerceIn(0, 255)
        val gi = gf.toInt().coerceIn(0, 255)
        val bi = bf.toInt().coerceIn(0, 255)
        px[i] = (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
    }
    return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
}

fun drawWatermark(src: Bitmap, text: String, showDate: Boolean, pos: Int): Bitmap {
    val out = src.copy(Bitmap.Config.ARGB_8888, true)
    val c = GCanvas(out)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.textSize = out.width * 0.035f
    p.color = GColor.WHITE
    p.setShadowLayer(5f, 0f, 2f, GColor.BLACK)
    val lines = ArrayList<String>()
    if (text.isNotBlank()) lines.add(text)
    if (showDate) lines.add(SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date()))
    val margin = out.width * 0.03f
    var y = out.height - margin
    for (i in lines.indices.reversed()) {
        val line = lines[i]
        val tw = p.measureText(line)
        val x = when (pos) {
            1 -> out.width - margin - tw
            2 -> (out.width - tw) / 2f
            else -> margin
        }
        c.drawText(line, x, y, p)
        y -= p.textSize * 1.25f
    }
    return out
}

fun motionPhotoPack(jpeg: ByteArray, mp4: ByteArray, presentationUs: Long): ByteArray {
    if (jpeg.size < 4 || jpeg[0] != 0xFF.toByte() || jpeg[1] != 0xD8.toByte()) return jpeg
    val len = mp4.size
    val xmp = "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\" x:xmptk=\"Adobe XMP Core 5.1.0-jc003\">" +
        "<rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">" +
        "<rdf:Description rdf:about=\"\" xmlns:GCamera=\"http://ns.google.com/photos/1.0/camera/\" " +
        "xmlns:Container=\"http://ns.google.com/photos/1.0/container/\" " +
        "xmlns:Item=\"http://ns.google.com/photos/1.0/container/item/\" " +
        "GCamera:MotionPhoto=\"1\" GCamera:MotionPhotoVersion=\"1\" " +
        "GCamera:MotionPhotoPresentationTimestampUs=\"$presentationUs\" " +
        "GCamera:MicroVideo=\"1\" GCamera:MicroVideoVersion=\"1\" GCamera:MicroVideoOffset=\"$len\" " +
        "GCamera:MicroVideoPresentationTimestampUs=\"$presentationUs\">" +
        "<Container:Directory><rdf:Seq>" +
        "<rdf:li rdf:parseType=\"Resource\"><Container:Item Item:Mime=\"image/jpeg\" Item:Semantic=\"Primary\" Item:Length=\"0\" Item:Padding=\"0\"/></rdf:li>" +
        "<rdf:li rdf:parseType=\"Resource\"><Container:Item Item:Mime=\"video/mp4\" Item:Semantic=\"MotionPhoto\" Item:Length=\"$len\" Item:Padding=\"0\"/></rdf:li>" +
        "</rdf:Seq></Container:Directory></rdf:Description></rdf:RDF></x:xmpmeta>"
    val header = "http://ns.adobe.com/xap/1.0/\u0000".toByteArray(Charsets.ISO_8859_1)
    val body = xmp.toByteArray(Charsets.UTF_8)
    val segLen = 2 + header.size + body.size
    val bos = ByteArrayOutputStream(jpeg.size + len + segLen + 8)
    bos.write(jpeg, 0, 2)
    bos.write(0xFF)
    bos.write(0xE1)
    bos.write((segLen shr 8) and 255)
    bos.write(segLen and 255)
    bos.write(header)
    bos.write(body)
    bos.write(jpeg, 2, jpeg.size - 2)
    bos.write(mp4)
    return bos.toByteArray()
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
