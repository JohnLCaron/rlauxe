package org.cryptobiotic.rlauxe.oneaudit

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.checkEquivilentVotes
import org.cryptobiotic.rlauxe.audit.tabulateVotesFromCvrs
import org.cryptobiotic.rlauxe.audit.tabulateVotesWithUndervotes
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.*
import kotlin.math.max

// margin = (winner - loser) / Nc
// (winner - loser) = margin * Nc
// (winner + loser) = nvotes
// 2 * winner = margin * Nc + nvotes
// winner = (margin * Nc + nvotes) / 2
fun makeContestOA(
    margin: Double,
    Nc: Int,
    cvrPercent: Double,
    undervotePercent: Double,
    phantomPercent: Double,
    skewPct: Double = 0.0,
): OneAuditContest {
    val nvotes = roundToInt(Nc * (1.0 - undervotePercent - phantomPercent))
    // margin = (winner - loser) / Nc
    // nvotes = winner + loser
    // margin * Nc = (winner - (nvotes - winner))
    // margin * Nc = (winner - nvotes + winner)
    // (margin * Nc + nvotes) / 2 = winner
    val winner = roundToInt((margin * Nc + nvotes) / 2)
    val loser = nvotes - winner
    require(doubleIsClose(margin, (winner - loser) / Nc.toDouble()))
    return makeContestOA(winner, loser, cvrPercent, undervotePercent, phantomPercent, skewPct)
}

// two contest, specified total votes
// divide into two stratum based on cvrPercent
// skewVotesPercent positive: move winner votes to cvr stratum, else to nocvr stratum
fun makeContestOA(
    winnerVotes: Int,
    loserVotes: Int,
    cvrPercent: Double,
    undervotePercent: Double,
    phantomPercent: Double,
    skewPct: Double = 0.0,
    contestId: Int = 0,
): OneAuditContest {
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

    val votesCvr = mapOf(0 to winnerCvr + skewVotes, 1 to loserCvr)
    val votesNoCvr = mapOf(0 to winnerPool - skewVotes, 1 to loserPool)
    val votesCvrSum = votesCvr.values.sum()
    val votesPoolSum = votesNoCvr.values.sum()

    val undervotes = undervotePercent * Nc
    val cvrUnderVotes = roundToInt(undervotes * cvrPercent)
    val poolUnderVotes = roundToInt(undervotes - cvrUnderVotes)

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

    val expectNc = noCvrSize + cvrSize + cvrUnderVotes + poolUnderVotes + Np
    if (expectNc != Nc) {
        println("nope")
    }

    val cvrNc = votesCvrSum + cvrUnderVotes
    if (cvrNc < votesCvrSum) {
        println("nope")
    }

    val expectNc3 = pools.sumOf { it.ncards } + cvrNc + Np
    if (expectNc3 != Nc) {
        println("nope")
    }

    val result = OneAuditContest.make(info, votesCvr, cvrNc, pools, Np = Np)
    if (result.Nc != Nc) {
        println("nope")
    }

    return result
}

fun makeTestMvrsScaled(oaContest: OneAuditContest, sampleLimit: Int, show: Boolean = false): List<Cvr> {
    if (sampleLimit < 0 || oaContest.Nc <= sampleLimit) return makeTestMvrs(oaContest)

    // otherwise scale everything
    val scale = sampleLimit / oaContest.Nc.toDouble()

    // add the regular cvrs
    val id = oaContest.id
    val voteForN = oaContest.info.voteForN
    val cvrs = mutableListOf<Cvr>()
    cvrs.addAll(makeScaledCvrs(id, oaContest.cvrNc, oaContest.Np(), oaContest.cvrVotes, scale, voteForN, poolId = null))

    // add the pooled cvrs
    oaContest.pools.values.forEach { pool: BallotPool ->
        cvrs.addAll(makeScaledCvrs(id, Nc = pool.ncards, Np = 0, pool.votes, scale, voteForN, poolId = pool.poolId))
    }

    // the whole point is that cvrs.size != Nc
    if (show) {
        println("  want scale = $scale have scale = ${cvrs.size / oaContest.Nc.toDouble()}")
    }
    cvrs.shuffle()
    return cvrs
}

fun makeScaledCvrs(
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
}

////////////////////////////////////////

fun makeTestMvrs(oaContest: OneAuditContest): List<Cvr> {
    val cvrs = mutableListOf<Cvr>()
    val info = oaContest.info

    // add the regular cvrs
    if (oaContest.cvrNc > 0) {
        val vunderCvrs = VotesAndUndervotes(oaContest.cvrVotes, oaContest.cvrUndervotes, info.voteForN)
        val cvrCvrs = makeVunderCvrs(mapOf(info.id to vunderCvrs), poolId = null)
        cvrs.addAll(cvrCvrs) // makes a new, independent set of simulated Cvrs with the contest's votes, undervotes, and phantoms.
    }

    // add the pooled cvrs
    oaContest.pools.values.forEach { pool ->
        val vunderPool = pool.votesAndUndervotes(info.voteForN)
        val vunderCvrs = makeVunderCvrs(mapOf(info.id to vunderPool), poolId = pool.poolId)
        cvrs.addAll(vunderCvrs)
    }

    // add phantoms
    repeat(oaContest.Np()) {
        cvrs.add(Cvr("phantom$it", mapOf(oaContest.info.id to intArrayOf()), phantom = true))
    }

    if (oaContest.Nc != cvrs.size) {
        println("why")
    }
    require(oaContest.Nc == cvrs.size)
    cvrs.shuffle()
    return cvrs
}

