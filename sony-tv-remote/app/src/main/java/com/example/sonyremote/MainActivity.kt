package com.example.sonyremote

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import org.json.JSONObject
import java.net.*

// One-screen remote for a Sony Bravia TV on the same Wi-Fi.
//  - Buttons, power, apps, typing: Sony's own network remote service (needs the pre-shared key).
//  - YouTube: the standard "DIAL" casting service that every TV with Chromecast built in offers.
//  - Screen mirroring: opens the phone's own Cast / Smart View screen.
class MainActivity : Activity() {

    private lateinit var hostBox: EditText
    private lateinit var keyBox: EditText
    private lateinit var status: TextView

    @Volatile private var host = ""
    @Volatile private var key = "0000"
    private val prefs by lazy { getSharedPreferences("tv", MODE_PRIVATE) }

    // Button codes published by Sony for Bravia televisions.
    private val codes = mapOf(
        "Up" to "AAAAAQAAAAEAAAB0Aw==",
        "Down" to "AAAAAQAAAAEAAAB1Aw==",
        "Left" to "AAAAAQAAAAEAAAA0Aw==",
        "Right" to "AAAAAQAAAAEAAAAzAw==",
        "OK" to "AAAAAQAAAAEAAABlAw==",
        "Home" to "AAAAAgAAABoAAABXAw==",
        "Back" to "AAAAAgAAAJcAAAAjAw==",
        "Input" to "AAAAAQAAAAEAAAAlAw==",
        "Vol +" to "AAAAAQAAAAEAAAASAw==",
        "Vol -" to "AAAAAQAAAAEAAAATAw==",
        "Mute" to "AAAAAQAAAAEAAAAUAw==",
        "Play" to "AAAAAgAAAJcAAAAaAw==",
        "Pause" to "AAAAAgAAAJcAAAAZAw=="
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()

        hostBox = EditText(this).apply {
            hint = "TV address, e.g. 192.168.1.20"
            setText(prefs.getString("host", ""))
        }
        keyBox = EditText(this).apply {
            hint = "Pre-shared key"
            setText(prefs.getString("key", "0000"))
        }
        status = TextView(this).apply { text = "Enter the TV address, or tap Find TV." }
        // Fixed ids let Android restore typed text after the phone is rotated.
        val textBox = EditText(this).apply { id = 101; hint = "Text to type on the TV" }
        val ytBox = EditText(this).apply { id = 102; hint = "YouTube link or video id" }

        // Left panel: connection and navigation. Right panel: everything else.
        val left = column().apply {
            addView(hostBox)
            addView(keyBox)
            addView(status)
            addView(row(
                btn("Find TV") { findTv() },
                btn("Power on") { act { bravia("system", "setPowerStatus", """{"status":true}"""); say("Power on sent") } },
                btn("Power off") { act { bravia("system", "setPowerStatus", """{"status":false}"""); say("Power off sent") } }
            ))
            addView(row(Space(context), btn("▲", true) { press("Up") }, Space(context)))
            addView(row(btn("◀", true) { press("Left") }, btn("OK", true) { press("OK") }, btn("▶", true) { press("Right") }))
            addView(row(Space(context), btn("▼", true) { press("Down") }, Space(context)))
            addView(row(btn("Back") { press("Back") }, btn("Home") { press("Home") }, btn("Input") { press("Input") }))
        }
        val right = column().apply {
            addView(row(btn("Vol -") { press("Vol -") }, btn("Mute") { press("Mute") }, btn("Vol +") { press("Vol +") }))
            addView(row(btn("Play") { press("Play") }, btn("Pause") { press("Pause") }, btn("Apps") { showApps() }))
            addView(textBox)
            addView(btn("Send text to TV") {
                val t = textBox.text.toString()
                act { bravia("appControl", "setTextForm", JSONObject.quote(t)); say("Text sent") }
            })
            addView(ytBox)
            addView(btn("Play YouTube on TV") { youtube(ytBox.text.toString()) })
            addView(btn("Cast phone screen") {
                try {
                    startActivity(Intent(Settings.ACTION_CAST_SETTINGS))
                } catch (e: Exception) {
                    say("Open your phone's quick settings and tap Smart View or Cast.")
                }
            })
        }

        // Phones: one column. Tablets, foldables and landscape (600dp or wider): two columns.
        val widthDp = resources.configuration.screenWidthDp
        val wide = widthDp >= 600
        val content = LinearLayout(this).apply {
            orientation = if (wide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        for (panel in listOf(left, right)) {
            content.addView(
                panel,
                if (wide) LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(pad / 2, 0, pad / 2, 0)
                } else LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            )
        }

        // Never stretch wider than 900dp; centre the content on very large screens.
        val widthPx = minOf(dp(widthDp), dp(900))
        val holder = FrameLayout(this).apply {
            addView(content, FrameLayout.LayoutParams(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL))
        }
        setContentView(ScrollView(this).apply { isFillViewport = true; addView(holder) })
    }

    // ---------- small helpers ----------

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

    // Buttons keep a comfortable touch height; the arrow pad uses taller ones.
    private fun btn(label: String, tall: Boolean = false, onClick: () -> Unit) =
        Button(this).apply {
            text = label
            minHeight = dp(if (tall) 72 else 52)
            setOnClickListener { onClick() }
        }

    private fun row(vararg views: View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        views.forEach {
            addView(it, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(dp(4), dp(4), dp(4), dp(4))
            })
        }
    }

