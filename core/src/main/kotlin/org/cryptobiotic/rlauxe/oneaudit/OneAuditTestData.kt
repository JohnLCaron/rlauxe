package org.cryptobiotic.rlauxe.oneaudit

import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.estimate.ContestSimulation
import org.cryptobiotic.rlauxe.util.CvrBuilders
import org.cryptobiotic.rlauxe.util.roundToInt
import kotlin.math.round
import kotlin.random.Random

// margin = (winner - loser) / Nc
// (winner - loser) = margin * Nc
// (winner + loser) = nvotes
// 2 * winner = margin * Nc + nvotes
// winner = (margin * Nc + nvotes) / 2
fun makeContestOA(margin: Double, Nc: Int, cvrPercent: Double, skewVotesPercent: Double, undervotePercent: Double, phantomPercent: Double): OneAuditContest {
    val nvotes = roundToInt(Nc * (1.0 - undervotePercent - phantomPercent))
    val winner = ((margin * Nc + nvotes) / 2)
    val loser = nvotes - winner
    return makeContestOA(roundToInt(winner), roundToInt(loser), cvrPercent, skewVotesPercent, undervotePercent, phantomPercent)
}

// two contest, specified total votes
// divide into two stratum based on cvrPercent
// skewVotesPercent positive: move winner votes to cvr stratum, else to nocvr stratum
fun makeContestOA(winnerVotes: Int, loserVotes: Int, cvrPercent: Double, skewVotesPercent: Double, undervotePercent: Double, phantomPercent: Double): OneAuditContest {
    require(cvrPercent > 0.0)

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

    val nvotes = winnerVotes + loserVotes
    val Nc = roundToInt(nvotes / (1.0 - undervotePercent - phantomPercent))
    // val noCvrPercent = (1.0 - cvrPercent)
    val skewVotes = skewVotesPercent * Nc

    val cvrSize = roundToInt(Nc * cvrPercent)
    val noCvrSize = Nc - cvrSize

    // reported results for the two strata
    val winnerCvr = roundToInt(winnerVotes * cvrPercent + skewVotes)
    val loserCvr = roundToInt(loserVotes * cvrPercent)
    val votesCvr = mapOf(0 to winnerCvr, 1 to loserCvr)
    val votesNoCvr = mapOf(0 to (winnerVotes - winnerCvr), 1 to (loserVotes - loserCvr))

    val pools = mutableListOf<BallotPool>()
    pools.add(
        // data class BallotPool(val name: String, val id: Int, val contest:Int, val ncards: Int, val votes: Map<Int, Int>) {
        BallotPool(
            "noCvr",
            1, // poolId
            0, // contestId
            noCvrSize,
            votes = votesNoCvr,
        )
    )

    //    override val info: ContestInfo,
    //    cvrVotes: Map<Int, Int>,   // candidateId -> nvotes;  sum is nvotes or V_c
    //    cvrNc: Int,
    //    val pools: Map<Int, OneAuditPool>, // pool id -> pool
    return OneAuditContest(info, votesCvr, cvrSize, pools.associateBy { it.id })
}

fun makeContestOA(Nc: Int, margin: Double, poolPct: Double, poolMargin: Double) : OneAuditContest {
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

    val winnerTotal = roundToInt(Nc * ((margin + 1) / 2))
    val loserTotal = Nc - winnerTotal

    val poolSize = roundToInt(poolPct * Nc)
    val poolWinner = roundToInt(poolSize * ((poolMargin + 1) / 2))
    val poolLoser = poolSize - poolWinner
    val votesPool = mapOf(0 to poolWinner, 1 to poolLoser)

    val cvrSize = Nc - poolSize
    val cvrWinner = winnerTotal - poolWinner
    val cvrLoser = loserTotal - poolLoser
    val votesCvr = mapOf(0 to cvrWinner, 1 to cvrLoser)

    val pools = mutableListOf<BallotPool>()
    pools.add(
        // data class BallotPool(val name: String, val id: Int, val contest:Int, val ncards: Int, val votes: Map<Int, Int>) {
        BallotPool(
            "noCvr",
            1, // poolId
            0, // contestId
            poolSize,
            votes = votesPool,
        )
    )

    //    override val info: ContestInfo,
    //    cvrVotes: Map<Int, Int>,   // candidateId -> nvotes;  sum is nvotes or V_c
    //    cvrNc: Int,
    //    val pools: Map<Int, OneAuditPool>, // pool id -> pool
    return OneAuditContest(info, votesCvr, cvrSize, pools.associateBy { it.id })
}

// used by simulateSampleSizeOneAuditAssorter()
fun OneAuditContest.makeTestCvrs(): List<Cvr> {
    val cvrs = mutableListOf<Cvr>()

    // add the regular cvrs
    val contestCvrs = Contest(this.info, this.cvrVotes, Nc = this.cvrNc, Np = 0)
    val sim = ContestSimulation(contestCvrs)
    cvrs.addAll(sim.makeCvrs()) // makes a new, independent set of simulated Cvrs with the contest's votes, undervotes, and phantoms.

    // TODO multiwinner contests
    this.pools.values.forEach { pool ->
        val contestPool = Contest(this.info, pool.votes, Nc = pool.ncards, Np = 0)
        val poolSim = ContestSimulation(contestPool)
        val poolCvrs = poolSim.makeCvrs(pool.id)
        if (pool.ncards != poolCvrs.size) {
            poolSim.makeCvrs(pool.id)
        }
        // TODO why dont these agree?
        //if (pool.ncards != poolCvrs.size)
        //    println("why")
        // require(pool.ncards == poolCvrs.size)
        cvrs.addAll(poolCvrs)
    }
    // require(this.Nc == cvrs.size)
    cvrs.shuffle()
    return cvrs
}

fun OneAuditContest.makeTestCvrs(sampleLimit: Int): List<Cvr> {
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