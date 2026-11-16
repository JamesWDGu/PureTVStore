package com.puretv.store.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puretv.store.ui.FocusButton
import com.puretv.store.ui.StoreUiState

@Composable
fun FileShareScreen(
    state: StoreUiState,
    onToggleServer: () -> Unit,
    onOpenAllFiles: () -> Unit,
    onOpenInstallPermission: () -> Unit,
    onRecheck: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        InfoCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "网页传文件",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (state.serverRunning) {
                            "电脑和电视连接同一个 Wi-Fi，在浏览器打开下面的地址。"
                        } else {
                            "服务没有运行，开启后电脑才能访问。"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FocusButton(
                    text = if (state.serverRunning) "停止服务" else "开启服务",
                    onClick = onToggleServer,
                    tonal = state.serverRunning,
                )
            }

            Spacer(Modifier.height(14.dp))
            if (state.serverRunning && state.serverUrl != null) {
                Text(
                    text = state.serverUrl,
                    fontSize = 30.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Text(
                    text = "尚未开启",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.serverError != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = state.serverError,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        if (!state.allFilesAccess) {
            InfoCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "现在只能管理应用自己的文件夹",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "允许访问所有文件后，才能在网页上浏览、上传、下载电视上的文件。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    FocusButton(text = "去允许", onClick = onOpenAllFiles)
                }
            }
        }

        if (!state.canInstall) {
            InfoCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "还不能安装上传的 APK",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "允许安装未知应用后，从网页上传 APK 才会弹出安装确认。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    FocusButton(text = "去允许", onClick = onOpenInstallPermission)
                }
            }
        }

        InfoCard {
            Text(
                text = "使用步骤",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Step("1", "在电脑浏览器打开上面的地址")
            Step("2", "选择 APK 上传，电视会弹出安装确认，用遥控器确认")
            Step("3", "在“文件管理”里上传、下载、重命名或删除文件")
            Spacer(Modifier.height(10.dp))
            Text(
                text = "服务随应用启动。电视重启或手动停止后，需要重新进入本页开启。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            FocusButton(text = "重新检查权限", onClick = onRecheck, tonal = true)
        }
    }
}

@Composable
private fun InfoCard(content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 22.dp, vertical = 18.dp)) {
            content()
        }
    }
}

@Composable
private fun Step(number: String, text: String) {
    Row(modifier = Modifier.padding(vertical = 3.dp)) {
        Text(
            text = number,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(26.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
