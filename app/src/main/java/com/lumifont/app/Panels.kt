package com.lumifont.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RotateLeft
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.FontDownload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.foundation.Canvas
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectDragGestures
import kotlin.math.abs
import kotlin.math.roundToInt

class Actions(
    val openFonts: () -> Unit,
    val openIcons: () -> Unit,
    val pickPhotos: () -> Unit,
    val importFont: () -> Unit,
    val useFont: (String) -> Unit,
    val addIcon: (String, String, Boolean) -> Unit
)

private fun pct(v: Float) = "${(v * 100).roundToInt()}%"
private fun deg(v: Float) = "${v.roundToInt()}°"
private fun px(v: Float) = "${v.roundToInt()} px"

// ---------------- Adjust ----------------
@Composable
fun AdjustPanel(m: EditorModel) {
    val a = m.s.adj
    Column {
        LSlider("Exposure", a.exposure, -1f..1f, "%.2f".format(a.exposure), { v -> m.setAdj("exp") { it.copy(exposure = v) } }, m::endGesture)
        LSlider("Contrast", a.contrast, -1f..1f, pct(a.contrast), { v -> m.setAdj("con") { it.copy(contrast = v) } }, m::endGesture)
        LSlider("Saturation", a.saturation, -1f..1f, pct(a.saturation), { v -> m.setAdj("sat") { it.copy(saturation = v) } }, m::endGesture)
        LSlider("Temperature", a.temp, -1f..1f, pct(a.temp), { v -> m.setAdj("tmp") { it.copy(temp = v) } }, m::endGesture)
        LSlider("Tint", a.tint, -1f..1f, pct(a.tint), { v -> m.setAdj("tin") { it.copy(tint = v) } }, m::endGesture)
        Spacer(Modifier.height(6.dp))
        SoftPill("Reset colours", Icons.Rounded.Refresh) { m.change { it.copy(adj = Adj()) } }
    }
}

// ---------------- Text ----------------
@Composable
fun TextPanel(m: EditorModel, a: Actions) {
    val t = m.selected as? TextLayer
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            PrimaryPill("Add text", Icons.Rounded.Add, enabled = m.s.base != null) {
                m.addLayer { id -> TextLayer(id = id) }
            }
            if (t != null) {
                SoftPill("Duplicate", Icons.Rounded.ContentCopy) { m.duplicate(t.id) }
                SoftPill("Delete", Icons.Rounded.Delete, danger = true) { m.remove(t.id) }
            }
        }
        if (t == null) {
            Text(
                "Tap Add text, or tap a text on the photo to edit it. Drag to move, pinch to resize and rotate.",
                color = LF.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp)
            )
        } else {
            TextEditor(m, t, a)
        }
    }
}

