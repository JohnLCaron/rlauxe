package org.cryptobiotic.rlauxe.core

import kotlin.test.Test

class TestOneAudit {

    @Test
    fun testMakeContest() {
        val contest = makeContestOA(23000, 21000, cvrPercent = .70, undervotePercent=.01)
        println(contest)
    }
}

// two contest, specified margin
fun makeContestOA(winner: Int, loser: Int, cvrPercent: Double, undervotePercent: Double): OneAuditContest { // TODO set margin

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

    // reported results for the two strata
    val candidates = mapOf(     // candidateName -> [votes(cvr), votes(nocvr)]
        "winner" to listOf((winner * cvrPercent).toInt(), (winner * noCvrPercent).toInt()),
        "loser" to listOf((loser * cvrPercent).toInt(), (loser * noCvrPercent).toInt()),
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
                Nc = stratumSizes[idx],
                Np = 0  // TODO investigate
            )
        )
    }
    return OneAuditContest(info, strata)
}