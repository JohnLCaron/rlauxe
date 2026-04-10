package org.cryptobiotic.rlauxe.persist.json

import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.betting.TausRates
import kotlin.io.path.createTempFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

import kotlin.test.assertNotNull

class TestAuditCreationConfigJson {
// data class AuditCreationConfig(
//    val auditType: AuditType, // must agree with ElectionInfo
//    val riskLimit: Double = 0.05,
//
//    val seed: Long = secureRandom.nextLong(),
//    val riskMeasuringSampleLimit: Int? = null, // the number of samples we are willing to audit; this turns the audit into a "risk measuring" audit
//    val other: Map<String, Any> = emptyMap(),    // soft parameters
//)

    @Test
    fun testRoundtrip() {
        testRoundtrips(AuditCreationConfig(AuditType.CLCA, seed = 12356667890L))
        testRoundtrips(AuditCreationConfig(AuditType.POLLING, seed = 12356667890L))
        testRoundtrips(AuditCreationConfig(AuditType.ONEAUDIT, seed = 12356667890L))

        testRoundtrips(
            AuditCreationConfig(
                AuditType.CLCA, seed = 12356667890L, riskLimit=.03, riskMeasuringSampleLimit=42, other=mapOf("who" to "what"))
        )
        testRoundtrips(
            AuditCreationConfig(
                AuditType.POLLING, seed = 12356667890L, riskLimit=.03, riskMeasuringSampleLimit=4222, other=mapOf("who" to "2.0"))
        )
        testRoundtrips(
            AuditCreationConfig(
                AuditType.ONEAUDIT, seed = 12356667890L, riskLimit=.03, riskMeasuringSampleLimit=4222, other=mapOf("who" to "22"))
        )
    }

    fun testRoundtrips(target: AuditCreationConfig) {
        testRoundtrip(target)
        testRoundtripIO(target)
    }

    fun testRoundtrip(target: AuditCreationConfig) {
        val json = target.publishJson()
        val roundtrip = json.import()
        assertNotNull(roundtrip)
        assertEquals(roundtrip, target)
        assertTrue(roundtrip.equals(target))
    }

    fun testRoundtripIO(target: AuditCreationConfig) {
        val scratchFile = createTempFile().toFile()
        writeAuditCreationConfigJsonFile(target, scratchFile.toString())

        val result = readAuditCreationConfigJsonFile(scratchFile.toString())
        assertTrue(result .isOk)
        val roundtrip = result.unwrap()
        assertEquals(roundtrip, target)
        assertEquals(target, roundtrip)

        scratchFile.delete()
    }
}