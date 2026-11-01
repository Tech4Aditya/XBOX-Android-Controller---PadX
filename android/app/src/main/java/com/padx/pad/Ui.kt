package com.padx.pad

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import android.content.Context
import android.content.SharedPreferences
import kotlin.math.roundToInt
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


// ---------- Layout persistence ----------
data class Ctl(val x: Float, val y: Float, val s: Float)   // x,y = center as fraction of screen, s = scale

object LayoutStore {
    val state = mutableStateMapOf<String, Ctl>()
    private var prefs: SharedPreferences? = null

    val defaults = mapOf(
        "LT" to Ctl(0.08f, 0.10f, 1f), "LB" to Ctl(0.08f, 0.20f, 1f),
        "RT" to Ctl(0.92f, 0.10f, 1f), "RB" to Ctl(0.92f, 0.20f, 1f),
        "BACK" to Ctl(0.41f, 0.09f, 1f), "GUIDE" to Ctl(0.50f, 0.09f, 1f), "START" to Ctl(0.59f, 0.09f, 1f),
        "LS" to Ctl(0.14f, 0.42f, 1f), "L3" to Ctl(0.25f, 0.31f, 1f),
        "DPAD" to Ctl(0.32f, 0.80f, 1f), "FACE" to Ctl(0.84f, 0.45f, 1f),
        "RS" to Ctl(0.675f, 0.80f, 1f), "R3" to Ctl(0.78f, 0.69f, 1f)
    )

    fun init(ctx: Context) {
        if (prefs != null) return
        val p = ctx.getSharedPreferences("padx_layout", 0)
        prefs = p
        for ((id, d) in defaults) {
            val raw = p.getString(id, null)?.split(",")
            state[id] = if (raw != null && raw.size == 3)
                Ctl(raw[0].toFloatOrNull() ?: d.x, raw[1].toFloatOrNull() ?: d.y, raw[2].toFloatOrNull() ?: d.s)
            else d
        }
    }

    fun get(id: String): Ctl = state[id] ?: defaults[id]!!
    fun move(id: String, dx: Float, dy: Float) {
        val c = get(id)
        state[id] = c.copy(x = (c.x + dx).coerceIn(0.03f, 0.97f), y = (c.y + dy).coerceIn(0.03f, 0.97f))
    }
    fun scale(id: String, s: Float) { state[id] = get(id).copy(s = s.coerceIn(0.5f, 2f)) }
    fun save() {
        val e = prefs?.edit() ?: return
        state.forEach { (k, v) -> e.putString(k, "${v.x},${v.y},${v.s}") }
        e.apply()
    }
    fun reset() { state.putAll(defaults); save() }
}

// ---------- Controls ----------
@Composable
fun DPad() {
    Box(Modifier.size(114.dp)) {
        PadButton("▲", Btn.UP, Modifier.align(Alignment.TopCenter), 38.dp, 38.dp, round = false)
        PadButton("▼", Btn.DOWN, Modifier.align(Alignment.BottomCenter), 38.dp, 38.dp, round = false)
        PadButton("◀", Btn.LEFT, Modifier.align(Alignment.CenterStart), 38.dp, 38.dp, round = false)
        PadButton("▶", Btn.RIGHT, Modifier.align(Alignment.CenterEnd), 38.dp, 38.dp, round = false)
    }
}

@Composable
fun FaceButtons() {
    Box(Modifier.size(150.dp)) {
        PadButton("Y", Btn.Y, Modifier.align(Alignment.TopCenter), 50.dp, 50.dp, Color(0xFFB8860B))
        PadButton("A", Btn.A, Modifier.align(Alignment.BottomCenter), 50.dp, 50.dp, Color(0xFF107C10))
        PadButton("X", Btn.X, Modifier.align(Alignment.CenterStart), 50.dp, 50.dp, Color(0xFF1565C0))
        PadButton("B", Btn.B, Modifier.align(Alignment.CenterEnd), 50.dp, 50.dp, Color(0xFFB71C1C))
    }
}

private class CtlDef(val id: String, val w: Dp, val h: Dp, val content: @Composable () -> Unit)

