package com.puretv.store.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.puretv.store.ui.screens.FileShareScreen
import com.puretv.store.ui.screens.StoreScreen

@Composable
fun StoreRoot(viewModel: StoreViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(0) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshPermissions()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface,
                    ),
                ),
            )
            .systemBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 40.dp, vertical = 20.dp),
        ) {
            Header(
                tab = tab,
                onTab = { tab = it },
                loading = state.loading,
                onRefresh = viewModel::refresh,
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
    onRefresh: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text(
                text = "PureTV 商店",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "安装开源电视应用 · 网页传文件",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.weight(1f))
        TabChip("应用商店", selected = tab == 0) { onTab(0) }
        Spacer(Modifier.width(10.dp))
        TabChip("网页传文件", selected = tab == 1) { onTab(1) }
        Spacer(Modifier.width(20.dp))
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
    Button(
        onClick = onClick,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        ),
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .border(
                width = 3.dp,
                color = if (focused) MaterialTheme.colorScheme.secondary else Color.Transparent,
                shape = shape,
            ),
        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
    ) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}
