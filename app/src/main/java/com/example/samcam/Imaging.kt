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

private fun lumInt(p: Int): Int = (((p shr 16) and 255) * 77 + ((p shr 8) and 255) * 150 + (p and 255) * 29) shr 8

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
                val dx = ((x + 0.5f) / mw - 0.5f) / 0.42f
                val dy = ((y + 0.5f) / mh - 0.5f) / 0.52f
                val d = kotlin.math.sqrt(dx * dx + dy * dy) * 0.75f
                conf[y * mw + x] = ((1.1f - d) / 0.4f).coerceIn(0f, 1f)
            }
        }
    }
    val soft = FloatArray(conf.size)
    for (y in 0 until mh) {
        for (x in 0 until mw) {
            var sum = 0f
            var cnt = 0
            for (j in -1..1) {
                for (i in -1..1) {
                    val yy = y + j
                    val xx = x + i
                    if (yy in 0 until mh && xx in 0 until mw) {
                        sum += conf[yy * mw + xx]
                        cnt++
                    }
                }
            }
            soft[y * mw + x] = sum / cnt
        }
    }
    conf = soft
    val gp = IntArray(mw * mh) {
        val v = (conf[it] * 255f).toInt().coerceIn(0, 255)
        (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }
    val rr = max(2, mw / 10)
    boxBlur(gp, mw, mh, rr)
    boxBlur(gp, mw, mh, rr)
    val near = FloatArray(mw * mh) { (gp[it] and 255) / 255f }

    val sp = IntArray(w * h)
    src.getPixels(sp, 0, w, 0, 0, w, h)
    fun blurLayer(d: Int): IntArray {
        val bw = max(16, w / d)
        val bh = max(16, h / d)
        val tiny = Bitmap.createScaledBitmap(src, bw, bh, true)
        val tp = IntArray(bw * bh)
        tiny.getPixels(tp, 0, bw, 0, 0, bw, bh)
        boxBlur(tp, bw, bh, 3)
        boxBlur(tp, bw, bh, 3)
        val up = Bitmap.createScaledBitmap(Bitmap.createBitmap(tp, bw, bh, Bitmap.Config.ARGB_8888), w, h, true)
        val arr = IntArray(w * h)
        up.getPixels(arr, 0, w, 0, 0, w, h)
        return arr
    }
    val mild = blurLayer(max(2, div / 2))
    val strong = blurLayer(div)

    val x0 = IntArray(w)
    val x1 = IntArray(w)
    val tx = FloatArray(w)
    for (x in 0 until w) {
        val fx = (x + 0.5f) * mw / w - 0.5f
        val a0 = fx.toInt().coerceIn(0, mw - 1)
        x0[x] = a0
        x1[x] = min(mw - 1, a0 + 1)
        tx[x] = (fx - a0).coerceIn(0f, 1f)
    }
    val out = IntArray(w * h)
    for (y in 0 until h) {
        val fy = (y + 0.5f) * mh / h - 0.5f
        val y0 = fy.toInt().coerceIn(0, mh - 1)
        val y1 = min(mh - 1, y0 + 1)
        val ty = (fy - y0).coerceIn(0f, 1f)
        for (x in 0 until w) {
            val i00 = y0 * mw + x0[x]
            val i10 = y0 * mw + x1[x]
            val i01 = y1 * mw + x0[x]
            val i11 = y1 * mw + x1[x]
            val ctop = conf[i00] + (conf[i10] - conf[i00]) * tx[x]
            val cbot = conf[i01] + (conf[i11] - conf[i01]) * tx[x]
            val cv = ctop + (cbot - ctop) * ty
            val ntop = near[i00] + (near[i10] - near[i00]) * tx[x]
            val nbot = near[i01] + (near[i11] - near[i01]) * tx[x]
            val nv = ntop + (nbot - ntop) * ty
            var al = ((cv - 0.5f) / 0.28f).coerceIn(0f, 1f)
            al = al * al * (3f - 2f * al)
            val i = y * w + x
            if (al >= 0.995f) {
                out[i] = sp[i]
            } else {
                val t = 1f - nv
                val m = mild[i]
                val st = strong[i]
                val br = ((m shr 16) and 255) + ((((st shr 16) and 255) - ((m shr 16) and 255)) * t)
                val bg = ((m shr 8) and 255) + ((((st shr 8) and 255) - ((m shr 8) and 255)) * t)
                val bb2 = (m and 255) + (((st and 255) - (m and 255)) * t)
                val s = sp[i]
                val r = (br + ((((s shr 16) and 255) - br) * al)).toInt().coerceIn(0, 255)
                val g = (bg + ((((s shr 8) and 255) - bg) * al)).toInt().coerceIn(0, 255)
                val b3 = (bb2 + (((s and 255) - bb2) * al)).toInt().coerceIn(0, 255)
                out[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b3
            }
        }
    }
    return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
}

fun docEnhance(src: Bitmap, bw: Boolean): Bitmap {
    val w = src.width
    val h = src.height
    val px = IntArray(w * h)
    src.getPixels(px, 0, w, 0, 0, w, h)
    val lw = max(16, w / 12)
    val lh = max(16, h / 12)
    val small = Bitmap.createScaledBitmap(src, lw, lh, true)
    val sp = IntArray(lw * lh)
    small.getPixels(sp, 0, lw, 0, 0, lw, lh)
    val rad = max(3, lw / 10)
    boxBlur(sp, lw, lh, rad)
    boxBlur(sp, lw, lh, rad)
    val bgl = FloatArray(lw * lh) { lumInt(sp[it]).toFloat() }
    val x0 = IntArray(w)
    val x1 = IntArray(w)
    val tx = FloatArray(w)
    for (x in 0 until w) {
        val fx = (x + 0.5f) * lw / w - 0.5f
        val a0 = fx.toInt().coerceIn(0, lw - 1)
        x0[x] = a0
        x1[x] = min(lw - 1, a0 + 1)
        tx[x] = (fx - a0).coerceIn(0f, 1f)
    }
    val out = IntArray(w * h)
    for (y in 0 until h) {
        val fy = (y + 0.5f) * lh / h - 0.5f
        val y0 = fy.toInt().coerceIn(0, lh - 1)
        val y1 = min(lh - 1, y0 + 1)
        val ty = (fy - y0).coerceIn(0f, 1f)
        for (x in 0 until w) {
            val a = bgl[y0 * lw + x0[x]]
            val b = bgl[y0 * lw + x1[x]]
            val c = bgl[y1 * lw + x0[x]]
            val d = bgl[y1 * lw + x1[x]]
            val top = a + (b - a) * tx[x]
            val bot = c + (d - c) * tx[x]
            val bgv = (top + (bot - top) * ty) * 1.1f
            val gain = 255f / max(60f, bgv)
            val i = y * w + x
            val p = px[i]
            var r = ((p shr 16) and 255) * gain
            var g = ((p shr 8) and 255) * gain
            var bl = (p and 255) * gain
            if (bw) {
                val gr = 0.299f * r + 0.587f * g + 0.114f * bl
                val v = ((gr - 165f) * 2.3f + 215f).coerceIn(0f, 255f)
                r = v
                g = v
                bl = v
            } else {
                r = (r - 150f) * 1.25f + 165f
                g = (g - 150f) * 1.25f + 165f
                bl = (bl - 150f) * 1.25f + 165f
            }
            out[i] = (0xFF shl 24) or (r.toInt().coerceIn(0, 255) shl 16) or (g.toInt().coerceIn(0, 255) shl 8) or bl.toInt().coerceIn(0, 255)
        }
    }
    return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
}

private fun dist(a: PointF, b: PointF): Float = hypot(a.x - b.x, a.y - b.y)

private fun docQuad(src: Bitmap): Array<PointF>? {
    val sw = 320
    val sc = sw.toFloat() / src.width
    val sh = max(8, (src.height * sc).toInt())
    val small = Bitmap.createScaledBitmap(src, sw, sh, true)
    val px = IntArray(sw * sh)
    small.getPixels(px, 0, sw, 0, 0, sw, sh)
    boxBlur(px, sw, sh, 2)
    val g = IntArray(px.size) { lumInt(px[it]) }
    val total = g.size
    val hist = IntArray(256)
    for (v in g) hist[v]++
    var sumAll = 0L
    for (i in 0 until 256) sumAll += i.toLong() * hist[i]
    var wB = 0
    var sumB = 0L
    var best = 0.0
    var thr = 127
    for (t in 0 until 256) {
        wB += hist[t]
        if (wB == 0) continue
        val wF = total - wB
        if (wF == 0) break
        sumB += t.toLong() * hist[t]
        val mB = sumB.toDouble() / wB
        val mF = (sumAll - sumB).toDouble() / wF
        val between = wB.toDouble() * wF * (mB - mF) * (mB - mF)
        if (between > best) {
            best = between
            thr = t
        }
    }
    val mask = BooleanArray(total) { g[it] > thr }
    val visited = BooleanArray(total)
    val queue = IntArray(total)
    var bestArea = 0
    var bTl = 0
    var bTr = 0
    var bBr = 0
    var bBl = 0
    for (start in 0 until total) {
        if (!mask[start] || visited[start]) continue
        var qh = 0
        var qt = 0
        queue[qt++] = start
        visited[start] = true
        var area = 0
        var minS = Int.MAX_VALUE
        var maxS = Int.MIN_VALUE
        var minD = Int.MAX_VALUE
        var maxD = Int.MIN_VALUE
        var tlI = start
        var trI = start
        var brI = start
        var blI = start
        while (qh < qt) {
            val i = queue[qh++]
            area++
            val x = i % sw
            val y = i / sw
            val s = x + y
            val d = x - y
            if (s < minS) { minS = s; tlI = i }
            if (s > maxS) { maxS = s; brI = i }
            if (d > maxD) { maxD = d; trI = i }
            if (d < minD) { minD = d; blI = i }
            if (x > 0) {
                val n = i - 1
                if (mask[n] && !visited[n]) { visited[n] = true; queue[qt++] = n }
            }
            if (x < sw - 1) {
                val n = i + 1
                if (mask[n] && !visited[n]) { visited[n] = true; queue[qt++] = n }
            }
            if (y > 0) {
                val n = i - sw
                if (mask[n] && !visited[n]) { visited[n] = true; queue[qt++] = n }
            }
            if (y < sh - 1) {
                val n = i + sw
                if (mask[n] && !visited[n]) { visited[n] = true; queue[qt++] = n }
            }
        }
        if (area > bestArea) {
            bestArea = area
            bTl = tlI
            bTr = trI
            bBr = brI
            bBl = blI
        }
    }
    if (bestArea < total * 0.18 || bestArea > total * 0.97) return null
    fun pt(i: Int) = PointF((i % sw) / sc, (i / sw) / sc)
    return arrayOf(pt(bTl), pt(bTr), pt(bBr), pt(bBl))
}

private suspend fun textQuad(src: Bitmap, rec: TextRecognizer): Array<PointF>? {
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
    if (pts.size < 12) return null
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
    fun ex(p: PointF) = PointF((cx + (p.x - cx) * 1.1f).coerceIn(0f, mw), (cy + (p.y - cy) * 1.1f).coerceIn(0f, mh))
    return arrayOf(ex(tl), ex(tr), ex(br), ex(bl))
}

private fun warpQuad(src: Bitmap, q: Array<PointF>, shrink: Float): Bitmap? {
    val cx = (q[0].x + q[1].x + q[2].x + q[3].x) / 4f
    val cy = (q[0].y + q[1].y + q[2].y + q[3].y) / 4f
    val p = Array(4) { PointF(cx + (q[it].x - cx) * shrink, cy + (q[it].y - cy) * shrink) }
    val wOut = max(dist(p[0], p[1]), dist(p[3], p[2]))
    val hOut = max(dist(p[0], p[3]), dist(p[1], p[2]))
    if (wOut < 300f || hOut < 300f) return null
    val k = min(1f, 3200f / max(wOut, hOut))
    val ow = max(2, (wOut * k).toInt())
    val oh = max(2, (hOut * k).toInt())
    val m = Matrix()
    val s = floatArrayOf(p[0].x, p[0].y, p[1].x, p[1].y, p[2].x, p[2].y, p[3].x, p[3].y)
    val t = floatArrayOf(0f, 0f, ow.toFloat(), 0f, ow.toFloat(), oh.toFloat(), 0f, oh.toFloat())
    if (!m.setPolyToPoly(s, 0, t, 0, 4)) return null
    val flat = Bitmap.createBitmap(ow, oh, Bitmap.Config.ARGB_8888)
    GCanvas(flat).drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG))
    return flat
}

