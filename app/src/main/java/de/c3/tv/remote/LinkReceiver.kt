package de.c3.tv.remote

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import de.c3.tv.core.WebStore
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.MessageDigest

/**
 * "An C3 TV senden": receives links from the iPhone (Shortcuts app) in the home network
 * and opens them in Silk.
 *
 * Active only while (1) the private area is switched on in the C3 Updater (it provides the
 * secret code) and (2) the HOME redirect service is connected – that service already runs
 * and allows starting activities from the background, so no extra service is needed.
 * Nothing is logged or stored about received links.
 *
 * API (port [PORT]):
 *   POST /open  header "X-C3-Token: <code>", body JSON {"url": "..."}  (or {"url","token"})
 *   GET  /      small page with a form (fallback for any device)
 */
object LinkReceiver {

    const val PORT = 8765
    private const val TAG = "C3LinkReceiver"
    private const val MAX_HEADER = 8 * 1024
    private const val MAX_BODY = 8 * 1024

    @Volatile private var server: ServerSocket? = null
    @Volatile private var serviceConnected = false
    private var mdns: MdnsResponder? = null
    private var appContext: Context? = null
    private var lastOpenAt = 0L

    val isRunning: Boolean get() = server != null

    fun onServiceConnected(context: Context) {
        serviceConnected = true
        update(context)
    }

    fun onServiceDisconnected() {
        serviceConnected = false
        stop()
    }

    /** Starts or stops according to the private-area state. Safe to call often. */
    @Synchronized
    fun update(context: Context) {
        appContext = context.applicationContext
        val token = WebStore(context).receiverToken()
        if (serviceConnected && token != null) start(context.applicationContext) else stop()
    }

