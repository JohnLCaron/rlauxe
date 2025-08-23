package org.cryptobiotic.rlauxe.raire

import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.util.df
import kotlin.math.min
import kotlin.random.Random

// called from estimateSampleSize
data class SimulateIrvTestData(
    val contest: RaireContest,
    val minMargin: Double,
    val sampleLimits: Int,
    val excessVotes: Int? = null,
    val quiet: Boolean = true
) {
    val ncands = contest.ncandidates
    val ncards = if (sampleLimits > 0) min(contest.Nc, sampleLimits) else contest.Nc

    fun makeCvrs(): List<Cvr> {
        var count = 0
        val cvrs = mutableListOf<Cvr>()

        val excess = excessVotes ?: (ncards * minMargin).toInt()
        repeat(excess) {
            cvrs.add(makeCvrWithLeading0(count++))
        }
        repeat(ncards - excess - contest.Np()) {
            cvrs.add(makeCvr(count++))
        }
        repeat(contest.Np()) {
            val pcvr = Cvr("pcvr$count", mapOf(contest.id to IntArray(0)), phantom = true)
            count++
            cvrs.add(pcvr)
        }
        // println("makeCvrs: excess=$excess phantoms=${this.phantomCount}")
        cvrs.shuffle()
        return cvrs
    }

    private fun makeCvrWithLeading0(cvrIdx: Int): Cvr {
        // vote for a random number of candidates, including 0
        val nprefs = 1 + Random.nextInt(ncands - 1)
        val prefs = mutableListOf<Int>()
        prefs.add(0) // vote for zero first
        while (prefs.size < nprefs) {
            val voteFor = Random.nextInt(ncands)
            if (!prefs.contains(voteFor)) prefs.add(voteFor)
        }
        return Cvr("cvr$cvrIdx", mapOf(contest.id to prefs.toIntArray()))
    }

    private fun makeCvr(cvrIdx: Int): Cvr {
        // vote for a random number of candidates, including 0
        val nprefs = Random.nextInt(ncands)
        val prefs = mutableListOf<Int>()
        while (prefs.size < nprefs) {
            val voteFor = Random.nextInt(ncands)
            if (!prefs.contains(voteFor)) prefs.add(voteFor)
        }
        return Cvr("cvr$cvrIdx", mapOf(contest.id to prefs.toIntArray()))
    }

    override fun toString() = buildString {
        append("SimulateRaireTestData(${contest.id}} phantomPct=${df(contest.phantomRate())} ncards=${ncards}")
    }
}