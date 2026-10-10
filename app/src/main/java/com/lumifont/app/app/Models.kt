package com.lumifont.app

import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.graphics.PathParser
import java.util.IdentityHashMap
import kotlin.math.abs

data class Adj(
    val exposure: Float = 0f, val contrast: Float = 0f, val saturation: Float = 0f,
    val temp: Float = 0f, val tint: Float = 0f
)

/** A vector icon (Google Material Symbols) kept as path data so it stays sharp at any size. */
class IconSvg(val d: List<String>, val vbX: Float, val vbY: Float, val vbW: Float, val vbH: Float) {
    val paths: List<android.graphics.Path> by lazy {
        d.mapNotNull { runCatching { PathParser.createPathFromPathData(it) }.getOrNull() }
    }
}

sealed class Layer {
    abstract val id: Int
    abstract val cx: Float
    abstract val cy: Float
    abstract val rot: Float
    abstract val opacity: Float
}

/** All "px" values are expressed for a 1080px-wide image and scale with the image. */
data class TextLayer(
    override val id: Int,
    val text: String = "Your text",
    override val cx: Float = 0.5f,
    override val cy: Float = 0.5f,
    val size: Float = 0.08f,
    val color: Int = 0xFFFFFFFF.toInt(),
    val font: String? = null,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val align: Int = 1,
    val spacing: Float = 0f,
    override val rot: Float = 0f,
    override val opacity: Float = 1f,
    val outlineW: Float = 0f,
    val outlineColor: Int = 0xFF000000.toInt(),
    val shadowOn: Boolean = false,
    val shadowColor: Int = 0xCC000000.toInt(),
    val shadowBlur: Float = 8f,
    val shadowDx: Float = 4f,
    val shadowDy: Float = 4f,
    val boxOn: Boolean = false,
    val boxColor: Int = 0xFF000000.toInt(),
    val boxOpacity: Float = 0.6f,
    val boxPadX: Float = 24f,
    val boxPadY: Float = 12f,
    val boxRadius: Float = 16f,
    val boxBorderW: Float = 0f,
    val boxBorderColor: Int = 0xFFFFFFFF.toInt()
) : Layer()

data class PhotoLayer(
    override val id: Int,
    val uri: Uri,
    val aspect: Float,
    override val cx: Float = 0.5f,
    override val cy: Float = 0.5f,
    val size: Float = 0.5f,
    override val rot: Float = 0f,
    override val opacity: Float = 1f
) : Layer()

data class IconLayer(
    override val id: Int,
    val name: String,
    val svg: IconSvg,
    val color: Int = 0xFFFFFFFF.toInt(),
    override val cx: Float = 0.5f,
    override val cy: Float = 0.5f,
    val size: Float = 0.2f,
    override val rot: Float = 0f,
    override val opacity: Float = 1f
) : Layer()

fun Layer.placed(cx: Float, cy: Float, rot: Float): Layer = when (this) {
    is TextLayer -> copy(cx = cx, cy = cy, rot = rot)
    is PhotoLayer -> copy(cx = cx, cy = cy, rot = rot)
    is IconLayer -> copy(cx = cx, cy = cy, rot = rot)
}

fun Layer.withOpacity(v: Float): Layer = when (this) {
    is TextLayer -> copy(opacity = v)
    is PhotoLayer -> copy(opacity = v)
    is IconLayer -> copy(opacity = v)
}

fun Layer.sizeVal(): Float = when (this) {
    is TextLayer -> size
    is PhotoLayer -> size
    is IconLayer -> size
}

fun Layer.withSize(v: Float): Layer = when (this) {
    is TextLayer -> copy(size = v)
    is PhotoLayer -> copy(size = v)
    is IconLayer -> copy(size = v)
}

