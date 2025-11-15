package org.cryptobiotic.rlauxe.persist.json

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.dhondt.DhondtCandidate
import org.cryptobiotic.rlauxe.dhondt.makeProtoContest
import org.cryptobiotic.rlauxe.estimate.MultiContestTestData
import org.cryptobiotic.rlauxe.estimate.makeFuzzedCvrsFrom
import org.cryptobiotic.rlauxe.raire.RaireContestUnderAudit
import org.cryptobiotic.rlauxe.raire.simulateRaireTestContest
import org.cryptobiotic.rlauxe.workflow.*
import kotlin.io.path.createTempFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

import kotlin.test.assertNotNull

class TestAuditRoundJson {

    @Test
    fun testRoundtrip() {
        val testData = MultiContestTestData(11, 4, 50000)
        val contestsUAs: List<ContestUnderAudit> = testData.contests. map { ContestUnderAudit(it, isClca=false, hasStyle=false).addStandardAssertions()}
        val contestRounds = contestsUAs.map{ contest ->
            val cr = ContestRound(contest, 1,)
            //     var actualMvrs = 0 // Actual number of ballots with this contest contained in this round's sample.
            //    var actualNewMvrs = 0 // Actual number of new ballots with this contest contained in this round's sample.
            //
            //    var estNewSamples = 0 // Estimate of the new sample size required to confirm the contest
            //    var estSampleSize = 0 // number of total samples estimated needed, consistentSampling
            //    var estSampleSizeNoStyles = 0 // number of total samples estimated needed, uniformSampling
            //    var auditorWantNewMvrs: Int = -1
            //
            //    var done = false
            //    var included = true
            //    var status = TestH0Status.InProgress
            cr.actualMvrs = 420
            cr.actualNewMvrs = 42
            cr.estNewSamples = 66
            cr.estSampleSize = 77
            cr.estSampleSizeNoStyles = 88
            cr.auditorWantNewMvrs = 88
            cr.done = true
            cr.included = false
            cr.status = TestH0Status.FailMaxSamplesAllowed

            cr
        }

        val target = AuditRound(
            2,
            contestRounds,
            true,
            false,
            samplePrns = listOf(1,2,3),
            nmvrs = 42,
        )
        val json = target.publishJson()
        val roundtrip = json.import(contestsUAs, target.samplePrns)
        assertNotNull(roundtrip)
        check(target, roundtrip)
        assertTrue(roundtrip.equals(target))
        assertEquals(roundtrip, target)
    }

    @Test
    fun testRoundtripIO() {

        val testData = MultiContestTestData(11, 4, 50000)
        val contestsUAs: List<ContestUnderAudit> = testData.contests. map { ContestUnderAudit(it, isClca=false, hasStyle=false).addStandardAssertions()}
        val contestRounds = contestsUAs.map{ contest ->
            val cr = ContestRound(contest, 1)
            cr.actualMvrs = 420
            cr.actualNewMvrs = 42
            cr.estNewSamples = 66
            cr.estSampleSize = 77
            cr.estSampleSizeNoStyles = 88
            cr.auditorWantNewMvrs = 88
            cr.done = true
            cr.included = false
            cr.status = TestH0Status.FailMaxSamplesAllowed

            cr
        }
        val target = AuditRound(
            1,
            contestRounds,
            false,
            false,
            samplePrns = listOf(1,2,3, 21),
            nmvrs = 129182,
            auditorWantNewMvrs = 2223,
            )

        val scratchFile = createTempFile().toFile()

        writeAuditRoundJsonFile(target, scratchFile.toString())
        val result = readAuditRoundJsonFile(scratchFile.toString(), contestsUAs, target.samplePrns)
        assertTrue(result is Ok)
        val roundtrip = result.unwrap()
        check(target, roundtrip)
        assertTrue(roundtrip.equals(target))
        assertEquals(roundtrip, target)

        scratchFile.delete()
    }

