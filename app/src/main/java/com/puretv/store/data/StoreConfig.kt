package com.puretv.store.data

import org.json.JSONObject

data class StoreApp(
    val id: String,
    val name: String,
    val packageName: String,
    val repo: String,
    val description: String,
    val assetPattern: String,
    val iconUrl: String? = null,
)

class StoreConfigException(message: String) : Exception(message)

enum class AbiArch { ARM64, ARMV7, X86_64, X86 }

/** Maps Android ABI names and common asset suffixes such as `arm64-v8a` or `armv7`. */
private val archAliases = listOf(
    AbiArch.X86_64 to listOf("x86_64", "x86-64", "amd64"),
    AbiArch.ARM64 to listOf("arm64-v8a", "arm64", "aarch64", "armv8", "v8a"),
    AbiArch.ARMV7 to listOf("armeabi-v7a", "armeabi", "armv7a", "armv7", "arm32"),
    AbiArch.X86 to listOf("x86", "i386", "i686"),
)

internal fun archOfAsset(name: String): AbiArch? {
    val lower = name.lowercase()
    return archAliases.firstOrNull { (_, aliases) -> aliases.any { lower.contains(it) } }?.first
}

internal fun archOfAbi(abi: String): AbiArch? = when (abi.trim().lowercase()) {
    "arm64-v8a", "arm64", "aarch64" -> AbiArch.ARM64
    "armeabi-v7a", "armeabi", "armv7", "armv7a" -> AbiArch.ARMV7
    "x86_64", "x86-64", "amd64" -> AbiArch.X86_64
    "x86", "i386", "i686" -> AbiArch.X86
    else -> null
}

/**
 * Parses `store/apps.json` from the PureTVStore repository.
 * The file is the only supported way to add apps; keep the schema documented in AGENTS.md.
 */
object StoreConfigParser {
    private val repoRegex = Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")
    private val packageRegex = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")

    fun parse(json: String): List<StoreApp> {
        val root = try {
            JSONObject(json)
        } catch (_: Exception) {
            throw StoreConfigException("商店配置不是有效的 JSON")
        }
        val appsJson = root.optJSONArray("apps")
            ?: throw StoreConfigException("商店配置缺少应用列表")

        val apps = buildList {
            for (index in 0 until appsJson.length()) {
                val item = appsJson.optJSONObject(index) ?: continue
                val id = item.optString("id").trim()
                val name = item.optString("name").trim()
                val packageName = item.optString("packageName").trim()
                val repo = item.optString("repo").trim()
                val description = item.optString("description").trim()
                val assetPattern = item.optString("assetPattern").trim().ifBlank { ".*\\.apk$" }
                val iconUrl = item.optString("iconUrl").trim().ifBlank { null }

                if (id.isBlank()) throw StoreConfigException("第 ${index + 1} 个应用缺少标识")
                if (name.isBlank()) throw StoreConfigException("${id} 缺少名称")
                if (!packageRegex.matches(packageName)) {
                    throw StoreConfigException("$name 的包名不正确")
                }
                if (!repoRegex.matches(repo)) {
                    throw StoreConfigException("$name 的仓库地址应为主持人/仓库名")
                }
                try {
                    Regex(assetPattern, RegexOption.IGNORE_CASE)
                } catch (_: Exception) {
                    throw StoreConfigException("$name 的安装包匹配规则无效")
                }
                if (iconUrl != null && !iconUrl.startsWith("https://") && !iconUrl.startsWith("http://")) {
                    throw StoreConfigException("$name 的图标地址必须是 http(s) 链接")
                }
                add(StoreApp(id, name, packageName, repo, description, assetPattern, iconUrl))
            }
        }

        if (apps.isEmpty()) throw StoreConfigException("商店配置中没有应用")
        if (apps.map { it.id }.toSet().size != apps.size) {
            throw StoreConfigException("商店配置中的应用标识重复")
        }
        return apps
    }

    /**
     * Full-name match wins; a partial match is accepted as a fallback.
     * When [supportedAbis] is non-empty and the release has arch-specific APKs, the first
     * compatible architecture wins; otherwise a universal (unlabelled) APK is used.
     * Returns null when every matching APK targets an incompatible architecture.
     */
    fun selectAsset(
        names: List<String>,
        pattern: String,
        supportedAbis: List<String> = emptyList(),
    ): String? {
        val regex = try {
            Regex(pattern, RegexOption.IGNORE_CASE)
        } catch (_: Exception) {
            return null
        }
        val matched = names.filter { regex.matches(it) }
            .ifEmpty { names.filter { regex.containsMatchIn(it) } }
        if (matched.isEmpty()) return null
        if (supportedAbis.isEmpty() || matched.none { archOfAsset(it) != null }) {
            return matched.first()
        }
        val byArch = matched.groupBy { archOfAsset(it) }
        for (abi in supportedAbis) {
            val arch = archOfAbi(abi) ?: continue
            byArch[arch]?.firstOrNull()?.let { return it }
        }
        return matched.firstOrNull { archOfAsset(it) == null }
    }
}

/** Numeric comparison for tags such as v0.2.170 against installed versionName 0.2.170. */
object VersionNames {
    fun normalize(tag: String): String = tag.trim().removePrefix("v").removePrefix("V")

    fun compare(left: String, right: String): Int {
        val a = segments(left)
        val b = segments(right)
        val size = maxOf(a.size, b.size)
        for (index in 0 until size) {
            val x = a.getOrElse(index) { 0 }
            val y = b.getOrElse(index) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }

    fun isNewer(latest: String?, installed: String?): Boolean {
        if (latest.isNullOrBlank()) return false
        if (installed.isNullOrBlank()) return true
        return compare(normalize(latest), installed) > 0
    }

    private fun segments(value: String): List<Int> =
        normalize(value)
            .split('.', '-', '+', '_')
            .mapNotNull { part -> part.takeWhile { it.isDigit() }.toIntOrNull() }
}
