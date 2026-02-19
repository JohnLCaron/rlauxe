package org.cryptobiotic.rlauxe.dhondt

import org.cryptobiotic.rlauxe.core.AboveThreshold
import org.cryptobiotic.rlauxe.core.BelowThreshold
import org.cryptobiotic.rlauxe.util.doublePrecision
import org.cryptobiotic.rlauxe.util.Welford
import org.cryptobiotic.rlauxe.util.df
import kotlin.test.Test
import kotlin.test.assertEquals

class TestDhondtContest {
    val minPct = 0.05

    @Test
    fun testMakeDhondtContest1() {
        val dcontest = makeProtoContest("contest1", 1, listOf(DhondtCandidate(1, 10000), DhondtCandidate(2, 6000), DhondtCandidate(3, 1500)), 8, 0, minPct)
        val contestd = dcontest.createContest()
        println(contestd.show())
        println(contestd.showCandidates())

        assertEquals(listOf(1,2), contestd.winners)
        assertEquals(listOf("party-1", "party-2"), contestd.winnerNames)
        assertEquals(listOf(3), contestd.losers)
        assertEquals(mapOf(1 to 5, 2 to 3), contestd.winnerSeats)
        assertEquals(8, contestd.winnerSeats.map { it.value }.sum())
    }

    @Test
    fun testMakeDhondtContest2() {
        val dcontest = makeProtoContest("contest2", 2, listOf(DhondtCandidate(1, 11000), DhondtCandidate(2, 7000), DhondtCandidate(3, 2500)), 11, 0, minPct)
        val contestd = dcontest.createContest()
        println(contestd.show())
    }


    @Test
    fun testCvrs() {
        val undervotes = 200
        val parties = listOf(DhondtCandidate(1, 10000), DhondtCandidate(2, 6000), DhondtCandidate(3, 1500))
        val dcontest: ProtoContest = makeProtoContest("contest1", 1, parties, 8, undervotes, minPct)

        println("\nContestDHondt.cvrs, AssorterIF")
        val nvotes = dcontest.validVotes
        val Ncast = nvotes + undervotes
        val contestd: DHondtContest = dcontest.createContest(Ncast, Ncast)
        val cvrsIF = contestd.createSimulatedCvrs()
        println("validVotes = ${contestd.votes.values.sum()} undervotes=${contestd.undervotes} ncvrsIF = ${cvrsIF.size}")

        contestd.assorters.forEach { assorter ->
            println(" assorterif dilutedMean= ${df(assorter.dilutedMean())} dilutedMargin= ${df(assorter.dilutedMargin())}")

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
        testAssorters(listOf(DhondtCandidate(1, 10), DhondtCandidate(2, 20), DhondtCandidate(3, 30)), 2, minPct)
        testAssorters(listOf(DhondtCandidate(1, 10000), DhondtCandidate(2, 6000), DhondtCandidate(3, 1500)), 8, minPct)
    }

    fun testAssorters(parties: List<DhondtCandidate>, nseats: Int, minPct: Double) {
        val dcontest = makeProtoContest("contest1", 1, parties, nseats, 0, minPct)
        val contestd = dcontest.createContest()

        contestd.assorters.forEach {
            println(it)
            assertEquals(it, it)
            assertEquals(it.hashCode(), it.hashCode())

            if (it is DHondtAssorter) {
                println(" setDilutedMean = ${setDilutedMean(it, contestd)}")
                println(" dilutedMean= ${it.dilutedMean()}")
                assertEquals(it.dilutedMean(), setDilutedMean(it, contestd), doublePrecision)

                val diff = contestd.difficulty(it)
                println(" diff = $diff")
                val gmean = diff/contestd.Nc
                println(" diff/Nc = ${diff/contestd.Nc}")
                val hmean = it.h2(gmean)
                println(" hmean = ${it.h2(gmean)}")
                assertEquals(it.dilutedMean(), hmean, doublePrecision)

            } else if (it is BelowThreshold) {
                println(" dilutedMean= ${it.dilutedMean()}")

                val diff = contestd.difficulty(it)
                println(" diff = $diff")
                val gmean = diff/contestd.Nc
                println(" diff/Nc = ${diff/contestd.Nc}")
                val hmean = it.h2(gmean)
                println(" hmean = ${it.h2(gmean)}")
                assertEquals(it.dilutedMean(), hmean, doublePrecision)

            } else if (it is AboveThreshold) {
                println(" dilutedMean= ${it.dilutedMean()}")

                val diff = contestd.difficulty(it)
                println(" diff = $diff")
                val gmean = diff/contestd.Nc
                println(" diff/Nc = ${diff/contestd.Nc}")
                val hmean = it.h2(gmean)
                println(" hmean = ${it.h2(gmean)}")
                assertEquals(it.dilutedMean(), hmean, doublePrecision)
            }

            println(" dilutedMargin = ${it.dilutedMargin()}")
            println(" calcMarginFromRegVotes = ${it.calcMarginFromRegVotes(contestd.votes, contestd.Nc)}")
            assertEquals(it.dilutedMargin(), it.calcMarginFromRegVotes(contestd.votes, contestd.Nc), doublePrecision)
            println()
        }
    }
}

// from AssorterBuilder
fun setDilutedMean(assorter: DHondtAssorter, contest: DHondtContest): Double {
    // Let f_e,s = Te/d(s) for entity e and seat s
    // f_A,WA > f_B,LB, so e = A and s = Wa

    val winnerVotes = contest.votes[assorter.winner()]!!
    val loserVotes = contest.votes[assorter.loser()]!!

    val fw = winnerVotes / assorter.lastSeatWon.toDouble()
    val fl = loserVotes / assorter.firstSeatLost.toDouble()
    val gmean = (fw - fl) / contest.Nc

    val lower = -1.0 / assorter.firstSeatLost  // lower bound of g
    val upper = 1.0 / assorter.lastSeatWon  // upper bound of g
    val c = -1.0 / (2 * lower)  // affine transform h = c * g + 1/2

    val hmean = assorter.h2(gmean)
    val hmean2 = h(gmean, c)
    assertEquals(hmean, hmean2)

    return hmean
   /* fun makeAssorter() = DHondtAssorter(
        contest.createInfo(),
        winner.id,
        loser.id,
        lastSeatWon = winner.lastSeatWon!!,
        firstSeatLost = loser.firstSeatLost!!)
        .setDilutedMean(hmean) */
}

private fun h(g: Double, c: Double): Double = c * g + 0.5