    @Test
    fun testRoundtripWithRounds() {
        val fuzzMvrs = .01
        val config = AuditConfig(
            AuditType.CLCA, hasStyle = true, seed = 12356667890L, nsimEst = 10,
        )
        val N = 5000
        val testData = MultiContestTestData(11, 4, N, marginRange = 0.03..0.05)

        val contests: List<Contest> = testData.contests
        println("Start testComparisonWorkflow $testData")
        contests.forEach { println("  $it") }
        println()

        // Synthetic cvrs for testing reflecting the exact contest votes, plus undervotes and phantoms.
        val testCvrs = testData.makeCvrsFromContests()
        val testMvrs = if (fuzzMvrs == 0.0) testCvrs
            // fuzzPct of the Mvrs have their votes randomly changed ("fuzzed")
            else makeFuzzedCvrsFrom(contests, testCvrs, fuzzMvrs)

        var clcaWorkflow = WorkflowTesterClca(config, contests, emptyList(),
            MvrManagerClcaForTesting(testCvrs, testMvrs, config.seed))
        val lastRound = runTestAuditToCompletion("testComparisonWorkflow", clcaWorkflow, quiet = true)
        assertNotNull(lastRound)

        val target = AuditRound(
            1,
            lastRound.contestRounds,
            false,
            false,
            samplePrns = lastRound.samplePrns,
            nmvrs = 33333,
            auditorWantNewMvrs = 33334533,
        )
        val json = target.publishJson()
        val roundtrip = json.import(clcaWorkflow.contestsUA(), target.samplePrns)
        assertNotNull(roundtrip)
        check(target, roundtrip)
        assertEquals(roundtrip, target)

        val scratchFile = createTempFile().toFile()

        writeAuditRoundJsonFile(target, scratchFile.toString())
        val result = readAuditRoundJsonFile(scratchFile.toString(), clcaWorkflow.contestsUA(), target.samplePrns)
        if (result is Err) println("result = $result")
        assertTrue(result is Ok)
        val roundtripIO = result.unwrap()
        assertTrue(roundtripIO.equals(target))
        assertEquals(roundtripIO, target)

        scratchFile.delete()
    }

    @Test
    fun testRoundtripWithRaire() {
        val fuzzMvrs = .01
        val config = AuditConfig(
            AuditType.CLCA, hasStyle = true, seed = 12356667890L, nsimEst = 10,
        )

        val N = 5000
        val testData = MultiContestTestData(11, 4, N, marginRange = 0.03..0.05)

        val contests: List<Contest> = testData.contests
        println("Start testComparisonWorkflow $testData")
        contests.forEach { println("  $it") }

        val (rcontest: RaireContestUnderAudit, rcvrs: List<Cvr>) = simulateRaireTestContest(N/2, contestId=111, ncands=5, minMargin=.04, quiet = true, hasStyle=config.hasStyle)
        println(rcontest)
        println()

        val testCvrs = testData.makeCvrsFromContests() + rcvrs

        val testMvrs = if (fuzzMvrs == 0.0) testCvrs
            else makeFuzzedCvrsFrom(contests, testCvrs, fuzzMvrs)

        var clcaWorkflow = WorkflowTesterClca(config, contests, listOf(rcontest),
            MvrManagerClcaForTesting(testCvrs, testMvrs, config.seed))
        val nextRound = clcaWorkflow.startNewRound()
        clcaWorkflow.runAuditRound(nextRound)

        val target = AuditRound(
            1,
            nextRound.contestRounds,
            false,
            false,
            samplePrns = nextRound.samplePrns,
            nmvrs = 33333,
            auditorWantNewMvrs = 33733,
        )
        val json = target.publishJson()
        val roundtrip: AuditRound = json.import(clcaWorkflow.contestsUA(), target.samplePrns)
        assertNotNull(roundtrip)
        check(target, roundtrip)
        assertEquals(roundtrip, target)

        val scratchFile = createTempFile().toFile()

        writeAuditRoundJsonFile(target, scratchFile.toString())
        val result = readAuditRoundJsonFile(scratchFile.toString(), clcaWorkflow.contestsUA(), target.samplePrns)
        assertTrue(result is Ok)
        val roundtripIO = result.unwrap()
        assertTrue(roundtripIO.equals(target))
        assertEquals(roundtripIO, target)

        scratchFile.delete()
    }

