package com.puretv.store.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.puretv.store.ui.FocusButton
import com.puretv.store.ui.RowStatus
import com.puretv.store.ui.StoreRow
import com.puretv.store.ui.StoreUiState

@Composable
fun StoreScreen(
    state: StoreUiState,
    onInstall: (String) -> Unit,
    onOpenInstallPermission: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        if (!state.canInstall) {
            NoticeCard(
                title = "还不能安装应用",
                body = "请允许 PureTV 商店安装未知应用，之后才能安装商店里的软件。",
                action = "去开启",
                onAction = onOpenInstallPermission,
            )
            Spacer(Modifier.height(14.dp))
        }
        if (state.error != null) {
            NoticeCard(
                title = "应用列表没有加载出来",
                body = state.error,
                action = null,
                onAction = {},
            )
            Spacer(Modifier.height(14.dp))
        }

        when {
            state.rows.isEmpty() && state.loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text("正在获取应用列表…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            state.rows.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "暂时没有可用的应用\n请检查网络后按“刷新”重试",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(state.rows, key = { it.app.id }) { row ->
                        AppCard(row = row, onInstall = onInstall)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppCard(row: StoreRow, onInstall: (String) -> Unit) {
    val actionable = row.status == RowStatus.IDLE || row.status == RowStatus.ERROR
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)

    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (actionable) {
                    Modifier.clickable { onInstall(row.app.id) }
                } else {
                    Modifier
                },
            )
            .onFocusChanged { focused = it.isFocused }
            .border(
                width = 3.dp,
                color = if (focused) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = shape,
            ),
    ) {
        Column(Modifier.padding(horizontal = 22.dp, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.app.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(12.dp))
                VersionBadge(
                    text = row.installedVersion?.let { "已安装 $it" } ?: "未安装",
                    highlighted = row.installedVersion != null,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = row.app.repo,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (row.app.description.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = row.app.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = statusText(row),
                    style = MaterialTheme.typography.bodyMedium,
                    color = when (row.status) {
                        RowStatus.ERROR -> MaterialTheme.colorScheme.error
                        RowStatus.UP_TO_DATE -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                )
                Spacer(Modifier.weight(1f))
                ActionHint(actionable = actionable, status = row.status)
            }

            if (row.status == RowStatus.DOWNLOADING) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { row.progress ?: 0f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun VersionBadge(text: String, highlighted: Boolean) {
    val shape = RoundedCornerShape(50)
    Surface(
        shape = shape,
        color = if (highlighted) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        } else {
            MaterialTheme.colorScheme.surface
        },
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (highlighted) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun ActionHint(actionable: Boolean, status: RowStatus) {
    val text = when (status) {
        RowStatus.IDLE -> if (actionable) "按确定安装" else ""
        RowStatus.ERROR -> if (actionable) "按确定重试" else ""
        RowStatus.DOWNLOADING -> "下载中"
        RowStatus.INSTALLING -> "等待确认"
        RowStatus.UP_TO_DATE -> "已是最新"
        RowStatus.CHECKING -> ""
    }
    if (text.isNotEmpty()) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

private fun statusText(row: StoreRow): String = when (row.status) {
    RowStatus.CHECKING -> "正在获取版本…"
    RowStatus.IDLE -> {
        val latest = row.release?.versionName
        if (row.installedVersion == null) {
            latest?.let { "可以安装 $it" } ?: "可以安装"
        } else {
            latest?.let { "可更新到 $it" } ?: "有可用更新"
        }
    }
    RowStatus.DOWNLOADING -> "正在下载 ${((row.progress ?: 0f) * 100).toInt()}%"
    RowStatus.INSTALLING -> "请在电视上确认安装"
    RowStatus.UP_TO_DATE -> "已是最新版本"
    RowStatus.ERROR -> row.message ?: "暂时无法获取版本"
}

@Composable
private fun NoticeCard(
    title: String,
    body: String?,
    action: String?,
    onAction: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                if (!body.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (action != null) {
                Spacer(Modifier.width(16.dp))
                FocusButton(text = action, onClick = onAction, tonal = true)
            }
        }
    }
}
