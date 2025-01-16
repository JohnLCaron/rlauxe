package org.cryptobiotic.rlauxe.oneaudit

import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.OneAuditContest
import org.cryptobiotic.rlauxe.core.OneAuditStratum
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction


fun makeContestOA(margin: Double, N: Int, cvrPercent: Double, skewVotesPercent: Double, undervotePercent: Double): OneAuditContest {
    val winner = ((1.0 + margin) * N / 2).toInt()
    val loser = N - winner
    return makeContestOA(winner, loser, cvrPercent, skewVotesPercent, undervotePercent)
}

// two contest, specified total votes
// divide into two stratum based on cvrPercent
// skewVotesPercent positive: move winner votes to cvr stratum, else to nocvr stratum
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

    val stratumNames = when {
        (cvrPercent == 0.0) -> listOf("noCvr")
        (cvrPercent == 1.0) -> listOf("hasCvr")
        else -> listOf("noCvr", "hasCvr")
    }
    val stratumSizes = when {
        (cvrPercent == 0.0) -> listOf((totalVotes * noCvrPercent).toInt())
        (cvrPercent == 1.0) -> listOf((totalVotes * cvrPercent).toInt())
        else -> listOf((totalVotes * noCvrPercent).toInt(), (totalVotes * cvrPercent).toInt())
    }
    val cvrIdx = if (cvrPercent == 1.0) 0 else 1

    // reported results for the two strata
    val votes = when {
        (cvrPercent == 0.0 || cvrPercent == 1.0) -> mapOf(
            "winner" to listOf((winner + skewVotes).toInt()),
            "loser" to listOf((loser - skewVotes).toInt()),
        ) else -> mapOf(
            "winner" to listOf((winner * noCvrPercent - skewVotes).toInt(), (winner * cvrPercent + skewVotes).toInt()),
            "loser" to listOf((loser * noCvrPercent + skewVotes).toInt(), (loser * cvrPercent - skewVotes).toInt()),
        )
    }

    val strata = mutableListOf<OneAuditStratum>()
    repeat(stratumNames.size) { idx ->
        strata.add(
            OneAuditStratum(
                stratumNames[idx],
                hasCvrs = (idx == cvrIdx),
                info,
                votes = votes.map { (key, value) -> Pair(info.candidateNames[key]!!, value[idx]) }.toMap(),
                Ng = stratumSizes[idx],
                Np = 0  // TODO investigate
            )
        )
    }
    return OneAuditContest(info, strata)
}