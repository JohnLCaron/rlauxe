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

    @Test
    fun testRoundtrip() {
        testRoundtrips(AuditConfig(AuditType.CLCA, seed = 12356667890L))
        testRoundtrips(AuditConfig(AuditType.POLLING, seed = 12356667890L))
        testRoundtrips(AuditConfig(AuditType.ONEAUDIT, seed = 12356667890L))

        testRoundtrips(
            AuditConfig(
                AuditType.CLCA, seed = 12356667890L, riskLimit=.03, nsimEst=42, quantile=.50, simFuzzPct=.111, contestSampleCutoff=10000,  version=2.0,
                clcaConfig= ClcaConfig(fuzzMvrs = 0.42, apriori = TausRates(mapOf("oth-los" to .001, "oth-win" to .002)), d = 99)
            )
        )
        testRoundtrips(
            AuditConfig(
                AuditType.POLLING, seed = 12356667890L, riskLimit=.03, nsimEst=42, quantile=.50, simFuzzPct=.111,
                contestSampleCutoff=10000,  version=2.0,
            pollingConfig= PollingConfig(d = 99)
        )
        )
        testRoundtrips(
            AuditConfig(
                AuditType.ONEAUDIT, seed = 12356667890L, riskLimit=.03, nsimEst=42, quantile=.50, simFuzzPct=.111,
                contestSampleCutoff=10000,  version=2.0,
            )
        )
    }

    fun testRoundtrips(target: AuditConfig) {
        testRoundtrip(target)
        testRoundtripIO(target)
    }

    fun testRoundtrip(config: AuditConfig) {
        val target = AuditCreationConfig.fromAuditConfig(config)
        val json = target.publishJson()
        val roundtrip = json.import()
        assertNotNull(roundtrip)
        assertEquals(roundtrip, target)
        assertTrue(roundtrip.equals(target))
    }

    fun testRoundtripIO(config: AuditConfig) {
        val target = AuditCreationConfig.fromAuditConfig(config)
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