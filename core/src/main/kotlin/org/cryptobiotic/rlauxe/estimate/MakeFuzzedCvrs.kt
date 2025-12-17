package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.core.ContestIF
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.util.ContestVoteBuilder
import org.cryptobiotic.rlauxe.util.CvrBuilder
import org.cryptobiotic.rlauxe.util.CvrBuilders
import org.cryptobiotic.rlauxe.util.CvrContest
import org.cryptobiotic.rlauxe.util.Welford
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach
import kotlin.random.Random

fun makeFuzzedCvrsFrom(contests: List<ContestIF>,
                       cvrs: List<Cvr>,
                       fuzzPct: Double,
): List<Cvr>  {
    return makeFuzzedCvrsFrom(contests.map { it.info()}, cvrs, fuzzPct)
}

// includes undervotes i think
fun makeFuzzedCvrsFrom(infoList: List<ContestInfo>,
                       cvrs: List<Cvr>,
                       fuzzPct: Double,
                       welford: Welford? = null,
                       filter: ((CvrBuilder) -> Boolean)? = null,
                       underVotes: Boolean = true,
): List<Cvr> {
    if (fuzzPct == 0.0) return cvrs

    val infos = infoList.associate { it.id to it }.toMap()
    val isIRV = infoList.associate { it.name to it.isIrv }.toMap()
    var count = 0
    val cvrbs: List<CvrBuilder> = CvrBuilders.convertCvrsToBuilders(infoList, cvrs)

    cvrbs.filter { !it.phantom && (filter == null || filter(it)) }.forEach { cvrb: CvrBuilder ->
        val r = Random.nextDouble(1.0)
        cvrb.contests.forEach { (_, cvb) ->
        if (r < fuzzPct) {
                val ccontest: CvrContest = cvb.contest
                if (isIRV[ccontest.name]!!) {
                    switchCandidateRankings(cvb, ccontest.candidateIds)
                } else {
                    val currId: Int? = if (cvb.votes.size == 0) null else cvb.votes[0] // TODO only one vote allowed, cant use on Raire
                    cvb.votes.clear()
                    // choose a different candidate, or none.
                    val ncandId = chooseNewCandidate(currId, ccontest.candidateIds, underVotes)
                    if (ncandId != null) {
                        cvb.votes.add(ncandId)
                    }
                }
            }
        }
        if (r < fuzzPct) count++
    }

    val expect = (cvrs.size * fuzzPct).toInt()
    val got = (count / cvrs.size.toDouble())
    if (welford != null) { welford.update(fuzzPct - got) }
    // println("   fuzzPct=$fuzzPct expect=$expect count: $count")
    return cvrbs.map { it.build() }
}

// for IRV
fun switchCandidateRankings(cvb: ContestVoteBuilder, candidateIds: List<Int>) {
    val ncands = candidateIds.size
    val size = cvb.votes.size
    if (size == 0) { // no votes -> random one vote
        val candIdx = Random.nextInt(ncands)
        cvb.votes.add(candidateIds[candIdx])
    } else if (size == 1) { // one votes -> no votes
        cvb.votes.clear()
    } else { // switch two randomly selected votes
        val ncandIdx1 = Random.nextInt(size)
        val ncandIdx2 = Random.nextInt(size)
        val save = cvb.votes[ncandIdx1]
        cvb.votes[ncandIdx1] = cvb.votes[ncandIdx2]
        cvb.votes[ncandIdx2] = save
    }
}
