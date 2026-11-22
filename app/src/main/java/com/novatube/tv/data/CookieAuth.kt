package com.novatube.tv.data

import java.security.MessageDigest

/**
 * Authenticates InnerTube requests with a signed-in youtube.com session cookie instead of OAuth.
 *
 * This is Google's own "SAPISIDHASH" scheme: the same header youtube.com's own web client sends
 * on every request once you're logged in, derived from the `SAPISID` (or `__Secure-3PAPISID`)
 * cookie, a timestamp, and the request origin. Using it means the app never needs a Google Cloud
 * OAuth client of its own (which, for the YouTube Data scope, Google only allows for an app it has
 * personally verified, or a per-developer client with the user manually added as a tester) — it
 * just needs the person to have signed in to youtube.com once, in the in-app browser.
 */
object CookieAuth {

    private val sidPattern = Regex("(?:^|;\\s*)(__Secure-3PAPISID|SAPISID)=([^;]+)")

    fun hasSession(cookie: String?): Boolean = cookie != null && sidPattern.containsMatchIn(cookie)

    fun header(cookie: String, origin: String = "https://www.youtube.com"): String? {
        val sid = sidPattern.find(cookie)?.groupValues?.get(2) ?: return null
        val timestamp = System.currentTimeMillis() / 1000
        val raw = "$timestamp $sid $origin"
        val digest = MessageDigest.getInstance("SHA-1").digest(raw.toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it) }
        return "SAPISIDHASH ${timestamp}_$hex"
    }
}
