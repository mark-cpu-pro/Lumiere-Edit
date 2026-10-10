package com.lumifont.app

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.runtime.mutableStateMapOf
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

// ---------- image loading ----------

/** maxSide = 0 keeps native resolution. */
fun decodeBitmap(ctx: Context, uri: Uri, maxSide: Int): Bitmap? = runCatching {
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { dec, info, _ ->
        dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val big = max(info.size.width, info.size.height)
        if (maxSide in 1 until big) {
            val k = maxSide.toFloat() / big
            dec.setTargetSize(
                (info.size.width * k).roundToInt().coerceAtLeast(1),
                (info.size.height * k).roundToInt().coerceAtLeast(1)
            )
        }
    }
}.getOrNull()

/** Light previews of overlay pictures (the full-quality file is re-read from disk when saving). */
object PhotoCache {
    val map = mutableStateMapOf<String, Bitmap>()
}

interface PhotoSource {
    fun get(l: PhotoLayer, targetPx: Int): Bitmap?
    fun release(b: Bitmap) {}
}

object PreviewPhotos : PhotoSource {
    override fun get(l: PhotoLayer, targetPx: Int): Bitmap? = PhotoCache.map[l.uri.toString()]
}

class ExportPhotos(private val ctx: Context) : PhotoSource {
    // Native resolution, but never more pixels than the picture will actually occupy.
    override fun get(l: PhotoLayer, targetPx: Int): Bitmap? = decodeBitmap(ctx, l.uri, targetPx)
    override fun release(b: Bitmap) { b.recycle() }
}

// ---------- colour ----------

fun adjMatrix(a: Adj): FloatArray {
    val m = ColorMatrix()
    m.setSaturation((1f + a.saturation).coerceAtLeast(0f))
    val e = 2f.pow(a.exposure)
    val c = 1f + a.contrast
    val sr = e * c * (1f + a.temp * 0.25f)
    val sg = e * c * (1f - a.tint * 0.2f)
    val sb = e * c * (1f - a.temp * 0.25f)
    val t = (-0.5f * c + 0.5f) * 255f
    m.postConcat(
        ColorMatrix(
            floatArrayOf(
                sr, 0f, 0f, 0f, t,
                0f, sg, 0f, 0f, t,
                0f, 0f, sb, 0f, t,
                0f, 0f, 0f, 1f, 0f
            )
        )
    )
    return m.array
}

// ---------- text geometry ----------

private class TextGeom(val lines: List<String>, val lw: FloatArray, val lineH: Float, val blockW: Float, val blockH: Float)

private fun textPaint(l: TextLayer, w: Float): Paint {
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.textSize = (l.size * w).coerceAtLeast(1f)
    val base = Fonts.cache[l.font] ?: Typeface.DEFAULT
    val style = when {
        l.bold && l.italic -> Typeface.BOLD_ITALIC
        l.bold -> Typeface.BOLD
        l.italic -> Typeface.ITALIC
        else -> Typeface.NORMAL
    }
    p.typeface = Typeface.create(base, style)
    p.letterSpacing = (l.spacing * w / 1080f) / p.textSize // Paint wants em units; the UI works in px
    return p
}

private fun textGeom(l: TextLayer, p: Paint, w: Float): TextGeom {
    val fm = p.fontMetrics
    val lineH = fm.descent - fm.ascent + fm.leading
    val lines = l.text.split("\n")
    val sp = l.spacing * w / 1080f
    val lw = FloatArray(lines.size) { i ->
        if (lines[i].isEmpty()) 0f else max(0f, p.measureText(lines[i]) - sp)
    }
    val bw = max(lw.maxOrNull() ?: 0f, p.textSize * 0.5f)
    return TextGeom(lines, lw, lineH, bw, lineH * lines.size)
}

fun halfExtent(l: Layer, w: Float): Pair<Float, Float> = when (l) {
    is TextLayer -> {
        val g = textGeom(l, textPaint(l, w), w)
        val u = w / 1080f
        val px = if (l.boxOn) l.boxPadX * u else 0f
        val py = if (l.boxOn) l.boxPadY * u else 0f
        Pair(g.blockW / 2 + px, g.blockH / 2 + py)
    }
    is PhotoLayer -> Pair(l.size * w / 2, l.size * w / l.aspect / 2)
    is IconLayer -> {
        val dw = l.size * w
        Pair(dw / 2, dw * l.svg.vbH / l.svg.vbW / 2)
    }
}