    @Test
    fun testRoundtripWithDHondt() {
        val parties = listOf(DhondtCandidate(1, 10000), DhondtCandidate(2, 6000), DhondtCandidate(3, 1500))
        val dcontest = makeProtoContest("contest1", 1, parties, 8, 0, 0.01)
        val info = dcontest.createInfo()
        val contestd = dcontest.createContest(dcontest.validVotes, dcontest.validVotes)
        val contests = listOf(contestd)

        val fuzzMvrs = .01
        val config = AuditConfig(
            AuditType.CLCA, hasStyle = true, seed = 12356667890L, nsimEst = 10,
        )

        val testCvrs = contestd.createSimulatedCvrs()
        val testMvrs = if (fuzzMvrs == 0.0) testCvrs
            else makeFuzzedCvrsFrom(contests, testCvrs, fuzzMvrs)

        val clcaWorkflow = WorkflowTesterClca(config, contests, emptyList(),
            MvrManagerClcaForTesting(testCvrs, testMvrs, config.seed))
        val nextRound = clcaWorkflow.startNewRound()
        clcaWorkflow.runAuditRound(nextRound)

        val target = AuditRound(
            1,
            nextRound.contestRounds,
            false,
            false,
            samplePrns = nextRound.samplePrns,
            nmvrs = testCvrs.size,
            auditorWantNewMvrs = 333,
        )
        val json = target.publishJson()
        val roundtrip: AuditRound = json.import(clcaWorkflow.contestsUA(), target.samplePrns)
        assertNotNull(roundtrip)
        check(target, roundtrip)
        assertEquals(roundtrip, target)

        val scratchFile = createTempFile().toFile()

        writeAuditRoundJsonFile(target, scratchFile.toString())
        val result = readAuditRoundJsonFile(scratchFile.toString(), clcaWorkflow.contestsUA(), target.samplePrns)
        assertTrue(result is Ok)
        val roundtripIO = result.unwrap()
        assertTrue(roundtripIO.equals(target))
        assertEquals(roundtripIO, target)

        scratchFile.delete()
    }
}

fun check(s1: AuditRound, s2: AuditRound) {
    assertEquals(s1.roundIdx, s2.roundIdx)
    assertEquals(s1.auditWasDone, s2.auditWasDone)
    assertEquals(s1.auditIsComplete, s2.auditIsComplete)
    assertEquals(s1.samplePrns, s2.samplePrns)
    assertEquals(s1.nmvrs, s2.nmvrs)
    assertEquals(s1.newmvrs, s2.newmvrs)
    assertEquals(s1.auditorWantNewMvrs, s2.auditorWantNewMvrs)

    assertEquals(s1.contestRounds.size, s2.contestRounds.size)
    s1.contestRounds.forEachIndexed { idx, c1 ->
        val c2 = s2.contestRounds[idx]
        assertEquals(c1.contestUA.contest, c2.contestUA.contest, "contest ${c1.contestUA.contest.show()}\n not ${c2.contestUA.contest.show()}")
        c1.contestUA.clcaAssertions.forEachIndexed { asnIdx, a1 ->
            val a2 = c2.contestUA.clcaAssertions[asnIdx]
            assertEquals(a1.cassorter, a2.cassorter, "clcaAssertion.cassorter ${a1.cassorter}\n not ${a2.cassorter}")
            assertEquals(a1, a2, "clcaAssertion ${a1}\n not ${a2}")
        }
        val ok = c1.equivalent(c2)
        assertEquals(c1, c2, "contestUA $c1\n not equal $c2")
    }
}
