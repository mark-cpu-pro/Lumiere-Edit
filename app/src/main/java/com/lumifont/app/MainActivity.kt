package com.lumifont.app

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.pow
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.ColorMatrix as ComposeMatrix
import androidx.compose.ui.text.font.Typeface as ComposeTypeface

data class Adj(
    val exposure: Float = 0f, val contrast: Float = 0f, val saturation: Float = 0f,
    val temp: Float = 0f, val tint: Float = 0f
)

data class TextLayer(
    val id: Int, val text: String, val x: Float, val y: Float,
    val size: Float, val color: Int, val font: String?
)

// One color matrix used for BOTH the live preview and the saved file.
fun matrix(a: Adj): FloatArray {
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

object Fonts {
    val cache = mutableStateMapOf<String, Typeface>()
    val popular = listOf(
        "Roboto", "Lobster", "Pacifico", "Bebas Neue", "Oswald", "Playfair Display",
        "Montserrat", "Dancing Script", "Anton", "Caveat", "Permanent Marker",
        "Abril Fatface", "Satisfy", "Righteous", "Cinzel", "Amatic SC",
        "Archivo Black", "Great Vibes", "Shadows Into Light", "Press Start 2P"
    )

    private fun dir(ctx: Context) = File(ctx.filesDir, "fonts").apply { mkdirs() }

    fun loadSaved(ctx: Context) {
        dir(ctx).listFiles()?.forEach { f ->
            runCatching { cache[f.nameWithoutExtension] = Typeface.createFromFile(f) }
        }
    }

    // Downloads a font from Google Fonts (no API key needed) and keeps it on the phone.
    suspend fun download(ctx: Context, family: String): Boolean {
        val file = withContext(Dispatchers.IO) {
            runCatching {
                val q = URLEncoder.encode(family, "UTF-8")
                val conn = URL("https://fonts.googleapis.com/css2?family=$q")
                    .openConnection() as HttpURLConnection
                conn.setRequestProperty("User-Agent", "Mozilla/4.0")
                val css = conn.inputStream.bufferedReader().use { it.readText() }
                val link = Regex("url\\((https://[^)]+)\\)").find(css)?.groupValues?.get(1)
                    ?: error("no font url")
                val out = File(dir(ctx), "$family.ttf")
                URL(link).openStream().use { i -> out.outputStream().use { o -> i.copyTo(o) } }
                out
            }.getOrNull()
        } ?: return false
        return runCatching { cache[family] = Typeface.createFromFile(file); true }.getOrDefault(false)
    }

    fun compose(name: String?): FontFamily =
        cache[name]?.let { FontFamily(ComposeTypeface(it)) } ?: FontFamily.Default
}

fun loadBitmap(ctx: Context, uri: Uri): Bitmap? = runCatching {
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { dec, info, _ ->
        dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val big = maxOf(info.size.width, info.size.height)
        if (big > 3000) {
            val k = 3000f / big
            dec.setTargetSize((info.size.width * k).toInt(), (info.size.height * k).toInt())
        }
    }
}.getOrNull()

fun exportPhoto(ctx: Context, src: Bitmap, adj: Adj, layers: List<TextLayer>): Boolean = runCatching {
    val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
    val c = Canvas(out)
    val p = Paint(Paint.FILTER_BITMAP_FLAG)
    p.colorFilter = ColorMatrixColorFilter(matrix(adj))
    c.drawBitmap(src, 0f, 0f, p)
    for (l in layers) {
        val tp = Paint(Paint.ANTI_ALIAS_FLAG)
        tp.color = l.color
        tp.textSize = l.size * src.width
        tp.typeface = Fonts.cache[l.font] ?: Typeface.DEFAULT
        val fm = tp.fontMetrics
        val lineH = fm.descent - fm.ascent + fm.leading
        l.text.split("\n").forEachIndexed { i, line ->
            c.drawText(line, l.x * src.width, l.y * src.height - fm.ascent + i * lineH, tp)
        }
    }
    val v = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "Lumifont_${System.currentTimeMillis()}.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Lumifont")
    }
    val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v)!!
    ctx.contentResolver.openOutputStream(uri)!!.use { out.compress(Bitmap.CompressFormat.JPEG, 95, it) }
    true
}.getOrDefault(false)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Fonts.loadSaved(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) { Editor() }
            }
        }
    }
}

@Composable
fun Slide(label: String, v: Float, onV: (Float) -> Unit, range: ClosedFloatingPointRange<Float> = -1f..1f) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(96.dp))
        Slider(value = v, onValueChange = onV, valueRange = range, modifier = Modifier.weight(1f))
    }
}

