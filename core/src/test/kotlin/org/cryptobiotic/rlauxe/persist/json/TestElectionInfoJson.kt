package org.cryptobiotic.rlauxe.persist.json

import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.audit.*
import kotlin.io.path.createTempFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

import kotlin.test.assertNotNull

// data class ElectionInfo(
//    val auditType: AuditType,
//    val ncards: Int,
//    val ncontests: Int,
//    val cvrsContainUndervotes: Boolean,
//    val poolsHaveOneCardStyle: Boolean?,
// )
class TestElectionInfoJson {

    @Test
    fun testRoundtrip() {
        testRoundtrips(ElectionInfo("CLCA-InfoJson", AuditType.CLCA, 42, 99, true,))
        testRoundtrips(ElectionInfo("POLLING-InfoJson", AuditType.POLLING, totalCardCount=412, contestCount=63, cvrsContainUndervotes=false,
            pollingMode = PollingMode.withBatches))
        testRoundtrips(ElectionInfo("ONEAUDIT-InfoJson", AuditType.ONEAUDIT, cvrsContainUndervotes = true, totalCardCount=42, contestCount=9339))
        testRoundtrips(ElectionInfo("CLCA-InfoJsonNostyle", AuditType.CLCA, totalCardCount=42, contestCount=9, false, ))
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