package com.novatube.tv

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novatube.tv.data.Auth
import com.novatube.tv.data.DeviceCode
import com.novatube.tv.data.InnerTube
import com.novatube.tv.data.PollResult
import com.novatube.tv.data.Prefs
import com.novatube.tv.ui.AccentRed
import com.novatube.tv.ui.CardBg
import com.novatube.tv.ui.Muted
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    prefs: Prefs,
    auth: Auth,
    innerTube: InnerTube,
    accountLabel: String?,
    isSignedIn: Boolean,
    onAccountChanged: (String?) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var device by remember { mutableStateOf<DeviceCode?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }
    val editFocus = remember { FocusRequester() }

    fun startSignIn() {
        if (busy) return
        busy = true
        status = "Contacting Google…"
        scope.launch {
            val code = runCatching { auth.startDeviceFlow() }.getOrElse {
                status = it.message ?: "Could not start sign-in."
                busy = false
                return@launch
            }
            device = code
            status = "Waiting for approval…"
            while (true) {
                delay(code.interval.coerceAtLeast(5) * 1000L)
                val result = runCatching { auth.pollToken(code.deviceCode) }.getOrNull()
                when (result) {
                    is PollResult.Success -> {
                        prefs.accountLabel = "Google account"
                        onAccountChanged(prefs.accountLabel)
                        device = null
                        status = "Signed in. Your subscriptions and library are now available."
                        busy = false
                        break
                    }
                    PollResult.Pending -> Unit
                    is PollResult.Failure -> {
                        status = result.message
                        device = null
                        busy = false
                        break
                    }
                    null -> {
                        status = "Network error while waiting for approval. Try again."
                        device = null
                        busy = false
                        break
                    }
                }
            }
        }
    }

    if (editing != null) {
        val isSecret = editing == "secret"
        LaunchedEffect(Unit) {
            draft = (if (isSecret) prefs.clientSecret else prefs.clientId).orEmpty()
            delay(150)
            runCatching { editFocus.requestFocus() }
        }
        Column(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF151515))
                .padding(horizontal = 40.dp, vertical = 30.dp),
        ) {
            Text(
                if (isSecret) "OAuth client secret" else "OAuth client id",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .fillMaxWidth(0.62f)
                    .height(52.dp)
                    .background(Color(0xFF262626), RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    draft.ifEmpty { "Enter value…" },
                    color = if (draft.isEmpty()) Muted else Color.White,
                    fontSize = 16.sp,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(20.dp))
            TvKeyboard(
                onChar = { draft += it },
                onBackspace = { draft = draft.dropLast(1) },
                onSpace = { },
                onSearch = {
                    if (isSecret) prefs.clientSecret = draft else prefs.clientId = draft
                    editing = null
                    status = "Saved. You can sign in now."
                },
                firstFocus = editFocus,
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ActionButton("Save", {
                    if (isSecret) prefs.clientSecret = draft else prefs.clientId = draft
                    editing = null
                    status = "Saved. You can sign in now."
                })
                ActionButton("Cancel", { editing = null }, primary = false)
            }
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 40.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Text("Settings", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        }

        item {
            SectionCard("Account") {
                Text(
                    if (isSignedIn) "Signed in" else "Not signed in",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (isSignedIn) {
                        "Your subscriptions, library and history come from your Google account."
                    } else {
                        "Use your phone to sign in — no password typed on this remote. " +
                            "Browsing, search and playback work without an account too."
                    },
                    color = Muted,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (isSignedIn) {
                        ActionButton("Sign out", {
                            prefs.signOut()
                            onAccountChanged(null)
                            status = "Signed out."
                        }, primary = false)
                    } else {
                        ActionButton(
                            if (busy) "Starting…" else "Sign in with your phone",
                            { startSignIn() },
                        )
                    }
                }
                if (status != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(status!!, color = AccentRed, fontSize = 13.sp)
                }
            }
        }

        val code = device
        if (code != null) {
            item {
                SectionCard("Use your phone to sign in") {
                    Text(
                        "On your phone, in any browser, go to:",
                        color = Muted,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        code.verificationUrl.removePrefix("https://").removePrefix("http://"),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("Then enter this code:", color = Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier
                            .background(Color(0xFF262626), RoundedCornerShape(12.dp))
                            .padding(horizontal = 24.dp, vertical = 14.dp),
                    ) {
                        Text(
                            code.userCode,
                            color = Color.White,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 8.sp,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(status ?: "Waiting for you to approve on your phone…", color = Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ActionButton("Open this page here instead", onClick = {
                            openInAppBrowser(context, code.verificationUrl)
                        }, primary = false)
                        ActionButton("Cancel", {
                            device = null
                            busy = false
                            status = "Sign-in cancelled."
                        }, primary = false)
                    }
                }
            }
        }

        item {
            var showAdvanced by remember { mutableStateOf(false) }
            SectionCard("Advanced") {
                Text(
                    "Sign-in works out of the box. If you'd rather use your own Google Cloud OAuth " +
                        "client (type \"TVs and Limited Input devices\") instead of the app's built-in one, " +
                        "you can set it here.",
                    color = Muted,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(10.dp))
                ActionButton(if (showAdvanced) "Hide" else "Use my own OAuth client", {
                    showAdvanced = !showAdvanced
                }, primary = false)
                if (showAdvanced) {
                    Spacer(Modifier.height(14.dp))
                    CredentialRow("Client id", prefs.clientId) {
                        editing = "id"
                    }
                    Spacer(Modifier.height(10.dp))
                    CredentialRow("Client secret", if (prefs.clientSecret.isNullOrEmpty()) null else "••••••••") {
                        editing = "secret"
                    }
                    if (!prefs.clientId.isNullOrBlank() || !prefs.clientSecret.isNullOrBlank()) {
                        Spacer(Modifier.height(10.dp))
                        ActionButton("Clear (use built-in sign-in)", {
                            prefs.clientId = null
                            prefs.clientSecret = null
                            status = "Cleared. Sign-in will use the app's built-in client again."
                        }, primary = false)
                    }
                }
            }
        }

        item {
            SectionCard("About") {
                Text("NovaTube for Android TV", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "A 10-foot YouTube client built directly on Google's InnerTube API — the same private API " +
                        "the repo you referenced wraps. No API key, no ad slots requested, no ad player: video is " +
                        "played straight from the stream URLs YouTube returns.",
                    color = Muted,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth(0.78f)
            .background(CardBg, RoundedCornerShape(14.dp))
            .padding(20.dp),
    ) {
        Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun CredentialRow(label: String, value: String?, onEdit: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(420.dp)) {
            Text(label, color = Color.White, fontSize = 14.sp)
            Text(value ?: "Not set", color = Muted, fontSize = 12.sp, maxLines = 1)
        }
        ActionButton("Edit", onEdit, primary = false)
    }
}

/** Opens the sign-in page in an in-app browser (Chrome Custom Tab), falling back to any browser. */
private fun openInAppBrowser(context: Context, url: String) {
    val uri = Uri.parse(url)
    val launched = runCatching {
        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, uri)
    }
    if (launched.isFailure) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
    }
}
