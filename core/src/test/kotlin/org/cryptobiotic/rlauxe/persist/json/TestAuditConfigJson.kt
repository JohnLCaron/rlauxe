package org.cryptobiotic.rlauxe.persist.json

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.ClcaErrorRates
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

import kotlin.test.assertNotNull

class TestAuditConfigJson {

    @Test
    fun testRoundtrip() {
        testRoundtrips(AuditConfig(AuditType.CLCA, hasStyles=true, seed = 12356667890L))
        testRoundtrips(AuditConfig(AuditType.POLLING, hasStyles=true, seed = 12356667890L))
        testRoundtrips(AuditConfig(AuditType.ONEAUDIT, hasStyles=true, seed = 12356667890L))

        testRoundtrips(
            AuditConfig(
                AuditType.CLCA, hasStyles=true, seed = 12356667890L, riskLimit=.03, nsimEst=42, quantile=.50,
            contestSampleCutoff=10000,  version=2.0,
            clcaConfig= ClcaConfig(ClcaStrategyType.fuzzPct, simFuzzPct=.111, ClcaErrorRates(.01, .02, .03, .04), d = 99)
        )
        )
        testRoundtrips(
            AuditConfig(
                AuditType.POLLING, hasStyles=false, seed = 12356667890L, riskLimit=.03, nsimEst=42, quantile=.50,
                contestSampleCutoff=10000,  version=2.0,
            pollingConfig= PollingConfig(simFuzzPct=.111, d = 99)
        )
        )
        testRoundtrips(
            AuditConfig(
                AuditType.ONEAUDIT, hasStyles=false, seed = 12356667890L, riskLimit=.03, nsimEst=42, quantile=.50,
                contestSampleCutoff=10000,  version=2.0,
            oaConfig= OneAuditConfig(OneAuditStrategyType.bet99, simFuzzPct=.111, d = 99)
        )
        )
    }

    fun testRoundtrips(target: AuditConfig) {
        testRoundtrip(target)
        testRoundtripIO(target)
    }

    fun testRoundtrip(target: AuditConfig) {
        val json = target.publishJson()
        val roundtrip = json.import()
        assertNotNull(roundtrip)
        assertEquals(roundtrip, target)
        assertTrue(roundtrip.equals(target))
    }

    fun testRoundtripIO(target: AuditConfig) {
        val scratchFile = kotlin.io.path.createTempFile().toFile()

        val target = AuditConfig(AuditType.CLCA, hasStyles=true, seed = 12356667890L)
        writeAuditConfigJsonFile(target, scratchFile.toString())

        val result = readAuditConfigJsonFile(scratchFile.toString())
        assertTrue(result is Ok)
        val roundtrip = result.unwrap()
        assertEquals(roundtrip, target)
        assertEquals(roundtrip, target)

        scratchFile.delete()
    }
}