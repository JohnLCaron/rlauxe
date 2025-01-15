package org.cryptobiotic.rlauxe.core

import kotlin.test.Test
import kotlin.test.assertNotNull

class TestOneAudit {

    @Test
    fun testMakeContest() {
        val contest = makeContestOA(23000, 21000, cvrPercent = .70, 0.0, undervotePercent=.01)
        println(contest)
        val v0 = contest.strata[0].votes
        val v1 = contest.strata[1].votes

        val contest7 = makeContestOA(23000, 21000, cvrPercent = .70, .007, undervotePercent=.01)
        println(contest7)
        val v07 = contest7.strata[0].votes
        val v17 = contest7.strata[1].votes

        repeat(2) {
            println(if (it == 0) "winner" else "loser")
            val diff0 = -(v0[it]!! - v07[it]!!)
            val diff0p = diff0/ v0[it]!!.toDouble()
            println(" hasCvr $it diff=$diff0 pct=$diff0p (${v0[it]!!} ->  ${v07[it]!!})")

            val diff1 = -(v1[it]!! - v17[it]!!)
            val diff1p = diff1 / v1[it]!!.toDouble()
            println(" noCvr $it diff=$diff1 pct=$diff1p (${v1[it]!!} ->  ${v17[it]!!})")
        }
    }

    @Test
    fun testAssorters() {
        val contest = makeContestOA(23000, 21000, cvrPercent = .70, 0.0, undervotePercent=.01)
        val testCvrs = contest.makeTestCvrs()
        val contestOA = contest.makeContestUnderAudit(testCvrs)
        val minAllAsserter = contestOA.minComparisonAssertion()
        assertNotNull(minAllAsserter)
        val minAllAssorter = minAllAsserter.assorter
        println(minAllAssorter)

        val minAssorterMargin = minAllAssorter.calcAssorterMargin(contest.id, testCvrs)
        println(" calcAssorterMargin for minAllAssorter = $minAssorterMargin")

        val cass = minAllAsserter.cassorter as OneAuditComparisonAssorter
        println("   $cass")

    }
}

// two contest, specified total votes
// divide into two stratum based on cvrPercent
// skew votes
fun makeContestOA(winner: Int, loser: Int, cvrPercent: Double, skewVotesPercent: Double, undervotePercent: Double): OneAuditContest {

    // the candidates
    val info = ContestInfo(
        "makeContestOA", 0,
        mapOf(
            "winner" to 0,
            "loser" to 1,
        ),
        SocialChoiceFunction.PLURALITY,
        nwinners = 1,
    )

    val noCvrPercent = (1.0 - cvrPercent)
    val totalVotes = (1.0 + undervotePercent) * (winner+loser)
    val skewVotes = skewVotesPercent * totalVotes
    // println(" skewVotes $skewVotes pct=$skewVotesPercent")

    // reported results for the two strata
    val candidates = mapOf(     // candidateName -> [votes(cvr), votes(nocvr)]
        "winner" to listOf((winner * cvrPercent + skewVotes).toInt(), (winner * noCvrPercent - skewVotes).toInt()),
        "loser" to listOf((loser * cvrPercent - skewVotes).toInt(), (loser * noCvrPercent + skewVotes).toInt()),
    )
    val stratumNames = listOf("hasCvr", "noCvr")
    val stratumSizes = listOf((totalVotes * cvrPercent).toInt(), (totalVotes * noCvrPercent).toInt()) // hasCvr, noCvr

    //    val strataName: String,
    //    val info: ContestInfo,
    //    val votes: Map<Int, Int>,   // candidateId -> nvotes
    //    val Nc: Int,  // upper limit on number of ballots in this starata for this contest
    //    val Np: Int,  // number of phantom ballots in this starata for this contest
    val strata = mutableListOf<OneAuditStratum>()
    repeat(2) { idx ->
        strata.add(
            OneAuditStratum(
                stratumNames[idx],
                hasCvrs = (idx == 0),
                info,
                candidates.map { (key, value) -> Pair(info.candidateNames[key]!!, value[idx]) }.toMap(),
                Ng = stratumSizes[idx],
                Np = 0  // TODO investigate
            )
        )
    }
    return OneAuditContest(info, strata)
}