package com.puretv.store.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.puretv.store.ui.FocusButton
import com.puretv.store.ui.UpdateUiState

@Composable
fun StoreUpdateDialog(
    state: UpdateUiState,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
) {
    val update = state.available
    if (update == null && state.error == null && state.message == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when {
                    update != null -> "发现新版本"
                    state.error != null -> "检查更新失败"
                    else -> "检查更新"
                },
            )
        },
        text = {
            Column(Modifier.heightIn(max = 420.dp)) {
                when {
                    update != null -> {
                        Text(
                            "当前版本 ${state.currentVersion}，新版本 ${update.versionName}",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        if (update.notes.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Text("更新内容", style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.height(4.dp))
                            Column(
                                Modifier
                                    .heightIn(max = 190.dp)
                                    .verticalScroll(rememberScrollState()),
                            ) {
                                Text(
                                    update.notes,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (state.downloading) {
                            Spacer(Modifier.height(12.dp))
                            LinearProgressIndicator(
                                progress = { state.progress ?: 0f },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${formatBytes(state.downloadedBytes)} / " +
                                    formatBytes(state.totalBytes),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    state.error != null -> Text(state.error)
                    else -> Text(state.message.orEmpty())
                }
            }
        },
        confirmButton = {
            when {
                update != null && !state.downloading ->
                    FocusButton("下载并更新", onClick = onDownload)
                update != null -> FocusButton("正在下载…", onClick = {}, enabled = false)
                else -> FocusButton("关闭", onClick = onDismiss)
            }
        },
        dismissButton = {
            if (update != null) FocusButton("稍后", onClick = onDismiss, tonal = true)
        },
    )
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / 1024f / 1024f)
    bytes >= 1024L -> "%.0f KB".format(bytes / 1024f)
    else -> "$bytes B"
}
