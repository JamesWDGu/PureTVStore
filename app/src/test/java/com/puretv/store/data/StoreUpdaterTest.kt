package com.puretv.store.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreUpdaterTest {

    private val releaseJson = """
        {
          "tag_name": "v1.2.0",
          "draft": false,
          "body": "## 新增\n\n- **检查更新**\n- 修复 `安装`\n",
          "assets": [
            {"name": "notes.txt", "browser_download_url": "https://example.com/notes.txt", "size": 1},
            {"name": "PureTVStore-1.2.0.apk", "browser_download_url": "https://example.com/app.apk", "size": 1055543}
          ]
        }
    """.trimIndent()

    @Test
    fun `parses release and picks store asset`() {
        val update = parseStoreUpdate(releaseJson)!!
        assertEquals("1.2.0", update.versionName)
        assertEquals("https://example.com/app.apk", update.apkUrl)
        assertEquals(1055543L, update.apkSize)
        assertTrue(update.notes.contains("检查更新"))
        assertTrue(!update.notes.contains("**") && !update.notes.contains("`"))
    }

    @Test
    fun `release without apk is ignored`() {
        val json = """{"tag_name":"v1.2.0","assets":[{"name":"notes.txt"}]}"""
        assertNull(parseStoreUpdate(json))
    }

    @Test
    fun `atom fallback builds standard store asset url`() {
        val xml = """
            <feed xmlns="http://www.w3.org/2005/Atom">
              <entry>
                <title>1.2.0</title>
                <content type="html">&lt;h2&gt;新增&lt;/h2&gt;&lt;p&gt;检查更新&lt;/p&gt;</content>
                <link rel="alternate" href="https://github.com/JamesWDGu/PureTVStore/releases/tag/v1.2.0"/>
              </entry>
            </feed>
        """.trimIndent()
        val update = parseStoreUpdateAtom(xml)!!
        assertEquals("1.2.0", update.versionName)
        assertEquals(
            "https://github.com/JamesWDGu/PureTVStore/releases/download/v1.2.0/PureTVStore-1.2.0.apk",
            update.apkUrl,
        )
        assertEquals("新增\n检查更新", update.notes)
    }

    @Test
    fun `atom entry without version tag is ignored`() {
        val xml = """<feed><entry><title>x</title></entry></feed>"""
        assertNull(parseStoreUpdateAtom(xml))
    }
}
