package org.cryptobiotic.rlauxe.persist.json

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.estimate.MultiContestTestData
import org.cryptobiotic.rlauxe.estimate.makeFuzzedCvrsFrom
import org.cryptobiotic.rlauxe.workflow.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

import kotlin.test.assertNotNull

class TestAuditStateJson {
    val filename = "/home/stormy/temp/persist/test/TestAuditStateJson.json"

    @Test
    fun testRoundtripNaked() {
        val testData = MultiContestTestData(11, 4, 50000)
        val contests: List<ContestUnderAudit> = testData.contests. map { ContestUnderAudit(it, false, false)}
        val target = AuditState(
            "TestContestJson",
            2,
            42,
            99,
            true,
            false,
            contests,
        )
        val json = target.publishJson()
        val roundtrip = json.import()
        assertNotNull(roundtrip)
        assertTrue(roundtrip.equals(target))
        assertEquals(roundtrip, target)
    }

    @Test
    fun testRoundtripIOnaked() {
        val testData = MultiContestTestData(11, 4, 50000)
        val contests: List<ContestUnderAudit> = testData.contests. map { ContestUnderAudit(it, false, false)}
        val target = AuditState(
            "TestContestJson",
            1,
            129182,
            423487234,
            false,
            false,
            contests,
        )
        writeAuditStateJsonFile(target, filename)
        val result = readAuditStateJsonFile(filename)
        assertTrue(result is Ok)
        val roundtrip = result.unwrap()
        assertTrue(roundtrip.equals(target))
        assertEquals(roundtrip, target)
    }

    @Test
    fun testRoundtripWithRounds() {
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
        val nmvrs = runWorkflow("testComparisonWorkflow", clcaWorkflow, testMvrs, quiet = true)

        val target = AuditState(
            "TestContestJson",
            1,
            nmvrs,
            nmvrs,
            false,
            false,
            clcaWorkflow.contestsUA,
        )
        val json = target.publishJson()
        val roundtrip = json.import()
        assertNotNull(roundtrip)
        check(target, roundtrip)
        assertEquals(roundtrip, target)

        val useFilename = filename + "2"
        writeAuditStateJsonFile(target, useFilename)
        val result = readAuditStateJsonFile(useFilename)
        assertTrue(result is Ok)
        val roundtripIO = result.unwrap()
        assertTrue(roundtripIO.equals(target))
        assertEquals(roundtripIO, target)
    }
}

// data class AuditState(
//    val name: String,
//    val roundIdx: Int,
//    val nmvrs: Int,
//    val newMvrs: Int,
//    val auditWasDone: Boolean,
//    val auditIsComplete: Boolean,
//    val contests: List<ContestUnderAudit>,
//)
fun check(s1: AuditState, s2: AuditState) {
    assertEquals(s1.name, s2.name)
    assertEquals(s1.roundIdx, s2.roundIdx)
    assertEquals(s1.nmvrs, s2.nmvrs)
    assertEquals(s1.newMvrs, s2.newMvrs)
    assertEquals(s1.auditWasDone, s2.auditWasDone)
    assertEquals(s1.auditIsComplete, s2.auditIsComplete)
    assertEquals(s1.contests.size, s2.contests.size)
    s1.contests.forEachIndexed { idx, c1 ->
        val c2 = s2.contests[idx]
        assertEquals(c1.contest, c2.contest, "contest ${c1.contest.show()}\n not ${c2.contest.show()}")
        c1.clcaAssertions.forEachIndexed { idx, a1 ->
            val a2 = c2.clcaAssertions[idx]
            assertEquals(a1.cassorter, a2.cassorter, "clcaAssertion.cassorter ${a1.cassorter}\n not ${a2.cassorter}")
            assertEquals(a1, a2, "clcaAssertion ${a1.show()}\n not ${a2.show()}")
        }
        assertEquals(c1, c2, "contestUA $c1\n not $c2")
    }
}
