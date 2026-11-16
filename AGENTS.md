# AGENTS.md

面向在本仓库中工作的编码代理。修改架构、依赖、网络行为或隐私策略时，同步更新本文件。

## 1. 仓库范围

- 仓库是电视端应用：`com.puretv.store`，使用 Jetpack Compose Material 3，单 Activity + ViewModel。
- 电视应用独立于手机端 `PureTVMobileHelper`：手机端通过 ADB 管理电视，本应用在电视上运行。
  不要在本仓库加入手机端代码或手机端签名。
- `store/apps.json` 是商店清单的权威来源，电视通过
  `https://raw.githubusercontent.com/JamesWDGu/PureTVStore/main/store/apps.json` 读取。
- 界面语言为简体中文，面向普通用户：称“电视”，不展示堆栈、内部英文错误或协议术语。

## 2. 固定版本与构建约束

|项目|值|
|---|---|
|Android Gradle Plugin|9.5.1（内置 Kotlin）|
|Gradle|9.6.0|
|Kotlin / Compose 编译器插件|2.4.21|
|Compose BOM|2026.09.00|
|compileSdk / targetSdk / minSdk|37 / 36 / 23|
|Java 兼容级别|17|
|HTTP 服务|NanoHTTPD 2.3.1|

- 不要应用 `org.jetbrains.kotlin.android` 或 `kotlin-kapt`；AGP 9 已内置 Kotlin。
- 只保留 Compose 编译器插件 `org.jetbrains.kotlin.plugin.compose`。
- `targetSdk` 保持 36。升级到 37 前必须处理 `ACCESS_LOCAL_NETWORK` 运行时权限，否则网页服务
  可能无法被局域网访问。
- CI 使用 JDK 25；本地构建 JDK 17+ 可编译，单元测试同样可在 JDK 17 运行。

## 3. 代码结构

|文件|职责|
|---|---|
|`MainActivity.kt`|启动网页服务、通知权限、显示 Compose 界面|
|`PureTVStoreApp.kt`|创建通知渠道|
|`data/StoreConfig.kt`|清单模型、JSON 校验、APK 资产选择、版本比较、可选图标地址|
|`data/GitHubClient.kt`|GitHub Release API、重定向下载与进度|
|`data/StoreRepository.kt`|清单读取（网络 → 缓存 → 内置）、Release 5 分钟缓存、下载|
|`data/InstalledApps.kt`|读取已安装应用的版本名/版本号|
|`data/AppIconLoader.kt`|已安装应用读取系统图标；未安装应用下载 `iconUrl` 并做内存/磁盘缓存|
|`data/StoreUpdater.kt`|商店自身更新检查（API → Atom 回退）、下载与版本解析|
|`data/ApkInstaller.kt`|安装会话（网页上传）、FileProvider 直接安装（商店行）、安装权限入口|
|`data/Notifications.kt`|网页服务与安装结果通知|
|`data/FileAccess.kt`|授权根目录、文件列表、上传、重命名、删除、新建目录|
|`data/NetworkAddress.kt`|本机局域网 IPv4 地址|
|`data/WebServer.kt`|NanoHTTPD 路由：页面、状态、文件 API、APK 上传安装|
|`data/WebServerService.kt`|前台服务与运行状态（`ServerState`）|
|`ui/StoreViewModel.kt`|商店列表、下载安装状态、服务与权限状态|
|`ui/TvApp.kt`|标题、标签页、权限刷新|
|`ui/TvComponents.kt`|电视焦点按钮：放大、提亮、投影，不绘制边框|
|`ui/screens/StoreScreen.kt`|双列应用卡片（真实图标、状态与安装操作）|
|`ui/screens/FileShareScreen.kt`|网页服务地址、开关与权限入口|
|`ui/screens/StoreUpdateDialog.kt`|同窗口全屏覆盖层：发现新版本/下载进度/失败提示，打开即聚焦主按钮|
|`assets/web/index.html`|电脑浏览器页面（原生 JS，无外部依赖）|
|`assets/store-apps.json`|断网时的内置清单，与 `store/apps.json` 同步|

## 4. 数据流

1. 启动 → `MainActivity` 调 `WebServerService.start()`，服务在 `onCreate` 创建通知、启动
   NanoHTTPD（端口 8686）、把地址写入 `ServerState`。
2. 商店页 → `StoreViewModel.refresh()` → `StoreRepository.loadApps()`：
   读取远端清单成功则写 `store_cache`；失败用缓存；再失败用内置清单。
