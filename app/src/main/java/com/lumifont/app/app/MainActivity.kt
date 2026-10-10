package com.lumifont.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

enum class Tool(val label: String, val icon: ImageVector) {
    Adjust("Adjust", Icons.Rounded.Tune),
    Type("Text", Icons.Rounded.TextFields),
    Pics("Pictures", Icons.Rounded.AddPhotoAlternate),
    Symbols("Icons", Icons.Rounded.Star),
    Crop("Crop", Icons.Rounded.Crop),
    Layers("Layers", Icons.Rounded.Layers)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Fonts.loadSaved(this)
        setContent {
            LumiTheme { Surface(Modifier.fillMaxSize(), color = LF.bg) { Editor() } }
        }
    }
}

private fun toolFor(l: Layer) = when (l) {
    is TextLayer -> Tool.Type
    is PhotoLayer -> Tool.Pics
    is IconLayer -> Tool.Symbols
}

private fun fitRect(rn: Float): Rect {
    var nw = 1f
    var nh = 1f / rn
    if (nh > 1f) { nh = 1f; nw = rn }
    val l = (1f - nw) / 2f
    val t = (1f - nh) / 2f
    return Rect(l, t, l + nw, t + nh)
}

@Composable
fun Editor() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val model = remember { EditorModel() }
    var tool by remember { mutableStateOf(Tool.Adjust) }
    var status by remember { mutableStateOf("") }
    var showFonts by remember { mutableStateOf(false) }
    var showIcons by remember { mutableStateOf(false) }
    var showSave by remember { mutableStateOf(false) }
    var cropRect by remember { mutableStateOf(Rect(0f, 0f, 1f, 1f)) }
    var cropRatio by remember { mutableStateOf<Float?>(null) }
    val s = model.s

    LaunchedEffect(status) { if (status.isNotEmpty()) { delay(2600); status = "" } }
    BackHandler(enabled = showFonts || showIcons) { showFonts = false; showIcons = false }

    // ---- pickers ----
    val openPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val b = withContext(Dispatchers.IO) { decodeBitmap(ctx, uri, 4096) }
            if (b != null) { model.open(b); cropRect = Rect(0f, 0f, 1f, 1f); tool = Tool.Adjust }
            else status = "Couldn't open that image"
        }
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            var added = 0
            for (u: Uri in uris) {
                if (model.s.layers.count { it is PhotoLayer } >= 20) break
                val bmp = withContext(Dispatchers.IO) { decodeBitmap(ctx, u, 1280) } ?: continue
                PhotoCache.map[u.toString()] = bmp
                val n = model.s.layers.count { it is PhotoLayer }
                val off = ((n % 6) - 2.5f) * 0.03f
                model.addLayer { id -> PhotoLayer(id, u, bmp.width.toFloat() / bmp.height, 0.5f + off, 0.5f + off, 0.5f) }
                added++
            }
            status = if (added < uris.size) "Limit is 20 pictures" else ""
        }
    }

    fun useFont(name: String) {
        scope.launch {
            val have = Fonts.cache.containsKey(name)
            if (!have) status = "Downloading $name…"
            val ok = have || Fonts.download(ctx, name)
            if (ok) { model.updateText(null) { it.copy(font = name) }; status = "" }
            else status = "Couldn't get \"$name\". Check the spelling and your internet."
        }
    }

    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val n = Fonts.importCustom(ctx, uri)
            if (n != null) { model.updateText(null) { it.copy(font = n) }; status = "Added font $n" }
            else status = "That file isn't a usable .ttf or .otf font"
        }
    }

    val actions = Actions(
        openFonts = { showIcons = false; showFonts = true },
        openIcons = { showFonts = false; showIcons = true },
        pickPhotos = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        importFont = { fontPicker.launch(arrayOf("*/*")) },
        useFont = { useFont(it) },
        addIcon = { name, style, filled ->
            scope.launch {
                val svg = GIcons.load(ctx, name, style, filled)
                if (svg == null) status = "Couldn't load \"$name\""
                else { model.addLayer { id -> IconLayer(id, name, svg) }; tool = Tool.Symbols }
            }
        }
    )

    fun save(png: Boolean) {
        scope.launch {
            status = "Saving…"
            val snap = model.s
            val ok = withContext(Dispatchers.Default) { exportImage(ctx, snap, png) }
            status = if (ok) "Saved to Pictures/Lumifont" else "Save failed"
        }
    }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(LF.bg, Color(0xFF10121A))))) {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            // ---------- top bar ----------
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(Brush.linearGradient(listOf(LF.accent, LF.accent2))),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Rounded.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
                Text("Lumifont", color = LF.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp).weight(1f))
                TopIcon(Icons.Rounded.PhotoLibrary, "Open photo", true) {
                    openPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                TopIcon(Icons.AutoMirrored.Rounded.Undo, "Undo", model.canUndo) { model.undo() }
                TopIcon(Icons.AutoMirrored.Rounded.Redo, "Redo", model.canRedo) { model.redo() }
                Box(
                    Modifier.padding(start = 6.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (s.base != null) LF.accent else LF.card)
                        .clickable(enabled = s.base != null) { showSave = true }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Save, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Text(" Save", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // ---------- canvas ----------
            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                val b = s.base
                if (b == null) {
                    EmptyState { openPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                } else {
                    Box(Modifier.aspectRatio(b.width.toFloat() / b.height).clip(RoundedCornerShape(10.dp))) {
                        Canvas(
                            Modifier.fillMaxSize().pointerInput(Unit) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    if (tool == Tool.Crop) return@awaitEachGesture
                                    val st = model.s
                                    val bm = st.base ?: return@awaitEachGesture
                                    val sc = bm.width / size.width.toFloat()
                                    val hit = hitTest(st.layers, bm.width.toFloat(), bm.height.toFloat(), down.position.x * sc, down.position.y * sc, 18f * sc)
                                    if (hit != null) {
                                        model.sel = hit.id
                                        if (tool != Tool.Layers) tool = toolFor(hit)
                                    }
                                    var moved = false
                                    var total = Offset.Zero
                                    do {
                                        val ev = awaitPointerEvent()
                                        val zoom = ev.calculateZoom()
                                        val rot = ev.calculateRotation()
                                        val pan = ev.calculatePan()
                                        total += pan
                                        if (!moved && (total.getDistance() > viewConfiguration.touchSlop || abs(zoom - 1f) > 0.02f || abs(rot) > 2f)) moved = true
                                        val tgt = hit?.id ?: if (ev.changes.count { it.pressed } >= 2) model.sel else -1
                                        if (moved && tgt >= 0) {
                                            model.transformLayer(tgt, pan.x / size.width, pan.y / size.height, zoom, rot)
                                            ev.changes.forEach { if (it.positionChanged()) it.consume() }
                                        }
                                    } while (ev.changes.any { it.pressed })
                                    if (!moved && hit == null) model.sel = -1
                                    model.endGesture()
                                }
                            }
                        ) {
                            val sc = size.width / b.width
                            val w = b.width.toFloat()
                            val h = b.height.toFloat()
                            drawIntoCanvas { cv ->
                                val nc = cv.nativeCanvas
                                nc.save()
                                nc.scale(sc, sc)
                                drawScene(nc, w, h, b, s.adj, s.layers, PreviewPhotos)
                                val sl = s.layers.firstOrNull { it.id == model.sel }
                                if (sl != null && tool != Tool.Crop) drawSelection(nc, sl, w, h, sc, LF.accent.toArgb())
                                nc.restore()
                            }
                        }
                        if (tool == Tool.Crop) {
                            CropOverlay(cropRect, b.width, b.height, cropRatio) { cropRect = it }
                        }
                    }
                }
            }

            // ---------- tool panel ----------
            Surface(
                color = LF.panel, shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
                modifier = Modifier.fillMaxWidth().animateContentSize(spring(stiffness = 500f))
            ) {
                AnimatedContent(
                    targetState = tool, label = "panel",
                    transitionSpec = {
                        (fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 10 }) togetherWith fadeOut(tween(120))
                    }
                ) { t ->
                    Column(Modifier.fillMaxWidth().heightIn(max = 310.dp).verticalScroll(rememberScrollState()).padding(16.dp)) {
                        when (t) {
                            Tool.Adjust -> AdjustPanel(model)
                            Tool.Type -> TextPanel(model, actions)
                            Tool.Pics -> PhotosPanel(model, actions)
                            Tool.Symbols -> SymbolsPanel(model, actions)
                            Tool.Crop -> CropPanel(
                                ratio = cropRatio, imgAspect = s.base?.let { it.width.toFloat() / it.height } ?: 1f,
                                onRatio = { r ->
                                    cropRatio = r
                                    val bb = s.base
                                    if (r != null && bb != null) cropRect = fitRect(r * bb.height / bb.width)
                                },
                                onRotL = { model.geo(Geo.RotL); cropRect = Rect(0f, 0f, 1f, 1f); cropRatio = null },
                                onRotR = { model.geo(Geo.RotR); cropRect = Rect(0f, 0f, 1f, 1f); cropRatio = null },
                                onFlipH = { model.geo(Geo.FlipH) },
                                onFlipV = { model.geo(Geo.FlipV) },
                                onApply = {
                                    val r = cropRect
                                    if (r.width < 0.999f || r.height < 0.999f) model.geo(Geo.Crop(r.left, r.top, r.right, r.bottom))
                                    cropRect = Rect(0f, 0f, 1f, 1f); cropRatio = null
                                },
                                onReset = { cropRect = Rect(0f, 0f, 1f, 1f); cropRatio = null }
                            )
                            Tool.Layers -> LayersPanel(model) { l -> model.sel = l.id }
                        }
                    }
                }
            }

            // ---------- bottom tool bar ----------
            Row(
                Modifier.fillMaxWidth().background(LF.panel).navigationBarsPadding().padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Tool.values().forEach { t ->
                    ToolTab(t, tool == t) {
                        if (t == Tool.Crop) cropRect = Rect(0f, 0f, 1f, 1f)
                        tool = t
                    }
                }
            }
        }

        // ---------- toast ----------
        AnimatedVisibility(
            visible = status.isNotEmpty(),
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 58.dp)
        ) {
            Surface(color = LF.card, shape = RoundedCornerShape(50), shadowElevation = 8.dp) {
                Text(status, color = LF.text, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), textAlign = TextAlign.Center)
            }
        }

        // ---------- sheets ----------
        AnimatedVisibility(
            visible = showFonts,
            enter = slideInVertically(tween(300)) { it } + fadeIn(), exit = slideOutVertically(tween(240)) { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).imePadding()
        ) {
            Surface(
                color = LF.panel, shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp), shadowElevation = 16.dp,
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.62f)
            ) {
                Box(Modifier.navigationBarsPadding()) {
                    FontBrowser((model.selected as? TextLayer)?.font, { showFonts = false }, actions)
                }
            }
        }
        AnimatedVisibility(
            visible = showIcons,
            enter = slideInVertically(tween(300)) { it } + fadeIn(), exit = slideOutVertically(tween(240)) { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).imePadding()
        ) {
            Surface(
                color = LF.panel, shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp), shadowElevation = 16.dp,
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.62f)
            ) {
                Box(Modifier.navigationBarsPadding()) { IconBrowser({ showIcons = false }, actions) }
            }
        }
    }

    if (showSave) {
        AlertDialog(
            onDismissRequest = { showSave = false },
            containerColor = LF.panel,
            title = { Text("Save image", color = LF.text) },
            text = { Text("Exports at full resolution with your text, icons and pictures baked in.", color = LF.muted) },
            confirmButton = {
                Row {
                    TextButton(onClick = { showSave = false; save(false) }) { Text("JPG") }
                    TextButton(onClick = { showSave = false; save(true) }) { Text("PNG") }
                }
            },
            dismissButton = { TextButton(onClick = { showSave = false }) { Text("Cancel", color = LF.muted) } }
        )
    }
}

