package com.jarvis.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlGuardTest {

    @Test
    fun realHuggingFaceIsAccepted() {
        assertTrue(UrlGuard.isHuggingFace("https://huggingface.co/litert-community/model/resolve/main/a.litertlm"))
        assertTrue(UrlGuard.isHuggingFace("https://cdn-lfs.huggingface.co/repos/ab/file"))
        assertTrue(UrlGuard.isHuggingFace("  HTTPS://HuggingFace.co/x  "))
    }

    @Test
    fun lookAlikeHostsAreRejected() {
        assertFalse(UrlGuard.isHuggingFace("https://evil.com/?x=huggingface.co"))
        assertFalse(UrlGuard.isHuggingFace("https://huggingface.co.evil.com/file"))
        assertFalse(UrlGuard.isHuggingFace("https://nothuggingface.co/file"))
        assertFalse(UrlGuard.isHuggingFace("https://huggingface.co@evil.com/file"))
        assertFalse(UrlGuard.isHuggingFace("https://evil.com/huggingface.co/file"))
    }

    @Test
    fun plainHttpAndGarbageAreRejected() {
        assertFalse(UrlGuard.isHuggingFace("http://huggingface.co/file"))
        assertFalse(UrlGuard.isHuggingFace("huggingface.co/file"))
        assertFalse(UrlGuard.isHuggingFace(""))
        assertFalse(UrlGuard.isHuggingFace("not a url at all"))
    }
}
