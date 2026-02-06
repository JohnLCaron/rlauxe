package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.util.tabulateCvrs
import org.cryptobiotic.rlauxe.util.tabulateVotesFromCvrs
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.test.assertEquals

class TestCvrSimulation {

    // fun simulateCvrsWithDilutedMargin(contestUA: ContestWithAssertions, config: AuditConfig): List<Cvr> {
    @Test
    fun testSimulateCvrsWithDilutedMargin() {
        val Nc = 50000
        val margin = 0.04
        val underVotePct = 0.20
        val phantomPercent = .05
        val (cu, vcrs) = simulateCvrsWithDilutedMargin(Nc, margin, underVotePct, phantomPercent, limit = null)
        val contest = cu.contest as Contest

        val nvcrs = vcrs.count { it.hasContest(contest.id) }
        assertEquals(contest.Nc, nvcrs)

        val tab2 = tabulateVotesFromCvrs(vcrs.iterator())
        assertEquals(mapOf(contest.id to contest.votes), tab2)
        println(tab2)
        println(contest.votes)
    }

    @Test
    fun testSimulateCvrsWithLimit() {
        val n = 100
        var max = 0.0
        var sum = 0.0
        repeat(n) {
            val diff = testSimulateCvrsWithLimit(false)
            sum += diff
            max = max(max, abs(diff))
        }
        println("sum diff = $sum avg = ${sum/n} max = $max")
    }

    fun testSimulateCvrsWithLimit(show: Boolean): Double {
        val Nc = 50000
        val margin = 0.04
        val underVotePct = 0.20
        val phantomPercent = .05

        val (cu, vcrs) = simulateCvrsWithDilutedMargin(Nc, margin, underVotePct, phantomPercent, limit = 30000)
        val nvcrs = vcrs.count { it.hasContest(cu.id) }
        val contest = cu.contest as Contest

        val tab = tabulateCvrs(vcrs.iterator(), mapOf(contest.id to contest.info()))
        val tab0 = tab[0]!!

        if (show) {
            println(" contest = $cu")
            println(" votes = ${contest.votes} N=${contest.Nc}")
            println(" contest reportedMargin=${contest.reportedMargin(0, 1)}")

            println(" nvcrs = $nvcrs pct = ${nvcrs/Nc.toDouble()}")
            println(" tab = $tab0 nphantoms = ${tab0.nphantoms}")
            println(" tab reportedMargin=${tab0.reportedMargin(0, 1)}")
            println(" nphantoms = ${tab0.nphantoms} pct = ${tab0.nphantoms/contest.Nphantoms().toDouble()}")
            println(" undervotes = ${tab0.undervotes} pct = ${tab0.undervotes/contest.Nundervotes().toDouble()}")
        }

        val diff = tab0.reportedMargin(0, 1) - contest.reportedMargin(0, 1)
        println("margin diff = $diff")
        return diff
    }

}

fun ContestTabulation.reportedMargin(winnerId: Int, loserId: Int): Double {
    val winnerVotes = votes[winnerId] ?: 0
    val loserVotes = votes[loserId] ?: 0
    return (winnerVotes - loserVotes) / ncards().toDouble()
}