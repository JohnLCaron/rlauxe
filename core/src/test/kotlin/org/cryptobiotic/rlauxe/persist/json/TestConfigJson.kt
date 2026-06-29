package org.cryptobiotic.rlauxe.persist.json

import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.betting.TausRates
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

import kotlin.test.assertNotNull

class TestConfigJson {

    @Test
    fun testRoundtrip() {
        testRoundtrips(Config.from(AuditType.CLCA).replaceSeed(12356667890L))
        testRoundtrips(Config.from(AuditType.POLLING))
        testRoundtrips(Config.from(AuditType.ONEAUDIT))

        testRoundtrips(
            Config.from(
                AuditType.CLCA,
                riskLimit = .03,
                nsimTrials = 42,
                simFuzzPct = .111,
                contestSampleCutoff = 10000
            )
        )

        testRoundtrips(
            Config.from(
                AuditType.POLLING, riskLimit = .03, nsimTrials = 42, simFuzzPct = .111, contestSampleCutoff = 10000
            )
        )
        testRoundtrips(
            Config.from(
                AuditType.ONEAUDIT, riskLimit = .03, nsimTrials = 42, simFuzzPct = .111,
                contestSampleCutoff = 10000,
            )
        )
    }

    @Test
    fun testNoDefaults() {
            //     val election: ElectionInfo,
            //    val creation: AuditCreationConfig,
            //    val round: AuditRoundConfig,
            //    val version: String = "0.8.4",
            val org = Config(
                // data class ElectionInfo(
                //    val electionName: String,
                //    val auditType: AuditType,
                //    val totalCardCount: Int,    // total cards in the election
                //    val contestCount: Int,
                //
                //    val cvrsContainUndervotes: Boolean = true, // TODO implement cvrsContainUndervotes = false
                //    // val poolsHaveOneCardStyle: Boolean? = null, // TODO dont seem to be using this; instead its set indididually on pool/batch
                //    val pollingMode: PollingMode? = null, // TODO also needed for cvrsContainUndervotes = false ? Maybe "BatchesMode ??
                //
                //    val mvrSource: MvrSource =
                //        if (auditType.isClca()) MvrSource.testClcaSimulated else MvrSource.testPrivateMvrs,
                //
                //    val other: Map<String, String> = emptyMap(),    // soft parameters to ease migration
                //)
                ElectionInfo("betty", AuditType.ONEAUDIT, 76543, 99, true, PollingMode.withBatches, MvrSource.real)
                    .addMetadata("self", "other"),
                // AuditCreationConfig(
                //    val auditType: AuditType, // must agree with ElectionInfo
                //    val riskLimit: Double = 0.05, // TODO can we allow this to relax ??
                //
                //    val seed: Long = secureRandom.nextLong(),
                //    val riskMeasuringSampleLimit: Int? = null, // the number of samples we are willing to audit; this turns the audit into a "risk measuring" audit
                //    val other: Map<String, String> = emptyMap(),
                AuditCreationConfig(AuditType.ONEAUDIT, riskLimit = 3.14159, seed=87346239482746L, riskMeasuringSampleLimit=1111, other=mapOf("truth" to "beauty", "alpha" to "omega")),
                // data class AuditRoundConfig(
                //    val simulation: SimulationControl,
                //    val sampling: ContestSampleControl,
                //    val clcaConfig: ClcaConfig?,
                //    val pollingConfig: PollingConfig?,
                //
                //    val other: Map<String, String> = emptyMap(),    // soft parameters
                //)
                AuditRoundConfig(
                    // class SimulationControl(
                    //    val nsimTrials: Int = 20, // number of simulation estimation trials
                    //    val estPercentile: List<Int> = listOf(50, 80), // use this percentile of the distribution of estimated sample sizes
                    //    val simFuzzPct: Double? = null, // for estimation fuzzing
                    //    val simulationStrategy: SimulationStrategy = SimulationStrategy.optimistic
                    //)
                    SimulationControl(7, listOf(77,90,99), .007, SimulationStrategy.regular),
                    // ContestSampleControl(
                    //    //// checkContestsCorrectlyFormed: preAuditStatus
                    //    val minRecountMargin: Double = 0.005, // do not audit contests less than this recount margin
                    //    val minMargin: Double = 0.0, // do not audit contests less than this margin TODO really it should be noerror for clca?
                    //    val minSize: Int? = null, // do not audit contests with Nc less than this
                    //
                    //    //// consistentSampling: contestRound.status, depends on having estimation
                    //    val maxSamplePct: Double = 0.0, // do not audit contests with (estimated nmvrs / Npop) greater than this
                    //    val contestSampleCutoff: Int? = 1000, // max number of cvrs for any one contest, set to null to use all
                    //    val auditSampleCutoff: Int? = 10000, // max number of cvrs in the audit, set to null to use all
                    //
                    //    // soft parameters
                    //    val other: Map<String, String> = emptyMap(),    // soft parameters to ease migration
                    //
                    //    val sampling: Sampling = Sampling.consistent
                    //)
                    ContestSampleControl(.007, .01, 11, .44, 1111, 11111,
                        other=mapOf("a" to "z", "1" to "9", "eich" to "I"), Sampling.uniform),
                    // ClcaConfig(
                    //    val strategy: ClcaStrategyType = ClcaStrategyType.generalAdaptive,
                    //    val fuzzMvrs: Double? = null, // used by PersistedMvrManagerTest to fuzz mvrs when persistedWorkflowMode=testSimulate
                    //    val d: Int = 100,  // shrinkTrunc weight for error rates
                    //    val maxLoss: Double = 1.0 / 1.03905,  // max loss on any one bet, 0 < maxLoss < 1 //  = .9624 from Corla gamma = 1.03905;
                    //                                          // SHANGRLA has gamma = 1.1 which gives .9 ~ 1/1.1
                    //    val apriori: TausRates = TausRates(emptyMap()),
                    //)
                    ClcaConfig(ClcaStrategyType.generalAdaptive, .0123, 1000, .90, TausRates(mapOf("los-win" to .01223))),
                    // PollingConfig(
                    //    val d: Int = 100,  // shrinkTrunc weight
                    //)
                    PollingConfig(2323),
                    other=mapOf("a" to "z", "1" to "9")
                ))
        val round = roundtripIO(org)
        println(org)
        println(round)
        assertEquals(org, round)
    }