@Composable
private fun TopIcon(icon: ImageVector, desc: String, enabled: Boolean, onClick: () -> Unit) {
    val tint by animateColorAsState(if (enabled) LF.text else LF.muted.copy(alpha = 0.35f), label = "tint")
    IconButton(onClick = onClick, enabled = enabled) { Icon(icon, desc, tint = tint) }
}

@Composable
private fun ToolTab(t: Tool, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) LF.accent.copy(alpha = 0.18f) else Color.Transparent, label = "bg")
    val tint by animateColorAsState(if (selected) LF.accent else LF.muted, label = "tint")
    val sc by animateFloatAsState(if (selected) 1.12f else 1f, spring(dampingRatio = 0.5f), label = "sc")
    Column(
        Modifier.clip(RoundedCornerShape(16.dp)).background(bg).clickable { onClick() }.padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(t.icon, t.label, tint = tint, modifier = Modifier.size(24.dp).scale(sc))
        Text(t.label, color = tint, fontSize = 10.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun EmptyState(onOpen: () -> Unit) {
    val inf = rememberInfiniteTransition(label = "empty")
    val pulse by inf.animateFloat(1f, 1.07f, infiniteRepeatable(tween(1500), RepeatMode.Reverse), label = "pulse")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(96.dp).scale(pulse).clip(CircleShape).background(Brush.linearGradient(listOf(LF.accent, LF.accent2))),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Rounded.PhotoLibrary, null, tint = Color.White, modifier = Modifier.size(44.dp)) }
        Text("Open a photo to start", color = LF.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 18.dp))
        Text("Add text, icons and pictures, adjust colours, crop.", color = LF.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
        PrimaryPill("Open photo", Icons.Rounded.PhotoLibrary) { onOpen() }
    }
}
