package dev.applimit.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import dev.applimit.R

private val Indigo = Color(0xFF3B2F9E)
private val Teal = Color(0xFF0F8B8D)

/** The launcher icon drawn in Compose, since painterResource cannot load an adaptive icon. */
// Launchers show the inner 72 of the 108 dp layer, hence the 1.5 scale.
@Composable
fun AppBadge(size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Indigo, Teal), Offset.Zero, Offset.Infinite)),
    ) {
        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, Modifier.size(size).scale(1.5f))
    }
}
