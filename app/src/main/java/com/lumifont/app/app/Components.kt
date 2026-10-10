package com.lumifont.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LSlider(
    label: String, value: Float, range: ClosedFloatingPointRange<Float>, text: String,
    onChange: (Float) -> Unit, onDone: () -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = LF.muted, fontSize = 12.sp)
            Text(text, color = LF.text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange, onValueChangeFinished = onDone, valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = LF.accent, activeTrackColor = LF.accent, inactiveTrackColor = LF.card
            )
        )
    }
}

@Composable
fun PrimaryPill(label: String, icon: ImageVector? = null, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled, shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = LF.accent, contentColor = Color.White,
            disabledContainerColor = LF.card, disabledContentColor = LF.muted
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp)
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(18.dp)); androidx.compose.foundation.layout.Spacer(Modifier.size(6.dp)) }
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SoftPill(label: String, icon: ImageVector? = null, danger: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled, shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = LF.card, contentColor = if (danger) LF.danger else LF.text,
            disabledContainerColor = LF.card, disabledContentColor = LF.muted
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp)
    ) {
        if (icon != null) Icon(icon, label.ifEmpty { null }, Modifier.size(18.dp))
        if (icon != null && label.isNotEmpty()) androidx.compose.foundation.layout.Spacer(Modifier.size(6.dp))
        if (label.isNotEmpty()) Text(label, fontSize = 13.sp)
    }
}

@Composable
fun LChip(label: String, selected: Boolean, fontFamily: androidx.compose.ui.text.font.FontFamily? = null, onClick: () -> Unit) {
    FilterChip(
        selected = selected, onClick = onClick,
        label = { Text(label, fontFamily = fontFamily, fontSize = 13.sp) },
        shape = RoundedCornerShape(12.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = LF.card, labelColor = LF.text,
            selectedContainerColor = LF.accent, selectedLabelColor = Color.White
        ),
        border = null
    )
}

@Composable
fun LSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = LF.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = LF.accent, checkedThumbColor = Color.White)
        )
    }
}

private val swatches = listOf(
    0xFFFFFFFF, 0xFF000000, 0xFFFF3B30, 0xFFFF9500, 0xFFFFCC00, 0xFF34C759,
    0xFF00C7BE, 0xFF0A84FF, 0xFF5E5CE6, 0xFFBF5AF2, 0xFFFF2D92, 0xFF8E8E93
).map { it.toInt() }

/** Preset swatches plus a custom hue/saturation/brightness picker. The colour's own alpha is preserved. */
@Composable
fun ColorPicker(id: Any, color: Int, onChange: (Int) -> Unit, onDone: () -> Unit) {
    var custom by remember(id) { mutableStateOf(false) }
    val hsv = remember(id) { mutableStateOf(FloatArray(3).also { android.graphics.Color.colorToHSV(color, it) }) }
    val alpha = color ushr 24
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            swatches.forEach { c ->
                val sel = (c and 0xFFFFFF) == (color and 0xFFFFFF)
                val sc by animateFloatAsState(if (sel) 1.18f else 1f, spring(), label = "sw")
                Box(
                    Modifier.size(30.dp).scale(sc).clip(CircleShape).background(Color(c))
                        .border(if (sel) 2.dp else 1.dp, if (sel) LF.accent else Color(0x44FFFFFF), CircleShape)
                        .clickable { onChange((alpha shl 24) or (c and 0xFFFFFF)); onDone() }
                )
            }
            Box(
                Modifier.size(30.dp).clip(CircleShape)
                    .background(Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)))
                    .border(if (custom) 2.dp else 1.dp, if (custom) LF.accent else Color(0x44FFFFFF), CircleShape)
                    .clickable { custom = !custom }
            )
        }
        AnimatedVisibility(custom) {
            Column {
                val f = hsv.value
                fun push(h: Float, s: Float, v: Float) {
                    hsv.value = floatArrayOf(h, s, v)
                    onChange(android.graphics.Color.HSVToColor(alpha, floatArrayOf(h, s, v)))
                }
                LSlider("Hue", f[0], 0f..360f, "${f[0].toInt()}°", { push(it, f[1], f[2]) }, onDone)
                LSlider("Saturation", f[1], 0f..1f, "${(f[1] * 100).toInt()}%", { push(f[0], it, f[2]) }, onDone)
                LSlider("Brightness", f[2], 0f..1f, "${(f[2] * 100).toInt()}%", { push(f[0], f[1], it) }, onDone)
            }
        }
    }
}

@Composable
fun LayerChip(l: Layer, selected: Boolean, onClick: () -> Unit, onRemove: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val ring by animateColorAsState(if (selected) LF.accent else LF.outline, label = "ring")
    Box(Modifier.size(62.dp)) {
        Box(
            Modifier.align(Alignment.Center).size(54.dp).clip(shape).background(LF.card)
                .border(if (selected) 2.dp else 1.dp, ring, shape).clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            when (l) {
                is PhotoLayer -> {
                    val bmp = PhotoCache.map[l.uri.toString()]
                    if (bmp != null) {
                        val ib = remember(bmp) { bmp.asImageBitmap() }
                        Image(ib, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                }
                is TextLayer -> Text("Aa", color = LF.text, fontSize = 18.sp, fontFamily = Fonts.compose(l.font))
                is IconLayer -> Canvas(Modifier.size(30.dp)) {
                    drawIntoCanvas { drawSvgFit(it.nativeCanvas, l.svg, size.width / 2, size.height / 2, size.width, l.color) }
                }
            }
        }
        Box(
            Modifier.align(Alignment.TopEnd).size(22.dp).clip(CircleShape).background(LF.danger).clickable { onRemove() },
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Rounded.Close, "Remove", tint = Color.White, modifier = Modifier.size(13.dp)) }
    }
}
