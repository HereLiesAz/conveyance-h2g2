package com.hereliesaz.conveyance.h2g2.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The pure parts of the Isolation Audit screen: what "send to AI" transmits, and what it tells the user. */
class IsolationAuditTest {

    private fun entry(audit: IsolationAudit) = IsolationAuditEntry(
        packageName = "demo",
        managerLabel = "pkg",
        version = "1.2",
        savedAudit = SavedIsolationAudit(1L, audit),
        fileCount = 3,
        bytes = 2048L
    )

    @Test
    fun promptSendsBasenamesNotPaths() {
        val prompt = auditInterpretationPrompt(
            entry(IsolationAudit(created = listOf("/data/user/0/app/files/x/bin/tool"), modified = emptyList(), deleted = emptyList()))
        )
        assertTrue(prompt.contains("created: tool"))
        assertFalse(prompt.contains("/data/user/0"))
    }

    @Test
    fun promptDropsSensitiveNamesAndCapsEachGroup() {
        val created = listOf("/s/id_token.txt", "/s/API_KEY.env") + (1..AI_MAX_ROWS + 10).map { "/s/file$it" }
        val prompt = auditInterpretationPrompt(entry(IsolationAudit(created, emptyList(), emptyList())))
        assertFalse(prompt.contains("id_token"))
        assertFalse(prompt.contains("API_KEY"))
        assertEquals(AI_MAX_ROWS, prompt.lines().count { it.startsWith("created: ") })
    }

    @Test
    fun promptRedactsNetworkPeersToAProtocolAndPort() {
        val telemetry = listOf("network:tcp:0100007F:1F90:0A01020B:01BB:01:123", "process:sh -c secret-cmd")
        val prompt = auditInterpretationPrompt(entry(IsolationAudit(emptyList(), emptyList(), emptyList(), telemetry)))
        assertTrue(prompt.contains("network: tcp remote port 443"))
        assertTrue(prompt.contains("observed NETWORK events: 1"))
        assertTrue(prompt.contains("observed PROCESS events: 1"))
        assertFalse(prompt.contains("0A01020B"))
        assertFalse(prompt.contains("secret-cmd"))
    }

    @Test
    fun promptListsEventKindsInAlphabeticalOrder() {
        val telemetry = listOf("process:a", "network:tcp:0:0:0:01BB:0:0", "authority-attempt:su", "file-open:/x", "weird")
        val prompt = auditInterpretationPrompt(entry(IsolationAudit(emptyList(), emptyList(), emptyList(), telemetry)))
        val kinds = prompt.lines().filter { it.startsWith("observed ") }.map { it.removePrefix("observed ").substringBefore(' ') }
        assertEquals(listOf("AUTHORITY", "FILES", "NETWORK", "OTHER", "PROCESS"), kinds)
    }

    @Test
    fun anEntryWithoutAnAuditHasNothingToSend() {
        assertEquals("No audit exists.", auditInterpretationPrompt(entry(IsolationAudit(emptyList(), emptyList(), emptyList())).copy(savedAudit = null)))
    }

    @Test
    fun disclosureNamesWhatTheCodeActuallySends() {
        val text = aiDisclosureText()
        assertTrue(text.contains("file names (never the folders)"))
        assertTrue(text.contains("how many runtime events of each kind"))
        assertTrue(text.contains("remote port (never the address)"))
        assertTrue(text.contains("up to $AI_MAX_ROWS"))
        assertTrue(aiDisclosureText("Gemini").contains("to Gemini, using your API key"))
    }

    @Test
    fun redactedNetworkRowRejectsUnparseableRows() {
        assertNull(redactedNetworkRow("network:tcp:garbage"))
        assertEquals("udp remote port 53", redactedNetworkRow("network:udp:0100007F:0035:08080808:0035:07:1"))
    }

    @Test
    fun nextVisibleCountStepsAndStopsAtTheTotal() {
        assertEquals(80, nextVisibleCount(40, 200, 40))
        assertEquals(60, nextVisibleCount(40, 60, 40))
        assertEquals(40, nextVisibleCount(40, 40, 40))
        assertEquals(40, nextVisibleCount(40, 10, 40))
    }

    @Test
    fun formatBytesUsesBinaryUnits() {
        assertEquals("512 B", formatBytes(512))
        assertEquals("2.0 KiB", formatBytes(2048))
        assertEquals("1.5 KiB", formatBytes(1536))
        assertEquals("1024.0 KiB", formatBytes(1024L * 1024L - 1L))
        assertEquals("1.0 MiB", formatBytes(1024L * 1024L))
        assertEquals("2.5 GiB", formatBytes(5L * 1024L * 1024L * 1024L / 2L))
    }

    @Test
    fun theDefaultTimestampIsUtcCivilTime() {
        assertEquals("1970-01-01 00:00:00 UTC", utcTimestamp(0L))
        assertEquals("2000-02-29 12:34:56 UTC", utcTimestamp(951_827_696_000L))
        assertEquals("2026-11-21 00:00:00 UTC", utcTimestamp(1_795_219_200_000L))
        assertEquals("1969-12-31 23:59:59 UTC", utcTimestamp(-1_000L))
    }

    @Test
    fun aNewRunGetsANewAuditKey() {
        val first = entry(IsolationAudit(emptyList(), emptyList(), emptyList()))
        val second = first.copy(savedAudit = first.savedAudit!!.copy(timestampMillis = 2L))
        assertNotEquals(first.auditKey, second.auditKey)
    }

    @Test
    fun telemetryRowsAreSortedIntoTheirGroups() {
        assertEquals("AUTHORITY", telemetryKind("authority-attempt:su"))
        assertEquals("NETWORK", telemetryKind("network:tcp:x"))
        assertEquals("PROCESS", telemetryKind("root-process:id"))
        assertEquals("PROCESS", telemetryKind("process:ls"))
        assertEquals("FILES", telemetryKind("file-write:/x"))
        assertEquals("OTHER", telemetryKind("something else"))
    }
}
