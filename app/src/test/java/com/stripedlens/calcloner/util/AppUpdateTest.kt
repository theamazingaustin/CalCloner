package com.stripedlens.calcloner.util

import org.junit.Assert.*
import org.junit.Test

class AppUpdateTest {

    @Test
    fun testIsNewerTagBuildIncrement() {
        assertTrue(AppUpdateManager.isNewerTag("v2.2-9.18-5", "v2.2-9.18-4"))
        assertFalse(AppUpdateManager.isNewerTag("v2.2-9.18-4", "v2.2-9.18-4"))
        assertFalse(AppUpdateManager.isNewerTag("v2.2-9.18-3", "v2.2-9.18-4"))
    }

    @Test
    fun testIsNewerTagDateIncrement() {
        assertTrue(AppUpdateManager.isNewerTag("v2.2-9.19-1", "v2.2-9.18-5"))
        assertTrue(AppUpdateManager.isNewerTag("v2.2-10.1-1", "v2.2-9.18-5"))
        assertFalse(AppUpdateManager.isNewerTag("v2.2-9.17-1", "v2.2-9.18-1"))
    }

    @Test
    fun testIsNewerTagVersionIncrement() {
        assertTrue(AppUpdateManager.isNewerTag("v2.3-9.18-1", "v2.2-9.18-4"))
        assertTrue(AppUpdateManager.isNewerTag("v3.0-1.1-1", "v2.2-9.18-4"))
        assertFalse(AppUpdateManager.isNewerTag("v2.1-9.18-9", "v2.2-9.18-1"))
    }

    @Test
    fun testIsNewerTagEdgeCases() {
        assertFalse(AppUpdateManager.isNewerTag("", "v2.2-9.18-4"))
        assertFalse(AppUpdateManager.isNewerTag("v2.2-9.18-4", ""))
        assertFalse(AppUpdateManager.isNewerTag("v2.2-9.18-4", "v2.2-9.18-4"))
    }
}