suspend fun docScan(src: Bitmap, rec: TextRecognizer, bw: Boolean, auto: Boolean): Bitmap {
    if (!auto) return docEnhance(src, bw)
    var flat: Bitmap? = null
    try {
        val q1 = docQuad(src)
        if (q1 != null) flat = warpQuad(src, q1, 0.992f)
    } catch (e: Throwable) {
        flat = null
    }
    if (flat == null) {
        try {
            val q2 = textQuad(src, rec)
            if (q2 != null) flat = warpQuad(src, q2, 1f)
        } catch (e: Throwable) {
            flat = null
        }
    }
    return docEnhance(flat ?: src, bw)
}

fun fuseExposures(bmps: List<Bitmap>): Bitmap? {
    val n = bmps.size
    if (n == 0) return null
    val w = bmps[0].width
    val h = bmps[0].height
    if (n == 1 || bmps.any { it.width != w || it.height != h }) return bmps[0]
    val lw = max(8, w / 8)
    val lh = max(8, h / 8)
    val lum = Array(n) { FloatArray(lw * lh) }
    for (k in 0 until n) {
        val sm = Bitmap.createScaledBitmap(bmps[k], lw, lh, true)
        val p = IntArray(lw * lh)
        sm.getPixels(p, 0, lw, 0, 0, lw, lh)
        boxBlur(p, lw, lh, 2)
        for (i in p.indices) lum[k][i] = lumInt(p[i]) / 255f
    }
    val locLut = FloatArray(256) { i ->
        val l = i / 255.0
        (Math.exp(-((l - 0.5) * (l - 0.5)) / 0.09) + 0.02).toFloat()
    }
    val pixLut = FloatArray(256) { i ->
        val l = i / 255.0
        (Math.exp(-((l - 0.5) * (l - 0.5)) / 0.2) + 0.05).toFloat()
    }
    val pxs = Array(n) { IntArray(w * h) }
    for (k in 0 until n) {
        bmps[k].getPixels(pxs[k], 0, w, 0, 0, w, h)
        bmps[k].recycle()
    }
    val x0 = IntArray(w)
    val x1 = IntArray(w)
    val tx = FloatArray(w)
    for (x in 0 until w) {
        val fx = (x + 0.5f) * lw / w - 0.5f
        val a0 = fx.toInt().coerceIn(0, lw - 1)
        x0[x] = a0
        x1[x] = min(lw - 1, a0 + 1)
        tx[x] = (fx - a0).coerceIn(0f, 1f)
    }
    val out = IntArray(w * h)
    for (y in 0 until h) {
        val fy = (y + 0.5f) * lh / h - 0.5f
        val y0 = fy.toInt().coerceIn(0, lh - 1)
        val y1 = min(lh - 1, y0 + 1)
        val ty = (fy - y0).coerceIn(0f, 1f)
        for (x in 0 until w) {
            val i = y * w + x
            var sw = 0f
            var sr = 0f
            var sg = 0f
            var sb = 0f
            for (k in 0 until n) {
                val L = lum[k]
                val a = L[y0 * lw + x0[x]]
                val b = L[y0 * lw + x1[x]]
                val c = L[y1 * lw + x0[x]]
                val d = L[y1 * lw + x1[x]]
                val top = a + (b - a) * tx[x]
                val bot = c + (d - c) * tx[x]
                val l = top + (bot - top) * ty
                val p = pxs[k][i]
                val r = (p shr 16) and 255
                val g = (p shr 8) and 255
                val bl = p and 255
                val py = (r * 77 + g * 150 + bl * 29) shr 8
                val wgt = locLut[(l * 255f).toInt().coerceIn(0, 255)] * pixLut[py]
                sw += wgt
                sr += wgt * r
                sg += wgt * g
                sb += wgt * bl
            }
            var r = sr / sw
            var g = sg / sw
            var b = sb / sw
            val yy = 0.299f * r + 0.587f * g + 0.114f * b
            r = yy + (r - yy) * 1.12f
            g = yy + (g - yy) * 1.12f
            b = yy + (b - yy) * 1.12f
            r = (r - 128f) * 1.06f + 128f
            g = (g - 128f) * 1.06f + 128f
            b = (b - 128f) * 1.06f + 128f
            out[i] = (0xFF shl 24) or (r.toInt().coerceIn(0, 255) shl 16) or (g.toInt().coerceIn(0, 255) shl 8) or b.toInt().coerceIn(0, 255)
        }
    }
    return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
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
