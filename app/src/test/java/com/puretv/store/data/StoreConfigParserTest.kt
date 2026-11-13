package com.puretv.store.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class StoreConfigParserTest {

    private val validJson = """
        {
          "apps": [
            {
              "id": "hongguo",
              "name": "红果短剧",
              "packageName": "rocks.aston.hongguotv",
              "repo": "aston314/hongguo-tv-releases",
              "description": "短剧",
              "assetPattern": "^hongguo-tv-[0-9].*\\.apk$"
            },
            {
              "id": "smarttube",
              "name": "SmartTube",
              "packageName": "org.smarttube.stable",
              "repo": "yuliskov/SmartTube"
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `parses apps and defaults the asset pattern`() {
        val apps = StoreConfigParser.parse(validJson)

        assertEquals(2, apps.size)
        assertEquals("rocks.aston.hongguotv", apps[0].packageName)
        assertEquals(".*\\.apk$", apps[1].assetPattern)
    }

    @Test
    fun `rejects an invalid package name`() {
        val json = validJson.replace("rocks.aston.hongguotv", "not a package")
        try {
            StoreConfigParser.parse(json)
            fail("expected StoreConfigException")
        } catch (e: StoreConfigException) {
            assertTrue(e.message!!.contains("包名"))
        }
    }

    @Test
    fun `rejects duplicate ids`() {
        val json = validJson.replace("\"smarttube\"", "\"hongguo\"")
        try {
            StoreConfigParser.parse(json)
            fail("expected StoreConfigException")
        } catch (e: StoreConfigException) {
            assertTrue(e.message!!.contains("重复"))
        }
    }

    @Test
    fun `selects the standard hongguo apk rather than the android5 variant`() {
        val names = listOf(
            "hongguo-tv-android5-0.2.170.apk",
            "hongguo-tv-mpv-0.2.170.apk",
            "hongguo-tv-0.2.170.apk",
        )

        val selected = StoreConfigParser.selectAsset(names, "^hongguo-tv-[0-9].*\\.apk$")

        assertEquals("hongguo-tv-0.2.170.apk", selected)
    }

    @Test
    fun `selects the universal smarttube apk`() {
        val names = listOf(
            "SmartTube_stable_32.71_arm64-v8a.apk",
            "SmartTube_stable_32.71_universal.apk",
        )

        val selected = StoreConfigParser.selectAsset(
            names,
            "^SmartTube_stable_.*_universal\\.apk$",
        )

        assertEquals("SmartTube_stable_32.71_universal.apk", selected)
    }
}
