package de.c3.tv.remote

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import java.net.DatagramPacket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface

/**
 * Minimal mDNS responder: answers A queries for [hostname] (e.g. "c3tv.local") with the
 * TV's current IPv4 address, so the iPhone shortcut keeps working when the router hands
 * out a new IP. Runs only together with [LinkReceiver].
 */
class MdnsResponder(private val context: Context, private val hostname: String) {

    @Volatile private var socket: MulticastSocket? = null
    private var lock: WifiManager.MulticastLock? = null

    fun start() {
        Thread({ run() }, "c3-mdns").apply { isDaemon = true }.start()
    }

    fun stop() {
        try { socket?.close() } catch (e: Exception) { }
        socket = null
        try { lock?.release() } catch (e: Exception) { }
        lock = null
    }

    private fun run() {
        try {
            lock = context.getSystemService(WifiManager::class.java)?.createMulticastLock("c3-mdns")?.apply {
                setReferenceCounted(false)
                acquire()
            }
            val group = InetAddress.getByName(GROUP)
            val s = MulticastSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(PORT))
                @Suppress("DEPRECATION")
                joinGroup(group)
            }
            socket = s
            val buf = ByteArray(1500)
            while (!s.isClosed) {
                val packet = DatagramPacket(buf, buf.size)
                s.receive(packet)
                try {
                    handle(packet, s, group)
                } catch (e: Exception) {
                    // Malformed packet – ignore.
                }
            }
        } catch (e: Exception) {
            if (socket != null) Log.w(TAG, "mDNS stopped: ${e.javaClass.simpleName}")
        }
    }

    private fun handle(packet: DatagramPacket, s: MulticastSocket, group: InetAddress) {
        val d = packet.data
        val len = packet.length
        if (len < 12) return
        val id = u16(d, 0)
        val flags = u16(d, 2)
        if (flags and 0x8000 != 0) return // a response, not a query
        val questions = u16(d, 4)
        var off = 12
        repeat(questions) {
            val (name, next) = readName(d, off, len) ?: return
            if (next + 4 > len) return
            val type = u16(d, next)
            val qclass = u16(d, next + 2)
            off = next + 4
            if (name.equals(hostname, ignoreCase = true) && (type == TYPE_A || type == TYPE_ANY) && (qclass and 0x7FFF) == 1) {
                val ip = ownIpv4() ?: return
                val legacy = packet.port != PORT
                val answer = buildAnswer(if (legacy) id else 0, ip, cacheFlush = !legacy)
                val target = if (legacy || (qclass and 0x8000) != 0) InetSocketAddress(packet.address, packet.port) else InetSocketAddress(group, PORT)
                s.send(DatagramPacket(answer, answer.size, target))
                return
            }
        }
    }

    private fun buildAnswer(id: Int, ip: ByteArray, cacheFlush: Boolean): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        fun w16(v: Int) { out.write(v shr 8 and 0xFF); out.write(v and 0xFF) }
        w16(id); w16(0x8400); w16(0); w16(1); w16(0); w16(0)
        hostname.trimEnd('.').split('.').forEach { label ->
            val bytes = label.toByteArray(Charsets.US_ASCII)
            out.write(bytes.size); out.write(bytes)
        }
        out.write(0)
        w16(TYPE_A)
        w16(if (cacheFlush) 0x8001 else 0x0001)
        w16(0); w16(120) // TTL 120 s
        w16(4)
        out.write(ip)
        return out.toByteArray()
    }

    private fun readName(d: ByteArray, start: Int, len: Int): Pair<String, Int>? {
        val labels = ArrayList<String>()
        var off = start
        var end = -1
        var jumps = 0
        while (off < len) {
            val l = d[off].toInt() and 0xFF
            when {
                l == 0 -> {
                    if (end < 0) end = off + 1
                    return labels.joinToString(".") to end
                }
                l and 0xC0 == 0xC0 -> {
                    if (off + 1 >= len || ++jumps > 8) return null
                    if (end < 0) end = off + 2
                    off = ((l and 0x3F) shl 8) or (d[off + 1].toInt() and 0xFF)
                }
                else -> {
                    if (off + 1 + l > len) return null
                    labels += String(d, off + 1, l, Charsets.US_ASCII)
                    off += 1 + l
                }
            }
        }
        return null
    }

    private fun ownIpv4(): ByteArray? = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .sortedBy { if (it.name.startsWith("wlan")) 0 else if (it.name.startsWith("eth")) 1 else 2 }
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
            ?.address
    } catch (e: Exception) {
        null
    }

    private fun u16(d: ByteArray, i: Int) = ((d[i].toInt() and 0xFF) shl 8) or (d[i + 1].toInt() and 0xFF)

    companion object {
        const val HOSTNAME = "c3tv.local"
        private const val TAG = "C3Mdns"
        private const val GROUP = "224.0.0.251"
        private const val PORT = 5353
        private const val TYPE_A = 1
        private const val TYPE_ANY = 255
    }
}
