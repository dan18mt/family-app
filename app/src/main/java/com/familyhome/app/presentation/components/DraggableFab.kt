package com.familyhome.app.presentation.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * A FloatingActionButton that the user can drag to any position within its parent container.
 *
 * Place this inside a [Box] (or similar) that fills the desired drag area. The FAB
 * defaults to the bottom-right corner and snaps back inside bounds if the container
 * is resized (e.g. on rotation). Position is saved across recompositions and
 * configuration changes via [rememberSaveable].
 *
 * Taps still trigger [onClick] — the drag only activates after the pointer exceeds
 * the system touch-slop threshold.
 */
@Composable
fun DraggableFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = FloatingActionButtonDefaults.containerColor,
    contentColor: Color = contentColorFor(containerColor),
    shape: Shape = FloatingActionButtonDefaults.shape,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current

    // Float.NaN signals "use default position" — persists through config changes
    var posX by rememberSaveable { mutableStateOf(Float.NaN) }
    var posY by rememberSaveable { mutableStateOf(Float.NaN) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val fabPx    = with(density) { 56.dp.toPx() }
        val marginPx = with(density) { 16.dp.toPx() }
        val maxW     = constraints.maxWidth.toFloat()
        val maxH     = constraints.maxHeight.toFloat()

        val defaultX = maxW - fabPx - marginPx
        val defaultY = maxH - fabPx - marginPx

        val x = if (posX.isNaN()) defaultX else posX.coerceIn(0f, maxW - fabPx)
        val y = if (posY.isNaN()) defaultY else posY.coerceIn(0f, maxH - fabPx)

        FloatingActionButton(
            onClick        = onClick,
            modifier       = Modifier
                .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val curX = if (posX.isNaN()) defaultX else posX
                        val curY = if (posY.isNaN()) defaultY else posY
                        posX = (curX + dragAmount.x).coerceIn(0f, maxW - fabPx)
                        posY = (curY + dragAmount.y).coerceIn(0f, maxH - fabPx)
                    }
                },
            containerColor = containerColor,
            contentColor   = contentColor,
            shape          = shape,
        ) {
            content()
        }
    }
}