    fun testRoundtrips(target: Config) {
        testRoundtrip(target)
        testRoundtripIO(target)
    }

    fun testRoundtrip(target: Config) {
        val roundtripElection = testElectionRoundtrip(target)
        val roundtripCreation = testCreationRoundtrip(target)
        val roundtripRound = testRoundRoundtrip(target)
        val roundtrip = Config(roundtripElection, roundtripCreation, roundtripRound) // hmm no version
        assertNotNull(roundtrip)
        assertEquals(roundtrip, target)
        assertTrue(roundtrip.equals(target))
        println("testRoundtrip for ${target.election.electionName} ${target.auditType}")
    }

    fun testElectionRoundtrip(target: Config): ElectionInfo {
        val json = target.election.publishJson()
        val roundtrip = json.import()
        assertNotNull(roundtrip)
        assertEquals(roundtrip, target.election)
        assertTrue(roundtrip.equals(target.election))
        return roundtrip
    }

    fun testCreationRoundtrip(target: Config): AuditCreationConfig {
        val json = target.creation.publishJson()
        val roundtrip = json.import()
        assertNotNull(roundtrip)
        assertEquals(roundtrip, target.creation)
        assertTrue(roundtrip.equals(target.creation))
        return roundtrip
    }

    fun testRoundRoundtrip(target: Config): AuditRoundConfig {
        val json = target.round.publishJson()
        val roundtrip = json.import()
        assertNotNull(roundtrip)
        assertEquals(roundtrip, target.round)
        assertTrue(roundtrip.equals(target.round))
        return roundtrip
    }

    fun testRoundtripIO(target: Config) {
        val scratchFile = kotlin.io.path.createTempFile().toFile()
        writeElectionInfoJsonFile(target.election, "$scratchFile.election.json")
        writeAuditCreationConfigJsonFile(target.creation, "$scratchFile.creation.json")
        writeAuditRoundConfigJsonFile(target.round, "$scratchFile.round.json")

        val electionrt = readElectionInfoUnwrapped("$scratchFile.election.json")
        assertNotNull(electionrt)
        val creationrt = readAuditCreationConfigUnwrapped("$scratchFile.creation.json")
        assertNotNull(creationrt)
        val roundrt = readAuditRoundConfigUnwrapped("$scratchFile.round.json")
        assertNotNull(roundrt)

        val roundtrip = Config(electionrt, creationrt, roundrt) // hmm no version

        assertEquals(roundtrip, target)
        assertTrue(roundtrip.equals(target))

        scratchFile.delete()
        println("testRoundtripIO for ${target.election.electionName} ${target.auditType}")
    }

    fun roundtripIO(target: Config): Config {
        val scratchFile = kotlin.io.path.createTempFile().toFile()
        writeElectionInfoJsonFile(target.election, "$scratchFile.election.json")
        writeAuditCreationConfigJsonFile(target.creation, "$scratchFile.creation.json")
        writeAuditRoundConfigJsonFile(target.round, "$scratchFile.round.json")

        val electionrt = readElectionInfoUnwrapped("$scratchFile.election.json")
        assertNotNull(electionrt)
        val creationrt = readAuditCreationConfigUnwrapped("$scratchFile.creation.json")
        assertNotNull(creationrt)
        val roundrt = readAuditRoundConfigUnwrapped("$scratchFile.round.json")
        assertNotNull(roundrt)

        val roundtrip = Config(electionrt, creationrt, roundrt) // hmm no version

        assertEquals(roundtrip, target)
        assertTrue(roundtrip.equals(target))

        scratchFile.delete()
        println("testRoundtripIO for ${target.election.electionName} ${target.auditType}")
        return roundtrip
    }
}