// lets say all the pools have to be the same poolId
fun makeTestNonPooledMvrs(oaContests: List<OneAuditContest>): List<Cvr> {

    val contestVunders = mutableMapOf<Int, VotesAndUndervotes>()
    oaContests.forEach { oaContest ->
        contestVunders[oaContest.id] = VotesAndUndervotes(oaContest.cvrVotes, oaContest.cvrUndervotes, oaContest.info.voteForN)
    }

    val cvrs = makeVunderCvrs(contestVunders, poolId = null)
    // println("makeRedactedCvrs cvrs=${cvrs.size}")
    val tabVotes: Map<Int, Map<Int, Int>> = tabulateVotesFromCvrs(cvrs.iterator())

    contestVunders.forEach { (contestId, vunders) ->
        val tv = tabVotes[contestId] ?: emptyMap()
        if (!checkEquivilentVotes(vunders.candVotesSorted, tv)) {
            println("  tabVotes=${tv}")
            println("  vunders.candVotesSorted ${vunders.candVotesSorted}")
            require(checkEquivilentVotes(vunders.candVotesSorted, tv))
        }

        val tabsWith =
            tabulateVotesWithUndervotes(cvrs.iterator(), contestId, vunders.votes.size, vunders.voteForN).toSortedMap()
        if (!checkEquivilentVotes(vunders.votesAndUndervotes(), tabsWith)) {
            println("  tabsWith=${tabsWith}")
            println("  vunders.votesAndUndervotes()= ${vunders.votesAndUndervotes()}")
            require(checkEquivilentVotes(vunders.votesAndUndervotes(), tabsWith))
        }
    }
    return cvrs
}

// lets say all the pools have to be the same poolId
fun makeTestPooledMvrs(oaContests: List<OneAuditContest>, poolId: Int): List<Cvr> {

    val contestVotes = mutableMapOf<Int, VotesAndUndervotes>()
    oaContests.forEach { oaContest ->
        oaContest.pools.values.forEach { pool ->
            contestVotes[oaContest.id] = pool.votesAndUndervotes(oaContest.info.voteForN)
        }
    }

    val cvrs = makeVunderCvrs(contestVotes, poolId = poolId)
    // println("makeRedactedCvrs cvrs=${cvrs.size}")
    val tabVotes: Map<Int, Map<Int, Int>> = tabulateVotesFromCvrs(cvrs.iterator())

    contestVotes.forEach { (contestId, vunders) ->
        val tv = tabVotes[contestId] ?: emptyMap()
        if (!checkEquivilentVotes(vunders.candVotesSorted, tv)) {
            println("  tabVotes=${tv}")
            println("  vunders.candVotesSorted ${vunders.candVotesSorted}")
            require(checkEquivilentVotes(vunders.candVotesSorted, tv))
        }

        val tabsWith =
            tabulateVotesWithUndervotes(cvrs.iterator(), contestId, vunders.votes.size, vunders.voteForN).toSortedMap()
        if (!checkEquivilentVotes(vunders.votesAndUndervotes(), tabsWith)) {
            println("  tabsWith=${tabsWith}")
            println("  vunders.votesAndUndervotes()= ${vunders.votesAndUndervotes()}")
            require(checkEquivilentVotes(vunders.votesAndUndervotes(), tabsWith))
        }
    }
    return cvrs
}

fun checkAssorterAvgFromCards(oaContest: OneAuditContest, cards: Iterable<AuditableCard>, show: Boolean = true, check: Boolean = true) {
    return checkAssorterAvg(oaContest, cards.map{ it.cvr() }, show, check)
}

fun checkAssorterAvg(oaContest: OneAuditContest, mvrs: Iterable<Cvr>, show: Boolean = true, check: Boolean = true) {
    val contestUA: OAContestUnderAudit = oaContest.makeContestUnderAudit()
    val clcaAssertion = contestUA.minAssertion() as ClcaAssertion
    val clcaAssorter = clcaAssertion.cassorter as OneAuditClcaAssorter
    println(clcaAssorter)

    val pAssorter = clcaAssorter.assorter()
    val oaAssorter = OaPluralityAssorter.makeFromClcaAssorter(clcaAssorter)

    val passortAvg = margin2mean(pAssorter.calcAssorterMargin(contestUA.id, mvrs))
    val oassortAvg = margin2mean(oaAssorter.calcAssorterMargin(contestUA.id, mvrs))

    if (show) {
        val mvrVotes = tabulateVotesWithUndervotes(mvrs.iterator(), oaContest.id, contestUA.ncandidates)
        println("  mvrVotes = ${mvrVotes} NC=${oaContest.Nc}")
        print("     pAssorter reportedMargin=${pAssorter.reportedMargin()} reportedAvg=${pAssorter.reportedMean()} assortAvg = $passortAvg")
        if (doubleIsClose(pAssorter.reportedMean(), passortAvg)) println() else println(" ******")
        print("     oaAssorter reportedMargin=${oaAssorter.reportedMargin()} reportedAvg=${oaAssorter.reportedMean()} assortAvg = $oassortAvg")
        if (doubleIsClose(oaAssorter.reportedMean(), oassortAvg)) println() else println(" ******")
        if (doubleIsClose(
                oaAssorter.reportedMean(),
                pAssorter.reportedMean()
            )
        ) println() else println(" ****** oaAssorter.reportedMean() != pAssorter.reportedMean()")
    }

    if (check) {
        require(doubleIsClose(oaAssorter.reportedMean(), oassortAvg))
        require(doubleIsClose(oaAssorter.reportedMean(), pAssorter.reportedMean()))
    }
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
    // println("  allCvrs.size=${allCvrs.size}")
    return allCvrs
}

fun mergeCvrWithSamePool(cvr1: Cvr, cvr2: Cvr): Cvr {
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