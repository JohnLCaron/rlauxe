package org.cryptobiotic.rlauxe.persist.json

import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.betting.TausRates
import kotlin.io.path.createTempFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

import kotlin.test.assertNotNull

// data class AuditRoundConfig(
//    val simulation: SimulationControl,
//    val sampling: ContestSampleControl,
//    val clcaConfig: ClcaConfig?,
//    val pollingConfig: PollingConfig?,
//
//    val other: Map<String, String> = emptyMap(),    // soft parameters
//)

class TestAuditRoundConfigJson {

    @Test
    fun testRoundtrip() {
        testRoundtrips(Config.forClca(ElectionInfo.forTest(AuditType.CLCA, MvrSource.testClcaSimulated)).round)
        testRoundtrips(Config.forPolling(ElectionInfo.forTest(AuditType.POLLING, MvrSource.real)).round)
        testRoundtrips(Config.forClca(ElectionInfo.forTest(AuditType.ONEAUDIT, MvrSource.testPrivateMvrs)).round)

        testRoundtrips(
            // from(auditType: AuditType,
            //                 riskLimit:Double= .05,
            //                 nsimTrials:Int=10,
            //                 simFuzzPct: Double? = null,
            //                 fuzzMvrs:Double? = null,
            //                 contestSampleCutoff:Int? = if (auditType.isPolling()) 10000 else 2000,
            //                 apriori: TausRates = TausRates(emptyMap()),
            //                 mvrSource: MvrSource = if (auditType.isClca())
            //                      MvrSource.testClcaSimulated else MvrSource.testPrivateMvrs
            Config.from(
                AuditType.CLCA, riskLimit=.03, nsimTrials=42, fuzzMvrs=.111, contestSampleCutoff=10000,
                apriori = TausRates(mapOf("oth-los" to .001, "oth-win" to .002))).round

        )
        //        fun forPolling(electionInfo: ElectionInfo,
        //                       riskLimit:Double= .05,
        //                       nsimTrials:Int=10,
        //                       simFuzzPct: Double? = null,
        //                       contestSampleCutoff:Int? = 10000,
        //        )
        testRoundtrips(
            Config.forPolling(ElectionInfo.forTest(AuditType.POLLING, MvrSource.testPrivateMvrs),
                riskLimit=.03, simFuzzPct=.111,
                contestSampleCutoff=10000).round
        )
        testRoundtrips(
            AuditRoundConfig(
                // data class SimulationControl(
                //    val nsimTrials: Int = 20, // number of simulation estimation trials
                //    val estPercentile: List<Int> = listOf(50, 80), // use this percentile of the distribution of estimated sample sizes
                //    val simFuzzPct: Double? = null, // for estimation fuzzing
                //    val simulationStrategy: SimulationStrategy = SimulationStrategy.optimistic
                //)
                SimulationControl(nsimTrials=42, estPercentile=listOf(11, 84, 96, 99), simFuzzPct=.0042, SimulationStrategy.regular),
                // data class ContestSampleControl(
                //    //// checkContestsCorrectlyFormed: preAuditStatus
                //    val minRecountMargin: Double = 0.005, // do not audit contests less than this recount margin
                //    val minMargin: Double = 0.0, // do not audit contests less than this margin TODO really it should be noerror for clca?
                //
                //    //// consistentSampling: contestRound.status, depends on having estimation
                //    val maxSamplePct: Double = 0.0, // do not audit contests with (estimated nmvrs / Npop) greater than this
                //    val contestSampleCutoff: Int? = 1000, // max number of cvrs for any one contest, set to null to use all
                //    val auditSampleCutoff: Int? = 10000, // max number of cvrs in the audit, set to null to use all
                //
                //    // soft parameters
                //    val other: Map<String, String> = emptyMap(),    // soft parameters to ease migration
                //    // val removeMaxContests: Int? = null, // remove top n estimated nmvrs contests, for plotting CaseStudiesRemoveNmax
                //)
                ContestSampleControl(minRecountMargin=0.0, minMargin=.011, maxSamplePct=.11, contestSampleCutoff=1111, auditSampleCutoff=3333,
                    other = mapOf("who" to "wants", "to" to "know?")),

                //
                //enum class ClcaStrategyType { generalAdaptive }
                //data class ClcaConfig(
                //    val strategy: ClcaStrategyType = ClcaStrategyType.generalAdaptive,
                //    val fuzzMvrs: Double? = null, // used by PersistedMvrManagerTest to fuzz mvrs when persistedWorkflowMode=testSimulate
                //    val d: Int = 100,  // shrinkTrunc weight for error rates
                //    val maxLoss: Double = 1.0 / 1.03905,  // max loss on any one bet, 0 < maxLoss < 1 //  = .9624 from Corla gamma = 1.03905;
                //                                          // SHANGRLA has gamma = 1.1 which is ~ 1/.9
                //    val apriori: TausRates = TausRates(emptyMap()),
                //)
                ClcaConfig(ClcaStrategyType.generalAdaptive, fuzzMvrs=.001, d = 1, maxLoss=.90),
                // data class PollingConfig(
                //    val d: Int = 100,  // shrinkTrunc weight
                //)
                PollingConfig(d=25)
            )
        )
    }

    fun testRoundtrips(target: AuditRoundConfig) {
        testRoundtrip(target)
        testRoundtripIO(target)
        testFullRoundtripIO(target)
    }

    fun testRoundtrip(target: AuditRoundConfig) {
        val json = target.publishJson()
        val roundtrip = json.import()
        assertNotNull(roundtrip)
        assertEquals(roundtrip, target)
        assertTrue(roundtrip.equals(target))
    }

    fun testRoundtripIO(target: AuditRoundConfig) {
        val scratchFile = createTempFile().toFile()
        writeAuditRoundConfigJsonFile(target, scratchFile.toString())

        val result = readAuditRoundConfigJsonFile(scratchFile.toString())
        assertTrue(result .isOk)
        val roundtrip = result.unwrap()
        assertEquals(roundtrip, target)
        assertEquals(target, roundtrip)

        scratchFile.delete()
    }

    fun testFullRoundtripIO(target1: AuditRoundConfig) {
        val scratchFile1 = createTempFile().toFile()
        writeAuditRoundConfigJsonFile(target1, scratchFile1.toString())

        val auditRoundConfig = readAuditRoundConfigUnwrapped(scratchFile1.toString())
        assertNotNull(auditRoundConfig)

        val target2 = auditRoundConfig
        val scratchFile2 = createTempFile().toFile()
        writeAuditRoundConfigJsonFile(target2, scratchFile2.toString())

        val auditRoundConfig2 = readAuditRoundConfigUnwrapped(scratchFile2.toString())
        assertNotNull(auditRoundConfig2)
        assertEquals(target1, auditRoundConfig2)

        scratchFile1.delete()
        scratchFile2.delete()
    }
}