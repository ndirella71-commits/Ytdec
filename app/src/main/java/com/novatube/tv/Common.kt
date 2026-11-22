package com.novatube.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novatube.tv.ui.FocusSurface

/** Pill button used for actions across the app. */
@Composable
fun ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    focusRequester: FocusRequester? = null,
) {
    FocusSurface(
        onClick = onClick,
        modifier = modifier,
        focusRequester = focusRequester,
        shape = RoundedCornerShape(22.dp),
    ) { focused ->
        Text(
            text = text,
            color = if (primary) Color.Black else Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .background(
                    when {
                        primary && focused -> Color.White
                        primary -> Color(0xE6FFFFFF)
                        focused -> Color(0x44FFFFFF)
                        else -> Color(0x22FFFFFF)
                    },
                    RoundedCornerShape(22.dp),
                )
                .padding(horizontal = 22.dp, vertical = 10.dp),
        )
    }
}

/**
 * On-screen keyboard. Leanback apps cannot rely on a system IME being available, so search and
 * credential entry use this D-pad friendly grid instead.
 */
@Composable
fun TvKeyboard(
    onChar: (Char) -> Unit,
    onBackspace: () -> Unit,
    onSpace: () -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    firstFocus: FocusRequester? = null,
) {
    val rows = listOf("1234567890", "qwertyuiop", "asdfghjkl", "zxcvbnm")
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEachIndexed { colIndex, ch ->
                    KeyboardKey(
                        label = ch.toString(),
                        onClick = { onChar(ch) },
                        focusRequester = if (rowIndex == 0 && colIndex == 0) firstFocus else null,
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            KeyboardKey("Space", onClick = onSpace, width = 196.dp)
            KeyboardKey("\u232B", onClick = onBackspace, width = 76.dp)
            KeyboardKey("Search", onClick = onSearch, width = 154.dp, accent = true)
        }
    }
}

@Composable
private fun KeyboardKey(
    label: String,
    onClick: () -> Unit,
    width: Dp = 52.dp,
    height: Dp = 46.dp,
    accent: Boolean = false,
    focusRequester: FocusRequester? = null,
) {
    FocusSurface(
        onClick = onClick,
        modifier = Modifier.width(width).height(height),
        focusRequester = focusRequester,
        focusedScale = 1.05f,
        shape = RoundedCornerShape(8.dp),
    ) { focused ->
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    when {
                        focused -> Color.White
                        accent -> Color(0xFF3A3A3A)
                        else -> Color(0xFF232323)
                    },
                    RoundedCornerShape(8.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                color = if (focused) Color.Black else Color.White,
                fontSize = 15.sp,
                fontWeight = if (accent) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

/** Formats milliseconds as m:ss or h:mm:ss. */
fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
