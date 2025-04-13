package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.PrevSamplesWithRates
import org.cryptobiotic.rlauxe.doublePrecision
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.roundToInt
import org.cryptobiotic.rlauxe.util.doubleIsClose
import org.cryptobiotic.rlauxe.audit.ClcaWithoutReplacement
import org.cryptobiotic.rlauxe.audit.checkEquivilentVotes
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestMultiContestTestData {
    val N = 50000
    val ncontests = 40
    val nbs = 11
    val marginRange = 0.01..0.04
    val underVotePct = 0.234..0.345
    val phantomRange = 0.001..0.01
    val test: MultiContestTestData

    init {
        test = MultiContestTestData(ncontests, nbs, N, marginRange, underVotePct, phantomRange)
        // println(test)
    }

    @Test
    fun testBallotPartitions() {
        val calcN = test.ballotStylePartition.map { it.value }.sum()
        assertEquals(N, calcN)
    }

    @Test
    fun testMakeContests() {
        assertEquals(ncontests, test.contests.size)
        test.contests.forEachIndexed { idx, contest ->
            val fcontest = test.fcontests[idx]
            assertEquals(fcontest.ncards + fcontest.phantomCount, contest.Nc)
            val avotes = fcontest.adjustedVotes.sumOf { it.second }
            assertEquals(fcontest.ncards, avotes, "failed for contest = ${contest.id}")
            val nvotes = contest.votes.values.sum()
            val aundervote = fcontest.adjustedVotes.first { it.first == contest.ncandidates}.second
            assertEquals(fcontest.underCount, aundervote, "failed for contest = ${contest.id}")
            assertEquals(fcontest.underCount, avotes - nvotes, "failed for contest = ${contest.id}")

            contest.winners.forEach { winner ->
                contest.losers.forEach { loser ->
                    assertTrue(marginRange.contains(fcontest.margin))
                    assertTrue(underVotePct.contains(fcontest.undervotePct))
                    assertTrue(phantomRange.contains(fcontest.phantomPct))

                    val calcMargin = contest.calcMargin(winner, loser)
                    val margin = (contest.votes[winner]!! - contest.votes[loser]!!) / contest.Nc.toDouble()
                    val calcReportedMargin = contest.calcMargin(winner, loser)
                    assertEquals(margin, calcReportedMargin, doublePrecision)
                    assertEquals(margin, calcMargin, doublePrecision)
                    println(" ${contest.id} fcontest= ${fcontest.margin} contest=$margin")
                }
            }
        }
    }

    @Test
    fun testCvrsFromContests() {
        val (testCvrs, ballots) = test.makeCvrsAndBallots(true)

        val votes: Map<Int, Map<Int, Int>> =
            org.cryptobiotic.rlauxe.util.tabulateVotes(testCvrs.iterator()).toSortedMap() // contestId -> candidateId -> nvotes
        votes.forEach { vcontest ->
            println("  tabulate contest $vcontest")
            votes.forEach { vcontest ->
                val contest = test.contests.find { it.id == vcontest.key }!!
                assertTrue(checkEquivilentVotes(vcontest.value, contest.votes))
            }
        }

        println("test makeBallotsForPolling nballots= ${ballots.size}")

        test.contests.forEachIndexed { idx, contest ->
            val fcontest = test.fcontests[idx]
            val Nc = fcontest.ncards + fcontest.phantomCount

            assertEquals(contest.Nc, Nc)
            println(" ${contest.id} ncards ${fcontest.ncards} Nc=${contest.Nc}")
            val ncvr = testCvrs.count { it.hasContest(contest.id) }
            assertEquals(contest.Nc, ncvr)
            val nbs = ballots.count { it.hasContest(contest.id) }
            assertEquals(contest.Nc, nbs)

            val nphantom = testCvrs.count { it.hasContest(contest.id) && it.phantom }
            assertEquals(fcontest.phantomCount, nphantom)
            val phantomPct = nphantom/ Nc.toDouble()
            println("  nphantom=$nphantom pct= $phantomPct =~ ${fcontest.phantomPct} abs=${abs(phantomPct - fcontest.phantomPct)} " +
                    " rel=${abs(phantomPct - fcontest.phantomPct)/phantomPct}")
            if (nphantom > 5) assertEquals(fcontest.phantomPct, phantomPct, 5.0/Nc)

            val nunder = testCvrs.count { it.hasContest(contest.id) && !it.phantom && it.votes[contest.id]!!.isEmpty() }
            assertEquals(fcontest.underCount, nunder)
            val underPct = nunder/ Nc.toDouble()
            println("  nunder=$nunder == ${fcontest.underCount}; pct= $underPct =~ ${fcontest.undervotePct} abs=${abs(underPct - fcontest.undervotePct)} " +
                    " rel=${abs(underPct - fcontest.undervotePct)/underPct}")
            if (nunder > 5) assertEquals(fcontest.undervotePct, underPct, .03)
        }
    }

    @Test
    fun testMultiContestTestDataOneContest() {
        val N = 50000
        val ncontests = 1
        val nbs = 1
        val marginRange = 0.04..0.04
        val underVotePct = 0.20..0.20
        val phantomPct = .05
        val phantomRange = phantomPct..phantomPct
        val test = MultiContestTestData(ncontests, nbs, N, marginRange, underVotePct, phantomRange)
        val calcN = test.ballotStylePartition.map { it.value }.sum()
        assertEquals(N, calcN)
        println(test)

        assertEquals(ncontests, test.contests.size)

        val cvrs = test.makeCvrsFromContests()
        val ballotManifest = test.makeCardLocationManifest(true)

        test.contests.forEachIndexed { idx, contest ->
            assertEquals(roundToInt(N * (1.0 + phantomPct)), contest.Nc)
            val fcontest = test.fcontests[idx]
            assertEquals(contest.Nc, fcontest.ncards + fcontest.phantomCount)
            println("contest $contest ncards=${fcontest.ncards}")
            val ncvr = cvrs.count { it.hasContest(contest.id) }
            assertEquals(contest.Nc, ncvr)
            val nbsCount = ballotManifest.cardLocations.count { it.hasContest(contest.id) }
            assertEquals(contest.Nc, nbsCount)

            print(" fcontest margin=${df(fcontest.margin)} undervotePct=${fcontest.undervotePct} phantomPct=${fcontest.phantomPct}")
            println(" underCount=${fcontest.underCount} phantomCount=${fcontest.phantomCount}")
            val contestUA = ContestUnderAudit(contest, isComparison = false).makePollingAssertions()
            contestUA.assertions().forEach {
                println("  $it")
            }
        }
    }

    @Test
    fun testPhantomCvrs() {
        val (cvrs, ballotManifest) = test.makeCvrsAndBallots(true)

        test.contests.forEachIndexed { idx, contest ->
            val fcontest = test.fcontests[idx]
            val Nc = fcontest.ncards + fcontest.phantomCount
            assertEquals(contest.Nc, Nc)

            val nphantom = cvrs.count { it.hasContest(contest.id) && it.phantom }
            assertEquals(fcontest.phantomCount, nphantom)
            val phantomPct = nphantom/ Nc.toDouble()
            println("Nc=${contest.Nc} nphantom=$nphantom pct= $phantomPct =~ ${fcontest.phantomPct} abs=${abs(phantomPct - fcontest.phantomPct)} tol=${1.0/Nc}")
            if (nphantom > 1) assertEquals(fcontest.phantomPct, phantomPct, 5.0/Nc) // TODO seems like should be 2 at the most, maybe 1

            val contestUA = ContestUnderAudit(contest, isComparison = true).makeClcaAssertions()
            val cassorter = contestUA.minClcaAssertion()!!.cassorter

            val sampler = ClcaWithoutReplacement(contest.id, true, cvrs.zip(cvrs), cassorter, true)
            val tracker = PrevSamplesWithRates(cassorter.noerror())
            while (sampler.hasNext()) { tracker.addSample(sampler.next()) }
            // println("   tracker.errorRates = ${tracker.errorRates()}")
            val p1o = tracker.errorRates().p1o
            if (!doubleIsClose(phantomPct, p1o, 2.0/Nc)) {
                println("   *** expected ${phantomPct} got=${p1o}")
            }
        }
    }
}