    private fun say(t: String) = runOnUiThread { status.text = t }

    private fun save() {
        host = hostBox.text.toString().trim()
        key = keyBox.text.toString().trim()
        prefs.edit().putString("host", host).putString("key", key).apply()
    }

    private fun bg(block: () -> Unit) {
        Thread {
            try {
                block()
            } catch (e: Exception) {
                say("Error: ${e.message}")
            }
        }.start()
    }

    // Saves the typed address and key, then runs the work off the main thread.
    private fun act(block: () -> Unit) {
        save()
        if (host.isEmpty()) {
            say("Enter the TV address first.")
            return
        }
        bg(block)
    }

    private fun press(name: String) = act { ircc(codes.getValue(name)); say("Sent $name") }

    // ---------- network ----------

    private class Reply(val body: String, private val headers: Map<String?, List<String>>) {
        fun header(name: String) =
            headers.entries.firstOrNull { it.key.equals(name, true) }?.value?.firstOrNull()
    }

    private fun http(method: String, url: String, body: String? = null, headers: Map<String, String> = emptyMap()): Reply {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 4000
        c.readTimeout = 6000
        headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
        if (body != null) {
            c.doOutput = true
            c.outputStream.use { it.write(body.toByteArray()) }
        }
        val code = c.responseCode
        val text = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.readText() ?: ""
        if (code == 403) throw Exception("TV refused access. Check the pre-shared key.")
        if (code >= 400) throw Exception("TV answered with error $code")
        return Reply(text, c.headerFields)
    }

    private fun ircc(code: String) {
        val xml = """<?xml version="1.0"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"><s:Body><u:X_SendIRCC xmlns:u="urn:schemas-sony-com:service:IRCC:1"><IRCCCode>$code</IRCCCode></u:X_SendIRCC></s:Body></s:Envelope>"""
        http(
            "POST", "http://$host/sony/IRCC", xml,
            mapOf(
                "X-Auth-PSK" to key,
                "Content-Type" to "text/xml; charset=UTF-8",
                "SOAPACTION" to "\"urn:schemas-sony-com:service:IRCC:1#X_SendIRCC\""
            )
        )
    }

    private fun bravia(service: String, method: String, params: String = ""): JSONObject {
        val body = """{"method":"$method","id":1,"params":[$params],"version":"1.0"}"""
        val reply = http(
            "POST", "http://$host/sony/$service", body,
            mapOf("X-Auth-PSK" to key, "Content-Type" to "application/json")
        )
        val json = JSONObject(reply.body)
        if (json.has("error")) throw Exception("TV refused: ${json.getJSONArray("error").optString(1)}")
        return json
    }

