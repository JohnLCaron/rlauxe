package org.cryptobiotic.rlauxe.oneaudit

import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.OneAuditContest
import org.cryptobiotic.rlauxe.core.OneAuditStratum
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.util.roundToInt
import kotlin.math.round

// margin = (winner - loser) / Nc
// (winner - loser) = margin * Nc
// (winner + loser) = nvotes
// 2 * winner = margin * Nc + nvotes
// winner = (margin * Nc + nvotes) / 2
fun makeContestOA(margin: Double, Nc: Int, cvrPercent: Double, skewVotesPercent: Double, undervotePercent: Double, phantomPercent: Double): OneAuditContest {
    val nvotes = round(Nc * (1.0 - undervotePercent - phantomPercent))
    val winner = ((margin * Nc + nvotes) / 2)
    val loser = nvotes - winner
    return makeContestOA(roundToInt(winner), roundToInt(loser), cvrPercent, skewVotesPercent, undervotePercent, phantomPercent)
}

// two contest, specified total votes
// divide into two stratum based on cvrPercent
// skewVotesPercent positive: move winner votes to cvr stratum, else to nocvr stratum
fun makeContestOA(winner: Int, loser: Int, cvrPercent: Double, skewVotesPercent: Double, undervotePercent: Double, phantomPercent: Double): OneAuditContest {

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

    val nvotes = winner + loser
    // Nc = nvotes + (undervotePercent + phantomPercent) * Nc
    // Nc - (undervotePercent + phantomPercent) * Nc = nvotes
    // Nc (1 - undervotePercent - phantomPercent) = nvotes
    // Nc  = nvotes / (1 - undervotePercent - phantomPercent)
    val Nc = (nvotes / (1.0 - undervotePercent - phantomPercent))
    val noCvrPercent = (1.0 - cvrPercent)
    val skewVotes = skewVotesPercent * Nc

    val stratumNames = when {
        (cvrPercent == 0.0) -> listOf("noCvr")
        (cvrPercent == 1.0) -> listOf("hasCvr")
        else -> listOf("noCvr", "hasCvr")
    }
    val stratumSizes = when {
        (cvrPercent == 0.0) -> listOf(roundToInt(Nc * noCvrPercent))
        (cvrPercent == 1.0) -> listOf(roundToInt(Nc * cvrPercent))
        else -> listOf(roundToInt(Nc * noCvrPercent), roundToInt(Nc * cvrPercent))
    }
    val cvrIdx = if (cvrPercent == 1.0) 0 else 1

    // reported results for the two strata
    val votes = when {
        (cvrPercent == 0.0 || cvrPercent == 1.0) -> mapOf(
            "winner" to listOf(roundToInt(winner + skewVotes)),
            "loser" to listOf(roundToInt(loser - skewVotes)),
        ) else -> mapOf(
            "winner" to listOf(roundToInt(winner * noCvrPercent - skewVotes), roundToInt(winner * cvrPercent + skewVotes)),
            "loser" to listOf(roundToInt(loser * noCvrPercent + skewVotes), roundToInt(loser * cvrPercent - skewVotes)),
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
                Np = roundToInt(phantomPercent * stratumSizes[idx])
            )
        )
    }
    return OneAuditContest(info, strata)
}