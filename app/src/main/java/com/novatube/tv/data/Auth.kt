package com.novatube.tv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class DeviceCode(
    val deviceCode: String,
    val userCode: String,
    val verificationUrl: String,
    val expiresIn: Int,
    val interval: Int,
)

sealed class PollResult {
    data class Success(val accessToken: String, val refreshToken: String?, val expiresIn: Long) : PollResult()
    data object Pending : PollResult()
    data class Failure(val message: String) : PollResult()
}

/**
 * Google OAuth 2.0 for TVs and limited-input devices ("TV code" sign-in).
 *
 * Google blocks OAuth inside embedded WebViews ("disallowed_useragent"), so a TV app must use the
 * device flow: the TV shows a short code, the person opens a sign-in page on their phone (or any
 * other browser) and types the code in, and the TV silently picks up an access token once they
 * approve it. This is exactly how the real YouTube TV app, game consoles, etc. pair an account —
 * no password is ever typed on the TV's remote.
 *
 * This ships with a built-in "TVs and Limited Input devices" OAuth client (the same kind of public
 * client id other open-source TV YouTube clients use) so sign-in works out of the box with nothing
 * to configure. Advanced users can still override it with their own client in Settings if they'd
 * rather use their own Google Cloud project.
 */
class Auth(private val prefs: Prefs) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val scope = "https://www.googleapis.com/auth/youtube.readonly"

    private fun requireClient(): Pair<String, String> {
        val id = prefs.clientId?.takeIf { it.isNotBlank() } ?: DEFAULT_CLIENT_ID
        val secret = prefs.clientSecret?.takeIf { it.isNotBlank() } ?: DEFAULT_CLIENT_SECRET
        return id to secret
    }

    companion object {
        // Public "TVs and Limited Input devices" OAuth client, the same kind of shared client id
        // used by other open-source YouTube TV apps (e.g. NewPipe/SmartTube's device-code sign-in).
        // It only grants youtube.readonly, is rate-limited by Google per app, and is what makes
        // "sign in with a code on your phone" work without the user creating their own project.
        private const val DEFAULT_CLIENT_ID =
            "861556708454-d6dlm3lh05idd8npek18k6be8ba3oc68.apps.googleusercontent.com"
        private const val DEFAULT_CLIENT_SECRET = "SboVhoG9s0rNafixCSGGKXAT"
    }

    suspend fun startDeviceFlow(): DeviceCode = withContext(Dispatchers.IO) {
        val (id, _) = requireClient()
        val body = FormBody.Builder()
            .add("client_id", id)
            .add("scope", scope)
            .build()
        val req = Request.Builder()
            .url("https://oauth2.googleapis.com/device/code")
            .post(body)
            .build()
        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IllegalStateException(friendlyError(text))
            val json = JSONObject(text)
            DeviceCode(
                deviceCode = json.getString("device_code"),
                userCode = json.getString("user_code"),
                verificationUrl = json.optString("verification_url", "https://www.google.com/device"),
                expiresIn = json.optInt("expires_in", 1800),
                interval = json.optInt("interval", 5),
            )
        }
    }

    suspend fun pollToken(deviceCode: String): PollResult = withContext(Dispatchers.IO) {
        val (id, secret) = requireClient()
        val body = FormBody.Builder()
            .add("client_id", id)
            .add("client_secret", secret)
            .add("device_code", deviceCode)
            .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
            .build()
        val req = Request.Builder()
            .url("https://oauth2.googleapis.com/token")
            .post(body)
            .build()
        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            val json = runCatching { JSONObject(text) }.getOrNull()
            if (resp.isSuccessful && json != null) {
                val access = json.optString("access_token")
                prefs.accessToken = access
                prefs.refreshToken = json.optString("refresh_token").ifEmpty { prefs.refreshToken }
                val expiresIn = json.optLong("expires_in", 3600)
                prefs.tokenExpiry = System.currentTimeMillis() + expiresIn * 1000
                PollResult.Success(access, prefs.refreshToken, expiresIn)
            } else {
                when (json?.optString("error")) {
                    "authorization_pending" -> PollResult.Pending
                    "slow_down" -> PollResult.Pending
                    "access_denied" -> PollResult.Failure("Sign-in was denied on the Google page.")
                    "expired_token" -> PollResult.Failure("The code expired. Start sign-in again.")
                    else -> PollResult.Failure(friendlyError(text))
                }
            }
        }
    }

    /** Returns a usable access token, refreshing it when it is about to expire. */
    suspend fun accessToken(): String? = withContext(Dispatchers.IO) {
        val current = prefs.accessToken
        val expiry = prefs.tokenExpiry
        if (!current.isNullOrEmpty() && System.currentTimeMillis() < expiry - 60_000) return@withContext current

        val refresh = prefs.refreshToken ?: return@withContext null
        val (id, secret) = requireClient()

        val body = FormBody.Builder()
            .add("client_id", id)
            .add("client_secret", secret)
            .add("refresh_token", refresh)
            .add("grant_type", "refresh_token")
            .build()
        val req = Request.Builder().url("https://oauth2.googleapis.com/token").post(body).build()
        val refreshed = runCatching {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                JSONObject(resp.body?.string().orEmpty()).optString("access_token").ifEmpty { null }
            }
        }.getOrNull()

        if (refreshed == null) {
            prefs.signOut()
            null
        } else {
            prefs.accessToken = refreshed
            prefs.tokenExpiry = System.currentTimeMillis() + 3600_000
            refreshed
        }
    }

    private fun friendlyError(raw: String): String {
        val json = runCatching { JSONObject(raw) }.getOrNull()
        val code = json?.optString("error").orEmpty()
        return when {
            code == "invalid_client" -> "Google rejected the client id/secret. Check them in Settings."
            code == "unauthorized_client" -> "This OAuth client is not enabled for TV device sign-in."
            raw.isBlank() -> "Google returned an empty response."
            else -> code.ifEmpty { raw.take(200) }
        }
    }
}