@Composable
private fun TextEditor(m: EditorModel, t: TextLayer, a: Actions) {
    var sub by remember { mutableIntStateOf(0) }
    val done = m::endGesture
    Column {
        OutlinedTextField(
            value = t.text, onValueChange = { v -> m.updateText("txt") { it.copy(text = v) } },
            label = { Text("Text") }, minLines = 1, maxLines = 3,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = LF.accent, unfocusedBorderColor = LF.outline,
                focusedLabelColor = LF.accent, cursorColor = LF.accent
            )
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Style", "Effects", "Box", "Font").forEachIndexed { i, n -> LChip(n, sub == i) { sub = i } }
        }
        AnimatedContent(
            targetState = sub, label = "textsub",
            transitionSpec = { fadeIn() togetherWith fadeOut() }
        ) { s ->
            Column {
                when (s) {
                    0 -> {
                        LSlider("Size", t.size, 0.02f..0.4f, pct(t.size), { v -> m.updateText("size") { it.copy(size = v) } }, done)
                        LSlider("Letter spacing", t.spacing, -5f..40f, px(t.spacing), { v -> m.updateText("spacing") { it.copy(spacing = v) } }, done)
                        LSlider("Rotation", t.rot, -180f..180f, deg(t.rot), { v -> m.updateSel("rot") { it.placed(it.cx, it.cy, v) } }, done)
                        LSlider("Opacity", t.opacity, 0f..1f, pct(t.opacity), { v -> m.updateSel("op") { it.withOpacity(v) } }, done)
                        Text("Colour", color = LF.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                        ColorPicker("c${t.id}", t.color, { c -> m.updateText("color") { it.copy(color = c) } }, done)
                        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LChip("Bold", t.bold) { m.updateText(null) { it.copy(bold = !it.bold) } }
                            LChip("Italic", t.italic) { m.updateText(null) { it.copy(italic = !it.italic) } }
                            LChip("Left", t.align == 0) { m.updateText(null) { it.copy(align = 0) } }
                            LChip("Center", t.align == 1) { m.updateText(null) { it.copy(align = 1) } }
                            LChip("Right", t.align == 2) { m.updateText(null) { it.copy(align = 2) } }
                        }
                    }
                    1 -> {
                        LSlider("Outline width", t.outlineW, 0f..24f, px(t.outlineW), { v -> m.updateText("ow") { it.copy(outlineW = v) } }, done)
                        if (t.outlineW > 0f) {
                            Text("Outline colour", color = LF.muted, fontSize = 12.sp)
                            ColorPicker("oc${t.id}", t.outlineColor, { c -> m.updateText("occ") { it.copy(outlineColor = c) } }, done)
                        }
                        LSwitch("Shadow", t.shadowOn) { v -> m.updateText(null) { it.copy(shadowOn = v) } }
                        if (t.shadowOn) {
                            LSlider("Blur", t.shadowBlur, 0f..60f, px(t.shadowBlur), { v -> m.updateText("sb") { it.copy(shadowBlur = v) } }, done)
                            LSlider("Offset X", t.shadowDx, -60f..60f, px(t.shadowDx), { v -> m.updateText("sx") { it.copy(shadowDx = v) } }, done)
                            LSlider("Offset Y", t.shadowDy, -60f..60f, px(t.shadowDy), { v -> m.updateText("sy") { it.copy(shadowDy = v) } }, done)
                            Text("Shadow colour", color = LF.muted, fontSize = 12.sp)
                            ColorPicker("sc${t.id}", t.shadowColor, { c -> m.updateText("scc") { it.copy(shadowColor = c) } }, done)
                        }
                    }
                    2 -> {
                        LSwitch("Background box", t.boxOn) { v -> m.updateText(null) { it.copy(boxOn = v) } }
                        if (t.boxOn) {
                            Text("Fill colour", color = LF.muted, fontSize = 12.sp)
                            ColorPicker("bc${t.id}", t.boxColor, { c -> m.updateText("bcc") { it.copy(boxColor = c) } }, done)
                            LSlider("Fill opacity", t.boxOpacity, 0f..1f, pct(t.boxOpacity), { v -> m.updateText("bo") { it.copy(boxOpacity = v) } }, done)
                            LSlider("Padding left/right", t.boxPadX, 0f..160f, px(t.boxPadX), { v -> m.updateText("bpx") { it.copy(boxPadX = v) } }, done)
                            LSlider("Padding top/bottom", t.boxPadY, 0f..160f, px(t.boxPadY), { v -> m.updateText("bpy") { it.copy(boxPadY = v) } }, done)
                            LSlider("Corner radius", t.boxRadius, 0f..160f, px(t.boxRadius), { v -> m.updateText("br") { it.copy(boxRadius = v) } }, done)
                            LSlider("Border width", t.boxBorderW, 0f..24f, px(t.boxBorderW), { v -> m.updateText("bbw") { it.copy(boxBorderW = v) } }, done)
                            if (t.boxBorderW > 0f) {
                                Text("Border colour", color = LF.muted, fontSize = 12.sp)
                                ColorPicker("bb${t.id}", t.boxBorderColor, { c -> m.updateText("bbc") { it.copy(boxBorderColor = c) } }, done)
                            }
                        }
                    }
                    else -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            PrimaryPill("Browse Google Fonts", Icons.Rounded.Search) { a.openFonts() }
                            SoftPill("Import .ttf / .otf", Icons.Rounded.UploadFile) { a.importFont() }
                        }
                        Text("Your fonts", color = LF.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                        val names = (Fonts.custom.toList() + Fonts.popular + Fonts.cache.keys).distinct()
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(names) { n ->
                                val have = Fonts.cache.containsKey(n)
                                LChip(if (have) n else "$n \u2193", t.font == n, Fonts.compose(n)) { a.useFont(n) }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------- Pictures ----------------
@Composable
fun PhotosPanel(m: EditorModel, a: Actions) {
    val photos = m.s.layers.filterIsInstance<PhotoLayer>()
    val l = m.selected as? PhotoLayer
    val done = m::endGesture
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            PrimaryPill("Add pictures", Icons.Rounded.AddPhotoAlternate, enabled = m.s.base != null && photos.size < 20) { a.pickPhotos() }
            Text("${photos.size} / 20", color = LF.muted, fontSize = 13.sp)
        }
        if (photos.isEmpty()) {
            Text(
                "Add up to 20 pictures on top of your photo. They keep their full quality when you save. Drag to move, pinch to resize and rotate.",
                color = LF.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp)
            )
        } else {
            LazyRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(photos, key = { it.id }) { p -> LayerChip(p, p.id == m.sel, { m.sel = p.id }, { m.remove(p.id) }) }
            }
        }
        if (l != null) {
            LSlider("Size", l.size, 0.05f..3f, pct(l.size), { v -> m.updateSel("size") { it.withSize(v) } }, done)
            LSlider("Rotation", l.rot, -180f..180f, deg(l.rot), { v -> m.updateSel("rot") { it.placed(it.cx, it.cy, v) } }, done)
            LSlider("Opacity", l.opacity, 0f..1f, pct(l.opacity), { v -> m.updateSel("op") { it.withOpacity(v) } }, done)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoftPill("Duplicate", Icons.Rounded.ContentCopy, enabled = photos.size < 20) { m.duplicate(l.id) }
                SoftPill("Remove", Icons.Rounded.Delete, danger = true) { m.remove(l.id) }
            }
        }
    }
}