/** Multiplies every linear dimension by k (used by pinch-zoom and when the image is cropped/rotated). */
fun Layer.rescaled(k: Float): Layer = when (this) {
    is TextLayer -> copy(
        size = size * k, spacing = spacing * k, outlineW = outlineW * k,
        shadowBlur = shadowBlur * k, shadowDx = shadowDx * k, shadowDy = shadowDy * k,
        boxPadX = boxPadX * k, boxPadY = boxPadY * k, boxRadius = boxRadius * k, boxBorderW = boxBorderW * k
    )
    is PhotoLayer -> copy(size = size * k)
    is IconLayer -> copy(size = size * k)
}

data class EditorState(
    val base: Bitmap? = null,
    val adj: Adj = Adj(),
    val layers: List<Layer> = emptyList(),
    val nextId: Int = 1
)

sealed class Geo {
    object RotL : Geo()
    object RotR : Geo()
    object FlipH : Geo()
    object FlipV : Geo()
    class Crop(val l: Float, val t: Float, val r: Float, val b: Float) : Geo()
}

private class GeoRes(val bmp: Bitmap, val map: (Float, Float) -> Pair<Float, Float>, val k: Float, val rot: (Float) -> Float)

fun EditorState.applyGeo(op: Geo): EditorState {
    val b = base ?: return this
    val bw = b.width
    val bh = b.height
    val res: GeoRes = when (op) {
        is Geo.RotR -> GeoRes(
            Bitmap.createBitmap(b, 0, 0, bw, bh, Matrix().apply { postRotate(90f) }, true),
            { x, y -> Pair(1f - y, x) }, bw.toFloat() / bh, { it + 90f }
        )
        is Geo.RotL -> GeoRes(
            Bitmap.createBitmap(b, 0, 0, bw, bh, Matrix().apply { postRotate(-90f) }, true),
            { x, y -> Pair(y, 1f - x) }, bw.toFloat() / bh, { it - 90f }
        )
        is Geo.FlipH -> GeoRes(
            Bitmap.createBitmap(b, 0, 0, bw, bh, Matrix().apply { postScale(-1f, 1f) }, true),
            { x, y -> Pair(1f - x, y) }, 1f, { -it }
        )
        is Geo.FlipV -> GeoRes(
            Bitmap.createBitmap(b, 0, 0, bw, bh, Matrix().apply { postScale(1f, -1f) }, true),
            { x, y -> Pair(x, 1f - y) }, 1f, { -it }
        )
        is Geo.Crop -> {
            val x = (op.l * bw).toInt().coerceIn(0, bw - 1)
            val y = (op.t * bh).toInt().coerceIn(0, bh - 1)
            val w = ((op.r - op.l) * bw).toInt().coerceIn(1, bw - x)
            val h = ((op.b - op.t) * bh).toInt().coerceIn(1, bh - y)
            val ln = x.toFloat() / bw
            val tn = y.toFloat() / bh
            val wn = w.toFloat() / bw
            val hn = h.toFloat() / bh
            GeoRes(
                Bitmap.createBitmap(b, x, y, w, h),
                { px, py -> Pair((px - ln) / wn, (py - tn) / hn) }, 1f / wn, { it }
            )
        }
    }
    val newLayers = layers.map { l ->
        val (nx, ny) = res.map(l.cx, l.cy)
        l.placed(nx, ny, res.rot(l.rot)).rescaled(res.k)
    }
    return copy(base = res.bmp, layers = newLayers)
}

/** Holds the editable document plus a snapshot-based undo/redo history. */
class EditorModel {
    var s by mutableStateOf(EditorState())
    var sel by mutableIntStateOf(-1)
    var canUndo by mutableStateOf(false)
    var canRedo by mutableStateOf(false)

    private val undoS = ArrayList<EditorState>()
    private val redoS = ArrayList<EditorState>()
    private var lastKey: String? = null
    private var lastT = 0L

    val selected: Layer? get() = s.layers.firstOrNull { it.id == sel }

    fun open(b: Bitmap) {
        undoS.clear(); redoS.clear()
        s = EditorState(base = b)
        sel = -1; lastKey = null; sync()
    }

    /** Edits sharing the same [key] within 1.5s are merged into one undo step (sliders, typing, dragging). */
    fun change(key: String? = null, f: (EditorState) -> EditorState) {
        val now = SystemClock.uptimeMillis()
        if (key == null || key != lastKey || now - lastT > 1500) pushUndo()
        lastKey = key
        lastT = now
        s = f(s)
    }

