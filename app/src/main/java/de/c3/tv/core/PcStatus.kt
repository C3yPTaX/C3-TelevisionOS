package de.c3.tv.core

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Online status of the C3 Desktop PCs, only while the home screen is visible.
 *
 * Sunshine announces itself as `_nvstream._tcp` (mDNS). C3 asks each host found for
 * `http://<host>:47989/serverinfo` – the same unauthenticated page Moonlight and the C3 Updater
 * read – and matches its `<uniqueid>` against the stored PC UUIDs. Known addresses are kept, so
 * the next start checks them directly and a host that stops answering turns "offline" within
 * one check interval. The request uses a plain socket (no HTTP stack), so the app needs no
 * cleartext-traffic exception. Nothing runs while C3 is in the background.
 */
class PcStatus(context: Context, private val onChange: (Map<String, State>) -> Unit) {

    enum class State { ONLINE, BUSY, OFFLINE }

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("c3_pc_hosts", Context.MODE_PRIVATE)
    private val nsd: NsdManager? = appContext.getSystemService(NsdManager::class.java)
    private val main = Handler(Looper.getMainLooper())

    private var worker: ScheduledExecutorService? = null
    private var discovery: NsdManager.DiscoveryListener? = null
    private var uuids: Set<String> = emptySet()

    // Worker thread only.
    private val hosts = HashMap<String, String>() // uuid -> host address
    private val states = HashMap<String, State>()
    private val resolveQueue = ArrayDeque<NsdServiceInfo>()
    private var resolving = false

    /** Starts checking [pcUuids]; replaces a previous run. Main thread. */
    fun start(pcUuids: Collection<String>) {
        stop()
        uuids = pcUuids.map { it.lowercase() }.toSet()
        if (uuids.isEmpty()) return
        val w = ScheduledThreadPoolExecutor(1).also { worker = it }
        w.execute {
            hosts.clear()
            states.clear()
            uuids.forEach { u -> prefs.getString(u, null)?.let { hosts[u] = it } }
            checkKnownHosts()
        }
        w.scheduleWithFixedDelay({ checkKnownHosts(); markMissingOffline() }, FIRST_ROUND_S, INTERVAL_S, TimeUnit.SECONDS)
        startDiscovery()
    }

    fun stop() {
        discovery?.let { l -> try { nsd?.stopServiceDiscovery(l) } catch (e: Exception) { } }
        discovery = null
        worker?.shutdownNow()
        worker = null
    }

    private fun startDiscovery() {
        val manager = nsd ?: return
        val listener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(info: NsdServiceInfo) {
                onWorker { resolveQueue.addLast(info); resolveNext() }
            }
            override fun onServiceLost(info: NsdServiceInfo) {}
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }
        try {
            manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
            discovery = listener
        } catch (e: Exception) {
            // mDNS unavailable: only known addresses are checked.
        }
    }

    /** NsdManager on Android 11 resolves one service at a time. Worker thread. */
    private fun resolveNext() {
        val manager = nsd ?: return
        if (resolving) return
        val next = resolveQueue.removeFirstOrNull() ?: return
        resolving = true
        try {
            @Suppress("DEPRECATION")
            manager.resolveService(next, object : NsdManager.ResolveListener {
                override fun onServiceResolved(info: NsdServiceInfo) {
                    onWorker {
                        resolving = false
                        info.host?.hostAddress?.let { probe(it) }
                        resolveNext()
                    }
                }
                override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                    onWorker { resolving = false; resolveNext() }
                }
            })
        } catch (e: Exception) {
            resolving = false
        }
    }

    /** Runs [task] on the worker; late NSD callbacks after [stop] are dropped. */
    private fun onWorker(task: () -> Unit) {
        try {
            worker?.execute(task)
        } catch (e: RejectedExecutionException) {
        }
    }

    private fun checkKnownHosts() {
        hosts.entries.toList().forEach { (uuid, host) ->
            val info = fetch(host)
            when {
                info == null -> set(uuid, State.OFFLINE)
                info.uuid == uuid -> set(uuid, info.state)
                // Another PC answers on this address now (new DHCP lease): forget it.
                else -> { hosts.remove(uuid); if (info.uuid in uuids) remember(info.uuid, host, info.state) }
            }
        }
    }

    /** After the first discovery round, PCs without an address are offline. */
    private fun markMissingOffline() {
        uuids.filter { it !in hosts }.forEach { set(it, State.OFFLINE) }
    }

    private fun probe(host: String) {
        val info = fetch(host) ?: return
        if (info.uuid in uuids) remember(info.uuid, host, info.state)
    }

    private fun remember(uuid: String, host: String, state: State) {
        if (hosts[uuid] != host) {
            hosts[uuid] = host
            prefs.edit().putString(uuid, host).apply()
        }
        set(uuid, state)
    }

    private fun set(uuid: String, state: State) {
        if (states[uuid] == state) return
        states[uuid] = state
        val snapshot = HashMap(states)
        main.post { if (worker != null) onChange(snapshot) }
    }

    private data class ServerInfo(val uuid: String, val state: State)

    /** Minimal HTTP/1.0 GET of Sunshine's serverinfo page. */
    private fun fetch(host: String): ServerInfo? = try {
        Socket().use { s ->
            s.connect(InetSocketAddress(host, PORT), TIMEOUT_MS)
            s.soTimeout = TIMEOUT_MS
            s.getOutputStream().write("GET /serverinfo HTTP/1.0\r\nHost: $host\r\nConnection: close\r\n\r\n".toByteArray())
            val out = ByteArrayOutputStream()
            val buf = ByteArray(4096)
            val input = s.getInputStream()
            while (out.size() < MAX_BYTES) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
            }
            val body = out.toString("UTF-8")
            val uuid = tag(body, "uniqueid")?.lowercase()
            if (uuid == null) null else {
                val busy = tag(body, "state")?.contains("BUSY", ignoreCase = true) == true
                ServerInfo(uuid, if (busy) State.BUSY else State.ONLINE)
            }
        }
    } catch (e: Exception) {
        null
    }

    private fun tag(xml: String, name: String): String? {
        val start = xml.indexOf("<$name>")
        if (start < 0) return null
        val end = xml.indexOf("</$name>", start)
        if (end < 0) return null
        return xml.substring(start + name.length + 2, end).trim().ifEmpty { null }
    }

    companion object {
        private const val SERVICE_TYPE = "_nvstream._tcp"
        private const val PORT = 47989
        private const val TIMEOUT_MS = 1500
        private const val MAX_BYTES = 32 * 1024
        private const val FIRST_ROUND_S = 8L
        private const val INTERVAL_S = 15L
    }
}