// ---------------- Icons ----------------
@Composable
fun SymbolsPanel(m: EditorModel, a: Actions) {
    val icons = m.s.layers.filterIsInstance<IconLayer>()
    val l = m.selected as? IconLayer
    val done = m::endGesture
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryPill("Browse Google Icons", Icons.Rounded.Search, enabled = m.s.base != null) { a.openIcons() }
        }
        if (icons.isEmpty()) {
            Text(
                "Pick any icon from Google's Material Symbols. They are vectors, so they stay sharp at any size and colour.",
                color = LF.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp)
            )
        } else {
            LazyRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(icons, key = { it.id }) { p -> LayerChip(p, p.id == m.sel, { m.sel = p.id }, { m.remove(p.id) }) }
            }
        }
        if (l != null) {
            LSlider("Size", l.size, 0.03f..1.5f, pct(l.size), { v -> m.updateSel("size") { it.withSize(v) } }, done)
            LSlider("Rotation", l.rot, -180f..180f, deg(l.rot), { v -> m.updateSel("rot") { it.placed(it.cx, it.cy, v) } }, done)
            LSlider("Opacity", l.opacity, 0f..1f, pct(l.opacity), { v -> m.updateSel("op") { it.withOpacity(v) } }, done)
            Text("Colour", color = LF.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            ColorPicker("ic${l.id}", l.color, { c -> m.updateSel("icol") { if (it is IconLayer) it.copy(color = c) else it } }, done)
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoftPill("Duplicate", Icons.Rounded.ContentCopy) { m.duplicate(l.id) }
                SoftPill("Remove", Icons.Rounded.Delete, danger = true) { m.remove(l.id) }
            }
        }
    }
}

