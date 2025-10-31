package org.cryptobiotic.rlauxe.dhondt

import org.cryptobiotic.rlauxe.doublePrecision
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

    /*
    @Test
    fun testAssortAvg() {
        testAssortAvg(listOf(DhondtCandidate(1, 10), DhondtCandidate(2, 20), DhondtCandidate(3, 30)), 2, minPct)
        testAssortAvg(listOf(DhondtCandidate(1, 10000), DhondtCandidate(2, 6000), DhondtCandidate(3, 1500)), 8, minPct)
    }

    fun testAssortAvg(parties: List<DhondtCandidate>, nseats: Int, minPct: Double) {
        val dcontest = makeProtoContest("contest1", 1, parties, nseats, 0, minPct)
        dcontest.assorters.forEach { it ->
            val (gavg, havg) = it.getAssortAvg(0)
            print(it.show())
            println("             gavg=${gavg.show2()}")
            println("             havg=${havg.show2()}")
            assertEquals(it.gmean, gavg.mean, doublePrecision)
            assertEquals(it.reportedMean(), havg.mean, doublePrecision)

            // TODO: in plurality, the avg assort value = margin2mean( assort.margin). here, avg assort value = assort margin. maybe a different choice of c ?
            // TODO margin2mean and mean2margin assumes lower = 1. is that wrong ?
        }
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

        dcontest.assorters.forEach { assorter ->
            val assorterif = assorter.makeAssorter()
            println(" assorterif reportedMean= ${df(assorterif.reportedMean())} reportedMargin= ${df(assorterif.reportedMargin())}")

            val welford = Welford()
            cvrsIF.forEach { cvr ->
                welford.update(assorterif.assort(cvr))
            }

            println("             assort mean = ${df(welford.mean)}")
            assertEquals(welford.mean, assorterif.reportedMean(), doublePrecision)
        }
    } */
}