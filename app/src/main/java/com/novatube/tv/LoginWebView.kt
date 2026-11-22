package com.novatube.tv

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.novatube.tv.data.CookieAuth
import kotlinx.coroutines.delay

/**
 * Lets the person sign in by logging in to youtube.com right here, in an embedded browser, and
 * captures the resulting session cookie — no Google Cloud OAuth client needed at all (see
 * [com.novatube.tv.data.CookieAuth] for why that matters: Google only lets a *verified* app or a
 * client the person made themselves touch the YouTube Data scope, so a shared "sign in with a
 * code" client baked into an app like this one gets rejected with `restricted_client` for most
 * people — this sidesteps that entirely).
 *
 * It still needs the TV's own keyboard/remote to type an email and password into the page (there
 * is no way to hand that off to a phone without a server in the middle), but needs zero setup.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GoogleLoginScreen(onDone: (cookie: String?) -> Unit) {
    var message by remember { mutableStateOf("Loading sign-in…") }
    var webView by remember { mutableStateOf<WebView?>(null) }

    BackHandler { onDone(null) }

    // Poll independently of page-load events: Google's sign-in flow is a chain of redirects and
    // XHRs that don't always fire a fresh onPageFinished once the session cookie actually lands.
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            val cookie = CookieManager.getInstance().getCookie("https://www.youtube.com")
            if (CookieAuth.hasSession(cookie)) {
                onDone(cookie)
                return@LaunchedEffect
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    // Google refuses to show the sign-in page to a WebView identifying itself as
                    // one ("...disallowed_useragent"); dropping the "; wv" token is the standard,
                    // widely documented workaround every embedded-browser YouTube sign-in uses.
                    settings.userAgentString = settings.userAgentString.replace("; wv", "")

                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String?) {
                            message = "Sign in with your Google account."
                        }
                    }
                    loadUrl(
                        "https://accounts.google.com/ServiceLogin?" +
                            "continue=https%3A%2F%2Fwww.youtube.com%2F&service=youtube&uilel=3",
                    )
                    webView = this
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (webView == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        }

        Text(
            message,
            color = Color.White,
            fontSize = 13.sp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .background(Color(0xCC000000))
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Text(
            "BACK to cancel",
            color = Color(0xFFAAAAAA),
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .background(Color(0xCC000000))
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}
