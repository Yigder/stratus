package com.weatherclone.app.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * An iOS-style page sheet: slides up to just below the status bar and follows the finger when
 * dragged down, either by its header or by pulling down on scrolled-to-top content. Released
 * past a quarter of the way or with a downward flick it closes; otherwise it springs back.
 *
 * [content] receives a `dismiss` callback that plays the closing animation first.
 */
@Composable
fun IosSheet(onDismiss: () -> Unit, color: Color, content: @Composable (dismiss: () -> Unit) -> Unit) {
    val dismissed by rememberUpdatedState(onDismiss)
    val scope = rememberCoroutineScope()
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnBackPress = false),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.setDimAmount(0f); window?.setWindowAnimations(0) }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 10.dp
            val sheetH = with(density) { (maxHeight - top).toPx() }
            var offset by remember { mutableFloatStateOf(sheetH) }
            val job = remember { arrayOfNulls<Job>(1) }

            fun animateTo(target: Float, velocity: Float = 0f, then: () -> Unit = {}) {
                job[0]?.cancel()
                job[0] = scope.launch {
                    animate(offset, target, velocity, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 500f)) { v, _ -> offset = v }
                    then()
                }
            }
            fun close() = animateTo(sheetH) { dismissed() }
            fun settle(velocity: Float) {
                if (velocity > 1200f || (offset > sheetH * 0.25f && velocity > -600f)) animateTo(sheetH, velocity) { dismissed() }
                else animateTo(0f, velocity)
            }

            LaunchedEffect(Unit) { animateTo(0f) }
            androidx.activity.compose.BackHandler { close() }

            val connection = remember {
                object : NestedScrollConnection {
                    // While the sheet is pulled down, an upward drag lifts the sheet before scrolling content.
                    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                        if (offset > 0f && available.y < 0f && source == NestedScrollSource.UserInput) {
                            val used = maxOf(available.y, -offset)
                            offset += used
                            return Offset(0f, used)
                        }
                        return Offset.Zero
                    }

                    // Content is at the top and the finger keeps pulling down: move the sheet.
                    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                        if (available.y > 0f && source == NestedScrollSource.UserInput) {
                            job[0]?.cancel()
                            offset += available.y
                            return Offset(0f, available.y)
                        }
                        return Offset.Zero
                    }

                    override suspend fun onPreFling(available: Velocity): Velocity {
                        if (offset > 0f) { settle(available.y); return available }
                        return Velocity.Zero
                    }
                }
            }

            val progress = (1f - offset / sheetH.coerceAtLeast(1f)).coerceIn(0f, 1f)
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f * progress))
                    .clickable(remember { MutableInteractionSource() }, null) { close() }
            )
            Box(
                Modifier
                    .offset(y = top)
                    .fillMaxWidth()
                    .height(maxHeight - top)
                    .graphicsLayer { translationY = offset }
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .background(color)
                    .clickable(remember { MutableInteractionSource() }, null) {}
                    .draggable(
                        rememberDraggableState { d -> job[0]?.cancel(); offset = (offset + d).coerceAtLeast(0f) },
                        Orientation.Vertical,
                        onDragStopped = { v -> settle(v) },
                    )
                    .nestedScroll(connection)
            ) {
                // No stretch overscroll: pulling down past the top moves the sheet instead.
                CompositionLocalProvider(LocalOverscrollFactory provides null) {
                    content { close() }
                }
            }
        }
    }
}
