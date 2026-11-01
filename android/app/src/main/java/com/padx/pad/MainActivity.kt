package com.padx.pad

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent { App() }
    }
}

@Composable
fun App() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { ctx.getSharedPreferences("padx", 0) }
    var job by remember { mutableStateOf<Job?>(null) }
    var status by remember { mutableStateOf("Searching for PC...") }
    var manual by remember { mutableStateOf(prefs.getString("host", "") ?: "") }
    var scanning by remember { mutableStateOf(false) }

    fun connect(host: String) {
        prefs.edit().putString("host", host).apply()
        job = Net.stream(scope, host)
    }
    fun scan() {
        scope.launch {
            scanning = true; status = "Searching for PC..."
            val h = Net.discover(ctx)
            scanning = false
            if (h != null) connect(h) else status = "No PC found. Is the server running and on the same Wi-Fi?"
        }
    }
    LaunchedEffect(Unit) { scan() }

    if (job != null) {
        PadScreen(onExit = { job?.cancel(); job = null; Pad.buttons.set(0); status = "Disconnected" })
    } else {
        Column(
            Modifier.fillMaxSize().background(BG).padding(24.dp),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("PADX", color = Cyan, style = TextStyle(fontSize = 34.sp_()))
            Spacer(Modifier.height(8.dp))
            Text(status, color = Color.White)
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { if (!scanning) scan() }) { Text(if (scanning) "Scanning..." else "Scan again") }
                Box(Modifier.width(180.dp).background(Panel).padding(12.dp)) {
                    BasicTextField(
                        manual, { manual = it }, singleLine = true,
                        textStyle = TextStyle(color = Color.White), cursorBrush = SolidColor(Cyan)
                    )
                    if (manual.isEmpty()) Text("PC IP (manual)", color = Color.Gray)
                }
                Button(onClick = { if (manual.isNotBlank()) connect(manual.trim()) }) { Text("Connect") }
            }
        }
    }
}

private fun Int.sp_() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
