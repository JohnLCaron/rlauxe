package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.mean2margin

// pA < t
//TA/TL < t
//TA < t * TL
//0 < t * TL - TA
//0 < t * Sum(Ti) - TA
//t * Sum(Ti) - TA > 0
//(t-1) TA + t * {Ti, i != A} > 0
//
//So the linear coefficients are:
//
//  aA = (t-1), ai = t for i != A.
//
//so if vote is for A, g = (t-1)
//   if vote for not A, r = t
//   else 0
//
//lower bound a = (t-1)
//upper bound u = t
//c = -1/2a
//h = (g(b) - a)/-2a

// h = (g(b) - a)/-2a
// h(lower) = (lower - a)/-2a = (a - a)/-2a = 0
// h(upper) = (t - a)/-2a = (t - t + 1)/-2a = 1 / -2a = c

data class UnderThreshold(val info: ContestInfo, val candId: Int, val t: Double): AssorterIF  {
    val lowerg = (t-1)
    val upperg = t
    val c = -1.0 / (2 * lowerg)  // affine transform h = c * g + 1/2
    var reportedMean: Double = 0.0

    fun setReportedMean(reportedMean: Double): UnderThreshold {
        this.reportedMean = reportedMean
        return this
    }

    fun g (vote: Int): Double {
        return if (vote == candId) lowerg else upperg
    }

    // h(b) = c · g(b) + 1/2
    fun h(partyVote: Int): Double {
        return c * g(partyVote) + 0.5
    }

    fun h2(g: Double): Double {
        return c * g + 0.5
    }

    override fun assort(mvr: Cvr, usePhantoms: Boolean): Double {
        if (!mvr.hasContest(info.id)) return 0.5
        if (usePhantoms && mvr.phantom) return 0.0 // worst case
        val cands = mvr.votes[info.id]!!
        return if (cands.size == 1) h(cands.first()) else 0.5
    }

    override fun upperBound() = h2(upperg)
    fun lowerBound() = h2(lowerg)

    override fun desc() = buildString {
        append("UnderThreshold cand= $candId: reportedMean=${df(reportedMean())} reportedMargin=${df(reportedMargin())} g=[$lowerg .. $upperg] h = [${h2(lowerg)} .. ${h2(upperg)}]")
    }

    override fun hashcodeDesc() = "UnderThreshold ${candId} ${info.name}" // must be unique for serialization

    override fun winner() = candId
    override fun loser() = -1

    override fun reportedMean() = reportedMean
    override fun reportedMargin() = mean2margin(reportedMean)

    override fun calcMargin(useVotes: Map<Int, Int>?, N: Int): Double {
        if (useVotes == null || N <= 0) {
            return 0.0
        } // shouldnt happen

        val winnerVotes = useVotes[winner()] ?: 0
        val otherVotes = useVotes.filter { it.key != winner() }.values.sum()
        val nuetralVotes = N - winnerVotes - otherVotes

        val winnerweight = h2(lowerg) // should be 0
        val otherweight = h2(upperg)
        val hmean =  (otherVotes * otherweight + nuetralVotes * 0.5) / N.toDouble()
        val margin = mean2margin(hmean)

        return margin
    }

    override fun toString() = desc()

    companion object {
        fun makeFromVotes(info: ContestInfo, partyId: Int, votes: Map<Int, Int>, minFraction: Double, Nc: Int): UnderThreshold {
            val result = UnderThreshold(info, partyId, minFraction)

            val winnerVotes = votes[partyId] ?: 0
            val otherVotes = votes.filter { it.key != partyId }.values.sum()
            val nuetralVotes = Nc - winnerVotes - otherVotes

            val winnerweight = result.h2(result.lowerg) // should be 0
            val otherweight = result.h2(result.upperg)
            val hmean =  (otherVotes * otherweight + nuetralVotes * 0.5) / Nc.toDouble()
            result.setReportedMean(hmean)

            return result
        }
    }
}

// pA > t
// TA/TL > t
// TA > t * TL
// TA − t * TL > 0
// TA − t * Sum(Ti) > 0
// (1-t) TA - t * {Ti, i != A}
//
// aA = (1-t), ai = -t for i != A.
//
// g(b) = a1 b1 + a2 b2 + · · · + am bm
//     = (1-t)*bA + t*sum(bi, i != A)
//
// so if vote is for A, g = (1-t)
//   if vote for not A, r = -t
//   else 0
//
//lower bound a = -t
//c = -1/2a
//h = c · g(b) + 1/2 = g/-2a + 1/2 = g/-2a + -a/-2a  =  (g(b) - a)/-2a

data class OverThreshold(val info: ContestInfo, val winner: Int, val t: Double): AssorterIF  {
    val lowerg = -t
    val upperg = (1.0 - t)
    val c = -1.0 / (2 * lowerg)  // affine transform h = c * g + 1/2
    var reportedMean: Double = 0.0

    fun setReportedMean(reportedMean: Double): OverThreshold {
        this.reportedMean = reportedMean
        return this
    }

    fun g (vote: Int): Double {
        return if (vote == winner) (1.0 - t) else -t
    }

    // h(b) = c · g(b) + 1/2
    fun h(partyVote: Int): Double {
        return c * g(partyVote) + 0.5
    }

    fun h2(g: Double): Double {
        return c * g + 0.5
    }

    override fun assort(mvr: Cvr, usePhantoms: Boolean): Double {
        if (!mvr.hasContest(info.id)) return 0.5
        if (usePhantoms && mvr.phantom) return 0.0 // worst case
        val cands = mvr.votes[info.id]!!
        return if (cands.size == 1) h(cands.first()) else 0.5
    }

    override fun upperBound() = h2(upperg)

    override fun desc() = buildString {
        append("OverThreshold cand= $winner: reportedMean=${df(reportedMean)} reportedMargin=${df(reportedMargin() )} g= [$lowerg .. $upperg] h = [${h2(lowerg)} .. ${h2(upperg)}]")
    }

    override fun hashcodeDesc() = "${winLose()} ${info.name}" // must be unique for serialization

    override fun winner() = winner
    override fun loser() = -1

    override fun reportedMean() = reportedMean
    override fun reportedMargin() = mean2margin(reportedMean)

    override fun calcMargin(useVotes: Map<Int, Int>?, N: Int): Double {
        if (useVotes == null || N <= 0) {
            return 0.0
        } // shouldnt happen

        val winnerVotes = useVotes[winner()] ?: 0
        val otherVotes = useVotes.filter { it.key != winner() }.values.sum()
        val nuetralVotes = N - winnerVotes - otherVotes

        val winnerweight = h2(upperg)
        val otherweight = h2(lowerg) // should be 0
        val hmean = (winnerVotes * winnerweight + otherVotes * otherweight + nuetralVotes * 0.5) / N.toDouble()
        val margin = mean2margin(hmean)

        return margin
    }

    override fun toString() = desc()

    companion object {
        fun makeFromVotes(info: ContestInfo, partyId: Int, votes: Map<Int, Int>, minFraction: Double, Nc: Int): OverThreshold {
            val result = OverThreshold(info, partyId, minFraction)

            val winnerVotes = votes[partyId] ?: 0
            val otherVotes = votes.filter { it.key != partyId }.values.sum()
            val nuetralVotes = Nc - winnerVotes - otherVotes

            val winnerweight = result.h2(result.upperg)
            val otherweight = result.h2(result.lowerg) // should be 0
            val hmean = (winnerVotes * winnerweight + otherVotes * otherweight + nuetralVotes * 0.5) / Nc.toDouble()
            result.setReportedMean(hmean)
            return result
        }
    }
}