// ---------------- Crop & rotate ----------------
@Composable
fun CropPanel(
    ratio: Float?, imgAspect: Float, onRatio: (Float?) -> Unit,
    onRotL: () -> Unit, onRotR: () -> Unit, onFlipH: () -> Unit, onFlipV: () -> Unit,
    onApply: () -> Unit, onReset: () -> Unit
) {
    Column {
        Text("Shape", color = LF.muted, fontSize = 12.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LChip("Free", ratio == null) { onRatio(null) }
            LChip("Original", ratio == imgAspect) { onRatio(imgAspect) }
            LChip("1:1", ratio == 1f) { onRatio(1f) }
            LChip("4:5", ratio == 0.8f) { onRatio(0.8f) }
            LChip("3:4", ratio == 0.75f) { onRatio(0.75f) }
            LChip("4:3", ratio == 4f / 3f) { onRatio(4f / 3f) }
            LChip("16:9", ratio == 16f / 9f) { onRatio(16f / 9f) }
            LChip("9:16", ratio == 9f / 16f) { onRatio(9f / 16f) }
        }
        Text("Rotate & flip", color = LF.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
            SoftPill("", Icons.Rounded.RotateLeft) { onRotL() }
            SoftPill("", Icons.Rounded.RotateRight) { onRotR() }
            SoftPill("", Icons.Rounded.Flip) { onFlipH() }
            Box(Modifier.rotate(90f)) { SoftPill("", Icons.Rounded.Flip) { onFlipV() } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
            PrimaryPill("Apply crop", Icons.Rounded.Check) { onApply() }
            SoftPill("Reset frame", Icons.Rounded.Refresh) { onReset() }
        }
    }
}

fun cropDrag(r: Rect, h: Int, dx: Float, dy: Float, rn: Float?): Rect {
    val min = 0.08f
    if (h == 4) {
        val w = r.width
        val hh = r.height
        val l = (r.left + dx).coerceIn(0f, 1f - w)
        val t = (r.top + dy).coerceIn(0f, 1f - hh)
        return Rect(l, t, l + w, t + hh)
    }
    val right = h == 1 || h == 3
    val bottom = h == 2 || h == 3
    val ox = if (right) r.left else r.right
    val oy = if (bottom) r.top else r.bottom
    val mx0 = (if (right) r.right else r.left) + dx
    val my0 = (if (bottom) r.bottom else r.top) + dy
    if (rn == null) {
        var mx = mx0.coerceIn(0f, 1f)
        var my = my0.coerceIn(0f, 1f)
        mx = if (right) mx.coerceAtLeast(ox + min) else mx.coerceAtMost(ox - min)
        my = if (bottom) my.coerceAtLeast(oy + min) else my.coerceAtMost(oy - min)
        return Rect(minOf(ox, mx), minOf(oy, my), maxOf(ox, mx), maxOf(oy, my))
    }
    val availW = if (right) 1f - ox else ox
    val availH = if (bottom) 1f - oy else oy
    val hi = minOf(availW, availH * rn)
    var nw = (if (right) mx0 - ox else ox - mx0)
    nw = nw.coerceAtMost(hi).coerceAtLeast(minOf(min, hi))
    val nh = nw / rn
    val l = if (right) ox else ox - nw
    val t = if (bottom) oy else oy - nh
    return Rect(l, t, l + nw, t + nh)
}

@Composable
fun CropOverlay(rect: Rect, imgW: Int, imgH: Int, ratio: Float?, onRect: (Rect) -> Unit) {
    var handle by remember { mutableIntStateOf(-1) }
    val cur by rememberUpdatedState(rect)
    val cb by rememberUpdatedState(onRect)
    val rn = ratio?.let { it * imgH / imgW }
    Canvas(
        Modifier.fillMaxSize().pointerInput(rn) {
            detectDragGestures(
                onDragStart = { o ->
                    val r = cur
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    val pts = listOf(Offset(r.left * w, r.top * h), Offset(r.right * w, r.top * h), Offset(r.left * w, r.bottom * h), Offset(r.right * w, r.bottom * h))
                    var best = -1
                    var bd = 56.dp.toPx()
                    pts.forEachIndexed { i, p -> val d = (p - o).getDistance(); if (d < bd) { bd = d; best = i } }
                    handle = if (best >= 0) best else if (r.contains(Offset(o.x / w, o.y / h))) 4 else -1
                },
                onDragEnd = { handle = -1 },
                onDragCancel = { handle = -1 },
                onDrag = { ch, d ->
                    if (handle >= 0) {
                        ch.consume()
                        cb(cropDrag(cur, handle, d.x / size.width, d.y / size.height, rn))
                    }
                }
            )
        }
    ) {
        val w = size.width
        val h = size.height
        val l = rect.left * w
        val t = rect.top * h
        val r = rect.right * w
        val b = rect.bottom * h
        val dim = Color(0x99000000)
        drawRect(dim, Offset(0f, 0f), Size(w, t))
        drawRect(dim, Offset(0f, b), Size(w, h - b))
        drawRect(dim, Offset(0f, t), Size(l, b - t))
        drawRect(dim, Offset(r, t), Size(w - r, b - t))
        val thin = Stroke(1.dp.toPx())
        val line = Color(0x66FFFFFF)
        for (i in 1..2) {
            drawLine(line, Offset(l + (r - l) * i / 3f, t), Offset(l + (r - l) * i / 3f, b), thin.width)
            drawLine(line, Offset(l, t + (b - t) * i / 3f), Offset(r, t + (b - t) * i / 3f), thin.width)
        }
        drawRect(Color.White, Offset(l, t), Size(r - l, b - t), style = Stroke(2.dp.toPx()))
        for (p in listOf(Offset(l, t), Offset(r, t), Offset(l, b), Offset(r, b))) {
            drawCircle(Color.White, 9.dp.toPx(), p)
            drawCircle(LF.accent, 5.dp.toPx(), p)
        }
    }
}

// ---------------- Layers ----------------
@Composable
fun LayersPanel(m: EditorModel, onPick: (Layer) -> Unit) {
    val ls = m.s.layers
    Column {
        if (ls.isEmpty()) {
            Text("No layers yet. Add text, pictures or icons and they will be listed here.", color = LF.muted, fontSize = 13.sp)
        } else {
            Text("Top of the list is in front.", color = LF.muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
            ls.asReversed().forEachIndexed { idx, l ->
                val sel = l.id == m.sel
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(14.dp))
                        .background(if (sel) LF.accent.copy(alpha = 0.18f) else LF.card)
                        .clickable { onPick(l) }.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(62.dp)) { LayerChip(l, sel, { onPick(l) }, { m.remove(l.id) }) }
                    Text(
                        when (l) { is TextLayer -> "Text: ${l.text.replace('\n', ' ')}"; is PhotoLayer -> "Picture"; is IconLayer -> "Icon: ${l.name}" },
                        color = LF.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    )
                    IconButton(onClick = { m.reorder(l.id, +1) }, enabled = idx > 0) { Icon(Icons.Rounded.KeyboardArrowUp, "Forward") }
                    IconButton(onClick = { m.reorder(l.id, -1) }, enabled = idx < ls.size - 1) { Icon(Icons.Rounded.KeyboardArrowDown, "Backward") }
                }
            }
        }
    }
}

