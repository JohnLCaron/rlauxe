package org.cryptobiotic.rlauxe.oneaudit

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.*
import kotlin.Int
import kotlin.math.max

// margin = (winner - loser) / Nc
// (winner - loser) = margin * Nc
// (winner + loser) = nvotes
// 2 * winner = margin * Nc + nvotes
// winner = (margin * Nc + nvotes) / 2
fun makeOneContestUA(
    margin: Double,
    Nc: Int,
    cvrPercent: Double,
    undervotePercent: Double,
    phantomPercent: Double,
    skewPct: Double = 0.0,
): Pair<OAContestUnderAudit, List<Cvr>> {
    val nvotes = roundToInt(Nc * (1.0 - undervotePercent - phantomPercent))
    val winner = roundToInt((margin * Nc + nvotes) / 2)
    val loser = nvotes - winner
    // println("margin = $margin, reported = ${(winner - loser) / Nc.toDouble()} ")
    // require(doubleIsClose(margin, (winner - loser) / Nc.toDouble()))
    return makeOneContestUA(winner, loser, cvrPercent, undervotePercent, phantomPercent, skewPct)
}

// two contest, specified total votes
// divide into two stratum based on cvrPercent
// skewVotesPercent positive: move winner votes to cvr stratum, else to nocvr stratum
fun makeOneContestUA(
    winnerVotes: Int,
    loserVotes: Int,
    cvrPercent: Double,
    undervotePercent: Double,
    phantomPercent: Double,
    skewPct: Double = 0.0,
    contestId: Int = 0,
): Pair<OAContestUnderAudit, List<Cvr>> {
    require(cvrPercent > 0.0)

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
    val Nc = roundToInt(nvotes / (1.0 - undervotePercent - phantomPercent))
    val Np = roundToInt(Nc * phantomPercent)

    val cvrSize = roundToInt(nvotes * cvrPercent)
    val noCvrSize = nvotes - cvrSize
    require(cvrSize + noCvrSize == nvotes)

    // reported results for the two strata
    val nvotesCvr = nvotes * cvrPercent
    val useSkewPct = minOf(skewPct, cvrPercent, 1.0 - cvrPercent)

    val winnerCvr = roundToInt(winnerVotes * cvrPercent)
    val loserCvr = roundToInt(nvotesCvr - winnerCvr)

    val winnerPool = winnerVotes - winnerCvr
    val loserPool = loserVotes - loserCvr
    val skewVotes = max(0, roundToInt(winnerVotes * useSkewPct))

    val cvrVotes = mapOf(0 to winnerCvr + skewVotes, 1 to loserCvr)
    val votesNoCvr = mapOf(0 to winnerPool - skewVotes, 1 to loserPool)
    val votesCvrSum = cvrVotes.values.sum()
    val votesPoolSum = votesNoCvr.values.sum()

    val undervotes = undervotePercent * Nc
    val cvrUndervotes = roundToInt(undervotes * cvrPercent)
    val poolUnderVotes = roundToInt(undervotes - cvrUndervotes)

    val pools = mutableListOf<BallotPool>()
    pools.add(
        // data class BallotPool(val name: String, val id: Int, val contest:Int, val ncards: Int, val votes: Map<Int, Int>) {
        BallotPool(
            "noCvr",
            1, // poolId
            contestId, // contestId
            ncards = votesPoolSum + poolUnderVotes,
            votes = votesNoCvr,
        )
    )

    val expectNc = noCvrSize + cvrSize + cvrUndervotes + poolUnderVotes + Np
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

    val contest = makeContest(info, cvrVotes, cvrNc, pools, Np=undervotes.toInt())

    val oaUA = OAContestUnderAudit(contest, true)
    val clcaAssertions = oaUA.pollingAssertions.map { assertion ->
        val passort = assertion.assorter
        val pairs = pools.map { pool ->
            val avg = pool.reportedAverage(passort.winner(), passort.loser())
            // println("pool ${pool.poolId} avg $avg")
            Pair(pool.poolId, avg)
        }
        val poolAvgs = AssortAvgsInPools(assertion.info.id, pairs.toMap())
        val clcaAssertion = OneAuditClcaAssorter(assertion.info, passort, true, poolAvgs)
        ClcaAssertion(assertion.info, clcaAssertion)
    }
    oaUA.clcaAssertions = clcaAssertions

    val cvrs = makeTestMvrs(oaUA, cvrNc, cvrVotes, cvrUndervotes, pools)
    return Pair(oaUA, cvrs)
}

fun makeContest(info: ContestInfo,
         cvrVotes: Map<Int, Int>,   // candidateId -> nvotes
         cvrNcards: Int,
         pools: List<BallotPool>,   // pools for this contest
         Np: Int): Contest {

    val poolNc = pools.sumOf { it.ncards }
    val Nc = poolNc + cvrNcards + Np

    //// construct total votes
    val voteBuilder = mutableMapOf<Int, Int>()  // cand -> vote
    cvrVotes.forEach { (cand, votes) ->
        val tvote = voteBuilder[cand] ?: 0
        voteBuilder[cand] = tvote + votes
    }
    pools.forEach { pool ->
        require(pool.contest == info.id)
        pool.votes.forEach { (cand, votes) ->
            val tvote = voteBuilder[cand] ?: 0
            voteBuilder[cand] = tvote + votes
        }
    }
    // add 0 candidate votes if needed
    info.candidateIds.forEach {
        if (!voteBuilder.contains(it)) {
            voteBuilder[it] = 0
        }
    }
    val voteInput = voteBuilder.toList().sortedBy{ it.second }.reversed().toMap()

    return Contest(info, voteInput, Nc = Nc, Ncast = poolNc + cvrNcards)
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
