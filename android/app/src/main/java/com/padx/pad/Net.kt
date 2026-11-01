package com.padx.pad

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.*
import java.net.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger

const val PORT = 26760

/** Bit values 0..0xFFFF map 1:1 to XInput wButtons. LT/RT are digital bits above 16. */
object Btn {
    const val UP = 0x0001; const val DOWN = 0x0002; const val LEFT = 0x0004; const val RIGHT = 0x0008
    const val START = 0x0010; const val BACK = 0x0020; const val L3 = 0x0040; const val R3 = 0x0080
    const val LB = 0x0100; const val RB = 0x0200; const val GUIDE = 0x0400
    const val A = 0x1000; const val B = 0x2000; const val X = 0x4000; const val Y = 0x8000
    const val LT = 0x10000; const val RT = 0x20000
}

object Pad {
    val buttons = AtomicInteger(0)
    @Volatile var lx = 0f
    @Volatile var ly = 0f
    @Volatile var rx = 0f
    @Volatile var ry = 0f
    fun press(m: Int) { buttons.updateAndGet { it or m } }
    fun release(m: Int) { buttons.updateAndGet { it and m.inv() } }
}

object Net {
    private fun subnetBroadcast(ctx: Context): InetAddress {
        val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION") val d = wm.dhcpInfo
        if (d == null || d.ipAddress == 0 || d.netmask == 0) return InetAddress.getByName("255.255.255.255")
        val b = (d.ipAddress and d.netmask) or d.netmask.inv()
        return InetAddress.getByAddress(
            byteArrayOf(b.toByte(), (b shr 8).toByte(), (b shr 16).toByte(), (b shr 24).toByte())
        )
    }

    /** Broadcasts a hello; the PC server answers. Returns the PC's IP or null. */
    suspend fun discover(ctx: Context): String? = withContext(Dispatchers.IO) {
        val msg = "CYBERPAD_DISCOVER".toByteArray()
        repeat(4) {
            try {
                DatagramSocket().use { s ->
                    s.broadcast = true
                    s.soTimeout = 700
                    for (addr in listOf(subnetBroadcast(ctx), InetAddress.getByName("255.255.255.255")))
                        s.send(DatagramPacket(msg, msg.size, addr, PORT))
                    val p = DatagramPacket(ByteArray(64), 64)
                    s.receive(p)
                    if (String(p.data, 0, p.length) == "CYBERPAD_HERE") return@withContext p.address.hostAddress
                }
            } catch (_: SocketTimeoutException) {
            } catch (_: Exception) {
            }
        }
        null
    }

    /** ~125 Hz state stream. Cancel the returned Job to disconnect. */
    fun stream(scope: CoroutineScope, host: String): Job = scope.launch(Dispatchers.IO) {
        DatagramSocket().use { s ->
            s.connect(InetAddress.getByName(host), PORT)
            val bb = ByteBuffer.allocate(15).order(ByteOrder.LITTLE_ENDIAN)
            var seq = 0
            fun axis(v: Float) = (v.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
            while (isActive) {
                val b = Pad.buttons.get()
                bb.clear()
                bb.put(0xCB.toByte())
                bb.putShort((seq++ and 0xFFFF).toShort())
                bb.putShort((b and 0xFFFF).toShort())
                bb.put((if (b and Btn.LT != 0) 255 else 0).toByte())
                bb.put((if (b and Btn.RT != 0) 255 else 0).toByte())
                bb.putShort(axis(Pad.lx))
                bb.putShort(axis(-Pad.ly))   // screen Y is down, XInput Y is up
                bb.putShort(axis(Pad.rx))
                bb.putShort(axis(-Pad.ry))
                try { s.send(DatagramPacket(bb.array(), 15)) } catch (_: Exception) {}
                delay(8)
            }
        }
    }
}
