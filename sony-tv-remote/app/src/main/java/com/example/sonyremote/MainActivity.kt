package com.example.sonyremote

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.wifi.WifiManager
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
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
    private lateinit var ytBox: EditText

    @Volatile private var host = ""
    @Volatile private var key = "0000"
    private val prefs by lazy { getSharedPreferences("tv", MODE_PRIVATE) }

    // Built-in copy of the button codes in Sony's published list. Used only if the TV
    // cannot give us its own list (see codeFor).
    private val codes = mapOf(
        "Up" to "AAAAAQAAAAEAAAB0Aw==",
        "Down" to "AAAAAQAAAAEAAAB1Aw==",
        "Left" to "AAAAAQAAAAEAAAA0Aw==",
        "Right" to "AAAAAQAAAAEAAAAzAw==",
        "OK" to "AAAAAQAAAAEAAABlAw==",
        "Home" to "AAAAAQAAAAEAAABgAw==",
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
        window.statusBarColor = c("#0F0C29")
        window.navigationBarColor = c("#24243E")
        val pad = dp(16)

        hostBox = field(EditText(this)).apply {
            hint = "TV address, e.g. 192.168.1.20"
            setText(prefs.getString("host", ""))
        }
        keyBox = field(EditText(this)).apply {
            hint = "Pre-shared key"
            setText(prefs.getString("key", "0000"))
        }
        status = TextView(this).apply {
            text = "Enter the TV address, or tap Find TV."
            textSize = 14f
            setTextColor(c("#7CFFCB"))
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.argb(40, 124, 255, 203))
                cornerRadius = dp(14).toFloat()
            }
        }
        // Fixed ids let Android restore typed text after the phone is rotated.
        val textBox = field(EditText(this)).apply { id = 101; hint = "Text to type on the TV" }
        ytBox = field(EditText(this)).apply { id = 102; hint = "YouTube link or video id" }

        // Left panel: connection and navigation. Right panel: everything else.
        val left = panel(
            card(
                "CONNECTION", hostBox, keyBox, status,
                row(
                    btn("Find TV", "find") { findTv() },
                    btn("Power on", "on") { act { bravia("system", "setPowerStatus", """{"status":true}"""); say("Power on sent") } },
                    btn("Power off", "off") { act { bravia("system", "setPowerStatus", """{"status":false}"""); say("Power off sent") } }
                )
            ),
            card(
                "NAVIGATE",
                row(Space(this), btn("▲", "glass", true) { press("Up") }, Space(this)),
                row(btn("◀", "glass", true) { press("Left") }, btn("OK", "ok", true) { press("OK") }, btn("▶", "glass", true) { press("Right") }),
                row(Space(this), btn("▼", "glass", true) { press("Down") }, Space(this)),
                row(btn("Back", "back") { press("Back") }, btn("Home", "home") { press("Home") }, btn("Input", "input") { press("Input") })
            )
        )
        val right = panel(
            card(
                "VOLUME & PLAYBACK",
                row(btn("Vol −", "vol") { press("Vol -") }, btn("Mute", "mute") { press("Mute") }, btn("Vol +", "vol") { press("Vol +") }),
                row(btn("▶ Play", "play") { press("Play") }, btn("⏸ Pause", "pause") { press("Pause") }, btn("Apps", "apps") { showApps() })
            ),
            card(
                "TYPE ON TV", textBox,
                btn("Send to TV", "send") {
                    val t = textBox.text.toString()
                    act { bravia("appControl", "setTextForm", JSONObject.quote(t)); say("Text sent") }
                }
            ),
            card(
                "PLAY ON TV", ytBox,
                btn("▶ Play YouTube on TV", "yt") { youtube(ytBox.text.toString()) },
                btn("Cast phone screen", "cast") {
                    try {
                        startActivity(Intent(Settings.ACTION_CAST_SETTINGS))
                    } catch (e: Exception) {
                        say("Open your phone's quick settings and tap Smart View or Cast.")
                    }
                }
            )
        )

        val header = column().apply {
            setPadding(dp(6), dp(4), dp(6), dp(14))
            addView(TextView(context).apply {
                text = "Sony TV Remote"
                textSize = 28f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
            })
            addView(TextView(context).apply {
                text = "Your Bravia, in your pocket"
                textSize = 14f
                setTextColor(c("#B8B5FF"))
            })
        }

        // Phones: one column. Tablets, foldables and landscape (600dp or wider): two columns.
        val widthDp = resources.configuration.screenWidthDp
        val wide = widthDp >= 600
        val body = LinearLayout(this).apply {
            orientation = if (wide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        }
        for (p in listOf(left, right)) {
            body.addView(
                p,
                if (wide) LinearLayout.LayoutParams(0, wrap, 1f).apply { setMargins(dp(6), 0, dp(6), 0) }
                else LinearLayout.LayoutParams(fill, wrap)
            )
        }
        val page = column().apply {
            setPadding(pad, pad, pad, pad)
            addView(header)
            addView(body)
        }

        // Never stretch wider than 900dp; centre the content on very large screens.
        val widthPx = minOf(dp(widthDp), dp(900))
        val holder = FrameLayout(this).apply {
            addView(page, FrameLayout.LayoutParams(widthPx, wrap, Gravity.CENTER_HORIZONTAL))
        }
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(c("#0F0C29"), c("#302B63"), c("#24243E"))
            )
            addView(holder)
        })

        // Only on a fresh start, so rotating the phone does not replay the video.
        if (savedInstanceState == null) handleShare(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    // Sharing a YouTube link from any app (YouTube, browser, chat) plays it on the TV.
    private fun handleShare(i: Intent?) {
        if (i == null || i.action != Intent.ACTION_SEND) return
        val shared = i.getStringExtra(Intent.EXTRA_TEXT) ?: return
        ytBox.setText(shared)
        youtube(shared)
    }

    // The phone's volume buttons control the TV volume while this app is open.
    private var lastVolume = 0L

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode != KeyEvent.KEYCODE_VOLUME_UP && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
            return super.onKeyDown(keyCode, event)
        }
        val now = System.currentTimeMillis()
        if (now - lastVolume >= 150) { // holding the button repeats, but not faster than the TV can follow
            lastVolume = now
            press(if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) "Vol +" else "Vol -")
        }
        return true
    }

    // ---------- look and feel ----------

    private val fill = ViewGroup.LayoutParams.MATCH_PARENT
    private val wrap = ViewGroup.LayoutParams.WRAP_CONTENT

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    private fun c(hex: String) = Color.parseColor(hex)

    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

    // A column of cards with space between them.
    private fun panel(vararg cards: View) = column().apply {
        cards.forEach { addView(it, LinearLayout.LayoutParams(fill, wrap).apply { bottomMargin = dp(12) }) }
    }

    // A frosted rounded card with a small coloured title.
    private fun card(title: String, vararg views: View) = column().apply {
        background = GradientDrawable().apply {
            setColor(Color.argb(38, 255, 255, 255))
            cornerRadius = dp(24).toFloat()
            setStroke(dp(1), Color.argb(60, 255, 255, 255))
        }
        setPadding(dp(14), dp(12), dp(14), dp(14))
        addView(TextView(context).apply {
            text = title
            textSize = 12f
            letterSpacing = 0.12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(c("#B8B5FF"))
            setPadding(dp(4), 0, 0, dp(6))
        })
        views.forEach { addView(it, LinearLayout.LayoutParams(fill, wrap).apply { setMargins(0, dp(3), 0, dp(3)) }) }
    }

    // A dark rounded text box with a soft outline.
    private fun field(e: EditText) = e.apply {
        setTextColor(Color.WHITE)
        setHintTextColor(Color.argb(130, 255, 255, 255))
        textSize = 15f
        isSingleLine = true
        setPadding(dp(14), dp(12), dp(14), dp(12))
        background = GradientDrawable().apply {
            setColor(Color.argb(70, 0, 0, 0))
            cornerRadius = dp(14).toFloat()
            setStroke(dp(1), Color.argb(50, 255, 255, 255))
        }
    }

    // Rounded gradient buttons. The style name picks the colours; "tall" is for the arrow pad.
    private fun btn(label: String, style: String, tall: Boolean = false, onClick: () -> Unit): Button {
        val (from, to, darkText) = when (style) {
            "find" -> Triple("#00C6FF", "#0072FF", false)
            "on" -> Triple("#11998E", "#38EF7D", true)
            "off" -> Triple("#FF416C", "#FF4B2B", false)
            "ok" -> Triple("#F857A6", "#FF5858", false)
            "back" -> Triple("#667EEA", "#764BA2", false)
            "home" -> Triple("#FC5C7D", "#6A82FB", false)
            "input" -> Triple("#00B4DB", "#0083B0", false)
            "vol" -> Triple("#43CEA2", "#185A9D", false)
            "mute" -> Triple("#757F9A", "#4B5670", false)
            "play" -> Triple("#56AB2F", "#A8E063", true)
            "pause" -> Triple("#F12711", "#F5AF19", false)
            "apps" -> Triple("#8E2DE2", "#4A00E0", false)
            "send" -> Triple("#4776E6", "#8E54E9", false)
            "yt" -> Triple("#FF0000", "#B20000", false)
            "cast" -> Triple("#00C9FF", "#92FE9D", true)
            else -> Triple("#4B4590", "#35306E", false) // "glass": arrow keys
        }
        val shape = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(c(from), c(to))).apply {
            cornerRadius = dp(if (tall) 28 else 18).toFloat()
        }
        return Button(this).apply {
            text = label
            isAllCaps = false
            typeface = Typeface.DEFAULT_BOLD
            textSize = if (tall) 22f else 15f
            setTextColor(if (darkText) c("#1B1B2F") else Color.WHITE)
            minHeight = dp(if (tall) 72 else 52)
            stateListAnimator = null
            background = RippleDrawable(ColorStateList.valueOf(Color.argb(90, 255, 255, 255)), shape, null)
            setOnClickListener { onClick() }
        }
    }

    private fun row(vararg views: View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        views.forEach {
            addView(it, LinearLayout.LayoutParams(0, wrap, 1f).apply {
                setMargins(dp(4), dp(4), dp(4), dp(4))
            })
        }
    }

    // ---------- small helpers ----------

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

    private fun press(name: String) = act { ircc(codeFor(name)); say("Sent $name") }

    // The TV's own button list is the most reliable source, because codes differ a little
    // between Sony models. Names below are the ones Sony uses where they differ from ours.
    private val tvNames = mapOf("OK" to "Confirm", "Back" to "Return", "Vol +" to "VolumeUp", "Vol -" to "VolumeDown")
    @Volatile private var tvCodes: Map<String, String>? = null
    @Volatile private var tvCodesHost = ""

    private fun codeFor(button: String): String {
        if (tvCodesHost != host) {
            tvCodes = try {
                val list = bravia("system", "getRemoteControllerInfo").getJSONArray("result").getJSONArray(1)
                (0 until list.length()).associate {
                    list.getJSONObject(it).getString("name") to list.getJSONObject(it).getString("value")
                }
            } catch (e: Exception) {
                null
            }
            tvCodesHost = host
        }
        return tvCodes?.get(tvNames[button] ?: button) ?: codes.getValue(button)
    }

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
        val headers = mapOf(
            "X-Auth-PSK" to key,
            "Content-Type" to "text/xml; charset=UTF-8",
            "SOAPACTION" to "\"urn:schemas-sony-com:service:IRCC:1#X_SendIRCC\""
        )
        // Sony's current guide writes the path in lower case; some older models only know upper case.
        try {
            http("POST", "http://$host/sony/ircc", xml, headers)
        } catch (e: Exception) {
            if (e.message?.contains("404") != true) throw e
            http("POST", "http://$host/sony/IRCC", xml, headers)
        }
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
                AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
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
            AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
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
