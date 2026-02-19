package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.doubleIsClose
import org.cryptobiotic.rlauxe.util.doublePrecision
import org.cryptobiotic.rlauxe.util.mean2margin
import org.cryptobiotic.rlauxe.util.pfn
import org.cryptobiotic.rlauxe.util.roundToClosest

// pA < t
// TA/TL < t
// TA < t * TL
// 0 < t * TL - TA              # t - TA
// 0 < t * Sum(Ti) - TA
// t * Sum(Ti) - TA > 0
// (t-1) TA + t * {Ti, i != A} > 0
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
//c = -1/2a = 1/2(1-t)
//h = (g(b) - a)/-2a

// h = (g(b) - a)/-2a
// h(lower) = (lower - a)/-2a = (a - a)/-2a = 0
// h(upper) = (t - a)/-2a = (t - t + 1)/-2a = 1 / -2a = c

/* Olivier has
    # Assertion:
    #     1 - p_A > 0.95 => 0.05 - p_A > 0
    #
    # Linearises to:              Proto-asserter:                  Minimum for b_a = 1, b_T = 1
    #     0.05 * T_L - T_A > 0        => g(b) = 0.05 * b_T - b_A       => a = -.95
    # Minimum `a` of proto-assorter is < -.5 so we set `c = -1 / 2a` and `h(b) = (g(b) - a) / 2a = (0.05 * b_T - b_A - a) / 2a`.
    #
    # Assorter mean:
    #     h_bar = - g_bar / 2a + .5
    #           = - (.05 * T_L - T_A) / T_L * 2 * -.95 + .5
    #           = (.05 * T_L - T_A) / T_L * 2*.95 + .5
 */

const val compareBelgium = false

data class BelowThreshold(val info: ContestInfo, val candId: Int, val t: Double): AssorterIF  {
    val id = info.id
    val lowerg = (t-1) // aka 'a'
    val upperg = t
    val c = -1.0 / (2 * lowerg)  // 1 / 2(1-t)
    var dilutedMean: Double = 0.0

    fun setDilutedMean(mean: Double): BelowThreshold {
        this.dilutedMean = mean
        return this
    }

    fun g(vote: Int): Double {
        return if (vote == candId) lowerg else upperg
    }

    // h(b) = c · g(b) + 1/2
    // l = h(t-1) = (t-1)/2(1-t) + 1/2 = (t-1 + 1-t)/2(1-t) = 0
    // u = h(t) = (t)/2(1-t) + 1/2 = (t + 1 - t) / 2(1-t) = 1/2(1-t)
    fun h(cand: Int): Double {
        if (!info.candidateIds.contains(cand)) return .5
        return c * g(cand) + 0.5
    }

    fun h2(g: Double): Double {
        val h = c * g + 0.5
        return if (h < doublePrecision) 0.0 else h
    }

    override fun assort(cvr: CvrIF, usePhantoms: Boolean): Double {
        if (!cvr.hasContest(id)) return 0.5
        if (usePhantoms && cvr.isPhantom()) return 0.0 // worst case
        val cands = cvr.votes(id)
        return if (cands != null && cands.size == 1) h(cands.first()) else 0.5
    }

    override fun upperBound() = h2(upperg)

    override fun desc() = buildString {
        append("${shortName()}: dilutedMean=${pfn(dilutedMean())} noerror=${pfn(noerror())} ")
        append("g=[$lowerg .. $upperg] h = [${h2(lowerg)} .. ${h2(upperg)}]")
    }

    override fun shortName() = "BelowThreshold for '${info.candidateIdToName[winner()]}'"

    override fun hashcodeDesc() = "BelowThreshold ${candId} ${info.name}" // must be unique for serialization

    override fun winner() = candId
    override fun loser() = -1

    override fun dilutedMean() = dilutedMean
    override fun dilutedMargin() = mean2margin(dilutedMean)

