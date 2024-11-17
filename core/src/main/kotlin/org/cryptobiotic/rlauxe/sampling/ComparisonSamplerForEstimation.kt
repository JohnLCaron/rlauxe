package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.ComparisonAssorter
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.CvrUnderAudit
import org.cryptobiotic.rlauxe.util.secureRandom
import kotlin.math.max

/*
  two vote overstatement: cvr has winner (1), mvr has loser (0)
  one vote overstatement: cvr has winner (1), mvr has other (1/2)
  two vote understatement: cvr has loser (0), mvr has winner (1)
  one vote understatement: cvr has other (1/2), mvr has winner (1)
 */

// create internal cvr and mvr with the correct under/over statements.
// specific to a contest. only used for estimating the sample size
class ComparisonSamplerForEstimation(
    cvras: List<CvrUnderAudit>,
    val contestUA: ContestUnderAudit,
    val cassorter: ComparisonAssorter,
    val p1: Double = 1.0e-2, // apriori rate of 1-vote overstatements; voted for other, cvr has winner
    val p2: Double = 1.0e-4, // apriori rate of 2-vote overstatements; voted for loser, cvr has winner
    val p3: Double = 1.0e-2, // apriori rate of 1-vote understatements; voted for winner, cvr has other
    val p4: Double = 1.0e-4, // apriori rate of 2-vote understatements; voted for winner, cvr has loser
    ): GenSampleFn {

    val N = cvras.size
    val mvrs: List<CvrUnderAudit>
    val cvrs: List<CvrUnderAudit>

    val permutedIndex = MutableList(N) { it }
    val sampleMean: Double
    val sampleCount: Double
    val flippedVotes1: Int
    val flippedVotes2: Int
    val flippedVotes3: Int
    val flippedVotes4: Int

    var idx = 0

    init {
        reset()

        // we want to flip the exact number of votes, for reproducibility
        // note we only do this on construction, reset just uses a different permutation
        val mmvrs = mutableListOf<CvrUnderAudit>()
        mmvrs.addAll(cvras)
        val ccvrs = mutableListOf<CvrUnderAudit>()
        ccvrs.addAll(cvras)

        flippedVotes1 = flip1votes(mmvrs, changeWinnerToOther = (N * p1).toInt())
        flippedVotes2 = flip2votes(mmvrs, needToChangeWinnerToLoser = (N * p2).toInt())
        flippedVotes4 = flip4votes(mmvrs, needToChangeLoserToWinner = (N * p4).toInt())
        flippedVotes3 = flip3votes(mmvrs, ccvrs, changeCvrToOther = (N * p3).toInt())

        mvrs = mmvrs.toList()
        cvrs = ccvrs.toList()

        sampleCount = cvras.filter { it.hasContest(contestUA.id) }.mapIndexed { idx, it -> cassorter.bassort(mvrs[idx], it) }.sum()
        sampleMean = sampleCount / N
    }

    override fun sampleMean() = sampleMean
    override fun sampleCount() = sampleCount
    override fun N() = N
    override fun reset() {
        permutedIndex.shuffle(secureRandom) // TODO ok to permute?
        idx = 0
    }

    override fun sample(): Double {
        while (idx < N) {
            val cvr = cvrs[permutedIndex[idx]]
            val mvr = mvrs[permutedIndex[idx]]
            if (cvr.hasContest(contestUA.id) && (cvr.sampleNum <= contestUA.sampleThreshold || contestUA.sampleThreshold == 0L)) {
                val result = cassorter.bassort(mvr, cvr) // TODO not sure of cvr vs cvr.cvr, w/re raire; not sure of permute
                idx++
                return result
            }
            idx++
        }
        throw RuntimeException("no samples left for contest=${contestUA.id} and ComparisonAssorter ${cassorter}")
    }

    //   two vote overstatement: cvr has winner, mvr has loser
    fun flip2votes(mcvrs: MutableList<CvrUnderAudit>, needToChangeWinnerToLoser: Int): Int {
        if (needToChangeWinnerToLoser == 0) return 0
        val ncards = mcvrs.size
        val startingAvotes = mcvrs.sumOf { it.hasMarkFor(contestUA.id, cassorter.assorter.winner()) }
        var changed = 0

        // we need more loser votes, needToChangeVotesFromA > 0
        while (changed < needToChangeWinnerToLoser) {
            val cvrIdx = secureRandom.nextInt(ncards)
            val mvr = mcvrs[cvrIdx]
            // if (cvr.hasMarkFor(0, 0) == 1) {
            if (mvr.hasMarkFor(contestUA.id, cassorter.assorter.winner()) == 1) {
                val votes = mapOf(contestUA.id to intArrayOf(cassorter.assorter.loser()))
                val altered = makeNewCvr(mvr, votes)
                mcvrs[cvrIdx] = altered
                require(cassorter.bassort(altered, mvr) == 0.0) // p2
                changed++
            }
        }
        val checkAvotes = mcvrs.sumOf { it.hasMarkFor(contestUA.id, cassorter.assorter.winner()) }
        require(checkAvotes == startingAvotes - needToChangeWinnerToLoser)
        return changed
    }

    //  two vote understatement: cvr has loser, mvr has winner
    fun flip4votes(mcvrs: MutableList<CvrUnderAudit>, needToChangeLoserToWinner: Int): Int {
        if (needToChangeLoserToWinner == 0) return 0
        val ncards = mcvrs.size
        val startingAvotes = mcvrs.sumOf { it.hasMarkFor(contestUA.id, cassorter.assorter.winner()) }
        var changed = 0

        while (changed < needToChangeLoserToWinner) {
            val cvrIdx = secureRandom.nextInt(ncards)
            val mvr = mcvrs[cvrIdx]
            // if (cvr.hasMarkFor(0, 1) == 1) {
            if (mvr.hasMarkFor(contestUA.id, cassorter.assorter.loser()) == 1) {
                val votes = mapOf(contestUA.id to intArrayOf(cassorter.assorter.winner()))
                val altered = makeNewCvr(mvr, votes)
                mcvrs[cvrIdx] = altered
                require(cassorter.bassort(altered, mvr) == 2.0 * cassorter.noerror) // p4
                changed++
            }
        }

        val checkAvotes = mcvrs.sumOf { it.hasMarkFor(contestUA.id, cassorter.assorter.winner()) }
        // if (debug) println("flipped = $needToChangeVotesFromA had $startingAvotes now have $checkAvotes votes for A")
        require(checkAvotes == startingAvotes + needToChangeLoserToWinner)
        return changed
    }

    fun makeNewCvr(old: CvrUnderAudit, votes: Map<Int, IntArray>): CvrUnderAudit {
        return CvrUnderAudit(Cvr(old.cvr, votes), old.phantom, old.sampleNum)
    }

    //  one vote overstatement: cvr has winner, mvr has other
    fun flip1votes(mcvrs: MutableList<CvrUnderAudit>, changeWinnerToOther: Int): Int {
        if (changeWinnerToOther == 0) return 0
        val ncards = mcvrs.size
        var changed = 0

        // random other candidate, just cant be winner or loser
        val otherCandidate = max(cassorter.assorter.winner(), cassorter.assorter.loser()) + 1

        // we need winner -> other vote, needToChangeVotesFromA > 0
        val startingAvotes = mcvrs.sumOf { it.hasMarkFor(contestUA.id, cassorter.assorter.winner()) }
        while (changed < changeWinnerToOther) {
            val cvrIdx = secureRandom.nextInt(ncards)
            val mvr = mcvrs[cvrIdx]
            // if (cvr.hasMarkFor(0, 0) == 1) {
            if (mvr.hasMarkFor(contestUA.id, cassorter.assorter.winner()) == 1) {
                val votes = mapOf(contestUA.id to intArrayOf(otherCandidate))
                val altered = makeNewCvr(mvr, votes)
                mcvrs[cvrIdx] = altered
                println("${cassorter.bassort(altered, mvr)} == ${0.5 * cassorter.noerror}") // p1 winner -> other
                require(cassorter.bassort(altered, mvr) == 0.5 * cassorter.noerror) // p1 winner -> other
                changed++
            }
        }
        val checkAvotes = mcvrs.sumOf { it.hasMarkFor(contestUA.id, cassorter.assorter.winner()) }
        require(checkAvotes == startingAvotes - changeWinnerToOther)

        return changed
    }

    //  one vote understatement: cvr has other, mvr has winner. have to change cvr to other
    fun flip3votes(mcvrs: MutableList<CvrUnderAudit>, cvrs: MutableList<CvrUnderAudit>, changeCvrToOther: Int): Int {
        if (changeCvrToOther == 0) return 0
        val ncards = mcvrs.size
        var changed = 0

        // random other candidate, just cant be winner or loser
        val otherCandidate = max(cassorter.assorter.winner(), cassorter.assorter.loser()) + 1

        val startingAvotes = cvrs.sumOf { it.hasMarkFor(contestUA.id, cassorter.assorter.winner()) }
        while (changed < changeCvrToOther) {
            val cvrIdx = secureRandom.nextInt(ncards)
            val mvr = mcvrs[cvrIdx]
            val cvr = cvrs[cvrIdx]
            if ((cvr.hasMarkFor(contestUA.id, cassorter.assorter.winner()) == 1) && (mvr.votes == cvr.votes)) {
                val votes = mapOf(contestUA.id to intArrayOf(otherCandidate))
                val altered = makeNewCvr(cvr, votes)
                cvrs[cvrIdx] = altered // Note we are changing the cvr, not the mvr
                require(cassorter.bassort(mvr, altered) == 1.5 * cassorter.noerror) // p3 loser -> other
                changed++
            }
        }
        val checkAvotes = cvrs.sumOf { it.hasMarkFor(contestUA.id, cassorter.assorter.winner()) }
        require(checkAvotes == startingAvotes - changeCvrToOther)

        return changed
    }
}