    fun endGesture() { lastKey = null }

    private fun pushUndo() {
        undoS.add(s); redoS.clear()
        while (undoS.size > 40) undoS.removeAt(0)
        while (undoS.size > 1 && distinctBitmaps() > 4) undoS.removeAt(0)
        sync()
    }

    private fun distinctBitmaps(): Int {
        val set = IdentityHashMap<Bitmap, Boolean>()
        for (st in undoS) st.base?.let { set[it] = true }
        for (st in redoS) st.base?.let { set[it] = true }
        s.base?.let { set[it] = true }
        return set.size
    }

    private fun sync() { canUndo = undoS.isNotEmpty(); canRedo = redoS.isNotEmpty() }

    private fun fixSel() { if (s.layers.none { it.id == sel }) sel = -1 }

    fun undo() {
        if (undoS.isEmpty()) return
        redoS.add(s); s = undoS.removeAt(undoS.lastIndex)
        lastKey = null; fixSel(); sync()
    }

    fun redo() {
        if (redoS.isEmpty()) return
        undoS.add(s); s = redoS.removeAt(redoS.lastIndex)
        lastKey = null; fixSel(); sync()
    }

    fun setAdj(key: String, f: (Adj) -> Adj) = change(key) { it.copy(adj = f(it.adj)) }

    fun addLayer(build: (Int) -> Layer) {
        val id = s.nextId
        change { it.copy(layers = it.layers + build(id), nextId = id + 1) }
        sel = id
    }

    fun updateLayer(id: Int, key: String?, f: (Layer) -> Layer) =
        change(key?.let { "$it#$id" }) { st -> st.copy(layers = st.layers.map { if (it.id == id) f(it) else it }) }

    fun updateSel(key: String?, f: (Layer) -> Layer) { if (sel >= 0) updateLayer(sel, key, f) }

    fun updateText(key: String?, f: (TextLayer) -> TextLayer) =
        updateSel(key) { if (it is TextLayer) f(it) else it }

    fun remove(id: Int) {
        change { st -> st.copy(layers = st.layers.filter { it.id != id }) }
        if (sel == id) sel = -1
    }

    fun duplicate(id: Int) {
        val l = s.layers.firstOrNull { it.id == id } ?: return
        val nid = s.nextId
        val nx = (l.cx + 0.04f).coerceAtMost(1f)
        val ny = (l.cy + 0.04f).coerceAtMost(1f)
        val c: Layer = when (l) {
            is TextLayer -> l.copy(id = nid, cx = nx, cy = ny)
            is PhotoLayer -> l.copy(id = nid, cx = nx, cy = ny)
            is IconLayer -> l.copy(id = nid, cx = nx, cy = ny)
        }
        change { it.copy(layers = it.layers + c, nextId = nid + 1) }
        sel = nid
    }

    /** delta +1 = bring forward, -1 = send backward */
    fun reorder(id: Int, delta: Int) {
        val i = s.layers.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j < 0 || j >= s.layers.size) return
        change { st ->
            val m = st.layers.toMutableList()
            val t = m[i]; m[i] = m[j]; m[j] = t
            st.copy(layers = m)
        }
    }

    fun transformLayer(id: Int, dx: Float, dy: Float, zoom: Float, rotDeg: Float) {
        change("xf$id") { st ->
            st.copy(layers = st.layers.map { l ->
                if (l.id != id) l else {
                    var n = l.placed((l.cx + dx).coerceIn(0f, 1f), (l.cy + dy).coerceIn(0f, 1f), l.rot + rotDeg)
                    if (abs(zoom - 1f) > 0.0005f) {
                        val sv = n.sizeVal() * zoom
                        if (sv in 0.01f..4f) n = n.rescaled(zoom)
                    }
                    n
                }
            })
        }
    }

    fun geo(op: Geo) { change { it.applyGeo(op) }; endGesture() }
}
