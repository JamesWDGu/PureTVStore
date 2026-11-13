package com.puretv.store.data

import org.json.JSONObject

data class StoreApp(
    val id: String,
    val name: String,
    val packageName: String,
    val repo: String,
    val description: String,
    val assetPattern: String,
)

class StoreConfigException(message: String) : Exception(message)

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
                add(StoreApp(id, name, packageName, repo, description, assetPattern))
            }
        }

        if (apps.isEmpty()) throw StoreConfigException("商店配置中没有应用")
        if (apps.map { it.id }.toSet().size != apps.size) {
            throw StoreConfigException("商店配置中的应用标识重复")
        }
        return apps
    }

    /** Full-name match wins; a partial match is accepted as a fallback. */
    fun selectAsset(names: List<String>, pattern: String): String? {
        val regex = try {
            Regex(pattern, RegexOption.IGNORE_CASE)
        } catch (_: Exception) {
            return null
        }
        return names.firstOrNull { regex.matches(it) }
            ?: names.firstOrNull { regex.containsMatchIn(it) }
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
