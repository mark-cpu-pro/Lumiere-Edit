package com.lumifont.app

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import androidx.compose.ui.text.font.Typeface as ComposeTypeface

fun httpText(url: String, ua: String = "Mozilla/5.0"): String {
    val c = URL(url).openConnection() as HttpURLConnection
    c.connectTimeout = 12000
    c.readTimeout = 25000
    c.setRequestProperty("User-Agent", ua)
    try {
        if (c.responseCode != 200) error("http ${c.responseCode}")
        return c.inputStream.bufferedReader().use { it.readText() }
    } finally {
        c.disconnect()
    }
}

data class GFont(val family: String, val category: String)

object Fonts {
    val cache = mutableStateMapOf<String, Typeface>()
    val custom = mutableStateListOf<String>()
    val catalog = mutableStateListOf<GFont>()
    var busy by mutableStateOf<String?>(null)

    val popular = listOf(
        "Roboto", "Lobster", "Pacifico", "Bebas Neue", "Oswald", "Playfair Display",
        "Montserrat", "Dancing Script", "Anton", "Caveat", "Permanent Marker",
        "Abril Fatface", "Satisfy", "Righteous", "Cinzel", "Amatic SC",
        "Archivo Black", "Great Vibes", "Shadows Into Light", "Press Start 2P"
    )

    private fun dir(ctx: Context, sub: String) = File(ctx.filesDir, "fonts/$sub").apply { mkdirs() }

    private fun load(name: String, f: File): Boolean {
        val tf = runCatching { Typeface.Builder(f).build() }.getOrNull() ?: return false
        cache[name] = tf
        return true
    }

    fun loadSaved(ctx: Context) {
        // fonts saved by the first version of the app lived directly in /fonts
        File(ctx.filesDir, "fonts").listFiles()?.filter { it.isFile }?.forEach { load(it.nameWithoutExtension, it) }
        dir(ctx, "google").listFiles()?.forEach { load(it.nameWithoutExtension, it) }
        dir(ctx, "custom").listFiles()?.forEach { f ->
            val n = f.nameWithoutExtension
            if (load(n, f) && n !in custom) custom.add(n)
        }
    }

    fun compose(name: String?): FontFamily =
        cache[name]?.let { FontFamily(ComposeTypeface(it)) } ?: FontFamily.Default

    /** Downloads a font from Google Fonts (no API key needed) and keeps it on the phone. */
    suspend fun download(ctx: Context, family: String): Boolean {
        busy = family
        val file = withContext(Dispatchers.IO) {
            runCatching {
                val q = URLEncoder.encode(family, "UTF-8")
                val css = httpText("https://fonts.googleapis.com/css2?family=$q", "Mozilla/4.0")
                val link = Regex("url\\((https://[^)]+)\\)").find(css)?.groupValues?.get(1) ?: error("no font url")
                val out = File(dir(ctx, "google"), "$family.ttf")
                URL(link).openStream().use { i -> out.outputStream().use { o -> i.copyTo(o) } }
                out
            }.getOrNull()
        }
        val ok = file != null && load(family, file)
        busy = null
        return ok
    }

    /** Copies a .ttf/.otf the user picked into the app so it keeps working offline. */
    suspend fun importCustom(ctx: Context, uri: Uri): String? {
        val r = withContext(Dispatchers.IO) {
            runCatching {
                var name = "Custom font.ttf"
                ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                    val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (i >= 0 && c.moveToFirst()) name = c.getString(i)
                }
                val ext = name.substringAfterLast('.', "ttf").lowercase()
                if (ext != "ttf" && ext != "otf") error("not a ttf/otf")
                val base = name.substringBeforeLast('.')
                val out = File(dir(ctx, "custom"), "$base.$ext")
                ctx.contentResolver.openInputStream(uri)!!.use { i -> out.outputStream().use { o -> i.copyTo(o) } }
                Pair(base, out)
            }.getOrNull()
        } ?: return null
        return if (load(r.first, r.second)) {
            if (r.first !in custom) custom.add(r.first)
            r.first
        } else {
            r.second.delete()
            null
        }
    }

    /** The full Google Fonts list (cached on the phone after the first load). */
    suspend fun loadCatalog(ctx: Context): Boolean {
        if (catalog.isNotEmpty()) return true
        val list = withContext(Dispatchers.IO) {
            val f = File(ctx.filesDir, "gfonts_catalog.json")
            val txt = runCatching { httpText("https://fonts.google.com/metadata/fonts").also { f.writeText(it) } }.getOrNull()
                ?: runCatching { f.readText() }.getOrNull()
            txt?.let { runCatching { parseCatalog(it) }.getOrNull() }
        }
        if (list.isNullOrEmpty()) return false
        catalog.addAll(list)
        return true
    }

    private fun parseCatalog(t: String): List<GFont> {
        val arr = JSONObject(t.substring(t.indexOf('{'))).getJSONArray("familyMetadataList")
        val out = ArrayList<Triple<String, String, Int>>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(Triple(o.getString("family"), o.optString("category", "Other"), o.optInt("popularity", 99999)))
        }
        return out.sortedBy { it.third }.map { GFont(it.first, it.second) }
    }
}