// ---------------- Google Fonts browser ----------------
@Composable
fun FontBrowser(currentFont: String?, onClose: () -> Unit, a: Actions) {
    val ctx = LocalContext.current
    var q by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf("All") }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { failed = !Fonts.loadCatalog(ctx) }
    val cats = listOf("All", "Sans Serif", "Serif", "Display", "Handwriting", "Monospace")
    val size = Fonts.catalog.size
    val list = remember(q, cat, size) {
        val src = if (Fonts.catalog.isNotEmpty()) Fonts.catalog.toList() else Fonts.popular.map { GFont(it, "Popular") }
        src.filter { (cat == "All" || it.category.equals(cat, true)) && it.family.contains(q.trim(), true) }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        SheetHeader("Google Fonts", onClose)
        SearchField(q, "Search ${if (size > 0) "$size fonts" else "fonts"}") { q = it }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            cats.forEach { c -> LChip(c, cat == c) { cat = c } }
        }
        if (failed && size == 0) {
            Text("Couldn't load the full catalogue (check your internet). Showing popular fonts. You can still type any Google Font name above and tap it.", color = LF.muted, fontSize = 12.sp)
        }
        LazyColumn(Modifier.weight(1f)) {
            if (list.isEmpty() && q.isNotBlank()) {
                item {
                    Row(Modifier.fillMaxWidth().clickable { a.useFont(q.trim()) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Download, null, tint = LF.accent)
                        Text("  Try downloading \"${q.trim()}\"", color = LF.text)
                    }
                }
            }
            items(list, key = { it.family }) { f ->
                val tf = Fonts.cache[f.family]
                val ff = remember(tf) { Fonts.compose(f.family) }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { a.useFont(f.family) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(f.family, fontFamily = ff, fontSize = 19.sp, color = if (currentFont == f.family) LF.accent else LF.text, maxLines = 1)
                        Text(f.category, fontSize = 11.sp, color = LF.muted)
                    }
                    when {
                        Fonts.busy == f.family -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = LF.accent)
                        tf != null -> Icon(Icons.Rounded.Check, "Downloaded", tint = LF.accent2)
                        else -> Icon(Icons.Rounded.Download, "Download", tint = LF.muted)
                    }
                }
            }
        }
    }
}

