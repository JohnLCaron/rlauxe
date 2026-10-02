package org.cryptobiotic.rlauxe.dhondt

import org.cryptobiotic.rlauxe.core.AboveThreshold
import org.cryptobiotic.rlauxe.core.BelowThreshold
import org.cryptobiotic.rlauxe.util.doublePrecision
import org.cryptobiotic.rlauxe.util.Welford
import org.cryptobiotic.rlauxe.util.df
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class TestDhondtContest {
    val minPct = 0.05
    val nseats = 8

    @Test
    fun testMakeDhondtContest() {
        val parties = listOf(DhondtPartyBuilder(1, 10000, nseats), DhondtPartyBuilder(2, 6000, nseats), DhondtPartyBuilder(3, 1500, nseats))
        val nvotes = parties.sumOf { it.totalVotes }
        val contestd = makeDhondtContest("contest1", 1,
            parties,
            8, nvotes, 0, minPct)
        println(contestd.show())
        println(contestd.showCandidates())

        assertEquals(listOf(1,2), contestd.winners)
        assertEquals(listOf("party-1", "party-2"), contestd.winnerNames)
        assertEquals(listOf(3), contestd.losers)
        assertEquals(mapOf(1 to 5, 2 to 3, 3 to 0), contestd.winnerSeatCount)
        assertEquals(8, contestd.winnerSeatCount.map { it.value }.sum())

        val parties2 = listOf(DhondtPartyBuilder(1, 11000, nseats), DhondtPartyBuilder(2, 7000, nseats), DhondtPartyBuilder(3, 2500, nseats))
        val nvotes2 = parties2.sumOf { it.totalVotes }
        val contestd2 = makeDhondtContest("contest2", 2,
            parties2,
            11, nvotes2, 0, minPct)
        println(contestd.show())

        assertEquals(contestd, contestd)
        assertEquals(contestd.hashCode(), contestd.hashCode())
        assertNotEquals(contestd, contestd2)
        assertNotEquals(contestd.hashCode(), contestd2.hashCode())
    }

    @Test
    fun testCvrs() {
        val undervotes = 200
        val Ncast = 17500
        val Nc = Ncast + undervotes
        val parties = listOf(DhondtPartyBuilder(1, 10000, nseats), DhondtPartyBuilder(2, 6000, nseats), DhondtPartyBuilder(3, 1500, nseats))
        val contestd: DhondtContest = makeDhondtContest("contest1", 1, parties, 8, Nc, undervotes, minPct)

        println("\nContestDHondt.cvrs, AssorterIF")
        val cvrsIF = contestd.createSimulatedCvrs() // TODO failing on undervotes != 0
        println("validVotes = ${contestd.votes.values.sum()} undervotes=${contestd.undervotes} ncvrsIF = ${cvrsIF.size}")

        contestd.assorters.forEach { assorter ->
            println(" assorterif dilutedMean= ${df(assorter.dilutedMean())} dilutedMargin= ${df(assorter.margin(true))}")

            val welford = Welford()
            cvrsIF.forEach { cvr ->
                welford.update(assorter.assort(cvr))
            }

            println("             assort mean = ${df(welford.mean)}")
            assertEquals(welford.mean, assorter.dilutedMean(), doublePrecision)
        }
    }

    @Test
    fun testAssorters() {
        testAssorters(listOf(DhondtPartyBuilder(1, 10, nseats), DhondtPartyBuilder(2, 20, nseats), DhondtPartyBuilder(3, 30, nseats)), 2, minPct)
        testAssorters(listOf(DhondtPartyBuilder(1, 10000, nseats), DhondtPartyBuilder(2, 6000, nseats), DhondtPartyBuilder(3, 1500, nseats)), 8, minPct)
    }

    fun testAssorters(parties: List<DhondtPartyBuilder>, nseats: Int, minPct: Double) {
        val Nc = parties.sumOf { it.totalVotes }
        val contestd = makeDhondtContest("contest1", 1, parties, nseats, Nc, 0, minPct)

        contestd.assorters.forEach {
            println(it)
            assertEquals(it, it)
            assertEquals(it.hashCode(), it.hashCode())

            if (it is DhondtAssorter) {
                println(" setDilutedMean = ${setDilutedMean(it, contestd)}")
                println(" dilutedMean= ${it.dilutedMean()}")
                println(" reportedMean= ${it.reportedMean()}")
                assertEquals(it.dilutedMean(), setDilutedMean(it, contestd), doublePrecision)

                assertEquals(contestd.difficulty(it), 1.0 / it.reportedMargin(), doublePrecision)

                val gmean = contestd.marginInVotes(it)/contestd.Nc.toDouble()
                println(" gmean = ${gmean}")

                val hmean = it.h2(gmean)
                println(" hmean = ${it.h2(gmean)}")
                assertEquals(it.dilutedMean(), hmean, 1.0e-5) // why not perfect ??

            } else if (it is BelowThreshold) {
                println(" dilutedMean= ${it.dilutedMean()}")

                assertEquals(contestd.difficulty(it), 1.0 / it.reportedMargin(), doublePrecision)

                val gmean = contestd.marginInVotes(it)/contestd.Nc.toDouble()
                val hmean = it.h2(gmean)
                println(" hmean = ${it.h2(gmean)}")
                assertEquals(it.dilutedMean(), hmean, 1.0e-5) // why not perfect ??

            } else if (it is AboveThreshold) {
                println(" dilutedMean= ${it.dilutedMean()}")

                assertEquals(contestd.difficulty(it), 1.0 / it.reportedMargin(), doublePrecision)

                val gmean = contestd.marginInVotes(it)/contestd.Nc.toDouble()
                println(" gmean = ${gmean}")
                val hmean = it.h2(gmean)
                println(" hmean = ${it.h2(gmean)}")
                assertEquals(it.dilutedMean(), hmean, 1.0e-5) // why not perfect ??
            }

            println(" margin = ${it.margin(true)}")
            println(" calcMarginFromRegVotes = ${it.calcMarginFromRegVotes(contestd.votes, contestd.Nc)}")
            assertEquals(it.margin(true), it.calcMarginFromRegVotes(contestd.votes, contestd.Nc), doublePrecision)

            println("recountMargin = ${contestd.recountMargin(it)}")
            println("showDifficulty = ${contestd.showAssertionDifficulty(it)}")
            println()
        }
    }
}

// from AssorterBuilder
fun setDilutedMean(assorter: DhondtAssorter, contest: DhondtContest): Double {
    // Let f_e,s = Te/d(s) for entity e and seat s
    // f_A,WA > f_B,LB, so e = A and s = Wa

    val winnerVotes = contest.votes[assorter.winner()]!!
    val loserVotes = contest.votes[assorter.loser()]!!

    val fw = winnerVotes / assorter.winnerDivisor.toDouble()
    val fl = loserVotes / assorter.loserDivisor.toDouble()
    val gmean = (fw - fl) / contest.Nc

    val lower = -1.0 / assorter.loserDivisor  // lower bound of g
    val upper = 1.0 / assorter.winnerDivisor  // upper bound of g
    val c = -1.0 / (2 * lower)  // affine transform h = c * g + 1/2

    val hmean = assorter.h2(gmean)
    val hmean2 = h(gmean, c)
    assertEquals(hmean, hmean2)

    return hmean
   /* fun makeAssorter() = DhondtAssorter(
        contest.createInfo(),
        winner.id,
        loser.id,
        lastSeatWon = winner.lastSeatWon!!,
        firstSeatLost = loser.firstSeatLost!!)
        .setDilutedMean(hmean) */
}

private fun h(g: Double, c: Double): Double = c * g + 0.5
