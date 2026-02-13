package org.cryptobiotic.rlauxe.persist.json

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.workflow.PersistedWorkflowMode
import kotlin.io.path.createTempFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

import kotlin.test.assertNotNull

// data class ElectionInfo(
//    val auditType: AuditType,
//    val ncards: Int,
//    val ncontests: Int,
//    val cvrsContainUndervotes: Boolean = true,
//    val persistedWorkflowMode: PersistedWorkflowMode =  PersistedWorkflowMode.testSimulated,
//)
class TestElectionInfoJson {

    @Test
    fun testRoundtrip() {
        testRoundtrips(ElectionInfo(AuditType.CLCA, ncards=42, ncontests=99))
        testRoundtrips(ElectionInfo(AuditType.POLLING, ncards=42, ncontests=99, cvrsContainUndervotes=false))
        testRoundtrips(ElectionInfo(AuditType.ONEAUDIT, ncards=42, ncontests=99, persistedWorkflowMode = PersistedWorkflowMode.testPrivateMvrs))
        testRoundtrips(ElectionInfo(AuditType.CLCA, ncards=42, ncontests=99, false, PersistedWorkflowMode.real))
    }

    fun testRoundtrips(target: ElectionInfo) {
        testRoundtrip(target)
        testRoundtripIO(target)
    }

    fun testRoundtrip(target: ElectionInfo) {
        val json = target.publishJson()
        val roundtrip = json.import()
        assertNotNull(roundtrip)
        assertEquals(roundtrip, target)
        assertTrue(roundtrip.equals(target))
    }

    fun testRoundtripIO(target: ElectionInfo) {
        val scratchFile = createTempFile().toFile()
        writeElectionInfoJsonFile(target, scratchFile.toString())

        val result = readElectionInfoJsonFile(scratchFile.toString())
        assertTrue(result .isOk)
        val roundtrip = result.unwrap()
        assertEquals(roundtrip, target)
        assertEquals(roundtrip, target)

        scratchFile.delete()
    }
}