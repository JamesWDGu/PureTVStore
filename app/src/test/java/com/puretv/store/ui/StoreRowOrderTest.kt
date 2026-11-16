package com.puretv.store.ui

import com.puretv.store.data.GitHubRelease
import com.puretv.store.data.StoreApp
import org.junit.Assert.assertEquals
import org.junit.Test

class StoreRowOrderTest {
    private fun row(id: String, installed: String?, latest: String): StoreRow = StoreRow(
        app = StoreApp(
            id = id,
            name = id,
            packageName = "com.example.$id",
            repo = "owner/$id",
            description = "",
            assetPattern = ".*\\.apk",
        ),
        release = GitHubRelease(tag = latest, title = latest, notes = "", assets = emptyList()),
        installedVersion = installed,
        status = RowStatus.IDLE,
    )

    @Test
    fun `updateable rows come first then fresh installs then current`() {
        val current = row("current", installed = "2.0.0", latest = "2.0.0")
        val updateable = row("updateable", installed = "1.0.0", latest = "2.0.0")
        val fresh = row("fresh", installed = null, latest = "1.0.0")

        val ordered = orderRows(listOf(current, updateable, fresh))

        assertEquals(listOf("updateable", "fresh", "current"), ordered.map { it.app.id })
    }

    @Test
    fun `installed same version is not an update`() {
        assertEquals(false, row("x", installed = "2.0.0", latest = "2.0.0").hasUpdate)
        assertEquals(true, row("x", installed = "1.9.9", latest = "2.0.0").hasUpdate)
        assertEquals(false, row("x", installed = null, latest = "2.0.0").hasUpdate)
    }
}
