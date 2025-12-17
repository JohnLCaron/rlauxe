package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.raire.RaireContestUnderAudit
import org.cryptobiotic.rlauxe.raire.simulateRaireTestContest
import kotlin.test.Test
import kotlin.test.assertEquals

class TestFuzzedWithRaire {

    // @Test
    fun repeatFuzzedWithRaire() {
        repeat(100) { testFuzzedWithRaire() }
    }

    @Test
    fun testFuzzedWithRaire() {
        val margin = .05
        val fuzzMvrs = .01
        val underVotePct = .01
        val phantomPct = .01
        val N = 1000
        val show = false

        val testData =
            MultiContestTestDataP(1, 4, N,
                marginRange = margin..margin,
                underVotePctRange = underVotePct .. underVotePct,
                phantomPctRange = phantomPct..phantomPct)

        val contests: List<Contest> = testData.contests
        assertEquals(1, contests.size)
        val contest = contests.first()

        var cvrs1 = testData.makeCvrsFromContests()
        if (show) {
            println("\nregular cvrs")
            cvrs1.forEach {
                println(" $it")
            }
        }

        val (rcontest: RaireContestUnderAudit, rcvrs: List<Cvr>) = simulateRaireTestContest(N/2, contestId=111, ncands=3, minMargin=.04, quiet = true, hasStyle=true)
        if (show) {
            println("\nraire cvrs")
            rcvrs.forEach {
                println(" $it")
            }
        }

        val testCvrs = cvrs1 + rcvrs
        println("count1=${cvrs1.size} count2=${rcvrs.size} countAll = ${testCvrs.size}")

        val testMvrs = if (fuzzMvrs == 0.0) testCvrs
            else makeFuzzedCvrsFrom(listOf(contest, rcontest.contest), testCvrs, fuzzMvrs)

        println("after $fuzzMvrs fuzz")
        require(testCvrs.size == testMvrs.size)
        val cvrPairs: List<Pair<Cvr, Cvr>> = testMvrs.zip(testCvrs)
        cvrPairs.forEach { (mvr, cvr) ->
            if (show) println(" mvr='$mvr' cvr='$cvr' ")
            assertEquals(mvr.id, cvr.id)
        }

        val count1 = cvrPairs.count {
            it.first.hasContest(contest.id)
        }
        val count2 = cvrPairs.count {
            it.first.hasContest(rcontest.id)
        }
        println("hasContest1=$count1 hasContest2=$count2 \n")
        assertEquals(rcvrs.size, count2)
        assertEquals(cvrs1.size, count1)
    }
}