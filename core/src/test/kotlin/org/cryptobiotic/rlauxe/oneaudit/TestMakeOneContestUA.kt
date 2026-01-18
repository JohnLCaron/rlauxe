package org.cryptobiotic.rlauxe.oneaudit

import org.cryptobiotic.rlauxe.util.tabulateVotesFromCvrs
import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.betting.TestH0Status
import org.cryptobiotic.rlauxe.util.doublePrecision
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.util.roundToClosest
import org.cryptobiotic.rlauxe.util.sumContestTabulations
import org.cryptobiotic.rlauxe.util.tabulateOneAuditPools
import org.cryptobiotic.rlauxe.verify.checkEquivilentVotes
import org.junit.jupiter.api.Assertions.assertEquals
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.fail


class TestMakeOneContestUA {
    val Nc = 10000

    @Test
    fun testAllAreCvrs() {
        val margin = .02
        val (contestOA, mvrs, cards, cardPools) =
            makeOneAuditTest(margin, Nc, cvrFraction = 0.9, undervoteFraction = 0.0, phantomFraction = 0.0)

        assertEquals(Nc, contestOA.Nc)
        val contest = contestOA.contest as Contest
        val cvrVotes =  tabulateVotesFromCvrs(mvrs.iterator()).values.first()
        assertEquals(cvrVotes, contest.votes)
        assertEquals(margin, contest.reportedMargin(0, 1), doublePrecision)

        val oaAssorter = contestOA.minClcaAssertion()!!.cassorter as ClcaAssorterOneAudit
        assertEquals(1, oaAssorter.poolAverages.assortAverage.size)
        println("contestOA = $contestOA")
    }

    @Test
    fun testHalfAreCvrs() {
        val margin = .02
        val cvrPercent = 0.5
        val (contestOA, mvrs, cards, pools) =
            makeOneAuditTest(margin, Nc, cvrFraction = cvrPercent, undervoteFraction = 0.0, phantomFraction = 0.0)

        checkBasics(contestOA, pools, margin, cvrPercent)
    }

    @Test
    fun testWithUndervotes() {
        val margin = .02
        val cvrPercent = 0.5

        val (contestOA, mvrs, cards, pools) =
            makeOneAuditTest(margin, Nc, cvrFraction = cvrPercent, undervoteFraction = 0.10, phantomFraction = 0.0)
        checkBasics(contestOA, pools, margin, cvrPercent)
    }

    @Test
    fun testWithPhantoms() {
        val margin = .02
        val cvrPercent = 0.5

        val (contestOA, mvrs, cards, pools) =
            makeOneAuditTest(margin, Nc, cvrFraction = cvrPercent, undervoteFraction = 0.0, phantomFraction = 0.03)
        checkBasics(contestOA, pools, margin, cvrPercent)
    }

    @Test
    fun testWithUnderVotesAndPhantoms() {
        val margin = .02
        val cvrPercent = 0.5

        val (contestOA, mvrs, cards, pools) =
            makeOneAuditTest(margin, Nc, cvrFraction = cvrPercent, undervoteFraction = 0.10, phantomFraction = 0.03)
        checkBasics(contestOA, pools, margin, cvrPercent)
    }

    @Test
    fun testMakeOneAudit() {
        val undervotePercent = .33
        val phantomPercent = 0.03
        val margins =
            listOf(.001, .002, .003, .004, .005, .006, .008, .01, .012, .016, .02, .03, .04, .05, .06, .07, .08, .10)
        val cvrPercents = listOf(0.01, 0.5, 0.99)
        margins.forEach { margin ->
            cvrPercents.forEach { cvrPercent ->
                println("======================================================================================================")
                println("margin=$margin cvrPercent=$cvrPercent phantomPercent=$phantomPercent undervotePercent=$undervotePercent")
                val (contestOA, mvrs, cards, cardPools) =
                    makeOneAuditTest(margin, Nc, cvrFraction = cvrPercent, undervoteFraction = undervotePercent, phantomFraction = phantomPercent)
                checkBasics(contestOA, cardPools, margin, cvrPercent)
                checkAgainstCvrs(contestOA, cardPools, mvrs, cvrPercent, undervotePercent, phantomPercent)
                checkAgainstVerify(contestOA, cardPools, mvrs)
            }
        }
    }

    @Test
    fun testProblem() {
        val margin = .01
        val cvrPercent = 0.95
        val phantomPercent = 0.0
        val undervotePercent = 0.01
        val Nc2=10000
        println("======================================================================================================")
        println("margin=$margin cvrPercent=$cvrPercent phantomPercent=$phantomPercent undervotePercent=$undervotePercent")
        val (contestOA, mvrs, cards, populations) =
            makeOneAuditTest(margin, Nc2, cvrFraction = cvrPercent, undervoteFraction = undervotePercent, phantomFraction = phantomPercent)
        val cardPools = populations as List<OneAuditPoolIF>
        checkBasics(contestOA, cardPools, margin, cvrPercent, Nc2)
        checkAgainstCvrs(contestOA, cardPools, mvrs, cvrPercent, undervotePercent, phantomPercent)
        checkAgainstVerify(contestOA, cardPools, mvrs)
    }

