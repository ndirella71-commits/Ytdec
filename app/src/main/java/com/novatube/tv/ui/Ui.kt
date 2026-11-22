package com.novatube.tv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.focusable
import androidx.compose.foundation.Canvas
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import com.novatube.tv.data.Shelf
import com.novatube.tv.data.VideoItem

val Bg = Color(0xFF0F0F0F)
val CardBg = Color(0xFF1F1F1F)
val RailBg = Color(0xFF0A0A0A)
val Muted = Color(0xFFAAAAAA)
val AccentRed = Color(0xFFFF2A2A)

@Composable
fun NovaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Bg,
            surface = CardBg,
            primary = AccentRed,
            onBackground = Color.White,
            onSurface = Color.White,
        ),
    ) {
        Box(Modifier.fillMaxSize().background(Bg)) { content() }
    }
}

enum class GlyphKind { Home, Search, Subs, Library, Settings, Play, Pause, Back, Clock, Check }

@Composable
fun Glyph(kind: GlyphKind, tint: Color, size: Dp = 22.dp) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        val stroke = Stroke(width = s * 0.085f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (kind) {
            GlyphKind.Home -> {
                val roof = Path().apply {
                    moveTo(s * 0.12f, s * 0.48f)
                    lineTo(s * 0.5f, s * 0.16f)
                    lineTo(s * 0.88f, s * 0.48f)
                }
                drawPath(roof, tint, style = stroke)
                val body = Path().apply {
                    moveTo(s * 0.22f, s * 0.42f)
                    lineTo(s * 0.22f, s * 0.86f)
                    lineTo(s * 0.78f, s * 0.86f)
                    lineTo(s * 0.78f, s * 0.42f)
                }
                drawPath(body, tint, style = stroke)
            }

            GlyphKind.Search -> {
                drawCircle(tint, radius = s * 0.29f, center = Offset(s * 0.44f, s * 0.44f), style = stroke)
                drawLine(
                    tint,
                    Offset(s * 0.66f, s * 0.66f),
                    Offset(s * 0.88f, s * 0.88f),
                    strokeWidth = s * 0.09f,
                    cap = StrokeCap.Round,
                )
            }

            GlyphKind.Subs -> {
                drawLine(tint, Offset(s * 0.12f, s * 0.26f), Offset(s * 0.88f, s * 0.26f), s * 0.085f, StrokeCap.Round)
                drawLine(tint, Offset(s * 0.12f, s * 0.5f), Offset(s * 0.6f, s * 0.5f), s * 0.085f, StrokeCap.Round)
                drawLine(tint, Offset(s * 0.12f, s * 0.74f), Offset(s * 0.6f, s * 0.74f), s * 0.085f, StrokeCap.Round)
                val tri = Path().apply {
                    moveTo(s * 0.7f, s * 0.62f)
                    lineTo(s * 0.92f, s * 0.74f)
                    lineTo(s * 0.7f, s * 0.86f)
                    close()
                }
                drawPath(tri, tint)
            }

            GlyphKind.Library -> {
                drawLine(tint, Offset(s * 0.16f, s * 0.2f), Offset(s * 0.16f, s * 0.8f), s * 0.085f, StrokeCap.Round)
                drawLine(tint, Offset(s * 0.36f, s * 0.2f), Offset(s * 0.36f, s * 0.8f), s * 0.085f, StrokeCap.Round)
                val block = Path().apply {
                    moveTo(s * 0.55f, s * 0.24f)
                    lineTo(s * 0.86f, s * 0.33f)
                    lineTo(s * 0.79f, s * 0.8f)
                    lineTo(s * 0.48f, s * 0.71f)
                    close()
                }
                drawPath(block, tint, style = stroke)
            }

            GlyphKind.Settings -> {
                drawLine(tint, Offset(s * 0.1f, s * 0.28f), Offset(s * 0.9f, s * 0.28f), s * 0.08f, StrokeCap.Round)
                drawLine(tint, Offset(s * 0.1f, s * 0.72f), Offset(s * 0.9f, s * 0.72f), s * 0.08f, StrokeCap.Round)
                drawCircle(tint, radius = s * 0.1f, center = Offset(s * 0.36f, s * 0.28f))
                drawCircle(tint, radius = s * 0.1f, center = Offset(s * 0.66f, s * 0.72f))
            }

            GlyphKind.Play -> {
                val tri = Path().apply {
                    moveTo(s * 0.28f, s * 0.14f)
                    lineTo(s * 0.86f, s * 0.5f)
                    lineTo(s * 0.28f, s * 0.86f)
                    close()
                }
                drawPath(tri, tint)
            }

            GlyphKind.Pause -> {
                drawLine(tint, Offset(s * 0.34f, s * 0.16f), Offset(s * 0.34f, s * 0.84f), s * 0.14f, StrokeCap.Round)
                drawLine(tint, Offset(s * 0.66f, s * 0.16f), Offset(s * 0.66f, s * 0.84f), s * 0.14f, StrokeCap.Round)
            }

            GlyphKind.Back -> {
                val p = Path().apply {
                    moveTo(s * 0.56f, s * 0.2f)
                    lineTo(s * 0.28f, s * 0.5f)
                    lineTo(s * 0.56f, s * 0.8f)
                }
                drawPath(p, tint, style = stroke)
            }

            GlyphKind.Clock -> {
                drawCircle(tint, radius = s * 0.38f, center = Offset(s * 0.5f, s * 0.5f), style = stroke)
                drawLine(tint, Offset(s * 0.5f, s * 0.3f), Offset(s * 0.5f, s * 0.52f), s * 0.075f, StrokeCap.Round)
                drawLine(tint, Offset(s * 0.5f, s * 0.52f), Offset(s * 0.66f, s * 0.62f), s * 0.075f, StrokeCap.Round)
            }

            GlyphKind.Check -> {
                val p = Path().apply {
                    moveTo(s * 0.2f, s * 0.52f)
                    lineTo(s * 0.42f, s * 0.74f)
                    lineTo(s * 0.82f, s * 0.28f)
                }
                drawPath(p, tint, style = stroke)
            }
        }
    }
}

