package com.novatube.tv

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.novatube.tv.data.Auth
import com.novatube.tv.data.InnerTube
import com.novatube.tv.data.JsPlayer
import com.novatube.tv.data.Prefs
import com.novatube.tv.data.StreamChoice
import com.novatube.tv.data.Streams
import com.novatube.tv.ui.AccentRed
import com.novatube.tv.ui.Glyph
import com.novatube.tv.ui.GlyphKind
import com.novatube.tv.ui.Muted
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
private fun buildSource(context: Context, choice: StreamChoice): MediaSource? {
    val dataSource = DefaultDataSource.Factory(context)
    return when {
        choice.hlsUrl != null ->
            HlsMediaSource.Factory(dataSource).createMediaSource(MediaItem.fromUri(choice.hlsUrl))

        choice.muxedUrl != null ->
            ProgressiveMediaSource.Factory(dataSource).createMediaSource(MediaItem.fromUri(choice.muxedUrl))

        choice.videoUrl != null && choice.audioUrl != null ->
            MergingMediaSource(
                ProgressiveMediaSource.Factory(dataSource).createMediaSource(MediaItem.fromUri(choice.videoUrl)),
                ProgressiveMediaSource.Factory(dataSource).createMediaSource(MediaItem.fromUri(choice.audioUrl)),
            )

        else -> null
    }
}

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(request: PlayRequest, onExit: () -> Unit, onNext: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val auth = remember { Auth(prefs) }
    val innerTube = remember { InnerTube(prefs, auth) }
    val jsPlayer = remember { JsPlayer(context.applicationContext) }

    val currentOnNext by rememberUpdatedState(onNext)
    val currentOnExit by rememberUpdatedState(onExit)

    val player = remember { ExoPlayer.Builder(context).build().apply { playWhenReady = true } }
    var choice by remember { mutableStateOf<StreamChoice?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var controlsVisible by remember { mutableStateOf(true) }
    var playing by remember { mutableStateOf(true) }
    var position by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    val focus = remember { FocusRequester() }

    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) currentOnNext()
            }

            override fun onPlayerError(playerError: PlaybackException) {
                error = "Playback failed (${playerError.errorCodeName}). The stream may be region locked."
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(request.current.id) {
        choice = null
        error = null
        val resolved = Streams.resolve(innerTube, request.current.id, jsPlayer)
        choice = resolved
        if (!resolved.playable) {
            error = resolved.error ?: "This video cannot be played."
            return@LaunchedEffect
        }
        val source = buildSource(context, resolved)
        if (source == null) {
            error = "No compatible stream format was returned."
            return@LaunchedEffect
        }
        player.setMediaSource(source)
        player.prepare()
        player.playWhenReady = true
    }

    LaunchedEffect(player) {
        while (true) {
            playing = player.isPlaying
            position = player.currentPosition
            duration = player.duration.coerceAtLeast(0L)
            delay(400)
        }
    }

    LaunchedEffect(controlsVisible, playing) {
        if (controlsVisible && playing) {
            delay(4000)
            controlsVisible = false
        }
    }

    LaunchedEffect(Unit) {
        delay(120)
        runCatching { focus.requestFocus() }
    }

    BackHandler { currentOnExit() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .onKeyEvent { event ->
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        if (event.type == KeyEventType.KeyUp) {
                            if (player.isPlaying) player.pause() else player.play()
                            controlsVisible = true
                        }
                        true
                    }

                    Key.DirectionLeft, Key.MediaRewind -> {
                        if (event.type == KeyEventType.KeyDown) {
                            player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0L))
                            controlsVisible = true
                        }
                        true
                    }

                    Key.DirectionRight, Key.MediaFastForward -> {
                        if (event.type == KeyEventType.KeyDown) {
                            player.seekTo(player.currentPosition + 10_000)
                            controlsVisible = true
                        }
                        true
                    }

                    Key.DirectionUp, Key.DirectionDown -> {
                        controlsVisible = true
                        event.type == KeyEventType.KeyDown
                    }

                    Key.MediaPlayPause -> {
                        if (player.isPlaying) player.pause() else player.play()
                        controlsVisible = true
                        true
                    }

                    else -> false
                }
            }
            .focusable(),
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    this.player = player
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (choice == null && error == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentRed, strokeWidth = 3.dp, modifier = Modifier.size(42.dp))
            }
        }

        val failure = error
        if (failure != null) {
            Box(
                Modifier.fillMaxSize().background(Color(0xCC000000)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(48.dp)) {
                    Text("Can't play this video", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Text(failure, color = Muted, fontSize = 14.sp)
                    Spacer(Modifier.height(18.dp))
                    Text("Press BACK to go back", color = Muted, fontSize = 13.sp)
                }
            }
        }

        AnimatedVisibility(visible = controlsVisible, enter = fadeIn(), exit = fadeOut()) {
            Column(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                        .padding(horizontal = 34.dp, vertical = 24.dp),
                ) {
                    Column {
                        Text(
                            text = choice?.title?.ifEmpty { request.current.title } ?: request.current.title,
                            color = Color.White,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = choice?.author?.ifEmpty { request.current.channel }.orEmpty(),
                            color = Muted,
                            fontSize = 13.sp,
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (!playing) {
                        Box(
                            Modifier
                                .size(76.dp)
                                .background(Color(0x66000000), RoundedCornerShape(38.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Glyph(GlyphKind.Play, Color.White, 34.dp)
                        }
                    }
                }

                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
                        .padding(horizontal = 34.dp, vertical = 22.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(formatTime(position), color = Color.White, fontSize = 13.sp)
                        Spacer(Modifier.width(12.dp))
                        Box(
                            Modifier
                                .weight(1f)
                                .height(5.dp)
                                .background(Color(0x66FFFFFF), RoundedCornerShape(3.dp)),
                        ) {
                            val fraction = if (duration > 0) {
                                (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                            } else {
                                0f
                            }
                            Box(
                                Modifier
                                    .fillMaxWidth(fraction)
                                    .height(5.dp)
                                    .background(AccentRed, RoundedCornerShape(3.dp)),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(formatTime(duration), color = Color.White, fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        Text(
                            if (playing) "OK  Pause" else "OK  Play",
                            color = Muted,
                            fontSize = 12.sp,
                        )
                        Text("←/→  Seek 10s", color = Muted, fontSize = 12.sp)
                        Text("BACK  Exit", color = Muted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