data class IconMeta(val name: String, val tags: String)

object GIcons {
    val names = mutableStateListOf<IconMeta>()
    val memory = mutableStateMapOf<String, IconSvg>()
    private val sem = Semaphore(6)

    val fallback = listOf(
        "home", "star", "favorite", "search", "settings", "person", "mail", "call", "location_on", "photo_camera",
        "image", "music_note", "play_arrow", "pause", "bolt", "light_mode", "dark_mode", "cloud", "water_drop",
        "local_fire_department", "eco", "pets", "restaurant", "coffee", "shopping_cart", "shopping_bag", "work",
        "school", "sports_esports", "flight", "directions_car", "directions_bike", "check_circle", "cancel",
        "warning", "info", "help", "add_circle", "arrow_forward", "arrow_back", "arrow_upward", "arrow_downward",
        "thumb_up", "thumb_down", "share", "send", "lock", "key", "visibility", "notifications", "event",
        "schedule", "calendar_month", "bookmark", "flag", "diamond", "rocket_launch", "emoji_events",
        "celebration", "cake", "sunny", "nightlight", "mood", "sentiment_satisfied", "volunteer_activism",
        "format_quote", "tag", "link", "mic", "videocam", "palette", "brush", "draw", "edit", "delete",
        "download", "upload", "wifi", "bluetooth", "battery_full", "heart_broken", "psychology", "lightbulb"
    ).map { IconMeta(it, "") }

    suspend fun loadNames(ctx: Context): Boolean {
        if (names.isNotEmpty()) return true
        val list = withContext(Dispatchers.IO) {
            val f = File(ctx.filesDir, "gicons_catalog.json")
            val txt = runCatching {
                httpText("https://fonts.google.com/metadata/icons?key=material_symbols&incomplete=true").also { f.writeText(it) }
            }.getOrNull() ?: runCatching { f.readText() }.getOrNull()
            txt?.let { runCatching { parse(it) }.getOrNull() }
        }
        if (list.isNullOrEmpty()) return false
        names.addAll(list)
        return true
    }

    private fun parse(t: String): List<IconMeta> {
        val arr = JSONObject(t.substring(t.indexOf('{'))).getJSONArray("icons")
        val tmp = ArrayList<Pair<IconMeta, Int>>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val tags = o.optJSONArray("tags")
            val cats = o.optJSONArray("categories")
            val sb = StringBuilder()
            if (tags != null) for (k in 0 until tags.length()) sb.append(tags.optString(k)).append(' ')
            if (cats != null) for (k in 0 until cats.length()) sb.append(cats.optString(k)).append(' ')
            tmp.add(Pair(IconMeta(o.getString("name"), sb.toString().lowercase()), o.optInt("popularity", 0)))
        }
        return tmp.sortedByDescending { it.second }.map { it.first }
    }

    fun key(name: String, style: String, filled: Boolean) = "$style|${if (filled) 1 else 0}|$name"

    suspend fun load(ctx: Context, name: String, style: String, filled: Boolean): IconSvg? {
        val k = key(name, style, filled)
        memory[k]?.let { return it }
        val svg = withContext(Dispatchers.IO) {
            sem.withPermit {
                val f = File(File(ctx.filesDir, "icons").apply { mkdirs() }, k.replace('|', '_') + ".svg")
                val url = "https://fonts.gstatic.com/s/i/short-term/release/materialsymbols${style.lowercase()}/" +
                    "$name/${if (filled) "fill1" else "default"}/48px.svg"
                val text = if (f.exists()) f.readText()
                else runCatching { httpText(url).also { f.writeText(it) } }.getOrNull()
                text?.let { parseSvg(it) }
            }
        } ?: return null
        memory[k] = svg
        return svg
    }

    private fun parseSvg(t: String): IconSvg? {
        val vb = Regex("viewBox=\"([^\"]+)\"").find(t)?.groupValues?.get(1)?.trim()
            ?.split(Regex("[ ,]+"))?.mapNotNull { it.toFloatOrNull() }
        if (vb == null || vb.size != 4) return null
        val ds = Regex("<path([^>]*)>").findAll(t).mapNotNull { m ->
            val a = " " + m.groupValues[1]
            if (a.contains("fill=\"none\"")) null
            else Regex("\\sd=\"([^\"]+)\"").find(a)?.groupValues?.get(1)
        }.toList()
        return if (ds.isEmpty()) null else IconSvg(ds, vb[0], vb[1], vb[2], vb[3])
    }
}
