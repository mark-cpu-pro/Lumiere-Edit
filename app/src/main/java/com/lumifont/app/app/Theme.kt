package com.lumifont.app

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object LF {
    val bg = Color(0xFF0A0B10)
    val panel = Color(0xFF14161D)
    val card = Color(0xFF1E212B)
    val accent = Color(0xFF8B7CFF)
    val accent2 = Color(0xFF38E1D0)
    val text = Color(0xFFECEDF2)
    val muted = Color(0xFF8D93A5)
    val outline = Color(0xFF343848)
    val danger = Color(0xFFFF453A)
}

@Composable
fun LumiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = LF.accent, onPrimary = Color.White, secondary = LF.accent2,
            background = LF.bg, surface = LF.panel, surfaceVariant = LF.card,
            onSurface = LF.text, onSurfaceVariant = LF.muted, outline = LF.outline
        ),
        shapes = Shapes(
            small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(24.dp)
        ),
        content = content
    )
}