fun hitTest(layers: List<Layer>, w: Float, h: Float, x: Float, y: Float, slop: Float): Layer? {
    for (l in layers.asReversed()) {
        val (hx, hy) = halfExtent(l, w)
        val dx = x - l.cx * w
        val dy = y - l.cy * h
        val r = Math.toRadians(l.rot.toDouble())
        val cs = cos(r).toFloat()
        val sn = sin(r).toFloat()
        val lx = dx * cs + dy * sn
        val ly = -dx * sn + dy * cs
        if (abs(lx) <= hx + slop && abs(ly) <= hy + slop) return l
    }
    return null
}

// ---------- drawing (used by BOTH the live preview and the saved file) ----------

fun drawScene(c: Canvas, w: Float, h: Float, base: Bitmap, adj: Adj, layers: List<Layer>, photos: PhotoSource) {
    val p = Paint(Paint.FILTER_BITMAP_FLAG)
    p.colorFilter = ColorMatrixColorFilter(adjMatrix(adj))
    c.drawBitmap(base, null, RectF(0f, 0f, w, h), p)
    for (l in layers) {
        when (l) {
            is TextLayer -> drawTextLayer(c, l, w, h)
            is PhotoLayer -> drawPhotoLayer(c, l, w, h, photos)
            is IconLayer -> drawIconLayer(c, l, w, h)
        }
    }
}

private fun withAlpha(color: Int, a: Float): Int {
    val na = ((color ushr 24) * a.coerceIn(0f, 1f)).roundToInt()
    return (na shl 24) or (color and 0xFFFFFF)
}

fun drawTextLayer(c: Canvas, l: TextLayer, w: Float, h: Float) {
    if (l.text.isEmpty() && !l.boxOn) return
    val u = w / 1080f
    val p = textPaint(l, w)
    val g = textGeom(l, p, w)
    val fm = p.fontMetrics
    val px = if (l.boxOn) l.boxPadX * u else 0f
    val py = if (l.boxOn) l.boxPadY * u else 0f
    val bw = g.blockW + 2 * px
    val bh = g.blockH + 2 * py
    val margin = (l.outlineW + l.shadowBlur + max(abs(l.shadowDx), abs(l.shadowDy)) + l.boxBorderW + 8f) * u

    c.save()
    c.translate(l.cx * w, l.cy * h)
    c.rotate(l.rot)
    val alpha = (l.opacity.coerceIn(0f, 1f) * 255).roundToInt()
    val layered = alpha < 255
    if (layered) c.saveLayerAlpha(-bw / 2 - margin, -bh / 2 - margin, bw / 2 + margin, bh / 2 + margin, alpha)

    if (l.boxOn) {
        val r = RectF(-bw / 2, -bh / 2, bw / 2, bh / 2)
        val rad = min(l.boxRadius * u, min(bw, bh) / 2)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        fill.style = Paint.Style.FILL
        fill.color = withAlpha(l.boxColor, l.boxOpacity)
        c.drawRoundRect(r, rad, rad, fill)
        if (l.boxBorderW > 0f) {
            val st = Paint(Paint.ANTI_ALIAS_FLAG)
            st.style = Paint.Style.STROKE
            st.strokeWidth = l.boxBorderW * u
            st.color = l.boxBorderColor
            val half = st.strokeWidth / 2
            c.drawRoundRect(RectF(r.left + half, r.top + half, r.right - half, r.bottom - half), rad, rad, st)
        }
    }

    fun drawLines(paint: Paint) {
        for (i in g.lines.indices) {
            val x = when (l.align) {
                0 -> -g.blockW / 2
                2 -> g.blockW / 2 - g.lw[i]
                else -> -g.lw[i] / 2
            }
            val y = -g.blockH / 2 - fm.ascent + i * g.lineH
            c.drawText(g.lines[i], x, y, paint)
        }
    }

    if (l.shadowOn) {
        val sp = Paint(p)
        sp.color = l.color
        sp.style = if (l.outlineW > 0f) Paint.Style.FILL_AND_STROKE else Paint.Style.FILL
        sp.strokeWidth = l.outlineW * u * 2
        sp.strokeJoin = Paint.Join.ROUND
        sp.setShadowLayer(max(l.shadowBlur * u, 0.01f), l.shadowDx * u, l.shadowDy * u, l.shadowColor)
        drawLines(sp)
    }
    if (l.outlineW > 0f) {
        val op = Paint(p)
        op.style = Paint.Style.STROKE
        op.strokeWidth = l.outlineW * u * 2
        op.strokeJoin = Paint.Join.ROUND
        op.color = l.outlineColor
        drawLines(op)
    }
    val fp = Paint(p)
    fp.style = Paint.Style.FILL
    fp.color = l.color
    drawLines(fp)

    if (layered) c.restore()
    c.restore()
}

