package org.cryptobiotic.rlauxe.oneaudit

import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.estimate.ContestSimulation
import org.cryptobiotic.rlauxe.util.doubleIsClose
import org.cryptobiotic.rlauxe.util.roundToInt
import kotlin.math.max
import kotlin.math.min

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
fun makeContestOA(winnerVotes: Int, loserVotes: Int, cvrPercent: Double, undervotePercent: Double, phantomPercent: Double, skewPct: Double = 0.0): OneAuditContest {
    require(cvrPercent > 0.0)

    // the candidates
    val info = ContestInfo(
        "ContestOA", 0,
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
            0, // contestId
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

    val result =  OneAuditContest(info, votesCvr, cvrNc, pools.associateBy { it.id }, Np = Np)
    if (result.Nc != Nc) {
        println("nope")
    }

    return result
}

// used by simulateSampleSizeOneAuditAssorter()
fun OneAuditContest.makeTestCvrs(): List<Cvr> {
    val cvrs = mutableListOf<Cvr>()

    // add the regular cvrs
    val contestCvrs = Contest(this.info, voteInput=this.cvrVotes, Nc = this.cvrNc, Np = 0)
    val sim = ContestSimulation(contestCvrs)
    cvrs.addAll(sim.makeCvrs()) // makes a new, independent set of simulated Cvrs with the contest's votes, undervotes, and phantoms.
    cvrs.forEach { require (it.poolId == null) }
    // TODO multiwinner contests
    this.pools.values.forEach { pool ->
        val contestPool = Contest(this.info, pool.votes, Nc = pool.ncards, Np = 0)
        val poolSim = ContestSimulation(contestPool)
        val poolCvrs = poolSim.makeCvrs(pool.id)
        //if (pool.ncards != poolCvrs.size) {
        //    poolSim.makeCvrs(pool.id)
        //}
        require(pool.ncards == poolCvrs.size) {
            println("why")
        }
        poolCvrs.forEach { require (it.poolId != null) }

        cvrs.addAll(poolCvrs)
    }
    // add phantoms
    repeat(this.Np) {
        cvrs.add(Cvr("phantom$it", mapOf(info.id to intArrayOf()), phantom = true))
    }
    require(this.Nc == cvrs.size) {
        println("why")
    }
    cvrs.shuffle()
    return cvrs
}

fun OneAuditContest.makeTestCvrs(sampleLimit: Int): List<Cvr> { // TODO fix this
    if (sampleLimit < 0 || this.Nc <= sampleLimit) return this.makeTestCvrs()

    // otherwise scale everything
    val scale = sampleLimit / this.Nc.toDouble()

    // add the regular cvrs
    val cvrs = mutableListOf<Cvr>()
    cvrs.addAll(makeScaledCvrs(this.makeContest(), scale, null)) // makes a new, independent set of simulated Cvrs with the contest's votes, undervotes, and phantoms.

    // add the pooled cvrs
    this.pools.values.forEach { pool: BallotPool ->
        val contestPool = Contest(this.info, pool.votes, Nc = pool.ncards, Np = 0)
        cvrs.addAll(makeScaledCvrs(contestPool, scale, pool.id))
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
    val contestCvrs = Contest(org.info, scaledVotes, Nc = sNc, Np = sNp)
    val sim = ContestSimulation(contestCvrs)
    return sim.makeCvrs(poolId)
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