package org.cryptobiotic.rlauxe.persist.json

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
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
    fun testRoundtripNaked() {
        val testData = MultiContestTestData(11, 4, 50000)
        val contestsUAs: List<ContestUnderAudit> = testData.contests. map { ContestUnderAudit(it, false, false)}
        val contestRounds = contestsUAs.map{ contest -> ContestRound(contest, 1) }

        val target = AuditRound(
            2,
            contestRounds,
            true,
            false,
            sampledIndices = listOf(1,2,3),
            nmvrs = 42,
        )
        val json = target.publishJson()
        val roundtrip = json.import()
        assertNotNull(roundtrip)
        assertTrue(roundtrip.equals(target))
        assertEquals(roundtrip, target)
    }

    @Test
    fun testRoundtripIOnaked() {
        val filename = "/home/stormy/temp/persist/test/TestAuditStateJson.json"

        val testData = MultiContestTestData(11, 4, 50000)
        val contestsUAs: List<ContestUnderAudit> = testData.contests. map { ContestUnderAudit(it, false, false)}
        val contestRounds = contestsUAs.map{ contest -> ContestRound(contest, 1) }

        val target = AuditRound(
            1,
            contestRounds,
            false,
            false,
            sampledIndices = listOf(1,2,3, 21),
            nmvrs = 129182,
            auditorSetNewMvrs = 2223,
            )
        writeAuditRoundJsonFile(target, filename)
        val result = readAuditRoundJsonFile(filename)
        assertTrue(result is Ok)
        val roundtrip = result.unwrap()
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
            lastRound.contests,
            false,
            false,
            sampledIndices = lastRound.sampledIndices,
            nmvrs = 33333,
            auditorSetNewMvrs = 33334533,
        )
        val json = target.publishJson()
        val roundtrip = json.import()
        assertNotNull(roundtrip)
        check(target, roundtrip)
        assertEquals(roundtrip, target)

        writeAuditRoundJsonFile(target, filename)
        val result = readAuditRoundJsonFile(filename)
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
            nextRound.contests,
            false,
            false,
            sampledIndices = nextRound.sampledIndices,
            nmvrs = 33333,
            auditorSetNewMvrs = 33733,
        )
        val json = target.publishJson()
        val roundtrip = json.import()
        assertNotNull(roundtrip)
        check(target, roundtrip)
        assertEquals(roundtrip, target)

        writeAuditRoundJsonFile(target, filename)
        val result = readAuditRoundJsonFile(filename)
        assertTrue(result is Ok)
        val roundtripIO = result.unwrap()
        assertTrue(roundtripIO.equals(target))
        assertEquals(roundtripIO, target)
    }
}

// data class AuditRound(
//    val roundIdx: Int,
//    val contests: List<ContestRound>,
//
//    val auditWasDone: Boolean = false,
//    var auditIsComplete: Boolean = false,
//    var sampledIndices: List<Int>, // ballots to sample for this round
//    var nmvrs: Int = 0,
//    var newmvrs: Int = 0,
//    var auditorSetNewMvrs: Int = -1,
//)
fun check(s1: AuditRound, s2: AuditRound) {
    assertEquals(s1.roundIdx, s2.roundIdx)
    assertEquals(s1.auditWasDone, s2.auditWasDone)
    assertEquals(s1.auditIsComplete, s2.auditIsComplete)
    assertEquals(s1.sampledIndices, s2.sampledIndices)
    assertEquals(s1.nmvrs, s2.nmvrs)
    assertEquals(s1.newmvrs, s2.newmvrs)
    assertEquals(s1.auditorSetNewMvrs, s2.auditorSetNewMvrs)

    assertEquals(s1.contests.size, s2.contests.size)
    s1.contests.forEachIndexed { idx, c1 ->
        if (c1.contestUA.contest.choiceFunction == SocialChoiceFunction.IRV) {
            println("here")
        }
        val c2 = s2.contests[idx]
        assertEquals(c1.contestUA.contest, c2.contestUA.contest, "contest ${c1.contestUA.contest.show()}\n not ${c2.contestUA.contest.show()}")
        c1.contestUA.clcaAssertions.forEachIndexed { idx, a1 ->
            val a2 = c2.contestUA.clcaAssertions[idx]
            assertEquals(a1.cassorter, a2.cassorter, "clcaAssertion.cassorter ${a1.cassorter}\n not ${a2.cassorter}")
            assertEquals(a1, a2, "clcaAssertion ${a1}\n not ${a2}")
        }
        assertEquals(c1, c2, "contestUA $c1\n not $c2")
    }
}