    @Synchronized
    private fun start(context: Context) {
        if (server != null) return
        try {
            val socket = ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(PORT))
            }
            server = socket
            Thread({ acceptLoop(socket) }, "c3-link-receiver").apply { isDaemon = true }.start()
            mdns = MdnsResponder(context, MdnsResponder.HOSTNAME).also { it.start() }
            Log.i(TAG, "Listening on port $PORT")
        } catch (e: Exception) {
            Log.w(TAG, "Could not start receiver", e)
            server = null
        }
    }

    @Synchronized
    private fun stop() {
        try { server?.close() } catch (e: Exception) { }
        server = null
        mdns?.stop()
        mdns = null
    }

    private fun acceptLoop(socket: ServerSocket) {
        while (!socket.isClosed) {
            val client = try { socket.accept() } catch (e: Exception) { break }
            try {
                client.soTimeout = 4000
                handle(client)
            } catch (e: Exception) {
                Log.d(TAG, "Request failed: ${e.javaClass.simpleName}")
            } finally {
                try { client.close() } catch (e: Exception) { }
            }
        }
    }

    // ---------------------------------------------------------------- HTTP

    private class Request(val method: String, val path: String, val query: Map<String, String>, val headers: Map<String, String>, val body: String)

    private fun handle(client: Socket) {
        val input = BufferedInputStream(client.getInputStream())
        val request = read(input) ?: return respond(client, 400, "text/plain", "Bad request")
        val context = appContext ?: return respond(client, 503, "text/plain", "Not ready")

        if (request.method == "GET" && (request.path == "/" || request.path.isEmpty())) {
            return respond(client, 200, "text/html; charset=utf-8", PAGE)
        }
        if (request.path != "/open") return respond(client, 404, "text/plain", "Not found")
        if (request.method != "POST" && request.method != "GET") return respond(client, 405, "text/plain", "Method not allowed")

        val json = try { if (request.body.trimStart().startsWith("{")) JSONObject(request.body) else null } catch (e: Exception) { null }
        val token = request.headers["x-c3-token"] ?: json?.optString("token")?.ifEmpty { null } ?: request.query["token"]
        val expected = WebStore(context).receiverToken()
        if (expected == null) return respond(client, 403, "application/json", """{"ok":false,"error":"Privat ist aus"}""")
        if (token == null || !MessageDigest.isEqual(token.trim().toByteArray(), expected.toByteArray())) {
            return respond(client, 401, "application/json", """{"ok":false,"error":"Falscher Code"}""")
        }

        val raw = (json?.optString("url")?.ifEmpty { null } ?: request.query["url"] ?: request.body).trim()
        val url = extractUrl(raw) ?: return respond(client, 400, "application/json", """{"ok":false,"error":"Keine gültige Adresse"}""")

        val now = System.currentTimeMillis()
        if (now - lastOpenAt < 500) return respond(client, 429, "application/json", """{"ok":false,"error":"Zu schnell"}""")
        lastOpenAt = now

        val opened = open(context, url)
        respond(client, if (opened) 200 else 500, "application/json",
            if (opened) """{"ok":true}""" else """{"ok":false,"error":"Kein Browser"}""")
    }

    /** Accepts a plain URL or text containing one (Shortcuts may send page titles with the URL). */
    private fun extractUrl(raw: String): String? {
        if (raw.length > 4096) return null
        val candidate = Regex("https?://\\S+").find(raw)?.value ?: return null
        val scheme = Uri.parse(candidate).scheme?.lowercase()
        return if (scheme == "http" || scheme == "https") candidate else null
    }

    private fun open(context: Context, url: String): Boolean {
        val uri = Uri.parse(url)
        for (intent in listOf(
            Intent(Intent.ACTION_VIEW, uri).setPackage(WebStore.SILK_PACKAGE),
            Intent(Intent.ACTION_VIEW, uri),
        )) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (e: ActivityNotFoundException) {
            } catch (e: SecurityException) {
            }
        }
        return false
    }

    private fun read(input: InputStream): Request? {
        val head = ByteArrayOutputStream()
        var matched = 0
        while (head.size() < MAX_HEADER) {
            val b = input.read()
            if (b < 0) return null
            head.write(b)
            matched = when {
                (matched == 0 || matched == 2) && b == '\r'.code -> matched + 1
                (matched == 1 || matched == 3) && b == '\n'.code -> matched + 1
                else -> 0
            }
            if (matched == 4) break
        }
        if (matched != 4) return null
        val lines = head.toString("ISO-8859-1").split("\r\n")
        val parts = lines.first().split(" ")
        if (parts.size < 2) return null
        val headers = HashMap<String, String>()
        lines.drop(1).forEach { line ->
            val i = line.indexOf(':')
            if (i > 0) headers[line.substring(0, i).trim().lowercase()] = line.substring(i + 1).trim()
        }
        val length = headers["content-length"]?.toIntOrNull() ?: 0
        if (length < 0 || length > MAX_BODY) return null
        val body = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(body, read, length - read)
            if (n < 0) break
            read += n
        }
        val target = parts[1]
        val q = target.indexOf('?')
        val path = if (q >= 0) target.substring(0, q) else target
        val query = HashMap<String, String>()
        if (q >= 0) {
            target.substring(q + 1).split('&').forEach { pair ->
                val e = pair.indexOf('=')
                if (e > 0) query[URLDecoder.decode(pair.substring(0, e), "UTF-8")] = URLDecoder.decode(pair.substring(e + 1), "UTF-8")
            }
        }
        return Request(parts[0].uppercase(), path, query, headers, String(body, 0, read, Charsets.UTF_8))
    }

    private fun respond(client: Socket, status: Int, type: String, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val reason = when (status) { 200 -> "OK"; 400 -> "Bad Request"; 401 -> "Unauthorized"; 403 -> "Forbidden"; 404 -> "Not Found"; 429 -> "Too Many Requests"; else -> "Error" }
        val header = "HTTP/1.1 $status $reason\r\nContent-Type: $type\r\nContent-Length: ${bytes.size}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n"
        client.getOutputStream().apply {
            write(header.toByteArray(Charsets.ISO_8859_1))
            write(bytes)
            flush()
        }
    }

    private const val PAGE = """<!doctype html><html lang="de"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>C3 TV</title>
<style>body{margin:0;font-family:-apple-system,system-ui,sans-serif;background:#0a0b0f;color:#f2f4f7;display:flex;justify-content:center}main{width:100%;max-width:420px;padding:28px 16px}h1{font-size:22px}label{display:block;margin:14px 0 6px;color:#9aa0ad;font-size:14px}input{width:100%;box-sizing:border-box;padding:12px;border-radius:12px;border:1px solid #2a2f3a;background:#1a1d24;color:#f2f4f7;font-size:16px}button{margin-top:18px;width:100%;padding:13px;border:0;border-radius:12px;background:#5aa9ff;color:#08111c;font-size:16px;font-weight:600}p{color:#9aa0ad;font-size:14px}</style></head>
<body><main><h1>An C3 TV senden</h1><form id="f"><label for="u">Adresse</label><input id="u" type="url" required placeholder="https://…"><label for="c">Code</label><input id="c" type="password" required autocomplete="off"><button>Am TV öffnen</button></form><p id="r"></p></main>
<script>f.onsubmit=async e=>{e.preventDefault();try{const x=await fetch('/open',{method:'POST',headers:{'Content-Type':'application/json','X-C3-Token':c.value},body:JSON.stringify({url:u.value})});const j=await x.json();r.textContent=j.ok?'Geöffnet.':(j.error||'Fehler');}catch(err){r.textContent='Nicht erreichbar.';}};</script></body></html>"""
}