    // Asks every casting-capable device on the Wi-Fi to answer; returns address -> description link.
    private fun ssdp(): Map<String, String> {
        val lock = (applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
            .createMulticastLock("ssdp").apply { acquire() }
        val found = linkedMapOf<String, String>()
        try {
            DatagramSocket().use { s ->
                s.soTimeout = 1000
                val q = ("M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\n" +
                    "ST: urn:dial-multiscreen-org:service:dial:1\r\n\r\n").toByteArray()
                s.send(DatagramPacket(q, q.size, InetAddress.getByName("239.255.255.250"), 1900))
                val end = System.currentTimeMillis() + 3500
                val buf = ByteArray(2048)
                while (System.currentTimeMillis() < end) {
                    try {
                        val p = DatagramPacket(buf, buf.size)
                        s.receive(p)
                        val loc = Regex("(?im)^location:\\s*(\\S+)")
                            .find(String(p.data, 0, p.length))?.groupValues?.get(1)
                        val addr = p.address.hostAddress
                        if (loc != null && addr != null) found[addr] = loc
                    } catch (e: SocketTimeoutException) {
                        // keep waiting until the time is up
                    }
                }
            }
        } finally {
            lock.release()
        }
        return found
    }

    private fun findTv() {
        say("Searching…")
        bg {
            val found = ssdp()
            if (found.isEmpty()) throw Exception("No TV answered. Phone and TV must be on the same Wi-Fi.")
            val ips = found.keys.toList()
            val labels = ips.map { ip ->
                val name = try {
                    Regex("<friendlyName>(.*?)</friendlyName>")
                        .find(http("GET", found.getValue(ip)).body)?.groupValues?.get(1)
                } catch (e: Exception) {
                    null
                }
                "${name ?: "Device"} ($ip)"
            }
            runOnUiThread {
                status.text = "Pick your TV."
                AlertDialog.Builder(this)
                    .setTitle("Devices found")
                    .setItems(labels.toTypedArray()) { _, i -> hostBox.setText(ips[i]); save() }
                    .show()
            }
        }
    }

    private fun showApps() = act {
        val list = bravia("appControl", "getApplicationList").getJSONArray("result").getJSONArray(0)
        val titles = (0 until list.length()).map { list.getJSONObject(it).getString("title") }
        val uris = (0 until list.length()).map { list.getJSONObject(it).getString("uri") }
        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle("Open on TV")
                .setItems(titles.toTypedArray()) { _, i ->
                    bg {
                        bravia("appControl", "setActiveApp", JSONObject().put("uri", uris[i]).toString())
                        say("Opened ${titles[i]}")
                    }
                }
                .show()
        }
    }

    // The TV tells us the address of its casting service; we remember it per TV.
    private fun dialUrl(): String {
        prefs.getString("dial_$host", null)?.let { return it }
        val loc = ssdp()[host] ?: throw Exception("TV did not answer the search. Same Wi-Fi?")
        val url = http("GET", loc).header("Application-URL")
            ?: throw Exception("This TV does not offer YouTube casting.")
        val full = if (url.endsWith("/")) url else "$url/"
        prefs.edit().putString("dial_$host", full).apply()
        return full
    }

    private fun youtube(text: String) {
        val id = Regex("(?:v=|youtu\\.be/|shorts/|embed/)([A-Za-z0-9_-]{11})").find(text)?.groupValues?.get(1)
            ?: text.trim().takeIf { Regex("[A-Za-z0-9_-]{11}").matches(it) }
        if (id == null) {
            say("Paste a YouTube link or the 11 character video id.")
            return
        }
        act {
            http("POST", dialUrl() + "YouTube", "v=$id", mapOf("Content-Type" to "text/plain; charset=utf-8"))
            say("Playing on TV")
        }
    }
}
