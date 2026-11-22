package com.novatube.tv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * One InnerTube "client". YouTube returns different data shapes and stream formats per client,
 * so the app can pick a different one per request (feed vs playback).
 */
data class ClientSpec(
    val name: String,
    val version: String,
    val headerId: String,
    val userAgent: String,
    /** The public InnerTube key YouTube's own clients send for this client type. */
    val apiKey: String,
    val extra: Map<String, Any> = emptyMap(),
)

object Clients {
    // Bump these when YouTube rejects older clients; they are the only strings that normally need updating.
    val TV = ClientSpec(
        name = "TVHTML5",
        version = "7.20261118.18.00",
        headerId = "7",
        userAgent = "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/25.lts.30.0-gold (unlike Gecko) v8/8.8.278.8-jit " +
            "gles Starboard/17, SystemIntegratorName/AndroidTV, DeviceType/ANDROIDTV",
        apiKey = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8",
        extra = mapOf(
            "clientScreen" to "WATCH",
            "platform" to "TV",
            "osName" to "Cobalt",
            "osVersion" to "25",
        ),
    )

    val WEB = ClientSpec(
        name = "WEB",
        version = "2.20261118.00.00",
        headerId = "1",
        userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/131.0.0.0 Safari/537.36",
        apiKey = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8",
    )

    val ANDROID = ClientSpec(
        name = "ANDROID",
        version = "20.10.38",
        headerId = "3",
        userAgent = "com.google.android.youtube/20.10.38 (Linux; U; Android 11) gzip",
        apiKey = "AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w",
        extra = mapOf(
            "androidSdkVersion" to 30,
            "osName" to "Android",
            "osVersion" to "11",
        ),
    )

    val IOS = ClientSpec(
        name = "IOS",
        version = "20.10.4",
        headerId = "5",
        userAgent = "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X)",
        apiKey = "AIzaSyB-63vPrdThhKuerbB2N_l7Kwwcxj6yUAc",
        extra = mapOf(
            "deviceModel" to "iPhone16,2",
            "osName" to "iPhone",
            "osVersion" to "18.1.0.22B83",
        ),
    )
}

/**
 * Thin, dependency-light InnerTube (youtubei/v1) client. No API key of the user's own is needed:
 * the public web key is the same one youtube.com itself uses.
 *
 * Requests deliberately never ask for ad placements and the UI never renders ad slots, so the app
 * is ad-free; playback uses the raw stream URLs directly (no ad-serving player SDK).
 */
class InnerTube(private val prefs: Prefs, private val auth: Auth) {

    private val json = "application/json; charset=utf-8".toMediaType()

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private fun clientContext(spec: ClientSpec): JSONObject {
        val c = JSONObject()
        c.put("clientName", spec.name)
        c.put("clientVersion", spec.version)
        c.put("hl", Locale.getDefault().language.ifEmpty { "en" })
        c.put("gl", "US")
        c.put("userAgent", spec.userAgent)
        for ((k, v) in spec.extra) c.put(k, v)
        val visitor = prefs.visitorData
        if (!visitor.isNullOrEmpty()) c.put("visitorData", visitor)
        return c
    }

    private suspend fun post(
        path: String,
        body: JSONObject,
        spec: ClientSpec,
        useAuth: Boolean = true,
    ): JSONObject = withContext(Dispatchers.IO) {
        val cookie = prefs.youtubeCookie?.takeIf { useAuth && CookieAuth.hasSession(it) }
        val token = if (useAuth && cookie == null) auth.accessToken() else null

        val builder = Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/$path?prettyPrint=false&key=${spec.apiKey}")
            .header("Content-Type", "application/json")
            .header("User-Agent", spec.userAgent)
            .header("X-YouTube-Client-Name", spec.headerId)
            .header("X-YouTube-Client-Version", spec.version)
            .header("Accept-Language", Locale.getDefault().toString().replace('_', '-'))
            .header("Origin", "https://www.youtube.com")
            .header("Referer", "https://www.youtube.com/")
            .post(body.toString().toRequestBody(json))

        prefs.visitorData?.let { builder.header("X-Goog-Visitor-Id", it) }
        if (cookie != null) {
            // Cookie sign-in: authenticate the same way a logged-in youtube.com tab does.
            builder.header("Cookie", cookie)
            builder.header("X-Origin", "https://www.youtube.com")
            builder.header("X-Goog-AuthUser", "0")
            CookieAuth.header(cookie)?.let { builder.header("Authorization", it) }
        } else {
            token?.let { builder.header("Authorization", "Bearer $it") }
        }

        http.newCall(builder.build()).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            val parsed = runCatching { JSONObject(text) }.getOrNull()
                ?: throw IllegalStateException("YouTube returned an unreadable response (HTTP ${resp.code}).")

            parsed.optJSONObject("responseContext")
                ?.optString("visitorData")
                ?.takeIf { it.isNotEmpty() }
                ?.let { prefs.visitorData = it }

            val error = parsed.optJSONObject("error")
            if (error != null) {
                throw IllegalStateException(
                    error.optString("message").ifEmpty { "YouTube request failed (HTTP ${resp.code})." }
                )
            }
            parsed
        }
    }

    suspend fun browse(
        browseId: String,
        params: String? = null,
        continuation: String? = null,
        spec: ClientSpec = Clients.WEB,
    ): JSONObject {
        val body = JSONObject()
        body.put("context", JSONObject().put("client", clientContext(spec)))
        if (continuation != null) {
            body.put("continuation", continuation)
        } else {
            body.put("browseId", browseId)
            if (params != null) body.put("params", params)
        }
        return post("browse", body, spec)
    }

    /**
     * Browses with the signed-in account. A cookie session is a web-origin session, so the web
     * client is tried first when one is set; OAuth bearer tokens (from the Advanced/own-client
     * flow) are honoured by the TV clients first instead. A failed attempt must never abort the
     * whole page.
     */
    suspend fun browseSignedIn(browseId: String, continuation: String? = null): JSONObject {
        var last: Throwable? = null
        val order = if (CookieAuth.hasSession(prefs.youtubeCookie)) {
            listOf(Clients.WEB, Clients.TV)
        } else {
            listOf(Clients.TV, Clients.WEB)
        }
        for (spec in order) {
            try {
                return browse(browseId, continuation = continuation, spec = spec)
            } catch (t: Throwable) {
                last = t
            }
        }
        throw last ?: IllegalStateException("YouTube request failed.")
    }

    suspend fun search(
        query: String,
        continuation: String? = null,
        spec: ClientSpec = Clients.WEB,
    ): JSONObject {
        val body = JSONObject()
        body.put("context", JSONObject().put("client", clientContext(spec)))
        if (continuation != null) body.put("continuation", continuation) else body.put("query", query)
        return post("search", body, spec)
    }

    suspend fun next(videoId: String, spec: ClientSpec = Clients.WEB): JSONObject {
        val body = JSONObject()
        body.put("context", JSONObject().put("client", clientContext(spec)))
        body.put("videoId", videoId)
        return post("next", body, spec)
    }

    suspend fun player(videoId: String, spec: ClientSpec): JSONObject {
        val body = JSONObject()
        body.put("context", JSONObject().put("client", clientContext(spec)))
        body.put("videoId", videoId)
        body.put("contentCheckOk", true)
        body.put("racyCheckOk", true)
        body.put(
            "playbackContext",
            JSONObject().put(
                "contentPlaybackContext",
                JSONObject().put("html5Preference", "HTML5_PREF_WANTS"),
            ),
        )
        return post("player", body, spec)
    }
}
