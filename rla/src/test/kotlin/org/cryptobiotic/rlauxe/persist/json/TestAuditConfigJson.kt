package org.cryptobiotic.rlauxe.persist.json

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.sampling.MultiContestTestData
import org.cryptobiotic.rlauxe.workflow.AuditConfig
import org.cryptobiotic.rlauxe.workflow.AuditType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

import kotlin.test.assertNotNull

class TestAuditConfigJson {
    val filename = "/home/stormy/temp/persist/test/TestAuditConfig.json"

    @Test
    fun testRoundtrip() {
        val target = AuditConfig(AuditType.CARD_COMPARISON, hasStyles=true, seed = 12356667890L, fuzzPct = null)
        val json = target.publishJson()
        val roundtrip = json.import()
        assertNotNull(roundtrip)
        assertEquals(roundtrip, target)
        assertTrue(roundtrip.equals(target))
    }

    @Test
    fun testRoundtripIO() {
        val target = AuditConfig(AuditType.CARD_COMPARISON, hasStyles=true, seed = 12356667890L, fuzzPct = null)

        writeAuditConfigJsonFile(target, filename)
        val result = readAuditConfigJsonFile(filename)
        assertTrue(result is Ok)
        val roundtrip = result.unwrap()
        assertEquals(roundtrip, target)
        assertTrue(roundtrip.equals(target))
    }
}