// ---------------- Google Icons browser ----------------
@Composable
fun IconBrowser(onClose: () -> Unit, a: Actions) {
    val ctx = LocalContext.current
    var q by remember { mutableStateOf("") }
    var style by remember { mutableStateOf("Outlined") }
    var filled by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { failed = !GIcons.loadNames(ctx) }
    val size = GIcons.names.size
    val list = remember(q, size) {
        val src = if (GIcons.names.isNotEmpty()) GIcons.names.toList() else GIcons.fallback
        val k = q.trim().lowercase().replace(' ', '_')
        if (k.isEmpty()) src else {
            val words = q.trim().lowercase().split(' ').filter { it.isNotEmpty() }
            src.filter { m -> m.name.contains(k) || words.all { w -> m.name.contains(w) || m.tags.contains(w) } }
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        SheetHeader("Google Icons", onClose)
        SearchField(q, "Search ${if (size > 0) "$size icons" else "icons"}") { q = it }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf("Outlined", "Rounded", "Sharp").forEach { s -> LChip(s, style == s) { style = s } }
            LChip("Filled", filled) { filled = !filled }
        }
        if (failed && size == 0) {
            Text("Couldn't load the full icon list (check your internet). Showing common icons.", color = LF.muted, fontSize = 12.sp)
        }
        LazyVerticalGrid(GridCells.Adaptive(78.dp), Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list, key = { it.name }) { meta ->
                val key = GIcons.key(meta.name, style, filled)
                val svg = GIcons.memory[key]
                LaunchedEffect(key) { if (GIcons.memory[key] == null) GIcons.load(ctx, meta.name, style, filled) }
                Column(
                    Modifier.clip(RoundedCornerShape(14.dp)).background(LF.card)
                        .clickable { a.addIcon(meta.name, style, filled) }.padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        if (svg != null) {
                            Canvas(Modifier.size(36.dp)) {
                                drawIntoCanvas { drawSvgFit(it.nativeCanvas, svg, size.width / 2, size.height / 2, size.width, 0xFFFFFFFF.toInt()) }
                            }
                        }
                    }
                    Text(meta.name.replace('_', ' '), fontSize = 10.sp, color = LF.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun SheetHeader(title: String, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = LF.text, fontSize = 18.sp, modifier = Modifier.weight(1f), fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
        IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close", tint = LF.text) }
    }
}

@Composable
private fun SearchField(value: String, hint: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = true,
        placeholder = { Text(hint, color = LF.muted) },
        leadingIcon = { Icon(Icons.Rounded.Search, null, tint = LF.muted) },
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = LF.accent, unfocusedBorderColor = LF.outline, cursorColor = LF.accent,
            focusedContainerColor = LF.card, unfocusedContainerColor = LF.card
        )
    )
}
