package com.puretv.store.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionNamesTest {

    @Test
    fun `compares numeric segments rather than string order`() {
        assertTrue(VersionNames.compare("0.2.170", "0.2.9") > 0)
        assertTrue(VersionNames.compare("32.71", "32.9") > 0)
        assertEquals(0, VersionNames.compare("1.0", "1.0.0"))
    }

    @Test
    fun `strips the release tag prefix`() {
        assertEquals("0.2.170", VersionNames.normalize("v0.2.170"))
        assertFalse(VersionNames.isNewer("v0.2.170", "0.2.170"))
    }

    @Test
    fun `treats a missing installed version as upgradable`() {
        assertTrue(VersionNames.isNewer("32.71", null))
        assertFalse(VersionNames.isNewer(null, "1.0"))
    }

    @Test
    fun `ignores prerelease suffix for comparison`() {
        assertTrue(VersionNames.compare("1.2.0-beta2", "1.1.9") > 0)
        assertEquals(0, VersionNames.compare("1.2.0", "1.2.0-beta2"))
    }
}
