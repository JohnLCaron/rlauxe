package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.doublePrecision
import org.cryptobiotic.rlauxe.doublesAreClose
import org.cryptobiotic.rlauxe.sampling.makeCvr
import org.cryptobiotic.rlauxe.workflow.AuditType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestOneAudit {

    @Test
    fun testMakeContest() {
        val contest = makeContestOA(23000, 21000, cvrPercent = .70, undervotePercent=.01)
        println(contest)
    }
}

// rwo contest, controleld margin
fun makeContestOA(winner: Int, loser: Int, cvrPercent: Double, undervotePercent: Double): ContestOA { // TODO set margin

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
    // val votes0: Map<Int, Int> = candidates.map { (key: String, value: List<Int>) -> Pair(info.candidateNames[key]!!, value[0]) }.toMap()

    // The stratum with linked CVRs comprised 5,294 ballots with 5,218 reported votes in the contest
    // the “no-CVR” stratum comprised 22,372 ballots with 22,082 reported votes.
    val stratumNames = listOf("hasCvr", "noCvr")
    val stratumSizes = listOf((totalVotes * cvrPercent).toInt(), (totalVotes * noCvrPercent).toInt()) // hasCvr, noCvr

    //    val strataName: String,
    //    val info: ContestInfo,
    //    val votes: Map<Int, Int>,   // candidateId -> nvotes
    //    val Nc: Int,  // upper limit on number of ballots in this starata for this contest
    //    val Np: Int,  // number of phantom ballots in this starata for this contest
    val strata = mutableListOf<ContestStratum>()
    repeat(2) { idx ->
        strata.add(
            ContestStratum(
                stratumNames[idx],
                if (idx == 0) AuditType.CARD_COMPARISON else AuditType.POLLING,
                info,
                candidates.map { (key, value) -> Pair(info.candidateNames[key]!!, value[idx]) }.toMap(),
                Nc = stratumSizes[idx],
                Np = 0  // TODO investigate
            )
        )
    }
    return ContestOA(info, strata)
}