package com.puretv.store.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puretv.store.ui.FocusButton
import com.puretv.store.ui.UpdateUiState
import kotlinx.coroutines.delay

@Composable
fun StoreUpdateDialog(
    state: UpdateUiState,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
) {
    val update = state.available
    if (update == null && state.error == null && state.message == null) return
    val primary = remember { FocusRequester() }
    LaunchedEffect(update?.versionName, state.downloading, state.error, state.message) {
        // Retry until the overlay is attached and accepts focus.
        repeat(10) {
            delay(100)
            if (runCatching { primary.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }

    BackHandler(onBack = onDismiss)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center,
    ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.82f)
                    .widthIn(max = 760.dp),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 8.dp,
            ) {
                Column(Modifier.padding(horizontal = 34.dp, vertical = 28.dp)) {
                    Text(
                        text = when {
                            update != null -> "发现新版本"
                            state.error != null -> "检查更新失败"
                            else -> "检查更新"
                        },
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(14.dp))
                    when {
                        update != null -> {
                            Text(
                                text = "当前版本 ${state.currentVersion}，新版本 ${update.versionName}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (update.notes.isNotBlank()) {
                                Spacer(Modifier.height(14.dp))
                                Text(
                                    text = "更新内容",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(6.dp))
                                Column(
                                    Modifier
                                        .heightIn(max = 220.dp)
                                        .verticalScroll(rememberScrollState()),
                                ) {
                                    Text(
                                        text = update.notes,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (state.downloading) {
                                Spacer(Modifier.height(16.dp))
                                LinearProgressIndicator(
                                    progress = { state.progress ?: 0f },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "${formatBytes(state.downloadedBytes)} / " +
                                        formatBytes(state.totalBytes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        state.error != null -> Text(
                            text = state.error,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        else -> Text(
                            text = state.message.orEmpty(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (update != null) {
                            FocusButton("稍后", onClick = onDismiss, tonal = true)
                        }
                        when {
                            update != null && !state.downloading -> FocusButton(
                                text = "下载并更新",
                                onClick = onDownload,
                                modifier = Modifier.focusRequester(primary),
                            )
                            update != null -> FocusButton(
                                text = "正在下载…",
                                onClick = {},
                                enabled = false,
                                modifier = Modifier.focusRequester(primary),
                            )
                            else -> FocusButton(
                                text = "关闭",
                                onClick = onDismiss,
                                modifier = Modifier.focusRequester(primary),
                            )
                        }
                    }
                }
            }
        }
    }

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / 1024f / 1024f)
    bytes >= 1024L -> "%.0f KB".format(bytes / 1024f)
    else -> "$bytes B"
}