@Composable
fun Editor() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var bmp by remember { mutableStateOf<Bitmap?>(null) }
    var adj by remember { mutableStateOf(Adj()) }
    val layers = remember { mutableStateListOf<TextLayer>() }
    var sel by remember { mutableStateOf(-1) }
    var nextId by remember { mutableStateOf(1) }
    var tab by remember { mutableStateOf(0) }
    var status by remember { mutableStateOf("") }
    var boxW by remember { mutableStateOf(1f) }
    var boxH by remember { mutableStateOf(1f) }
    var query by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            bmp = loadBitmap(ctx, uri)
            adj = Adj(); layers.clear(); sel = -1
        }
    }

    fun edit(f: (TextLayer) -> TextLayer) {
        val i = layers.indexOfFirst { it.id == sel }
        if (i >= 0) layers[i] = f(layers[i])
    }

    fun useFont(name: String) {
        scope.launch {
            status = "Downloading $name..."
            val ok = Fonts.cache.containsKey(name) || Fonts.download(ctx, name)
            if (ok) { edit { it.copy(font = name) }; status = "" }
            else status = "Could not get \"$name\". Check the spelling and your internet."
        }
    }

    val cur = layers.firstOrNull { it.id == sel }

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(onClick = {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) { Text("Open photo") }
            Button(enabled = bmp != null, onClick = {
                val b = bmp ?: return@Button
                val snapshot = layers.toList()
                val a = adj
                scope.launch {
                    val ok = withContext(Dispatchers.Default) { exportPhoto(ctx, b, a, snapshot) }
                    status = if (ok) "Saved to Pictures/Lumifont" else "Save failed"
                }
            }) { Text("Save") }
        }

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val b = bmp
            if (b == null) {
                Text("Open a photo to start")
            } else {
                val img = remember(b) { b.asImageBitmap() }
                Box(
                    Modifier.aspectRatio(b.width.toFloat() / b.height)
                        .onSizeChanged { boxW = it.width.toFloat(); boxH = it.height.toFloat() }
                ) {
                    Image(
                        bitmap = img, contentDescription = null, modifier = Modifier.fillMaxSize(),
                        colorFilter = ColorFilter.colorMatrix(ComposeMatrix(matrix(adj)))
                    )
                    layers.forEach { l ->
                        key(l.id) {
                            Text(
                                text = l.text,
                                color = Color(l.color),
                                fontFamily = Fonts.compose(l.font),
                                fontSize = with(density) { (l.size * boxW).toSp() },
                                modifier = Modifier
                                    .offset { IntOffset((l.x * boxW).roundToInt(), (l.y * boxH).roundToInt()) }
                                    .let { if (l.id == sel) it.border(1.dp, Color.White) else it }
                                    .pointerInput(l.id) { detectTapGestures { sel = l.id } }
                                    .pointerInput(l.id) {
                                        detectDragGestures(onDragStart = { sel = l.id }) { ch, d ->
                                            ch.consume()
                                            val i = layers.indexOfFirst { it.id == l.id }
                                            if (i >= 0) {
                                                val c = layers[i]
                                                layers[i] = c.copy(
                                                    x = (c.x + d.x / boxW).coerceIn(0f, 1f),
                                                    y = (c.y + d.y / boxH).coerceIn(0f, 1f)
                                                )
                                            }
                                        }
                                    }
                            )
                        }
                    }
                }
            }
        }

        if (status.isNotEmpty()) Text(status, Modifier.padding(horizontal = 12.dp))

        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Color") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Text & Fonts") })
        }

        Column(
            Modifier.fillMaxWidth().heightIn(max = 300.dp)
                .verticalScroll(rememberScrollState()).padding(8.dp)
        ) {
            if (tab == 0) {
                Slide("Exposure", adj.exposure, { adj = adj.copy(exposure = it) })
                Slide("Contrast", adj.contrast, { adj = adj.copy(contrast = it) })
                Slide("Saturation", adj.saturation, { adj = adj.copy(saturation = it) })
                Slide("Temperature", adj.temp, { adj = adj.copy(temp = it) })
                Slide("Tint", adj.tint, { adj = adj.copy(tint = it) })
                TextButton(onClick = { adj = Adj() }) { Text("Reset") }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = bmp != null, onClick = {
                        layers.add(TextLayer(nextId, "Your text", 0.25f, 0.4f, 0.08f, 0xFFFFFFFF.toInt(), null))
                        sel = nextId; nextId++
                    }) { Text("Add text") }
                    if (cur != null) {
                        OutlinedButton(onClick = { layers.removeAll { it.id == sel }; sel = -1 }) { Text("Delete") }
                    }
                }
                if (cur != null) {
                    OutlinedTextField(
                        value = cur.text, onValueChange = { v -> edit { it.copy(text = v) } },
                        label = { Text("Text") }, modifier = Modifier.fillMaxWidth()
                    )
                    Slide("Size", cur.size, { v -> edit { it.copy(size = v) } }, 0.02f..0.3f)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            0xFFFFFFFF, 0xFF000000, 0xFFFF3B30, 0xFFFFCC00,
                            0xFF34C759, 0xFF0A84FF, 0xFFBF5AF2, 0xFFFF9500
                        ).forEach { c ->
                            Box(
                                Modifier.size(32.dp).clip(CircleShape).background(Color(c.toInt()))
                                    .border(1.dp, Color.Gray, CircleShape)
                                    .clickable { edit { it.copy(color = c.toInt()) } }
                            )
                        }
                    }
                    Text("Fonts (arrow = tap to download from Google Fonts)", Modifier.padding(top = 8.dp))
                    val names = (Fonts.popular + Fonts.cache.keys).distinct()
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(names) { n ->
                            FilterChip(
                                selected = cur.font == n, onClick = { useFont(n) },
                                label = {
                                    Text(
                                        if (Fonts.cache.containsKey(n)) n else "$n \u2193",
                                        fontFamily = Fonts.compose(n)
                                    )
                                }
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = query, onValueChange = { query = it }, singleLine = true,
                            label = { Text("Any Google Font name") }, modifier = Modifier.weight(1f)
                        )
                        Button(onClick = { if (query.isNotBlank()) useFont(query.trim()) }) { Text("Get") }
                    }
                } else {
                    Text("Tap \"Add text\", or tap a text on the photo to edit it. Drag to move it.")
                }
            }
        }
    }
}
