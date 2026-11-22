package com.novatube.tv.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Local storage for the visitor id and the OAuth credentials.
 *
 * Note: this uses plain SharedPreferences. On a TV box the app sandbox is the trust boundary;
 * if you ship this to other people, swap in EncryptedSharedPreferences and keep the same keys.
 */
class Prefs(context: Context) {

    private val sp: SharedPreferences =
        context.getSharedPreferences("novatube", Context.MODE_PRIVATE)

    var visitorData: String?
        get() = sp.getString("visitor_data", null)
        set(value) = sp.edit().putString("visitor_data", value).apply()

    var accessToken: String?
        get() = sp.getString("access_token", null)
        set(value) = sp.edit().putString("access_token", value).apply()

    var refreshToken: String?
        get() = sp.getString("refresh_token", null)
        set(value) = sp.edit().putString("refresh_token", value).apply()

    var tokenExpiry: Long
        get() = sp.getLong("token_expiry", 0L)
        set(value) = sp.edit().putLong("token_expiry", value).apply()

    var accountLabel: String?
        get() = sp.getString("account_label", null)
        set(value) = sp.edit().putString("account_label", value).apply()

    /** Google OAuth "TVs and Limited Input devices" client credentials, entered by the user. */
    var clientId: String?
        get() = sp.getString("client_id", null)
        set(value) = sp.edit().putString("client_id", value?.trim()).apply()

    var clientSecret: String?
        get() = sp.getString("client_secret", null)
        set(value) = sp.edit().putString("client_secret", value?.trim()).apply()

    /**
     * The raw `Cookie:` header captured after signing in to youtube.com in the in-app browser.
     * This is the primary sign-in method: it needs no Google Cloud OAuth client at all, since it
     * authenticates requests the same way youtube.com itself does for a logged-in tab (see
     * [CookieAuth]).
     */
    var youtubeCookie: String?
        get() = sp.getString("yt_cookie", null)
        set(value) = sp.edit().putString("yt_cookie", value).apply()

    fun isSignedIn(): Boolean = !youtubeCookie.isNullOrEmpty() || !refreshToken.isNullOrEmpty()

    fun signOut() {
        sp.edit()
            .remove("access_token")
            .remove("refresh_token")
            .remove("token_expiry")
            .remove("account_label")
            .remove("yt_cookie")
            .apply()
    }
}