fun drawPhotoLayer(c: Canvas, l: PhotoLayer, w: Float, h: Float, src: PhotoSource) {
    val dw = l.size * w
    val dh = dw / l.aspect
    val bmp = src.get(l, max(dw, dh).roundToInt().coerceAtLeast(1)) ?: return
    val p = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    p.alpha = (l.opacity.coerceIn(0f, 1f) * 255).roundToInt()
    c.save()
    c.translate(l.cx * w, l.cy * h)
    c.rotate(l.rot)
    c.drawBitmap(bmp, null, RectF(-dw / 2, -dh / 2, dw / 2, dh / 2), p)
    c.restore()
    src.release(bmp)
}

fun drawSvgFit(c: Canvas, svg: IconSvg, cx: Float, cy: Float, sizePx: Float, color: Int) {
    val sc = sizePx / svg.vbW
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.style = Paint.Style.FILL
    p.color = color
    c.save()
    c.translate(cx, cy)
    c.scale(sc, sc)
    c.translate(-(svg.vbX + svg.vbW / 2), -(svg.vbY + svg.vbH / 2))
    for (path in svg.paths) c.drawPath(path, p)
    c.restore()
}

fun drawIconLayer(c: Canvas, l: IconLayer, w: Float, h: Float) {
    val dw = l.size * w
    val dh = dw * l.svg.vbH / l.svg.vbW
    c.save()
    c.translate(l.cx * w, l.cy * h)
    c.rotate(l.rot)
    val alpha = (l.opacity.coerceIn(0f, 1f) * 255).roundToInt()
    val layered = alpha < 255
    if (layered) c.saveLayerAlpha(-dw / 2, -dh / 2, dw / 2, dh / 2, alpha)
    drawSvgFit(c, l.svg, 0f, 0f, dw, l.color)
    if (layered) c.restore()
    c.restore()
}

/** Selection frame, drawn only in the preview (never in the saved image). */
fun drawSelection(c: Canvas, l: Layer, w: Float, h: Float, scale: Float, color: Int) {
    val (hx, hy) = halfExtent(l, w)
    val pad = 6f / scale
    val line = Paint(Paint.ANTI_ALIAS_FLAG)
    line.style = Paint.Style.STROKE
    line.strokeWidth = 2f / scale
    line.color = color
    val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    dot.style = Paint.Style.FILL
    dot.color = 0xFFFFFFFF.toInt()
    c.save()
    c.translate(l.cx * w, l.cy * h)
    c.rotate(l.rot)
    c.drawRoundRect(-hx - pad, -hy - pad, hx + pad, hy + pad, 6f / scale, 6f / scale, line)
    for (sx in listOf(-1f, 1f)) for (sy in listOf(-1f, 1f)) {
        c.drawCircle(sx * (hx + pad), sy * (hy + pad), 5f / scale, dot)
        c.drawCircle(sx * (hx + pad), sy * (hy + pad), 5f / scale, line)
    }
    c.restore()
}

// ---------- export ----------

fun exportImage(ctx: Context, st: EditorState, png: Boolean): Boolean = runCatching {
    val base = st.base!!
    val out = Bitmap.createBitmap(base.width, base.height, Bitmap.Config.ARGB_8888)
    val c = Canvas(out)
    drawScene(c, base.width.toFloat(), base.height.toFloat(), base, st.adj, st.layers, ExportPhotos(ctx))
    val ext = if (png) "png" else "jpg"
    val v = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "Lumifont_${System.currentTimeMillis()}.$ext")
        put(MediaStore.Images.Media.MIME_TYPE, if (png) "image/png" else "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Lumifont")
    }
    val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v)!!
    ctx.contentResolver.openOutputStream(uri)!!.use {
        out.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, if (png) 100 else 95, it)
    }
    true
}.getOrDefault(false)
