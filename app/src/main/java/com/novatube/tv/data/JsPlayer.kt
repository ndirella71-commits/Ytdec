package com.novatube.tv.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Deciphers the stream URLs YouTube protects.
 *
 * YouTube attaches two protections to many playback URLs:
 *  - `signatureCipher` formats: the real URL is missing its `sig`/`s` value, which has to be run
 *    through a scrambling function defined inside YouTube's own player JavaScript.
 *  - an `n` query parameter on (almost) every URL: an unscrambled value throttles the download: it
 *    has to be run through a *different* function in that same script to get full speed, and on
 *    some formats an un-fixed `n` causes YouTube to refuse the request outright.
 *
 * Both functions are obfuscated and renamed on every YouTube release, so instead of reverse
 * engineering the obfuscation, this downloads the current player script as-is and runs it, as-is,
 * inside a throwaway, invisible WebView — the same JavaScript engine the real youtube.com web
 * player already runs this code in. We only need to find the two function *names* (which change
 * every release and are found with the same style of pattern YouTube's own code always leaves
 * behind), not to understand what they do.
 *
 * Because it depends on matching patterns in YouTube's current, unversioned player script, this is
 * inherently best-effort: if YouTube changes the script's shape enough, the regexes below will need
 * updating. Every step fails soft (returns null) rather than crashing playback.
 */
