package org.cryptobiotic.rlauxe.oneaudit

import org.cryptobiotic.rlauxe.audit.tabulateVotesWithUndervotes
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.estimate.ContestSimulation
import org.cryptobiotic.rlauxe.util.*
import kotlin.math.max

// TODO use VotesAndUndervotes

// margin = (winner - loser) / Nc
// (winner - loser) = margin * Nc
// (winner + loser) = nvotes
// 2 * winner = margin * Nc + nvotes
// winner = (margin * Nc + nvotes) / 2
fun makeContestOA(margin: Double, Nc: Int, cvrPercent: Double, undervotePercent: Double, phantomPercent: Double, skewPct: Double = 0.0): OneAuditContest {
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
fun makeContestOA(winnerVotes: Int, loserVotes: Int, cvrPercent: Double, undervotePercent: Double, phantomPercent: Double, skewPct: Double = 0.0, contestId: Int = 0): OneAuditContest {
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

    val result =  OneAuditContest.make(info, votesCvr, cvrNc, pools, Np = Np)
    if (result.Nc != Nc) {
        println("nope")
    }

    return result
}

// used by simulateSampleSizeOneAuditAssorter()
fun OneAuditContest.makeTestMvrs(prefix: String = "card"): List<Cvr> {
    val cvrs = mutableListOf<Cvr>()

    // add the regular cvrs
    if (this.cvrNc > 0) { // blca has cvrNc == 0
        val contestCvrs = Contest(this.info, voteInput = this.cvrVotes, iNc = this.cvrNc, Np = 0)
        val sim = ContestSimulation(contestCvrs)
        val cvrCvrs = sim.makeCvrs(prefix)
        cvrs.addAll(cvrCvrs) // makes a new, independent set of simulated Cvrs with the contest's votes, undervotes, and phantoms.
        cvrs.forEach { require(it.poolId == null) }
    }

    // TODO multiwinner contests
    this.pools.values.forEach { pool ->
        val contestPool = Contest(this.info, pool.votes, iNc = pool.ncards, Np = 0)
        val poolSim = ContestSimulation(contestPool)
        val poolCvrs = poolSim.makeCvrs("${prefix}P", pool.poolId)

        if (pool.ncards != poolCvrs.size) {
            println("why")
        }
        poolCvrs.forEach { require (it.poolId != null) }
        cvrs.addAll(poolCvrs)
    }

    // add phantoms
    repeat(this.Np) {
        cvrs.add(Cvr("phantom$it", mapOf(info.id to intArrayOf()), phantom = true))
    }

    if (this.Nc != cvrs.size) {
        println("why")
    }
    require(this.Nc == cvrs.size)
    cvrs.shuffle()
    return cvrs
}

// TODO test this
fun OneAuditContest.makeTestMvrs(sampleLimit: Int): List<Cvr> {
    if (sampleLimit < 0 || this.Nc <= sampleLimit) return this.makeTestMvrs()

    // otherwise scale everything
    val scale = sampleLimit / this.Nc.toDouble()

    // add the regular cvrs
    val cvrs = mutableListOf<Cvr>()
    cvrs.addAll(makeScaledCvrs(this, scale, null)) // makes a new, independent set of simulated Cvrs with the contest's votes, undervotes, and phantoms.

    // add the pooled cvrs
    this.pools.values.forEach { pool: BallotPool ->
        val contestPool = Contest(this.info, pool.votes, iNc = pool.ncards, Np = 0)
        cvrs.addAll(makeScaledCvrs(contestPool, scale, pool.poolId))
    }

    // require(this.Nc == cvrs.size)
    cvrs.shuffle()
    return cvrs
}

fun makeScaledCvrs(org: Contest, scale: Double, poolId: Int?): List<Cvr> {
    val sNc = roundToInt(scale * org.Nc)
    val sNp = roundToInt(scale * org.Np)
    val scaledVotes = org.votes.map { (id, nvotes) -> id to roundToInt(scale * nvotes) }.toMap()

    // add the regular cvrs
    val contestCvrs = Contest(org.info, scaledVotes, iNc = sNc, Np = sNp)
    val sim = ContestSimulation(contestCvrs)
    return sim.makeCvrs("scaled", poolId)
}

// data class BallotPool(
//    val name: String,
//    val id: Int,
//    val contest:Int,
//    val ncards: Int,
//    val votes: Map<Int, Int>, // candid-> nvotes
//)
/* fun makePoolCvrs(pool: BallotPool): List<Cvr> {
    val cvrs = mutableListOf<Cvr>()
    pool.votes.forEach { (candId, nvotes) ->
        repeat(nvotes) { cvrs.add(makeCvr(info.id, candId)) }
    }
    // undervotes
    repeat(contest.undervotes) {
        cvrs.add(makeCvr(info.id))
    }
    // phantoms
    // TODO if dont know Np, assume Np = Nc = nvotes ??
    repeat(this.Np) {
        cvrs.add(Cvr(strataName, mapOf(info.id to IntArray(0)), phantom = true))
    }
    return cvrs
} */

fun makeCvr(contestId: Int, winner: Int?, phantom: Boolean, poolId: Int?): Cvr {
    val votes = mutableMapOf<Int, IntArray>()
    votes[contestId] = if (winner != null) intArrayOf(winner) else IntArray(0)
    return Cvr("pool $poolId", votes, phantom, poolId)
}

////////////////////////////////////////

fun makeTestMvrs(oaContest: OneAuditContest): List<Cvr> {
    val cvrs = mutableListOf<Cvr>()
    val info = oaContest.info

    // add the regular cvrs
    if (oaContest.cvrNc > 0) {
        val cvrVotes = oaContest.cvrVotes
        val cvrNc = oaContest.cvrNc
        val cvrVotesTotal = cvrVotes.values.sumOf { it }
        val cvrUndervotes = cvrNc * info.voteForN - cvrVotesTotal
        val vunderCvrs = VotesAndUndervotes(cvrVotes, cvrUndervotes, info.voteForN)

        val cvrCvrs = makeVunderCvrs(mapOf(info.id to vunderCvrs), poolId = null)
        cvrs.addAll(cvrCvrs) // makes a new, independent set of simulated Cvrs with the contest's votes, undervotes, and phantoms.
    }

    // add the pooled cvrs
    oaContest.pools.forEach { (contestId, pool) ->
        val vunderPool = pool.votesAndUndervotes(info.voteForN)
        val vunderCvrs = makeVunderCvrs(mapOf(info.id to vunderPool), poolId = pool.poolId)
        cvrs.addAll(vunderCvrs)
    }

    // add phantoms
    repeat(oaContest.Np) {
        cvrs.add(Cvr("phantom$it", mapOf(oaContest.info.id to intArrayOf()), phantom = true))
    }

    if (oaContest.Nc != cvrs.size) {
        println("why")
    }
    require(oaContest.Nc == cvrs.size)
    cvrs.shuffle()
    return cvrs
}

fun checkAssorterAvg(oaContest: OneAuditContest, mvrs: List<Cvr>, show: Boolean = true, check: Boolean = true) {
    val contestUA: OAContestUnderAudit = oaContest.makeContestUnderAudit()
    val clcaAssertion = contestUA.minAssertion() as ClcaAssertion
    val clcaAssorter = clcaAssertion.cassorter as OneAuditClcaAssorter
    println(clcaAssorter)

    val pAssorter = clcaAssorter.assorter()
    val oaAssorter = clcaAssorter.oaAssorter
    val passortAvg = margin2mean(pAssorter.calcAssorterMargin(contestUA.id, mvrs))
    val oassortAvg = margin2mean(oaAssorter.calcAssorterMargin(contestUA.id, mvrs))

    if (show) {
        val mvrVotes = tabulateVotesWithUndervotes(mvrs.iterator(), oaContest.id, contestUA.ncandidates)
        println("  mvrVotes = ${mvrVotes} NC=${oaContest.Nc}")
        print("     pAssorter reportedMargin=${pAssorter.reportedMargin()} reportedAvg=${pAssorter.reportedMean()} assortAvg = $passortAvg")
        if (doubleIsClose(pAssorter.reportedMean(), passortAvg)) println() else println(" ******")
        print("     oaAssorter reportedMargin=${oaAssorter.reportedMargin()} reportedAvg=${oaAssorter.reportedMean()} assortAvg = $oassortAvg")
        if (doubleIsClose(oaAssorter.reportedMean(), oassortAvg)) println() else println(" ******")
        if (doubleIsClose(oaAssorter.reportedMean(), pAssorter.reportedMean())) println() else println(" ****** oaAssorter.reportedMean() != pAssorter.reportedMean()")
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