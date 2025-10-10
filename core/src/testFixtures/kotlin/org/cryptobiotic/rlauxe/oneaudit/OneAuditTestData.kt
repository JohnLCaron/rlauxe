package org.cryptobiotic.rlauxe.oneaudit

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.*
import kotlin.Int

// margin = (winner - loser) / Nc
// (winner - loser) = margin * Nc
// (winner + loser) = nvotes
// 2 * winner = margin * Nc + nvotes
// winner = (margin * Nc + nvotes) / 2
fun makeOneContestUA(
    margin: Double,
    Nc: Int,
    cvrFraction: Double,
    undervoteFraction: Double,
    phantomFraction: Double,
): Triple<OAContestUnderAudit, List<BallotPool>, List<Cvr>> {
    val nvotes = roundToClosest(Nc * (1.0 - undervoteFraction - phantomFraction))
    // margin = (w - l) / Nc ; nvotes = w + l
    // margin * Nc + l = w
    // margin * Nc + nvotes - w = w
    // margin * Nc + nvotes = 2w
    // (margin * Nc + nvotes)/2 = w
    val winner = roundToClosest((margin * Nc + nvotes) / 2)
    val loser = nvotes - winner
    return makeOneContestUA(winner, loser, cvrFraction, undervoteFraction, phantomFraction)
}

// two contest, specified total votes
// divide into two stratum based on cvrPercent
// skewVotesPercent positive: move winner votes to cvr stratum, else to nocvr stratum
fun makeOneContestUA(
    winnerVotes: Int,
    loserVotes: Int,
    cvrFraction: Double,
    undervoteFraction: Double,
    phantomFraction: Double,
    contestId: Int = 0,
): Triple<OAContestUnderAudit, List<BallotPool>, List<Cvr>> {
    require(cvrFraction > 0.0)

    // the candidates
    val info = ContestInfo(
        "ContestOA", contestId,
        mapOf(
            "winner" to 0,
            "loser" to 1,
        ),
        SocialChoiceFunction.PLURALITY,
        nwinners = 1,
    )

    val nvotes = winnerVotes + loserVotes
    // nvotes = Nc * (1.0 - undervotePercent - phantomPercent)
    // Nc = nvotes / (1.0 - undervotePercent - phantomPercent)
    val Nc = roundToClosest(nvotes / (1.0 - undervoteFraction - phantomFraction))
    val Np = roundToClosest(Nc * phantomFraction)
    val Ncast = Nc - Np

    val cvrSize = roundToClosest(Ncast * cvrFraction)
    val noCvrSize = Ncast - cvrSize
    require(cvrSize + noCvrSize == Ncast)

    // reported results for the two strata
    val nvotesCvr = nvotes * cvrFraction

    val winnerCvr = roundToClosest(winnerVotes * cvrFraction)
    val loserCvr = roundToClosest(nvotesCvr - winnerCvr)

    val winnerPool = winnerVotes - winnerCvr
    val loserPool = loserVotes - loserCvr

    val cvrVotes = mapOf(0 to winnerCvr, 1 to loserCvr)
    val votesNoCvr = mapOf(0 to winnerPool, 1 to loserPool)
    val votesCvrSum = cvrVotes.values.sum()
    val votesPoolSum = votesNoCvr.values.sum()

    val undervotes = undervoteFraction * Nc
    val cvrUndervotes = roundToClosest(undervotes * cvrFraction)
    val poolUnderVotes = roundToClosest(undervotes - cvrUndervotes)

    val poolNcards = votesPoolSum + poolUnderVotes
    val pools = listOf(
        // data class BallotPool(val name: String, val id: Int, val contest:Int, val ncards: Int, val votes: Map<Int, Int>) {
        BallotPool(
            "noCvr",
            1, // poolId
            contestId, // contestId
            ncards = poolNcards,
            votes = votesNoCvr,
        )
    )

    val expectNc = noCvrSize + cvrSize + Np
    if (expectNc != Nc) {
        println("fail1")
    }

    val cvrNc = votesCvrSum + cvrUndervotes
    if (cvrNc < votesCvrSum) {
        println("fail2")
    }

    val expectNc3 = pools.sumOf { it.ncards } + cvrNc + Np
    if (expectNc3 != Nc) {
        println("fail3")
    }

    val contest = Contest(info, mapOf(0 to winnerVotes, 1 to loserVotes), Nc = Nc, Ncast = Nc - Np)
    info.metadata["PoolPct"] = (100.0 * poolNcards / Nc).toInt()

    val oaUA = OAContestUnderAudit(contest, true)
    addOAClcaAssortersFromMargin(listOf(oaUA), pools)

    val cvrs = makeTestMvrs(oaUA, cvrNc, cvrVotes, cvrUndervotes, pools)
    return Triple(oaUA, pools, cvrs)
}

