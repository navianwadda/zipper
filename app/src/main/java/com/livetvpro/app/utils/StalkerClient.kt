package com.livetvpro.app.utils

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

object StalkerClient {

    const val SCHEME = "stalker://"

    private const val STB_UA =
        "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG250 stbapp ver: 4 rev: 2116 Mobile Safari/533.3"
    private const val X_UA = "Model: MAG250; Link: WiFi"

    data class Config(
        val portal: String,
        val mac: String,
        val sn: String = "",
        val id1: String = "",
        val id2: String = "",
        val model: String = "MAG250",
        val user: String = "",
        val pass: String = "",
    )

    private class Session(val token: String, val loadUrl: String)
    private val sessions = ConcurrentHashMap<String, Session>()

    fun isStalkerUrl(url: String) = url.trim().startsWith(SCHEME, ignoreCase = true)

    fun buildPlaylistUrl(c: Config): String = SCHEME + "portal?" + params(c)

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun dec(s: String) = URLDecoder.decode(s, "UTF-8")

    private fun params(c: Config) =
        "portal=${enc(c.portal.trim())}&mac=${enc(c.mac.trim().uppercase())}" +
            "&sn=${enc(c.sn.trim())}&id1=${enc(c.id1.trim())}&id2=${enc(c.id2.trim())}" +
            "&model=${enc(c.model.trim().ifBlank { "MAG250" })}&user=${enc(c.user.trim())}&pass=${enc(c.pass)}"

    private fun parseQuery(url: String): Map<String, String> =
        url.substringAfter('?', "").split('&').filter { it.contains('=') }.associate {
            dec(it.substringBefore('=')) to dec(it.substringAfter('='))
        }

    fun parseConfig(url: String): Config? {
        val q = parseQuery(url)
        val portal = q["portal"] ?: return null
        val mac = q["mac"] ?: return null
        return Config(portal, mac, q["sn"].orEmpty(), q["id1"].orEmpty(), q["id2"].orEmpty(),
            q["model"].orEmpty().ifBlank { "MAG250" }, q["user"].orEmpty(), q["pass"].orEmpty())
    }

        private fun loadUrl(portal: String): String {
        var p = portal.trim().trimEnd('/')
        if (!p.startsWith("http", ignoreCase = true)) p = "http://$p"
        if (p.endsWith("load.php", ignoreCase = true)) return p
        if (p.endsWith("/c", ignoreCase = true)) p = p.dropLast(2)
        return "$p/server/load.php"
    }

