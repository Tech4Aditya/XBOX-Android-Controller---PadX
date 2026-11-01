package com.padx.pad

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val BG = Color(0xFF0A0F14)
val Cyan = Color(0xFF00E5FF)
val Panel = Color(0xFF16222E)

@Composable
fun PadButton(
    label: String, mask: Int, modifier: Modifier = Modifier,
    w: Dp = 48.dp, h: Dp = 48.dp, color: Color = Panel, round: Boolean = true, textColor: Color = Color.White
) {
    var down by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val shape: Shape = if (round) CircleShape else RoundedCornerShape(12.dp)
    Box(
        modifier
            .size(w, h)
            .clip(shape)
            .background(if (down) Cyan else color)
            .border(BorderStroke(1.dp, Cyan.copy(alpha = 0.5f)), shape)
            .pointerInput(mask) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    down = true
                    Pad.press(mask)
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    try { waitForUpOrCancellation() } finally { down = false; Pad.release(mask) }
                }
            },
        contentAlignment = Alignment.Center
    ) { Text(label, color = if (down) Color.Black else textColor, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
}

@Composable
fun Stick(diam: Dp, isLeft: Boolean, modifier: Modifier = Modifier) {
    var knob by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier
            .size(diam)
            .clip(CircleShape)
            .background(Color(0xFF0F1A24))
            .border(BorderStroke(2.dp, Cyan.copy(alpha = 0.6f)), CircleShape)
            .pointerInput(isLeft) {
                val r = size.width / 2f
                fun set(x: Float, y: Float) {
                    if (isLeft) { Pad.lx = x; Pad.ly = y } else { Pad.rx = x; Pad.ry = y }
                }
                fun update(p: Offset) {
                    var v = (p - Offset(r, r)) / r
                    val len = v.getDistance()
                    if (len > 1f) v /= len
                    knob = v * (r * 0.6f)
                    set(v.x, v.y)
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    update(down.position)
                    do {
                        val ev = awaitPointerEvent(PointerEventPass.Main)
                        val c = ev.changes.firstOrNull { it.id == down.id }
                        if (c != null && c.pressed) { update(c.position); c.consume() }
                    } while (c != null && c.pressed)
                    knob = Offset.Zero
                    set(0f, 0f)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .offset { IntOffset(knob.x.toInt(), knob.y.toInt()) }
                .size(diam * 0.4f)
                .clip(CircleShape)
                .background(Cyan.copy(alpha = 0.85f))
        )
    }
}

@Composable
fun PadScreen(onExit: () -> Unit) {
    Box(Modifier.fillMaxSize().background(BG)) {
        // Triggers + bumpers
        Column(Modifier.align(Alignment.TopStart).padding(start = 28.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PadButton("LT", Btn.LT, w = 96.dp, h = 40.dp, round = false)
            PadButton("LB", Btn.LB, w = 96.dp, h = 34.dp, round = false)
        }
        Column(Modifier.align(Alignment.TopEnd).padding(end = 28.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.End) {
            PadButton("RT", Btn.RT, w = 96.dp, h = 40.dp, round = false)
            PadButton("RB", Btn.RB, w = 96.dp, h = 34.dp, round = false)
        }

        // Center cluster
        Row(Modifier.align(Alignment.TopCenter).padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            PadButton("BACK", Btn.BACK, w = 56.dp, h = 30.dp, round = false)
            PadButton("X", Btn.GUIDE, w = 40.dp, h = 40.dp, color = Color(0xFF107C10))
            PadButton("START", Btn.START, w = 56.dp, h = 30.dp, round = false)
        }

        // Left stick (upper-left, Xbox style) + L3
        Stick(120.dp, true, Modifier.align(Alignment.TopStart).padding(start = 56.dp, top = 100.dp))
        PadButton("L3", Btn.L3, Modifier.align(Alignment.TopStart).padding(start = 180.dp, top = 100.dp), 34.dp, 34.dp)

        // D-pad (lower-left of center)
        Box(Modifier.align(Alignment.BottomStart).padding(start = 200.dp, bottom = 20.dp).size(114.dp)) {
            PadButton("▲", Btn.UP, Modifier.align(Alignment.TopCenter), 38.dp, 38.dp, round = false)
            PadButton("▼", Btn.DOWN, Modifier.align(Alignment.BottomCenter), 38.dp, 38.dp, round = false)
            PadButton("◀", Btn.LEFT, Modifier.align(Alignment.CenterStart), 38.dp, 38.dp, round = false)
            PadButton("▶", Btn.RIGHT, Modifier.align(Alignment.CenterEnd), 38.dp, 38.dp, round = false)
        }

        // Face buttons (upper-right)
        Box(Modifier.align(Alignment.TopEnd).padding(end = 56.dp, top = 96.dp).size(150.dp)) {
            PadButton("Y", Btn.Y, Modifier.align(Alignment.TopCenter), 50.dp, 50.dp, Color(0xFFB8860B))
            PadButton("A", Btn.A, Modifier.align(Alignment.BottomCenter), 50.dp, 50.dp, Color(0xFF107C10))
            PadButton("X", Btn.X, Modifier.align(Alignment.CenterStart), 50.dp, 50.dp, Color(0xFF1565C0))
            PadButton("B", Btn.B, Modifier.align(Alignment.CenterEnd), 50.dp, 50.dp, Color(0xFFB71C1C))
        }

        // Right stick (lower-right of center) + R3
        Stick(120.dp, false, Modifier.align(Alignment.BottomEnd).padding(end = 200.dp, bottom = 16.dp))
        PadButton("R3", Btn.R3, Modifier.align(Alignment.BottomEnd).padding(end = 160.dp, bottom = 100.dp), 34.dp, 34.dp)

        // Disconnect
        Text(
            "DISCONNECT", color = Color(0x88FFFFFF), fontSize = 11.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp)
                .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(); if (waitForUpOrCancellation() != null) onExit() } }
        )
    }
}