class JsPlayer(private val context: Context) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val browserUa = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    private var playerJs: String? = null
    private var sigCall: String? = null
    private var nCall: String? = null
    private val lock = Any()

    private suspend fun fetchText(url: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", browserUa)
            .header("Referer", "https://www.youtube.com/")
            .build()
        http.newCall(req).execute().use { it.body?.string().orEmpty() }
    }

    /** Downloads and caches today's player script + the two function names, once per process. */
    private suspend fun ensureReady(videoId: String): Boolean {
        synchronized(lock) { if (playerJs != null) return true }
        return try {
            val watchHtml = fetchText("https://www.youtube.com/watch?v=$videoId&bpctr=9999999999&has_verified=1")
            val jsPath = Regex("\"jsUrl\"\\s*:\\s*\"([^\"]+)\"").find(watchHtml)?.groupValues?.get(1)
                ?: Regex("\"PLAYER_JS_URL\"\\s*:\\s*\"([^\"]+)\"").find(watchHtml)?.groupValues?.get(1)
                ?: return false
            val jsUrl = if (jsPath.startsWith("http")) jsPath else "https://www.youtube.com$jsPath"
            val js = fetchText(jsUrl)
            if (js.isBlank()) return false
            synchronized(lock) {
                playerJs = js
                sigCall = findSigCall(js)
                nCall = findNCall(js)
                true
            }
        } catch (t: Throwable) {
            false
        }
    }

    private fun findSigCall(js: String): String? {
        val name = listOf(
            Regex("\\b([a-zA-Z0-9$]{2,})=function\\(a\\)\\{a=a\\.split\\(\"\"\\)"),
            Regex("\\b([a-zA-Z0-9$]{2,})\\s*=\\s*function\\(\\s*a\\s*\\)\\s*\\{\\s*a\\s*=\\s*a\\.split\\(\\s*\"\"\\s*\\)"),
            Regex("(?:\\b|^)([a-zA-Z0-9$]{2,})=function\\(a\\)\\{a=a\\.split\\(\"\"\\);[a-zA-Z0-9$]+\\.[a-zA-Z0-9$]+\\(a,\\d+\\)"),
        ).firstNotNullOfOrNull { it.find(js)?.groupValues?.get(1) }
        return name?.let { "$it(%s)" }
    }

    private fun findNCall(js: String): String? {
        // Matches the `...(b=XYZ[0](c))...` / `...(b=XYZ(c))...` shape that wraps the n-parameter
        // transform in recent player releases.
        val name = listOf(
            Regex("\\.get\\(\"n\"\\)\\)&&\\(b=([a-zA-Z0-9$]+)(?:\\[(\\d+)\\])?\\([a-zA-Z0-9$]+\\)"),
            Regex("[;,]\\s*([a-zA-Z0-9$]+)\\s*=\\s*function\\([a-zA-Z0-9$]+\\)\\s*\\{\\s*var\\s+[a-zA-Z0-9$]+\\s*=\\s*[a-zA-Z0-9$]+\\.split\\(\"\"\\)[^}]*enhanced_except"),
        ).firstNotNullOfOrNull { regex ->
            val m = regex.find(js) ?: return@firstNotNullOfOrNull null
            val fn = m.groupValues.getOrNull(1) ?: return@firstNotNullOfOrNull null
            val idx = m.groupValues.getOrNull(2)
            if (!idx.isNullOrEmpty()) "$fn[$idx](%s)" else "$fn(%s)"
        }
        return name
    }

    /** Runs a tiny script wrapping the cached player JS and returns its string result, or null. */
    private suspend fun runOnPlayerJs(expression: String): String? {
        val js = playerJs ?: return null
        val script = buildString {
            append("(function(){try{")
            append(js)
            append(";return (")
            append(expression)
            append(");}catch(e){return '';}})();")
        }
        val result = evalInWebView(script) ?: return null
        val unquoted = result.removePrefix("\"").removeSuffix("\"")
        return unquoted.takeIf { it.isNotEmpty() && it != "null" }
            ?.replace("\\u003d", "=")
            ?.replace("\\\"", "\"")
    }

    private suspend fun evalInWebView(script: String): String? = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val webView = WebView(context)
            webView.settings.javaScriptEnabled = true
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String?) {
                    view.evaluateJavascript(script) { value ->
                        if (cont.isActive) cont.resume(value)
                        Handler(Looper.getMainLooper()).post { webView.destroy() }
                    }
                }
            }
            webView.loadDataWithBaseURL(
                "https://www.youtube.com/",
                "<!doctype html><html><head></head><body></body></html>",
                "text/html",
                "utf-8",
                null,
            )
            cont.invokeOnCancellation { Handler(Looper.getMainLooper()).post { webView.destroy() } }
        }
    }

    /** Deciphers one `signatureCipher`/`url`+`n` pair into a final, directly fetchable URL. */
    suspend fun resolveUrl(videoId: String, rawUrl: String?, signatureCipher: String?): String? {
        if (!rawUrl.isNullOrEmpty()) return fixThrottling(videoId, rawUrl)
        if (signatureCipher.isNullOrEmpty()) return null
        if (!ensureReady(videoId)) return null

        val parts = signatureCipher.split("&").associate { part ->
            val i = part.indexOf('=')
            if (i < 0) part to "" else {
                part.take(i) to URLDecoder.decode(part.substring(i + 1), "UTF-8")
            }
        }
        val baseUrl = parts["url"] ?: return null
        val sigParamName = parts["sp"]?.ifEmpty { "signature" } ?: "signature"
        val cipheredSig = parts["s"] ?: return null

        val call = sigCall ?: return null
        val deciphered = runOnPlayerJs(call.format(jsString(cipheredSig))) ?: return null

        val withSig = baseUrl + "&" + sigParamName + "=" + URLEncoder.encode(deciphered, "UTF-8")
        return fixThrottling(videoId, withSig)
    }

    /** Fixes up the throttling `n` parameter on an already-plain URL, if we can. */
    private suspend fun fixThrottling(videoId: String, url: String): String {
        return try {
            val uri = android.net.Uri.parse(url)
            val n = uri.getQueryParameter("n") ?: return url
            if (playerJs == null && !ensureReady(videoId)) return url
            val call = nCall ?: return url
            val fixed = runOnPlayerJs(call.format(jsString(n))) ?: return url
            uri.buildUpon().clearQuery().apply {
                for (key in uri.queryParameterNames) {
                    val value = if (key == "n") fixed else uri.getQueryParameter(key)
                    appendQueryParameter(key, value)
                }
            }.build().toString()
        } catch (t: Throwable) {
            url
        }
    }

    private fun jsString(value: String): String {
        val escaped = value.replace("\\", "\\\\").replace("\"", "\\\"")
        return "\"$escaped\""
    }
}