/**
 * A D-pad focusable surface. Focus is drawn as a white ring plus a small scale-up, matching the
 * focus treatment of the YouTube TV app.
 */
@Composable
fun FocusSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    focusedScale: Float = 1.07f,
    shape: RoundedCornerShape = RoundedCornerShape(10.dp),
    onFocused: (Boolean) -> Unit = {},
    content: @Composable (focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) focusedScale else 1f, tween(140), label = "focusScale")

    var mod = modifier
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .onKeyEvent { ev ->
            if (ev.type == KeyEventType.KeyUp &&
                (ev.key == Key.DirectionCenter || ev.key == Key.Enter || ev.key == Key.NumPadEnter)
            ) {
                onClick()
                true
            } else {
                false
            }
        }
        .onFocusChanged {
            focused = it.isFocused
            onFocused(it.isFocused)
        }
        .focusable()
    if (focusRequester != null) mod = mod.focusRequester(focusRequester)
    mod = mod
        .border(if (focused) 2.dp else 0.dp, if (focused) Color.White else Color.Transparent, shape)
        .clip(shape)

    Box(mod) { content(focused) }
}

@Composable
fun VideoCard(
    item: VideoItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 296.dp,
    focusRequester: FocusRequester? = null,
    showChannel: Boolean = true,
    onFocused: (Boolean) -> Unit = {},
) {
    Column(modifier.width(width)) {
        FocusSurface(
            onClick = onClick,
            focusRequester = focusRequester,
            modifier = Modifier.width(width).height(width * 9f / 16f),
            onFocused = onFocused,
        ) {
            Box(Modifier.fillMaxSize().background(CardBg)) {
                AsyncImage(
                    model = item.thumbnail,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color(0x55000000))),
                    ),
                )
                if (!item.durationText.isNullOrEmpty()) {
                    Text(
                        text = item.durationText,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .background(Color(0xCC000000), RoundedCornerShape(3.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = item.title,
            color = Color.White,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (showChannel && !item.channel.isNullOrEmpty()) {
            Spacer(Modifier.height(3.dp))
            Text(
                text = item.channel,
                color = Muted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A horizontally scrolling shelf of video cards. Focus follows the D-pad and the row scrolls to
 * keep the focused card visible.
 */
@Composable
fun ShelfRow(
    shelf: Shelf,
    onPlay: (VideoItem, List<VideoItem>) -> Unit,
    requestFocusOnFirst: FocusRequester? = null,
) {
    if (shelf.items.isEmpty()) return
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxWidth().padding(bottom = 26.dp)) {
        if (!shelf.title.isNullOrBlank()) {
            Text(
                text = shelf.title,
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 28.dp, bottom = 10.dp),
            )
        }
        LazyRow(
            state = state,
            contentPadding = PaddingValues(horizontal = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(count = shelf.items.size) { index ->
                val item = shelf.items[index]
                VideoCard(
                    item = item,
                    onClick = { onPlay(item, shelf.items) },
                    focusRequester = if (index == 0) requestFocusOnFirst else null,
                    onFocused = { focused ->
                        if (focused) {
                            scope.launch { state.animateScrollToItem(index) }
                        }
                    },
                )
            }
        }
    }
}
