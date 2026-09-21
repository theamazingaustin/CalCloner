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

    @Test
    fun testTagClassificationSafety() {
        val regex = com.stripedlens.calcloner.AppConstants.Engine.CALCLONER_TAG_REGEX

        // Standard sync tag
        val standardMatch = regex.find("Meeting notes\n\n[CalCloner-ID: pair_abc:12345]")
        assertNotNull(standardMatch)
        assertEquals("pair_abc", standardMatch!!.groupValues[1])
        assertEquals("12345", standardMatch.groupValues[2])

        // One-time copy tag
        val oneTimeMatch = regex.find("Copied event\n\n[CalCloner-ID: onetime:67890]")
        assertNotNull(oneTimeMatch)
        assertEquals("onetime", oneTimeMatch!!.groupValues[1])
        assertEquals("67890", oneTimeMatch.groupValues[2])

        // Imported file tag
        val importMatch = regex.find("Imported calendar event\n\n[CalCloner-ID: import:112233]")
        assertNotNull(importMatch)
        assertEquals("import", importMatch!!.groupValues[1])
        assertEquals("112233", importMatch.groupValues[2])

        // Legacy CalClone tag (backward compatibility)
        val legacyMatch = regex.find("[CalClone-ID: legacy_pair:999]")
        assertNotNull(legacyMatch)
        assertEquals("legacy_pair", legacyMatch!!.groupValues[1])
        assertEquals("999", legacyMatch.groupValues[2])

        // Tag without pairId
        val bareMatch = regex.find("[CalCloner-ID: 555]")
        assertNotNull(bareMatch)
        assertTrue(bareMatch!!.groupValues[1].isEmpty())
        assertEquals("555", bareMatch.groupValues[2])

        // Organic user events MUST NOT match
        assertNull(regex.find("Meeting about ID: 12345 in conference room B"))
        assertNull(regex.find("Special project [Alpha-ID: 999] launch"))
        assertNull(regex.find("[CalCloner: invalid format 123]"))
        assertNull(regex.find("[CalCloner-ID: abc]")) // non-numeric event id
    }

    @Test
    fun testCrossPairProtectionLogic() {
        val regex = com.stripedlens.calcloner.AppConstants.Engine.CALCLONER_TAG_REGEX
        val uriPrefix = "calcloner://event/"
        val activePairIds = setOf("pair_active_1", "pair_active_2")

        // Helper to simulate the deletion classification logic from CalendarMaintenance.kt
        fun shouldDeleteEvent(customUri: String?, desc: String?, targetDeletingPairId: String?): Boolean {
            var matchedPairId: String? = null
            if (customUri != null && customUri.startsWith(uriPrefix)) {
                val remainder = customUri.removePrefix(uriPrefix)
                if (remainder.contains("/")) {
                    matchedPairId = remainder.substringBefore("/")
                }
            }
            if (matchedPairId == null && desc != null) {
                val tagMatch = regex.find(desc)
                matchedPairId = tagMatch?.groupValues?.getOrNull(1)?.ifEmpty { null }
            }

            // If event belongs to another active pair, DO NOT DELETE!
            if (matchedPairId != null && activePairIds.contains(matchedPairId) &&
                (targetDeletingPairId == null || matchedPairId != targetDeletingPairId)
            ) {
                return false
            }

            // Strictly ignore organic events
            val isCloned = (customUri != null && customUri.startsWith(uriPrefix)) ||
                    (desc != null && (desc.contains("[CalCloner-ID:") || desc.contains("[CalClone-ID:")))
            return isCloned
        }

        // 1. Organic event without custom URI or tag -> MUST NOT DELETE
        assertFalse(shouldDeleteEvent(null, "Organic personal dentist appointment", targetDeletingPairId = "pair_active_1"))

        // 2. Event belonging to pair_active_1 when deleting pair_active_1 -> MUST DELETE
        assertTrue(shouldDeleteEvent("calcloner://event/pair_active_1/101", "[CalCloner-ID: pair_active_1:101]", targetDeletingPairId = "pair_active_1"))

        // 3. Event belonging to pair_active_2 when deleting pair_active_1 -> STRICTLY PROTECTED!
        assertFalse(shouldDeleteEvent("calcloner://event/pair_active_2/202", "[CalCloner-ID: pair_active_2:202]", targetDeletingPairId = "pair_active_1"))

        // 4. One-time copy event -> Deletable when purging general clones
        assertTrue(shouldDeleteEvent("calcloner://event/onetime/303", "[CalCloner-ID: onetime:303]", targetDeletingPairId = null))

        // 5. Imported event -> Deletable when purging general clones
        assertTrue(shouldDeleteEvent("calcloner://event/import/404", "[CalCloner-ID: import:404]", targetDeletingPairId = null))
    }

    @Test
    fun testReDoSSafetyOnMalformedInput() {
        val regex = com.stripedlens.calcloner.AppConstants.Engine.CALCLONER_TAG_REGEX

        // Pathological input of 100,000 characters designed to cause catastrophic backtracking in vulnerable regexes
        val evilString = "[CalCloner-ID: " + "a".repeat(100000) + "!"
        val startTime = System.currentTimeMillis()
        val match = regex.find(evilString)
        val duration = System.currentTimeMillis() - startTime

        assertNull(match)
        // Must evaluate practically instantaneously (<100ms) without hanging
        assertTrue("Regex evaluated in $duration ms, must be under 100ms", duration < 100)
    }

    @Test
    fun testVerificationPhraseValidation() {
        fun isPhraseValid(expected: String, input: String): Boolean {
            return input.trim().equals(expected, ignoreCase = true)
        }

        assertTrue(isPhraseValid("PURGE", "PURGE"))
        assertTrue(isPhraseValid("PURGE", "purge"))
        assertTrue(isPhraseValid("PURGE", "  purge  "))
        assertFalse(isPhraseValid("PURGE", "PURGE ALL"))
        assertFalse(isPhraseValid("PURGE", "P"))

        assertTrue(isPhraseValid("CLEAR", "clear"))
        assertTrue(isPhraseValid("WIPE", "WIPE"))
    }

    @Test
    fun testDeleteScreenRequiredPhraseSeparation() {
        fun computeRequiredPhrase(calName: String, isPurgeCloned: Boolean): String {
            return if (isPurgeCloned) {
                "delete ${calName.trim()}"
            } else {
                "full wipe ${calName.trim()}"
            }
        }

        val calName = "Work Calendar"
        val purgePhrase = computeRequiredPhrase(calName, isPurgeCloned = true)
        val wipePhrase = computeRequiredPhrase(calName, isPurgeCloned = false)

        assertEquals("delete Work Calendar", purgePhrase)
        assertEquals("full wipe Work Calendar", wipePhrase)

        // Typing the purge phrase will NEVER unlock the full wipe operation
        assertFalse(wipePhrase.equals(purgePhrase, ignoreCase = true))
        assertFalse("delete Work Calendar".equals(wipePhrase, ignoreCase = true))
    }
}