    fun showAssertionDifficulty(votesForWinner: Int, nvotes: Int): String {
        val pct = 100.0 * votesForWinner / nvotes
        return "votesForWinner=$votesForWinner pct=${dfn(pct, 4)} diff=${roundToClosest(t * nvotes - votesForWinner)} votes"
    }

    fun difficulty(votesForWinner: Int, nvotes: Int): Double {
        return t * nvotes - votesForWinner
    }

    override fun calcMarginFromRegVotes(useVotes: Map<Int, Int>?, N: Int): Double {
        if (useVotes == null || N <= 0) {
            return 0.0
        } // shouldnt happen

        val winnerVotes = useVotes[winner()] ?: 0
        val otherVotes = useVotes.filter { it.key != winner() }.values.sum()
        val nuetralVotes = N - winnerVotes - otherVotes

        val winnerweight = h2(lowerg) // should be 0
        val otherweight = h2(upperg) // c * t + 1/2 = -t / (2 * (t-1)) + 1/2 = [(2 * (t-1)) - t] / ((2 * (t-1)))
                                                    // (t - 2) / (2t - 2)
        val hmean =  (otherVotes * otherweight + nuetralVotes * 0.5) / N.toDouble()
        require(hmean == dilutedMean)

        val margin = mean2margin(hmean)
        val ratio = margin / h2(upperg)
        val noerror: Double = 1.0 / (2.0 - ratio)

        require(hmean == otherVotes * h2(t) / N)
        require(doubleIsClose(otherweight, 1/(2*(1-t)), doublePrecision))
        return margin
    }

    /* Olivier has
    # Assertion:
    #     1 - p_A > 0.95 => 0.05 - p_A > 0
    #
    # Linearises to:              Proto-asserter:                  Minimum for b_a = 1, b_T = 1
    #     0.05 * T_L - T_A > 0        => g(b) = 0.05 * b_T - b_A       => a = -.95
    # Minimum `a` of proto-assorter is < -.5 so we set `c = -1 / 2a` and `h(b) = (g(b) - a) / 2a = (0.05 * b_T - b_A - a) / 2a`.
    #
    # Assorter mean:
    #     h_bar = - g_bar / 2a + .5
    #           = - (.05 * T_L - T_A) / T_L * 2 * -.95 + .5
    #           = (.05 * T_L - T_A) / T_L * 2*.95 + .5

    fun calcMarginB(useVotes: Map<Int, Int>, N: Int): Double {
        val a = (t - 1)
        val c = -1/(2*a)

        fun hb(gv:Double):Double {
            val h = (c * gv + .5)
            return if (h < doublePrecision) 0.0 else h
        }

        // = (.05 * T_L - T_A) / T_L * 2*.95 + .5
        val TA = useVotes[winner()] ?: 0
        val TL = N.toDouble()
        val meanb= (t * TL - TA) / (TL * 2*(1-t))  + .5

        val margin = mean2margin(meanb)
        val ratio = margin / hb(upperg)
        val noerror: Double = 1.0 / (2.0 - ratio)

        println("Belgium ${shortName()} belgium_mean= $meanb g= [$lowerg .. $upperg] h = [${hb(lowerg)} .. ${hb(upperg)}] ratio=$ratio noerror=$noerror")

        return meanb
    } */

    override fun toString() = desc()

    companion object {
        fun makeFromVotes(info: ContestInfo, partyId: Int, votes: Map<Int, Int>, minFraction: Double, Npop: Int): BelowThreshold {
            val result = BelowThreshold(info, partyId, minFraction)

            val winnerVotes = votes[partyId] ?: 0
            val otherVotes = votes.filter { it.key != partyId }.values.sum()
            val nuetralVotes = Npop - winnerVotes - otherVotes

            val winnerweight = result.h2(result.lowerg) // should be 0
            val otherweight = result.h2(result.upperg)
            val hmean =  (otherVotes * otherweight + nuetralVotes * 0.5) / Npop.toDouble()
            result.setDilutedMean(hmean)

            return result
        }
    }
}