    fun checkBasics(contestOA: ContestWithAssertions, cardPools: List<OneAuditPoolIF>, margin: Double, cvrPercent: Double, expectedNc: Int = Nc) {
        println(contestOA)

        //val nvotes = contestOA.cvrNcards + ballotPools.map{ it.ncards }.sum()
        //assertEquals(roundToClosest(nvotes*cvrPercent), contestOA.cvrNcards)
        //showPct("cvrs", contestOA.cvrVotes, contestOA.cvrNcards)

        assertEquals(1, cardPools.size)
        val cardPool = cardPools.first()
        assertEquals("pool42", cardPool.poolName)
        println(cardPool)
        val vunder = cardPool.votesAndUndervotes(contestOA.id, contestOA.contest.info().voteForN)
        showPct("pool", vunder.candVotesSorted, cardPool.ncards())

        val ncast = contestOA.contest.Ncast()
        assertEquals(roundToClosest(ncast*(1.0 - cvrPercent)), cardPool.ncards())

        assertEquals(expectedNc, contestOA.Nc)
        val contest = contestOA.contest as Contest
        assertEquals(margin, contest.reportedMargin(0, 1), doublePrecision)
        showPct("allVotes", contest.votes, contestOA.Nc)
        println()
    }

    fun checkAgainstCvrs(contestOA: ContestWithAssertions, cardPools: List<OneAuditPoolIF>, testCvrs: List<Cvr>, cvrPercent: Double, undervotePercent: Double, phantomPercent: Double) {
        val bassorter = contestOA.minClcaAssertion()!!.cassorter as ClcaAssorterOneAudit
        println(bassorter)

        // sanity check
        val allCount = testCvrs.count()
        assertEquals(allCount, contestOA.Nc)

        val cvrCount = testCvrs.count { it.poolId == null && !it.phantom }
        val poolCount = testCvrs.count { it.poolId != null }
        println("allCount = $allCount cvrCount=$cvrCount poolCount=$poolCount")

        val nphantom = testCvrs.count { it.hasContest(contestOA.id) && it.phantom }
        assertEquals(contestOA.Nphantoms, nphantom)
        val phantomPct = nphantom / contestOA.Nc.toDouble()
        println(
            "  nphantom=$nphantom pct= $phantomPct =~ ${phantomPct} abs=${abs(phantomPct - phantomPercent)} " +
                    " rel=${abs(phantomPct - phantomPercent) / phantomPercent}"
        )
        if (nphantom > 2) assertEquals(phantomPct, phantomPercent, .001)

        val nunder = testCvrs.count { it.hasContest(contestOA.id) && !it.phantom && it.votes[contestOA.id]!!.isEmpty() }
        // assertEquals(contest.undervotes, nunder)
        val underPct = nunder / contestOA.Nc.toDouble()
        println(
            "  nunder=$nunder == ${undervotePercent}; pct= $underPct =~ ${undervotePercent} abs=${abs(underPct - undervotePercent)} " +
                    " rel=${abs(underPct - undervotePercent) / underPct}"
        )
        if (nunder > 2) assertEquals(undervotePercent, underPct, .001)
    }

    fun checkAgainstVerify(contestOA: ContestWithAssertions, cardPools: List<OneAuditPoolIF>, testCvrs: List<Cvr>) {

        val allCvrVotes = mutableMapOf<Int, ContestTabulation>()
        val nonpoolCvrVotes = mutableMapOf<Int, ContestTabulation>()
        val poolCvrVotes = mutableMapOf<Int, ContestTabulation>()

        testCvrs.forEach { cvr ->
            cvr.votes.forEach { (contestId, cands) ->
                val info = contestOA.contest.info()
                val allTab = allCvrVotes.getOrPut(contestId) { ContestTabulation(info) }
                allTab.addVotes(cands, cvr.phantom)
                if (cvr.poolId == null) {
                    val nonpoolCvrTab = nonpoolCvrVotes.getOrPut(contestId) { ContestTabulation(info) }
                    nonpoolCvrTab.addVotes(cands, cvr.phantom)
                } else {
                    val poolCvrTab = poolCvrVotes.getOrPut(contestId) { ContestTabulation(info) }
                    poolCvrTab.addVotes(cands, cvr.phantom)
                }
            }
        }

        val infos = mapOf(contestOA.id to contestOA.contest.info())
        val poolSums = tabulateOneAuditPools(cardPools, infos)
        val sumWithPools = mutableMapOf<Int, ContestTabulation>()
        sumWithPools.sumContestTabulations(nonpoolCvrVotes)
        sumWithPools.sumContestTabulations(poolSums)

        val id = contestOA.id
        println("  contest ${id}")
        println("       allCvrVotes = ${allCvrVotes[id]}")
        println("   nonpoolCvrVotes = ${nonpoolCvrVotes[id]}")
        println("      poolCvrVotes = ${poolCvrVotes[id]}")
        println("          poolSums = ${poolSums[id]}")
        println("      sumWithPools = ${sumWithPools[id]}")

        val contestVotes = contestOA.contest.votes()!!
        val sumWithPool = sumWithPools[contestOA.id]!!
        if (!checkEquivilentVotes(contestVotes, sumWithPool.votes)) {
            println("contest ${contestOA.id} votes disagree with cvrs = $sumWithPool")
            println("    contestVotes = $contestVotes")
            println("    sumWithPools = ${sumWithPool.votes}")
            contestOA.preAuditStatus = TestH0Status.ContestMisformed
            fail()
        } else {
            println("contest ${contestOA.id} contest.votes matches sumWithPool")
        }
    }
}