    private fun get(url: String, c: Config, token: String?): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", STB_UA)
        conn.setRequestProperty("X-User-Agent", "Model: ${c.model}; Link: WiFi")
        conn.setRequestProperty("User-Agent", STB_UA.replace("MAG250", c.model))
        conn.setRequestProperty("Accept", "*/*")
        conn.setRequestProperty("Cookie", "mac=${c.mac.uppercase()}; stb_lang=en; timezone=GMT")
        if (token != null) conn.setRequestProperty("Authorization", "Bearer $token")
        try {
            val code = conn.responseCode
            if (code != 200) throw java.io.IOException("HTTP $code from portal")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun json(text: String): JSONObject {
        val t = text.trim()
        if (!t.startsWith("{")) throw java.io.IOException("Portal returned an invalid response")
        return JSONObject(t)
    }

    private fun session(c: Config, force: Boolean = false): Session {
        val key = "${c.portal}|${c.mac}"
        if (!force) sessions[key]?.let { return it }
        val base = loadUrl(c.portal)

        val hs = json(get("$base?type=stb&action=handshake&token=&JsHttpRequest=1-xml", c, null))
        val token = hs.optJSONObject("js")?.optString("token").orEmpty()
        if (token.isEmpty()) throw java.io.IOException("Handshake failed (check portal URL / MAC)")

        val profile = "$base?type=stb&action=get_profile&hd=1&ver=ImageDescription:%200.2.18-r14-pub-250;" +
            "&num_banks=2&sn=${enc(c.sn)}&stb_type=${enc(c.model)}&client_type=STB&image_version=218" +
            "&video_out=hdmi&device_id=${enc(c.id1)}&device_id2=${enc(c.id2)}&signature=" +
            "&auth_second_step=1&hw_version=1.7-BD-00&not_valid_token=0&api_signature=262&prehash=" +
            "&JsHttpRequest=1-xml"
        runCatching { get(profile, c, token) }

        if (c.user.isNotBlank()) {
            runCatching {
                get("$base?type=stb&action=do_auth&login=${enc(c.user)}&password=${enc(c.pass)}" +
                    "&device_id=${enc(c.id1)}&device_id2=${enc(c.id2)}&JsHttpRequest=1-xml", c, token)
            }
        }

        return Session(token, base).also { sessions[key] = it }
    }

    private fun <T> withSession(c: Config, block: (Session) -> T): T =
        try { block(session(c)) } catch (e: java.io.IOException) {
            block(session(c, force = true))
        }

    fun fetchChannels(playlistUrl: String): List<M3uParser.M3uChannel> {
        val c = parseConfig(playlistUrl) ?: throw java.io.IOException("Invalid Stalker playlist")
        return withSession(c) { s ->
            val genres = HashMap<String, String>()
            runCatching {
                val arr = json(get("${s.loadUrl}?type=itv&action=get_genres&JsHttpRequest=1-xml", c, s.token))
                    .optJSONArray("js")
                if (arr != null) for (i in 0 until arr.length()) {
                    val g = arr.getJSONObject(i)
                    genres[g.optString("id")] = g.optString("title")
                }
            }

            val root = json(get("${s.loadUrl}?type=itv&action=get_all_channels&JsHttpRequest=1-xml", c, s.token))
            val data = root.optJSONObject("js")?.optJSONArray("data")
                ?: throw java.io.IOException("No channels returned (account inactive or MAC not authorised)")

            val out = ArrayList<M3uParser.M3uChannel>(data.length())
            for (i in 0 until data.length()) {
                val ch = data.getJSONObject(i)
                val cmd = ch.optString("cmd")
                if (cmd.isBlank()) continue
                var logo = ch.optString("logo")
                if (logo.isNotBlank() && !logo.startsWith("http")) {
                    logo = loadUrl(c.portal).substringBefore("/server/load.php").trimEnd('/') +
                        "/stalker_portal/misc/logos/320/" + logo
                }
                out.add(
                    M3uParser.M3uChannel(
                        name = ch.optString("name").ifBlank { "Channel ${i + 1}" },
                        logoUrl = logo,
                        streamUrl = SCHEME + "play?" + params(c) + "&cmd=" + enc(cmd),
                        groupTitle = genres[ch.optString("tv_genre_id")].orEmpty(),
                    )
                )
            }
            out
        }
    }

        fun resolve(streamUrl: String): String {
        if (!isStalkerUrl(streamUrl)) return streamUrl
        val c = parseConfig(streamUrl) ?: return streamUrl
        val cmd = parseQuery(streamUrl)["cmd"] ?: return streamUrl

        val direct = cmd.substringAfterLast(' ').trim()
        val needsLink = cmd.contains("localhost", true) || cmd.contains("/ch/", true) || !direct.startsWith("http")

        if (!needsLink) return direct

        val link = withSession(c) { s ->
            val r = get(
                "${s.loadUrl}?type=itv&action=create_link&cmd=${enc(cmd)}&series=&forced_storage=0" +
                    "&disable_ad=0&download=0&force_ch_link_check=0&JsHttpRequest=1-xml",
                c, s.token
            )
            json(r).optJSONObject("js")?.optString("cmd").orEmpty()
        }
        val resolved = link.substringAfterLast(' ').trim()
        if (!resolved.startsWith("http", ignoreCase = true)) {
            throw java.io.IOException("Portal did not return a playable link")
        }
        return resolved
    }
}