// pA > t                                   #  p_A > t
// TA/TL > t
// TA > t * TL
// TA − t * TL > 0                          # T_A - t * T_L > 0
// TA − t * Sum(Ti) > 0
// (1-t) TA - t * {Ti, i != A}
//
// aA = (1-t), ai = -t for i != A.
//
// g(b) = a1 b1 + a2 b2 + · · · + am bm    #  g(b) = b_A - t * b_T ; has range [-t, (1-t)]
//     = (1-t)*bA + t*sum(bi, i != A)
//
// so if vote is for A, g = (1-t)
//   if vote for not A, g = -t
//   else 0
//
// lower bound a = -t                        # Minimum (a) = -t
// c = -1/2a
// h = c · g(b) + 1/2                        # h = b_A - t b_T + .5 ; set c = 1. so h = g + .5 range of h is (.5-t, 1.5-t)
//   = g/-2a + 1/2
//   = g/-2a + -a/-2a  =  (g(b) - a)/-2a

/* Olivier has:
    # Assertion:
    #     p_A > 0.05
    #
    # Linearises to:              Proto-asserter:                  Minimum for b_a = 0, b_T = 1
    #     T_A - 0.05 * T_L > 0        => g(b) = b_A - 0.05 * b_T      => a = -.05

 difference is here:
    # Minimum `a` of proto-assorter is > -.5 so we set `c = 1` and `h(b) = c * g(b) + .5 = b_A - 0.05 b_T + .5`.
    #
    # Assorter mean:
    #     h_bar = g_bar + .5
    #           = T_A / T_L - .05 + .5

 this seems to give slightly bigger margins, so is preferrable
*/

data class AboveThreshold(val info: ContestInfo, val winner: Int, val t: Double): AssorterIF  {
    val id = info.id
    val lowerg = -t
    val upperg = (1.0 - t)
    val c = -1.0 / (2 * lowerg)  // = 1/(2t)
    var dilutedMean: Double = 0.0

    fun setDilutedMean(mean: Double): AboveThreshold {
        this.dilutedMean = mean
        return this
    }

    fun g(vote: Int): Double {
        return if (vote == winner) (1.0 - t) else -t
    }

    fun h(cand: Int): Double {
        if (!info.candidateIds.contains(cand)) return .5
        return c * g(cand) + 0.5
    }

    // affine transform h = g/2t + 1/2
    // l = h(-t) = -t/2t + 1/2 = 0
    // u = h(1-t) = (1-t)/2t + 1/2 = (1-t+t)/2t = 1/2t
    fun h2(g: Double): Double {
        return c * g + 0.5
    }

    override fun assort(cvr: CvrIF, usePhantoms: Boolean): Double {
        if (!cvr.hasContest(id)) return 0.5
        if (usePhantoms && cvr.isPhantom()) return 0.0 // worst case
        val cands = cvr.votes(id)
        return if (cands != null && cands.size == 1) h(cands.first()) else 0.5
    }

    override fun upperBound() = h2(upperg)

    override fun shortName() = "AboveThreshold for '${info.candidateIdToName[winner()]}'"

    override fun desc() = buildString {
        append("${shortName()}: dilutedMean=${pfn(dilutedMean)} noerror=${pfn(noerror() )}")
        append(" g= [$lowerg .. $upperg] h = [${h2(lowerg)} .. ${h2(upperg)}]")
    }

    override fun hashcodeDesc() = "AboveThreshold ${winLose()} ${info.name}" // must be unique for serialization

    override fun winner() = winner
    override fun loser() = -1

    override fun dilutedMean() = dilutedMean
    override fun dilutedMargin() = mean2margin(dilutedMean)

