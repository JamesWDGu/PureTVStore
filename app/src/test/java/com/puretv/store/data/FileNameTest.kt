package com.puretv.store.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FileNameTest {

    @Test
    fun `strips path components and trailing dots`() {
        assertEquals("evil.apk", sanitizeName("../../evil.apk"))
        assertEquals("movie.mkv", sanitizeName("C:\\tmp\\movie.mkv"))
        assertEquals("未命名", sanitizeName(".."))
        assertEquals("未命名", sanitizeName("   "))
    }

    @Test
    fun `limits very long names`() {
        assertEquals(180, sanitizeName("a".repeat(300)).length)
    }

    @Test
    fun `rejects a sibling directory sharing a prefix`() {
        assertFalse(isInside(File("/storage/emulated/0/Movies"), File("/storage/emulated/0/Movies2")))
        assertTrue(isInside(File("/storage/emulated/0/Movies"), File("/storage/emulated/0/Movies/a.mp4")))
    }
}