3. 每个应用 → `GitHubClient.latestRelease(repo)`（5 分钟内存缓存）→ `selectAsset()` 选 APK；
   `selectAsset` 先按 `assetPattern` 过滤，再用 `Build.SUPPORTED_ABIS` 优先选择架构匹配的包
   （别名：arm64-v8a/arm64/aarch64/v8a、armeabi-v7a/armeabi/armv7、x86_64/amd64、x86/i386），
   没有匹配时回退 universal，全部不兼容则显示“没有适合本机架构的安装包”；
   已装版本从 `PackageManager` 读取，用版本名比较决定“已是最新/可安装”。
4. 点击安装 → 下载到 `cacheDir/store/<id>-<version>.apk` → 校验包名与 `versionCode` 大于已装
   → `ApkInstaller.install()` 写 PackageInstaller 会话并提交。
5. 商店行安装 → `ApkInstaller.installWithSystemUi()`：复制到 `cache/install/`，经 FileProvider
   以 `ACTION_VIEW` 直接交给系统安装器，由前台应用发起，不依赖安装状态广播；用户返回商店后
   `refreshInstalled()` 用 PackageManager 刷新结果，行停在 `INSTALLING` 超过 90 秒提示重试。
   网页上传 APK → PackageInstaller 会话，`InstallResultReceiver` 收到
   `STATUS_PENDING_USER_ACTION` 时启动系统确认界面；最终状态经 `InstallCenter.complete()`
   写入 `InstallCenter.results` 并通知。
6. 网页上传 APK → `WebServer.uploadResponse` 保存文件 → 若扩展名为 `.apk` 立即调用
   `ApkInstaller.install()`，响应返回 `started`/`needs_permission`/`failed`。
7. 商店自身更新 → `StoreUpdater.findUpdate()`：先请求 Release API，403/429/解析失败时回退
   `https://github.com/JamesWDGu/PureTVStore/releases.atom`，按 `PureTVStore-<版本>.apk`
   构造下载地址；`downloadUpdate()` 写入 `cacheDir/updates/` 后经
   `ApkInstaller.installWithSystemUi()` 交给系统安装器。启动时静默检查，手动检查才显示
   “已是最新版本/检查失败”。
8. 文件管理 API 全部经 `FileAccess.resolve()` 规范化路径，只允许在授权根目录内操作。

## 5. 必须保持的不变量

- **安装必须经系统确认**：只能使用 PackageInstaller + 系统安装界面；不得使用 root、静默安装、
  `pm install` 或绕过用户确认。
- 商店行安装必须走 `ApkInstaller.installWithSystemUi()`（FileProvider + `ACTION_VIEW`），
  不得改成“提交会话后等待确认”：部分电视会拦截状态广播里启动的界面，表现为永远停在
  “等待确认”。网页上传保留 PackageInstaller 会话与 `InstallResultReceiver`，其状态
  PendingIntent 必须为 `FLAG_MUTABLE`，并处理 `STATUS_PENDING_USER_ACTION`。
- 安装行停在 `INSTALLING` 超过 90 秒时必须提示“没有收到电视的安装确认，请重试”。
- 安装前校验 APK 的包名与 `versionCode`，避免把其他应用或旧版本装到同一包名上。
- 商店自身更新只允许来自 `JamesWDGu/PureTVStore` 的 Release；先 API 后 Atom，两者都失败
  才提示错误；不得静默安装或后台自动安装，必须经系统安装器确认。
- 更新提示必须是主窗口内的全屏覆盖层（隐藏后台内容并自动聚焦主按钮），不得改回独立
  `Dialog` 窗口：部分电视上对话框拿不到初始焦点，遥控器方向键/确定键无法操作。
- 应用卡片优先显示真实图标：已安装包用 `PackageManager` 图标，未安装用清单 `iconUrl`；
  只有两者都不可用时才用首字母色块。图标请求在 IO 线程并缓存，不阻塞滚动。
- 图标绘制前必须裁掉透明边距（`AppIconLoader.trimTransparent`），让图像填满卡片图标区域；
  不要通过缩小图片或在图片外再加内边距来“留白”。
- 清单只能来自 `store/apps.json`、上次成功缓存或内置资产；不要从用户输入拼 URL。
- 包名进入 `PackageManager` 前必须通过包名正则校验；仓库名必须符合 `owner/repo`。
- 版本比较按数字段，不得用字符串排序；缺少已装版本视为可安装。
- 资产选择：正则全匹配优先，其次包含匹配；没有匹配时回退到第一个 `.apk`。
- 不得申请存储权限读写用户文件：API 30+ 只用 `MANAGE_EXTERNAL_STORAGE` 系统开关，
  未授权时仅访问应用自己的外部目录；不要申请 `READ/WRITE_EXTERNAL_STORAGE`（保留 maxSdk 兼容）。
- 文件路径必须 `canonicalFile` 后校验位于授权根目录内，防目录穿越；重命名只能改文件名，
  不得移动目录；根目录不能删除或重命名。
- 网页服务只监听局域网端口 8686，HTTP 明文，无账号体系；不得面向公网、不得自动开启热点或
  端口映射。页面与 API 不提供任意命令执行。
