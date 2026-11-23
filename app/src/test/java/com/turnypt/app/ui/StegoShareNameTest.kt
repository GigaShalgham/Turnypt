package com.turnypt.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * v1.6.0-beta.9 (user report: "some files, e.g. a song, still go wrong —
 * it changes the extension and that breaks it"): the contract behind
 * the stego share's name-typing rule.
 *
 * The extension is read from the display name's LAST dot, verbatim —
 * MimeTypeMap.getFileExtensionFromUrl URL-DECODES the name (Persian and
 * other non-ASCII names came out mangled and the honest audio file fell
 * back to the anonymous application/octet-stream, which some receivers
 * then re-typed by guess — the broken-send report).
 */
class StegoShareNameTest {

    @Test
    fun `the extension is the text after the last dot`() {
        assertEquals("mp3", stegoShareExtensionOf("song.mp3"))
        assertEquals("flac", stegoShareExtensionOf("lossless.flac"))
        assertEquals("png", stegoShareExtensionOf("photo.png"))
        assertEquals("mp4", stegoShareExtensionOf("clip.MP4"))
    }

    @Test
    fun `compound names take the LAST dot only`() {
        assertEquals("mp3", stegoShareExtensionOf("my.song.mp3"))
        assertEquals("tar", stegoShareExtensionOf("backup.2026.tar"))
    }

    @Test
    fun `persian names keep their extension intact`() {
        assertEquals("mp3", stegoShareExtensionOf("آهنگ.mp3"))
        assertEquals("jpg", stegoShareExtensionOf("تصویر من.jpg"))
    }

    @Test
    fun `a trailing dot is not an extension`() {
        assertNull(stegoShareExtensionOf("song."))
        assertNull(stegoShareExtensionOf("."))
    }

    @Test
    fun `a leading dot is a hidden file not an extension`() {
        assertNull(stegoShareExtensionOf(".gitignore"))
    }

    @Test
    fun `null blank and extension-less names carry no extension`() {
        assertNull(stegoShareExtensionOf(null))
        assertNull(stegoShareExtensionOf(""))
        assertNull(stegoShareExtensionOf("   "))
        assertNull(stegoShareExtensionOf("README"))
    }

    @Test
    fun `paths resolve against the last segment`() {
        assertEquals("mp3", stegoShareExtensionOf("/storage/emulated/0/song.mp3"))
        assertEquals("wav", stegoShareExtensionOf("C:\\audio\\track.wav"))
    }
}