fun makeTestMvrs(
    oaContestUA: OAContestUnderAudit,
    cvrNcards: Int,
    cvrVotes:Map<Int, Int>,
    cvrUndervotes: Int,
    pools: List<BallotPool>): List<Cvr> {

    val oaContest = oaContestUA.contest
    val cvrs = mutableListOf<Cvr>()
    val info = oaContest.info()

    // add the regular cvrs
    if (cvrNcards > 0) {
        val vunderCvrs = VotesAndUndervotes(cvrVotes, cvrUndervotes, info.voteForN)
        val cvrCvrs = makeVunderCvrs(mapOf(info.id to vunderCvrs), poolId = null)
        cvrs.addAll(cvrCvrs) // makes a new, independent set of simulated Cvrs with the contest's votes, undervotes, and phantoms.
    }

    // add the pooled cvrs
    pools.forEach { pool ->
        val vunderPool = pool.votesAndUndervotes(info.voteForN)
        val poolCvrs = makeVunderCvrs(mapOf(info.id to vunderPool), poolId = pool.poolId)
        cvrs.addAll(poolCvrs)
    }

    // add phantoms
    repeat(oaContest.Np()) {
        cvrs.add(Cvr("phantom$it", mapOf(oaContest.info().id to intArrayOf()), phantom = true))
    }

    if (oaContest.Nc() != cvrs.size) {
        println("oaContest.Nc() != cvrs.size")
    }
    // require(oaContest.Nc() == cvrs.size) TODO
    cvrs.shuffle()
    return cvrs
}

fun mergeCvrsWithPools(mvrs1: List<Cvr>, mvrs2: List<Cvr>): List<Cvr> {
    var mvr2count = 0
    val allCvrs = mutableListOf<Cvr>()
    mvrs1.forEach {
        if (mvr2count < mvrs2.size) {
            if (it.poolId == mvrs2[mvr2count].poolId) {
                allCvrs.add(mergeCvrWithSamePool(it, mvrs2[mvr2count]))
                mvr2count++
            } else {
                allCvrs.add(it)
            }
        } else {
            allCvrs.add(it)
        }
    }
    for (i in mvr2count until mvrs2.size) allCvrs.add(mvrs2[mvr2count++])
    return allCvrs
}

private fun mergeCvrWithSamePool(cvr1: Cvr, cvr2: Cvr): Cvr {
    require(cvr1.poolId == cvr2.poolId)

    val cvrb = CvrBuilder2("${cvr1.id}&${cvr2.id}", phantom = false, poolId = cvr1.poolId)
    cvr1.votes.forEach { (contestId, votes) ->
        cvrb.addContest(contestId, votes)
    }
    cvr2.votes.forEach { (contestId, votes) ->
        cvrb.addContest(contestId, votes)
    }
    return cvrb.build()
}

/*
fun makeTestMvrsScaled(oaContestUA: OAContestUnderAudit, sampleLimit: Int, show: Boolean = false): List<Cvr> {
    val oaContest = oaContestUA.contest
    if (sampleLimit < 0 || oaContest.Nc() <= sampleLimit) return makeTestMvrs(oaContestUA)

    // otherwise scale everything
    val scale = sampleLimit / oaContest.Nc().toDouble()

    // add the regular cvrs
    val id = oaContest.id
    val voteForN = oaContest.info().voteForN
    val cvrs = mutableListOf<Cvr>()
    cvrs.addAll(makeScaledCvrs(id, oaContest.cvrNcards, oaContest.Np(), oaContest.cvrVotes, scale, voteForN, poolId = null))

    // add the pooled cvrs
    oaContest.pools.values.forEach { pool: BallotPool ->
        cvrs.addAll(makeScaledCvrs(id, Nc = pool.ncards, Np = 0, pool.votes, scale, voteForN, poolId = pool.poolId))
    }

    // the whole point is that cvrs.size != Nc
    if (show) {
        println("  want scale = $scale have scale = ${cvrs.size / oaContest.Nc().toDouble()}")
    }
    cvrs.shuffle()
    return cvrs
}

private fun makeScaledCvrs(
    contestId: Int,
    Nc: Int,
    Np: Int,
    votes: Map<Int, Int>,
    scale: Double,
    voteForN: Int,
    poolId: Int?,
): List<Cvr> {
    val sNc = roundToInt(scale * Nc)
    val sNp = roundToInt(scale * Np)
    val scaledVotes = votes.map { (id, nvotes) -> id to roundToInt(scale * nvotes) }.toMap()

    val scaledVotesTotal = scaledVotes.values.sumOf { it }
    val scaledUndervotes = (sNc - sNp) * voteForN - scaledVotesTotal
    val vunderCvrs = VotesAndUndervotes(scaledVotes, scaledUndervotes, voteForN)
    return makeVunderCvrs(mapOf(contestId to vunderCvrs), poolId = poolId)
} */

////////////////////////////////////////
