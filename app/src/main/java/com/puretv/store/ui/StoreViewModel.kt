package com.puretv.store.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.puretv.store.data.ApkInstaller
import com.puretv.store.data.FileAccess
import com.puretv.store.data.GitHubRelease
import com.puretv.store.data.InstalledApps
import com.puretv.store.data.InstallCenter
import com.puretv.store.data.ReleaseAsset
import com.puretv.store.data.ServerState
import com.puretv.store.data.StoreApp
import com.puretv.store.data.StoreConfigParser
import com.puretv.store.data.StoreRepository
import com.puretv.store.data.VersionNames
import com.puretv.store.data.WebServerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

enum class RowStatus { CHECKING, IDLE, DOWNLOADING, INSTALLING, UP_TO_DATE, ERROR }

data class StoreRow(
    val app: StoreApp,
    val release: GitHubRelease? = null,
    val asset: ReleaseAsset? = null,
    val installedVersion: String? = null,
    val status: RowStatus = RowStatus.CHECKING,
    val progress: Float? = null,
    val message: String? = null,
)

data class StoreUiState(
    val loading: Boolean = false,
    val rows: List<StoreRow> = emptyList(),
    val error: String? = null,
    val serverRunning: Boolean = false,
    val serverUrl: String? = null,
    val serverError: String? = null,
    val canInstall: Boolean = true,
    val allFilesAccess: Boolean = false,
)

class StoreViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = StoreRepository(application)
    private val _state = MutableStateFlow(StoreUiState())
    val state: StateFlow<StoreUiState> = _state.asStateFlow()

    private var refreshJob: Job? = null
    private val installJobs = mutableMapOf<String, Job>()

    init {
        viewModelScope.launch {
            ServerState.running.collect { running ->
                _state.update { it.copy(serverRunning = running) }
            }
        }
        viewModelScope.launch {
            ServerState.url.collect { url -> _state.update { it.copy(serverUrl = url) } }
        }
        viewModelScope.launch {
            ServerState.error.collect { error -> _state.update { it.copy(serverError = error) } }
        }
        viewModelScope.launch {
            InstallCenter.results.filterNotNull().collect { result -> handleInstallResult(result) }
        }
        refreshPermissions()
        refresh()
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val apps = try {
                repository.loadApps()
            } catch (t: Throwable) {
                _state.update { it.copy(loading = false, error = friendly(t)) }
                return@launch
            }
            val rows = coroutineScope {
                apps.map { app -> async(Dispatchers.IO) { loadRow(app) } }.awaitAll()
            }
            _state.update { it.copy(loading = false, rows = rows) }
        }
    }

    private suspend fun loadRow(app: StoreApp): StoreRow {
        val context = getApplication<Application>()
        val installedVersion = InstalledApps.versionName(context, app.packageName)
        val release = try {
            repository.latestRelease(app)
        } catch (t: Throwable) {
            return StoreRow(
                app = app,
                installedVersion = installedVersion,
                status = RowStatus.ERROR,
                message = friendly(t),
            )
        }
        val assetName = StoreConfigParser.selectAsset(release.assets.map { it.name }, app.assetPattern)
        val asset = release.assets.firstOrNull { it.name == assetName }
        if (asset == null) {
            return StoreRow(
                app = app,
                release = release,
                installedVersion = installedVersion,
                status = RowStatus.ERROR,
                message = "发布中没有匹配的安装包",
            )
        }
        val upToDate = !VersionNames.isNewer(release.versionName, installedVersion)
        return StoreRow(
            app = app,
            release = release,
            asset = asset,
            installedVersion = installedVersion,
            status = if (upToDate) RowStatus.UP_TO_DATE else RowStatus.IDLE,
            message = if (upToDate) "已安装最新版本" else null,
        )
    }

    fun install(appId: String) {
        if (installJobs[appId]?.isActive == true) return
        val row = _state.value.rows.firstOrNull { it.app.id == appId } ?: return
        val asset = row.asset ?: return
        if (!ApkInstaller.canInstall(getApplication())) {
            _state.update { it.copy(canInstall = false) }
            return
        }
        installJobs[appId] = viewModelScope.launch {
            updateRow(appId) { it.copy(status = RowStatus.DOWNLOADING, progress = 0f, message = null) }
            val context = getApplication<Application>()
            val version = row.release?.versionName ?: "latest"
            val target = File(context.cacheDir, "store/${row.app.id}-$version.apk")
            try {
                target.parentFile?.mkdirs()
                repository.downloadAsset(asset, target) { done, total ->
                    val fraction = if (total > 0) done.toFloat() / total else null
                    updateRow(appId) { it.copy(progress = fraction) }
                }
                validateApk(target, row)
                val info = context.packageManager.getPackageArchiveInfo(target.absolutePath, 0)
                    ?: throw IOException("无法读取下载的安装包")
                val label = info.applicationInfo
                    ?.let { context.packageManager.getApplicationLabel(it).toString() }
                    ?: row.app.name

                when (val result = ApkInstaller.install(context, target, label, row.app.packageName)) {
                    ApkInstaller.Result.Started -> updateRow(appId) {
                        it.copy(
                            status = RowStatus.INSTALLING,
                            progress = null,
                            message = "请在电视上确认安装",
                        )
                    }
                    ApkInstaller.Result.NeedsPermission -> {
                        _state.update { it.copy(canInstall = false) }
                        updateRow(appId) {
                            it.copy(status = RowStatus.IDLE, progress = null, message = "需要先允许安装未知应用")
                        }
                    }
                    is ApkInstaller.Result.Failed -> updateRow(appId) {
                        it.copy(status = RowStatus.ERROR, progress = null, message = result.message)
                    }
                }
            } catch (t: Throwable) {
                target.delete()
                updateRow(appId) {
                    it.copy(status = RowStatus.ERROR, progress = null, message = friendly(t))
                }
            }
        }
    }

    private fun validateApk(file: File, row: StoreRow) {
        val context = getApplication<Application>()
        val info = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
            ?: throw IOException("安装包损坏")
        if (info.packageName != row.app.packageName) {
            throw IOException("安装包与应用不匹配")
        }
        val newCode = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        val installedCode = InstalledApps.versionCode(context, row.app.packageName)
        if (installedCode != null && newCode <= installedCode) {
            throw IOException("已安装相同或更新的版本")
        }
    }

    private fun handleInstallResult(result: InstallCenter.InstallResult) {
        val row = _state.value.rows.firstOrNull { it.app.packageName == result.packageName } ?: return
        if (result.success) {
            viewModelScope.launch {
                delay(1_500)
                val installed = withContext(Dispatchers.IO) {
                    InstalledApps.versionName(getApplication(), row.app.packageName)
                }
                updateRow(row.app.id) {
                    it.copy(
                        installedVersion = installed,
                        status = RowStatus.UP_TO_DATE,
                        progress = null,
                        message = "安装完成",
                    )
                }
            }
        } else {
            updateRow(row.app.id) {
                it.copy(
                    status = RowStatus.ERROR,
                    progress = null,
                    message = result.message ?: "安装未完成",
                )
            }
        }
    }

    fun refreshPermissions() {
        val context = getApplication<Application>()
        _state.update {
            it.copy(
                canInstall = ApkInstaller.canInstall(context),
                allFilesAccess = FileAccess(context).hasAllFilesAccess(),
            )
        }
    }

    fun toggleServer() {
        val context = getApplication<Application>()
        if (_state.value.serverRunning) WebServerService.stop(context) else WebServerService.start(context)
    }

    fun openInstallPermission() {
        val context = getApplication<Application>()
        runCatching {
            context.startActivity(
                ApkInstaller.permissionIntent(context)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    fun openAllFilesAccess() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val context = getApplication<Application>()
        val intent = Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    private fun updateRow(appId: String, transform: (StoreRow) -> StoreRow) {
        _state.update { current ->
            current.copy(
                rows = current.rows.map { row -> if (row.app.id == appId) transform(row) else row },
            )
        }
    }

    private fun friendly(t: Throwable): String = when (t) {
        is UnknownHostException -> "无法连接网络，请检查电视的网络"
        is SocketTimeoutException -> "网络超时，请稍后重试"
        else -> t.message?.takeIf { it.isNotBlank() && !it.contains("Exception") } ?: "操作失败，请稍后重试"
    }
}
