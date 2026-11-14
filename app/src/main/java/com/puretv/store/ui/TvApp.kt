package com.puretv.store.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.puretv.store.R
import com.puretv.store.ui.screens.FileShareScreen
import com.puretv.store.ui.screens.StoreScreen
import com.puretv.store.ui.screens.StoreUpdateDialog

@Composable
fun StoreRoot(viewModel: StoreViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(0) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshPermissions()
        viewModel.refreshInstalled()
    }

    val dialogVisible = state.update.available != null ||
        state.update.error != null ||
        state.update.message != null
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surfaceContainerLowest,
                    ),
                ),
            )
            .systemBarsPadding(),
    ) {
        if (dialogVisible) {
            StoreUpdateDialog(
                state = state.update,
                onDownload = viewModel::downloadUpdate,
                onDismiss = viewModel::dismissUpdate,
            )
            return@Box
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 24.dp),
        ) {
            Header(
                tab = tab,
                onTab = { tab = it },
                loading = state.loading,
                checkingUpdate = state.update.checking,
                versionName = state.versionName,
                onRefresh = viewModel::refresh,
                onCheckUpdate = { viewModel.checkForUpdate(manual = true) },
            )
            Spacer(Modifier.height(20.dp))
            if (tab == 0) {
                StoreScreen(
                    state = state,
                    onInstall = viewModel::install,
                    onOpenInstallPermission = viewModel::openInstallPermission,
                )
            } else {
                FileShareScreen(
                    state = state,
                    onToggleServer = viewModel::toggleServer,
                    onOpenAllFiles = viewModel::openAllFilesAccess,
                    onOpenInstallPermission = viewModel::openInstallPermission,
                    onRecheck = viewModel::refreshPermissions,
                )
            }
        }
    }

}

@Composable
private fun Header(
    tab: Int,
    onTab: (Int) -> Unit,
    loading: Boolean,
    checkingUpdate: Boolean,
    versionName: String,
    onRefresh: () -> Unit,
    onCheckUpdate: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(R.drawable.ic_launcher),
            contentDescription = null,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                text = "PureTV 商店",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "版本 $versionName · 开源电视应用 · 网页传文件",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.weight(1f))
        TabChip("应用商店", selected = tab == 0) { onTab(0) }
        Spacer(Modifier.width(10.dp))
        TabChip("网页传文件", selected = tab == 1) { onTab(1) }
        Spacer(Modifier.width(22.dp))
        FocusButton(
            text = if (checkingUpdate) "检查中…" else "检查更新",
            onClick = onCheckUpdate,
            enabled = !checkingUpdate,
            tonal = true,
        )
        Spacer(Modifier.width(10.dp))
        FocusButton(
            text = if (loading) "刷新中…" else "刷新",
            onClick = onRefresh,
            enabled = !loading,
            tonal = true,
        )
    }
}

@Composable
private fun TabChip(text: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.05f else 1f,
        animationSpec = tween(120),
        label = "tabScale",
    )
    val background by animateColorAsState(
        targetValue = when {
            selected -> MaterialTheme.colorScheme.primary
            focused -> MaterialTheme.colorScheme.surfaceContainerHighest
            else -> MaterialTheme.colorScheme.surfaceContainer
        },
        animationSpec = tween(120),
        label = "tabBackground",
    )
    val content by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(120),
        label = "tabContent",
    )
    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(background)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 9.dp),
    ) {
        Text(text, fontWeight = FontWeight.SemiBold, color = content)
    }
}
