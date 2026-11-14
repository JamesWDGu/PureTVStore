package com.puretv.store.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.puretv.store.R
import com.puretv.store.data.StoreApp
import com.puretv.store.ui.FocusButton
import com.puretv.store.ui.RowStatus
import com.puretv.store.ui.StoreRow
import com.puretv.store.ui.StoreUiState
import com.puretv.store.ui.theme.StorePalette

@Composable
fun StoreScreen(
    state: StoreUiState,
    onInstall: (String) -> Unit,
    onOpenInstallPermission: () -> Unit,
) {
    val firstCard = remember { FocusRequester() }
    LaunchedEffect(state.rows) {
        if (state.rows.isNotEmpty()) {
            delay(120)
            runCatching { firstCard.requestFocus() }
        }
    }

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
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
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
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        StoreHero(state.rows)
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "全部应用",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = "${state.rows.size} 个",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    itemsIndexed(state.rows, key = { _, row -> row.app.id }) { index, row ->
                        AppCard(
                            row = row,
                            onInstall = onInstall,
                            modifier = if (index == 0) {
                                Modifier.focusRequester(firstCard)
                            } else {
                                Modifier
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StoreHero(rows: List<StoreRow>) {
    val installed = rows.count { it.installedVersion != null }
    Surface(shape = RoundedCornerShape(28.dp), color = Color.Transparent) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(StorePalette.heroGradient)
                .padding(horizontal = 26.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "把好应用装进电视",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "精选开源电视应用，安装和更新都在这里完成。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatPill("已收录 ${rows.size} 个应用")
                    StatPill("已安装 $installed 个")
                }
            }
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.foundation.Image(
                    painter = painterResource(R.drawable.ic_launcher),
                    contentDescription = null,
                    modifier = Modifier.size(70.dp),
                )
            }
        }
    }
}

@Composable
private fun StatPill(text: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = Color.White.copy(alpha = 0.10f),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun AppCard(
    row: StoreRow,
    onInstall: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val actionable = row.status == RowStatus.IDLE || row.status == RowStatus.ERROR
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(22.dp)
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.025f else 1f,
        animationSpec = tween(140),
        label = "cardScale",
    )
    val container by animateColorAsState(
        targetValue = if (focused) {
            MaterialTheme.colorScheme.surfaceContainerHighest
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        animationSpec = tween(140),
        label = "cardColor",
    )

    Surface(
        shape = shape,
        color = container,
        modifier = modifier
            .fillMaxWidth()
            .height(190.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = if (focused) 18.dp else 0.dp,
                shape = shape,
                ambientColor = MaterialTheme.colorScheme.primary,
                spotColor = MaterialTheme.colorScheme.primary,
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable { if (actionable) onInstall(row.app.id) },
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppTile(row.app)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = row.app.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = row.installedVersion?.let { "已安装 $it" } ?: row.app.repo,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (row.installedVersion != null) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = row.app.description.ifBlank { "来自 ${row.app.repo} 的开源电视应用" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.weight(1f))
            if (row.status == RowStatus.DOWNLOADING) {
                LinearProgressIndicator(
                    progress = { row.progress ?: 0f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(50)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = statusText(row),
                    style = MaterialTheme.typography.bodySmall,
                    color = when (row.status) {
                        RowStatus.ERROR -> MaterialTheme.colorScheme.error
                        RowStatus.UP_TO_DATE -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                ActionPill(status = row.status)
            }
        }
    }
}

@Composable
private fun AppTile(app: StoreApp) {
    val colors = remember(app.id) {
        StorePalette.tileGradients[(app.id.hashCode() and Int.MAX_VALUE) % StorePalette.tileGradients.size]
    }
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(colors)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = app.name.take(1),
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }
}

@Composable
private fun ActionPill(status: RowStatus) {
    val label = when (status) {
        RowStatus.IDLE -> "安装"
        RowStatus.DOWNLOADING -> "下载中"
        RowStatus.INSTALLING -> "确认安装"
        RowStatus.UP_TO_DATE -> "已安装"
        RowStatus.ERROR -> "重试"
        RowStatus.CHECKING -> ""
    }
    if (label.isEmpty()) return
    val background = when (status) {
        RowStatus.ERROR -> MaterialTheme.colorScheme.errorContainer
        RowStatus.UP_TO_DATE -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.primary
    }
    val content = when (status) {
        RowStatus.ERROR -> MaterialTheme.colorScheme.onErrorContainer
        RowStatus.UP_TO_DATE -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onPrimary
    }
    Surface(shape = RoundedCornerShape(50), color = background) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = content,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
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
    val shape = RoundedCornerShape(16.dp)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
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