- 上传文件大小与类型不信任客户端；APK 也必须由系统安装器验证签名与兼容性。
- 网页与电视端文案使用普通用户语言；错误要给出下一步，不展示堆栈。
- 界面固定使用深色配色：`PureTVStoreTheme` 不得读取系统浅色设置，也不提供浅色入口，
  避免电视在明亮环境中整屏发白。
- 焦点与选中状态只用放大、提亮、填充色和投影表达；不得用 `border`/细描边画焦点框，
  避免出现按钮或卡片外圈未填充的空隙。
- 启动图标必须是 `@mipmap/ic_launcher` 自适应图标，背景铺满、前景位于 66dp 安全区内；
  不要改回带透明边距的普通 drawable，否则电视桌面会给图标套一圈空白。
- 不新增遥测、崩溃上报、广告 SDK；联网仅限清单、GitHub Release、APK 下载。
- 网络请求只在 `Dispatchers.IO`；下载进度节流，避免高频更新 UI。
- 只保留一个前台服务；停止服务时关闭 NanoHTTPD 并更新 `ServerState`。
- 代码与文档中不得出现真实密钥、密码、签名文件、真实家庭 IP、设备序列号或账号信息。

## 6. 版本与发布

- 版本号只在根目录 `gradle.properties` 的 `storeVersionName` 修改；
  `versionCode = MAJOR×10000 + MINOR×100 + PATCH`，可用 `storeVersionCode` 覆盖。
- 修复递增 PATCH、功能递增 MINOR、不兼容递增 MAJOR；发布前同步更新 `CHANGELOG.md`。
- 签名：`signing/puretvstore-release.jks`（alias `puretvstore`，RSA 4096）与本地
  `signing/signing.properties`，均被 `.gitignore` 忽略，**不要**提交或上传。
- CI：`.github/workflows/release.yml` 在 `release` 分支推送或手动触发；限制在 `release` 分支，
  使用 `release` Environment 与 4 个 Secrets；所有 `uses:` 固定完整 commit SHA；
  **禁止**添加 `pull_request` 触发。
- 发布流程：测试 → `assembleRelease` → 校验证书 `CN=PureTV Store` → 先建 draft 并上传
  `PureTVStore-<version>.apk` → 再发布，兼容 immutable releases；tag 为 `v<versionName>`，
  重跑追加 `-a<次数>`。
- Secrets（放 `release` Environment）：`RELEASE_KEYSTORE_BASE64`、`RELEASE_STORE_PASSWORD`、
  `RELEASE_KEY_ALIAS`、`RELEASE_KEY_PASSWORD`。

## 7. 构建与验证

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleRelease
```

模拟器 smoke test：

1. 启动 Android 模拟器，安装 debug APK，确认商店列表显示内置应用与版本。
2. `adb forward tcp:8686 tcp:8686`，`curl http://127.0.0.1:8686/api/status` 查看版本、地址、
   权限状态。
3. `curl -X POST .../api/mkdir`、`/api/upload`、`/api/files`、`/api/rename`、`/api/download`、
   `/api/delete` 覆盖文件管理。
4. 通过 `/api/upload` 上传另一个 APK，确认返回 `install.status=started`，电视弹出系统安装确认，
   安装后 `pm path <包名>` 可查到。
5. 商店卡片点击安装，确认下载进度、系统确认、安装完成后显示“已安装/已是最新”。
6. 截图统一 480px 宽，放 `docs/images/`；仅完整功能新增截图，小增强不加图；影响旧图内容时
   更新对应旧图。使用模拟器与占位数据，不显示真实设备信息。

## 8. 隐私与提交规则

- **绝不提交**：`signing/`、`*.jks`、`*.keystore`、`*.apk`、`*.aab`、`local.properties`、
  `build/`、`.gradle/`、日志、截图原图、真实密钥。
- 不写真实局域网 IP、MAC、设备序列号、账号或 Wi-Fi 名称；示例使用 `192.168.1.50`、
  `10.0.2.16` 等占位地址。
- 推送前检查：

```bash
git status --short
git ls-files | grep -E '(^|/)(build|\.gradle)/|local\.properties|\.(jks|keystore|apk|aab|log)$'
```

## 9. 已知限制

- 只支持 GitHub Release 的单个 APK；不支持 XAPK/APKS/分卷。
- 按包名和版本号判断更新；软件改包名会显示为新应用。
- 网页服务是明文 HTTP、无鉴权，仅适用于可信局域网；端口固定 8686。
- 未授予“访问所有文件”时只能管理应用自己的文件夹。
- 部分电视系统安装器可能限制后台安装或增加额外确认。
- 商店清单依赖 GitHub 可达；网络失败时使用缓存或内置清单。
