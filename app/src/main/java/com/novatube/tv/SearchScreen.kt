package com.novatube.tv

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novatube.tv.data.FeedParser
import com.novatube.tv.data.InnerTube
import com.novatube.tv.data.VideoItem
import com.novatube.tv.ui.AccentRed
import com.novatube.tv.ui.Muted
import com.novatube.tv.ui.VideoCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SearchScreen(innerTube: InnerTube, onPlay: (VideoItem, List<VideoItem>) -> Unit) {
    var query by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val keyboardFocus = remember { FocusRequester() }

    val store = remember {
        FeedStore { token ->
            if (query.isBlank()) return@FeedStore com.novatube.tv.data.FeedPage(emptyList(), null)
            FeedParser.parse(
                if (token != null) innerTube.search(query, token) else innerTube.search(query),
            )
        }
    }

    LaunchedEffect(Unit) {
        delay(150)
        runCatching { keyboardFocus.requestFocus() }
    }

    fun submit() {
        if (query.isBlank()) return
        submitted = true
        scope.launch { store.refresh() }
    }

    Row(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .width(620.dp)
                .fillMaxSize()
                .background(Color(0xFF151515))
                .padding(horizontal = 26.dp, vertical = 24.dp),
        ) {
            Text("Search", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(Color(0xFF262626), RoundedCornerShape(10.dp))
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = if (query.isEmpty()) "Search YouTube" else query,
                    color = if (query.isEmpty()) Muted else Color.White,
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(18.dp))

            TvKeyboard(
                onChar = { query += it },
                onBackspace = { query = query.dropLast(1) },
                onSpace = { if (query.isNotEmpty() && !query.endsWith(" ")) query += " " },
                onSearch = { submit() },
                firstFocus = keyboardFocus,
            )
        }

        Box(Modifier.fillMaxSize()) {
            when {
                !submitted -> Text(
                    "Use the keyboard to find videos, channels and playlists.",
                    color = Muted,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(28.dp),
                )

                store.error != null && store.shelves.isEmpty() -> Text(
                    store.error ?: "Search failed.",
                    color = Muted,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(28.dp),
                )

                store.loading && store.shelves.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = AccentRed, strokeWidth = 3.dp, modifier = Modifier.size(34.dp))
                }

                else -> {
                    val items = store.shelves.flatMap { it.items }
                    LazyColumn(
                        contentPadding = PaddingValues(24.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(count = (items.size + 2) / 3) { rowIndex ->
                            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                                for (col in 0 until 3) {
                                    val index = rowIndex * 3 + col
                                    val item = items.getOrNull(index) ?: continue
                                    VideoCard(
                                        item = item,
                                        onClick = { onPlay(item, items) },
                                        width = 264.dp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
