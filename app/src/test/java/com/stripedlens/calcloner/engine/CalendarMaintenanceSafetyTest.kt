package com.stripedlens.calcloner.engine

import com.stripedlens.calcloner.CalendarInfo
import com.stripedlens.calcloner.SyncPair
import com.stripedlens.calcloner.viewmodel.ImportPairStatus
import com.stripedlens.calcloner.viewmodel.ImportPreviewState
import com.stripedlens.calcloner.viewmodel.ValidatedImportPair
import org.junit.Assert.*
import org.junit.Test

class CalendarMaintenanceSafetyTest {

    @Test
    fun testCalendarWritePermissionDetection() {
        // CAL_ACCESS_OWNER = 700
        val ownerCal = CalendarInfo(
            id = 1L,
            displayName = "Primary Calendar",
            accountName = "user@example.com",
            isPrimary = true,
            accessLevel = 700
        )
        assertTrue(ownerCal.canWrite)

        // CAL_ACCESS_CONTRIBUTOR = 500
        val contributorCal = CalendarInfo(
            id = 2L,
            displayName = "Shared Team Calendar",
            accountName = "team@example.com",
            isPrimary = false,
            accessLevel = 500
        )
        assertTrue(contributorCal.canWrite)

        // CAL_ACCESS_READ = 200
        val readOnlyCal = CalendarInfo(
            id = 3L,
            displayName = "Holiday Calendar",
            accountName = "holidays@example.com",
            isPrimary = false,
            accessLevel = 200
        )
        assertFalse(readOnlyCal.canWrite)

        // CAL_ACCESS_NONE = 0
        val noAccessCal = CalendarInfo(
            id = 4L,
            displayName = "Restricted Calendar",
            accountName = "restricted@example.com",
            isPrimary = false,
            accessLevel = 0
        )
        assertFalse(noAccessCal.canWrite)
    }

    @Test
    fun testImportPreviewStateEvaluation() {
        val testPair = SyncPair(
            id = "test-1",
            fromCalendarId = 1L,
            fromCalendarName = "Source",
            toCalendarId = 2L,
            toCalendarName = "Target"
        )

        val readyPair = ValidatedImportPair(
            originalPair = testPair,
            resolvedPair = testPair,
            status = ImportPairStatus.READY
        )

        val remappedPair = ValidatedImportPair(
            originalPair = testPair,
            resolvedPair = testPair.copy(fromCalendarId = 10L, toCalendarId = 20L),
            status = ImportPairStatus.REMAPPED,
            remappedFrom = true,
            remappedTo = true
        )

        val unresolvedPair = ValidatedImportPair(
            originalPair = testPair,
            resolvedPair = testPair,
            status = ImportPairStatus.UNRESOLVED,
            issueDescription = "Target calendar not found"
        )

        val stateMixed = ImportPreviewState(
            pairs = listOf(readyPair, remappedPair, unresolvedPair)
        )
        assertEquals(3, stateMixed.totalCount)
        assertEquals(1, stateMixed.readyCount)
        assertEquals(1, stateMixed.remappedCount)
        assertEquals(1, stateMixed.unresolvedCount)
        assertTrue(stateMixed.canApply)

        val stateAllUnresolved = ImportPreviewState(
            pairs = listOf(unresolvedPair)
        )
        assertEquals(1, stateAllUnresolved.totalCount)
        assertEquals(0, stateAllUnresolved.readyCount)
        assertEquals(0, stateAllUnresolved.remappedCount)
        assertEquals(1, stateAllUnresolved.unresolvedCount)
        assertFalse(stateAllUnresolved.canApply)
    }
}
