package com.puretv.store.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubReleaseParserTest {

    @Test
    fun `parses assets and skips entries without a download url`() {
        val json = """
            {
              "tag_name": "v0.2.170",
              "name": "0.2.170",
              "body": "修复若干问题",
              "assets": [
                {
                  "name": "hongguo-tv-0.2.170.apk",
                  "browser_download_url": "https://example.com/a.apk",
                  "size": 1234
                },
                {
                  "name": "broken.apk",
                  "browser_download_url": "",
                  "size": 1
                }
              ]
            }
        """.trimIndent()

        val release = GitHubReleaseParser.parse(json)

        assertEquals("0.2.170", release.versionName)
        assertEquals(1, release.assets.size)
        assertEquals(1234L, release.assets[0].size)
        assertTrue(release.assets[0].downloadUrl.endsWith("a.apk"))
    }
}
