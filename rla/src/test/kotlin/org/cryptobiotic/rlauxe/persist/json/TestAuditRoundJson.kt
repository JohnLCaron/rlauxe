package org.cryptobiotic.rlauxe.persist.json

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.estimate.MultiContestTestData
import org.cryptobiotic.rlauxe.estimate.makeFuzzedCvrsFrom
import org.cryptobiotic.rlauxe.raire.RaireContestUnderAudit
import org.cryptobiotic.rlauxe.raire.makeRaireContest
import org.cryptobiotic.rlauxe.workflow.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

import kotlin.test.assertNotNull

class TestAuditRoundJson {

    @Test
    fun testRoundtrip() {
        val testData = MultiContestTestData(11, 4, 50000)
        val contestsUAs: List<ContestUnderAudit> = testData.contests. map { ContestUnderAudit(it, false, false)}
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
            sampledIndices = listOf(1,2,3),
            nmvrs = 42,
        )
        val json = target.publishJson()
        val roundtrip = json.import(contestsUAs, target.sampledIndices)
        assertNotNull(roundtrip)
        check(target, roundtrip)
        assertTrue(roundtrip.equals(target))
        assertEquals(roundtrip, target)
    }

    @Test
    fun testRoundtripIO() {
        val filename = "/home/stormy/temp/persist/test/TestAuditStateJson.json"

        val testData = MultiContestTestData(11, 4, 50000)
        val contestsUAs: List<ContestUnderAudit> = testData.contests. map { ContestUnderAudit(it, false, false)}
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
            sampledIndices = listOf(1,2,3, 21),
            nmvrs = 129182,
            auditorWantNewMvrs = 2223,
            )
        writeAuditRoundJsonFile(target, filename)
        val result = readAuditRoundJsonFile(contestsUAs, target.sampledIndices, filename)
        assertTrue(result is Ok)
        val roundtrip = result.unwrap()
        check(target, roundtrip)
        assertTrue(roundtrip.equals(target))
        assertEquals(roundtrip, target)
    }

    @Test
    fun testRoundtripWithRounds() {
        val filename = "/home/stormy/temp/persist/test/TestAuditStateJson2.json"

        val fuzzMvrs = .01
        val auditConfig = AuditConfig(
            AuditType.CLCA, hasStyles = true, seed = 12356667890L, nsimEst = 10,
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

        var clcaWorkflow = ClcaWorkflow(auditConfig, contests, emptyList(), testCvrs)
        val lastRound = runWorkflow("testComparisonWorkflow", clcaWorkflow, testMvrs, quiet = true)
        assertNotNull(lastRound)

        val target = AuditRound(
            1,
            lastRound.contestRounds,
            false,
            false,
            sampledIndices = lastRound.sampledIndices,
            nmvrs = 33333,
            auditorWantNewMvrs = 33334533,
        )
        val json = target.publishJson()
        val roundtrip = json.import(clcaWorkflow.getContests(), target.sampledIndices)
        assertNotNull(roundtrip)
        check(target, roundtrip)
        assertEquals(roundtrip, target)

        writeAuditRoundJsonFile(target, filename)
        val result = readAuditRoundJsonFile(clcaWorkflow.getContests(), target.sampledIndices, filename)
        if (result is Err) println("result = $result")
        assertTrue(result is Ok)
        val roundtripIO = result.unwrap()
        assertTrue(roundtripIO.equals(target))
        assertEquals(roundtripIO, target)
    }

    @Test
    fun testRoundtripWithRaire() {
        val filename = "/home/stormy/temp/persist/test/TestAuditStateJson3.json"

        val fuzzMvrs = .01
        val auditConfig = AuditConfig(
            AuditType.CLCA, hasStyles = true, seed = 12356667890L, nsimEst = 10,
        )

        val N = 5000
        val testData = MultiContestTestData(11, 4, N, marginRange = 0.03..0.05)

        val contests: List<Contest> = testData.contests
        println("Start testComparisonWorkflow $testData")
        contests.forEach { println("  $it") }

        val (rcontest: RaireContestUnderAudit, rcvrs: List<Cvr>) = makeRaireContest(N/2, ncands=5, minMargin=.04, quiet = true)
        println(rcontest)
        println()

        val testCvrs = testData.makeCvrsFromContests() + rcvrs

        val testMvrs = if (fuzzMvrs == 0.0) testCvrs
            else makeFuzzedCvrsFrom(contests, testCvrs, fuzzMvrs)

        var clcaWorkflow = ClcaWorkflow(auditConfig, contests, listOf(rcontest), testCvrs)
        val nextRound = clcaWorkflow.startNewRound()
        val sampledMvrs = nextRound.sampledIndices.map {
            testMvrs[it]
        }
        val done = clcaWorkflow.runAudit(nextRound, sampledMvrs)

        val target = AuditRound(
            1,
            nextRound.contestRounds,
            false,
            false,
            sampledIndices = nextRound.sampledIndices,
            nmvrs = 33333,
            auditorWantNewMvrs = 33733,
        )
        val json = target.publishJson()
        val roundtrip = json.import(clcaWorkflow.getContests(), target.sampledIndices)
        assertNotNull(roundtrip)
        check(target, roundtrip)
        assertEquals(roundtrip, target)

        writeAuditRoundJsonFile(target, filename)
        val result = readAuditRoundJsonFile(clcaWorkflow.getContests(), target.sampledIndices, filename)
        assertTrue(result is Ok)
        val roundtripIO = result.unwrap()
        assertTrue(roundtripIO.equals(target))
        assertEquals(roundtripIO, target)
    }
}

fun check(s1: AuditRound, s2: AuditRound) {
    assertEquals(s1.roundIdx, s2.roundIdx)
    assertEquals(s1.auditWasDone, s2.auditWasDone)
    assertEquals(s1.auditIsComplete, s2.auditIsComplete)
    assertEquals(s1.sampledIndices, s2.sampledIndices)
    assertEquals(s1.nmvrs, s2.nmvrs)
    assertEquals(s1.newmvrs, s2.newmvrs)
    assertEquals(s1.auditorWantNewMvrs, s2.auditorWantNewMvrs)

    assertEquals(s1.contestRounds.size, s2.contestRounds.size)
    s1.contestRounds.forEachIndexed { idx, c1 ->
        if (c1.contestUA.contest.choiceFunction == SocialChoiceFunction.IRV) {
            println("here")
        }
        val c2 = s2.contestRounds[idx]
        assertEquals(c1.contestUA.contest, c2.contestUA.contest, "contest ${c1.contestUA.contest.show()}\n not ${c2.contestUA.contest.show()}")
        c1.contestUA.clcaAssertions.forEachIndexed { idx, a1 ->
            val a2 = c2.contestUA.clcaAssertions[idx]
            assertEquals(a1.cassorter, a2.cassorter, "clcaAssertion.cassorter ${a1.cassorter}\n not ${a2.cassorter}")
            assertEquals(a1, a2, "clcaAssertion ${a1}\n not ${a2}")
        }
        assertEquals(c1, c2, "contestUA $c1\n not $c2")
    }
}