private val DEFS = listOf(
    CtlDef("LT", 96.dp, 40.dp) { PadButton("LT", Btn.LT, w = 96.dp, h = 40.dp, round = false) },
    CtlDef("LB", 96.dp, 34.dp) { PadButton("LB", Btn.LB, w = 96.dp, h = 34.dp, round = false) },
    CtlDef("RT", 96.dp, 40.dp) { PadButton("RT", Btn.RT, w = 96.dp, h = 40.dp, round = false) },
    CtlDef("RB", 96.dp, 34.dp) { PadButton("RB", Btn.RB, w = 96.dp, h = 34.dp, round = false) },
    CtlDef("BACK", 56.dp, 30.dp) { PadButton("BACK", Btn.BACK, w = 56.dp, h = 30.dp, round = false) },
    CtlDef("GUIDE", 40.dp, 40.dp) { PadButton("XB", Btn.GUIDE, w = 40.dp, h = 40.dp, color = Color(0xFF107C10)) },
    CtlDef("START", 56.dp, 30.dp) { PadButton("START", Btn.START, w = 56.dp, h = 30.dp, round = false) },
    CtlDef("LS", 120.dp, 120.dp) { Stick(120.dp, true) },
    CtlDef("L3", 34.dp, 34.dp) { PadButton("L3", Btn.L3, w = 34.dp, h = 34.dp) },
    CtlDef("DPAD", 114.dp, 114.dp) { DPad() },
    CtlDef("FACE", 150.dp, 150.dp) { FaceButtons() },
    CtlDef("RS", 120.dp, 120.dp) { Stick(120.dp, false) },
    CtlDef("R3", 34.dp, 34.dp) { PadButton("R3", Btn.R3, w = 34.dp, h = 34.dp) },
)

@Composable
fun PadScreen(onExit: () -> Unit) {
    val ctx = LocalContext.current
    remember { LayoutStore.init(ctx) }
    var edit by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<String?>(null) }

    BoxWithConstraints(Modifier.fillMaxSize().background(BG)) {
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }

        DEFS.forEach { d ->
            key(d.id) {
                val c = LayoutStore.get(d.id)
                Box(
                    Modifier
                        .offset {
                            val cc = LayoutStore.get(d.id)
                            IntOffset(
                                (cc.x * wPx - d.w.toPx() * cc.s / 2f).roundToInt(),
                                (cc.y * hPx - d.h.toPx() * cc.s / 2f).roundToInt()
                            )
                        }
                        .size(d.w * c.s, d.h * c.s)
                ) {
                    Box(
                        Modifier
                            .requiredSize(d.w, d.h)
                            .graphicsLayer {
                                scaleX = c.s; scaleY = c.s
                                transformOrigin = TransformOrigin(0f, 0f)
                            }
                    ) { d.content() }

                    if (edit) {
                        val isSel = selected == d.id
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Color(0x33000000))
                                .border(
                                    BorderStroke(if (isSel) 3.dp else 1.dp, if (isSel) Cyan else Color(0x99FFFFFF)),
                                    RoundedCornerShape(8.dp)
                                )
                                .pointerInput(d.id, wPx, hPx) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        selected = d.id
                                        do {
                                            val ev = awaitPointerEvent()
                                            val ch = ev.changes.firstOrNull { it.id == down.id }
                                            if (ch != null && ch.pressed) {
                                                val dl = ch.positionChange()
                                                if (dl != Offset.Zero) {
                                                    LayoutStore.move(d.id, dl.x / wPx, dl.y / hPx)
                                                    ch.consume()
                                                }
                                            }
                                        } while (ch != null && ch.pressed)
                                    }
                                }
                        )
                    }
                }
            }
        }

        if (edit) {
            Column(
                Modifier.align(Alignment.TopCenter).padding(top = 64.dp).width(270.dp)
                    .background(Color(0xEE0A0F14), RoundedCornerShape(12.dp))
                    .border(BorderStroke(1.dp, Cyan.copy(alpha = 0.6f)), RoundedCornerShape(12.dp))
                    .padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val sel = selected
                Text(
                    if (sel == null) "Drag any control to move it" else "$sel selected - drag to move",
                    color = Color.White, fontSize = 12.sp
                )
                if (sel != null) {
                    val s = LayoutStore.get(sel).s
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Size", color = Color.White, fontSize = 12.sp)
                        Slider(
                            value = s, onValueChange = { LayoutStore.scale(sel, it) },
                            valueRange = 0.5f..2f, modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                            colors = SliderDefaults.colors(thumbColor = Cyan, activeTrackColor = Cyan)
                        )
                        Text("${(s * 100).roundToInt()}%", color = Color.White, fontSize = 12.sp)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { LayoutStore.reset(); selected = null }) { Text("Reset") }
                    Button(onClick = { LayoutStore.save(); edit = false; selected = null }) { Text("Done") }
                }
            }
        } else {
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    "EDIT LAYOUT", color = Cyan.copy(alpha = 0.8f), fontSize = 11.sp,
                    modifier = Modifier.clickable {
                        Pad.buttons.set(0); Pad.lx = 0f; Pad.ly = 0f; Pad.rx = 0f; Pad.ry = 0f
                        edit = true
                    }.padding(8.dp)
                )
                Text(
                    "DISCONNECT", color = Color(0x88FFFFFF), fontSize = 11.sp,
                    modifier = Modifier.clickable { onExit() }.padding(8.dp)
                )
            }
        }
    }
}