    override fun calcMarginFromRegVotes(useVotes: Map<Int, Int>?, N: Int): Double {
        if (useVotes == null || N <= 0) {
            return 0.0
        } // shouldnt happen

        val winnerVotes = useVotes[winner()] ?: 0
        val otherVotes = useVotes.filter { it.key != winner() }.values.sum()
        val nuetralVotes = N - winnerVotes - otherVotes

        val winnerweight = h2(upperg) // = (1-t)/2t + 1/2 = 1/2t
        val otherweight = h2(lowerg) // should be 0
        val hmean = (winnerVotes * winnerweight + otherVotes * otherweight + nuetralVotes * 0.5) / N.toDouble()

        return mean2margin(hmean)
    }

    fun showAssertionDifficulty(votesForWinner: Int, nvotes: Int): String {
        val pct = 100.0 * votesForWinner / nvotes
        return "votesForWinner=$votesForWinner pct=${dfn(pct, 4)} diff=${roundToClosest(votesForWinner - t * nvotes)} votes"
    }

    fun difficulty(votesForWinner: Int, nvotes: Int): Double {
        return votesForWinner - t * nvotes
    }

    /* Olivier has:
    # Assertion:
    #     p_A > 0.05
    #
    # Linearises to:              Proto-asserter:                  Minimum for b_a = 0, b_T = 1
    #     T_A - 0.05 * T_L > 0        => g(b) = b_A - 0.05 * b_T      => a = -.05

 difference is here:
    # Minimum `a` of proto-assorter is > -.5 so we set `c = 1` and `h(b) = c * g(b) + .5 = b_A - 0.05 b_T + .5`.
    #
    # Assorter mean:
    #     h_bar = g_bar + .5
    #           = T_A / T_L - .05 + .5

    fun calcMarginB(useVotes: Map<Int, Int>, N: Int): Double {
        val a = -t
        val c = 1

        fun hb(gv:Double) = (gv + .5)

        // float(party1.tally) / num_votes - .05 + .5

        val winnerVotes = useVotes[winner()] ?: 0
        val meanb=winnerVotes/N.toDouble() +.45

        val margin = mean2margin(meanb)
        val ratio = margin / hb(upperg)
        val noerror: Double = 1.0 / (2.0 - ratio)

        println("Belgium ${shortName()} mean= $meanb g= [$lowerg .. $upperg] h = [${hb(lowerg)} .. ${hb(upperg)}] ratio=$ratio noerror=$noerror")
        return meanb
    } */

    override fun toString() = desc()

    companion object {
        fun makeFromVotes(info: ContestInfo, partyId: Int, votes: Map<Int, Int>, minFraction: Double, Npop: Int): AboveThreshold {
            val result = AboveThreshold(info, partyId, minFraction)

            val winnerVotes = votes[partyId] ?: 0
            val otherVotes = votes.filter { it.key != partyId }.values.sum()
            val nuetralVotes = Npop - winnerVotes - otherVotes

            val winnerweight = result.h2(result.upperg)
            val otherweight = result.h2(result.lowerg) // should be 0
            val hmean = (winnerVotes * winnerweight + otherVotes * otherweight + nuetralVotes * 0.5) / Npop.toDouble()
            result.setDilutedMean(hmean)
            return result
        }

        fun makeFromVotes(contest: Contest, partyId: Int, Npop: Int?): AboveThreshold {
            val result = AboveThreshold(contest.info, partyId, contest.info.minFraction!!)

            val votes = contest.votes()!!
            val winnerVotes = votes[partyId] ?: 0
            val otherVotes = votes.filter { it.key != partyId }.values.sum()
            val totalVotes = Npop ?: contest.Nc
            val nuetralVotes = totalVotes - winnerVotes - otherVotes

            val winnerweight = result.h2(result.upperg)
            val otherweight = result.h2(result.lowerg) // should be 0
            val hmean = (winnerVotes * winnerweight + otherVotes * otherweight + nuetralVotes * 0.5) / totalVotes.toDouble()
            result.setDilutedMean(hmean)
            return result
        }
    }
}