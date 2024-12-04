package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.doubleIsClose
import org.cryptobiotic.rlauxe.util.mean2margin

interface AssorterFunction {
    fun assort(mvr: CvrIF) : Double
    fun upperBound(): Double
    fun desc(): String
    fun winner(): Int
    fun loser(): Int

    // from SuperSimple:
    // The number µ is the “diluted margin”: the smallest margin of victory in votes among the contests, divided by the
    // total number of ballots cast across all the contests. (p. 1)
    // The diluted margin µ is the smallest margin in votes among the contests under audit, divided by the total
    // number of ballots cast across all the contests under audit. (p. 4)
    // The reported margin of reported winner w ∈ Wc over reported loser l ∈ Lc in contest c is
    //    Vwl ≡ Sum (vpw − vpl ), p=1..N = Sum (vpw) − Sum( vpl ) = votes(winner) - votes(loser) (p. 5)

    // Define v ≡ 2Āc − 1, the reported assorter margin. In a two-candidate plurality contest, v
    // is the fraction of ballot cards with valid votes for the reported winner, minus the fraction
    // with valid votes for the reported loser. This is the diluted margin of [22,12]. (Margins are
    // traditionally calculated as the difference in votes divided by the number of valid votes.
    // Diluted refers to the fact that the denominator is the number of ballot cards, which is
    // greater than or equal to the number of valid votes.) (SHANGRLA p. 10)
    fun reportedAssorterMargin(): Double
}

/** See SHANGRLA, section 2.1. */
data class PluralityAssorter(val contest: Contest, val winner: Int, val loser: Int): AssorterFunction {
    // SHANGRLA section 2, p 4.
    override fun assort(mvr: CvrIF): Double {
        val w = mvr.hasMarkFor(contest.id, winner)
        val l = mvr.hasMarkFor(contest.id, loser)
        return (w - l + 1) * 0.5
    }
    override fun upperBound() = 1.0
    override fun desc() = "PluralityAssorter winner=$winner loser=$loser"
    override fun winner() = winner
    override fun loser() = loser

    override fun reportedAssorterMargin(): Double {
        val winnerVotes = contest.votes[winner] ?: 0
        val loserVotes = contest.votes[loser] ?: 0
        return (winnerVotes - loserVotes) / contest.Nc.toDouble()  // or divide by total votes ??
    }
}

/** See SHANGRLA, section 2.3. */
data class SuperMajorityAssorter(val contest: Contest, val winner: Int, val minFraction: Double): AssorterFunction {
    val upperBound = 0.5 / minFraction

    // SHANGRLA eq (1), section 2.3, p 5.
    override fun assort(mvr: CvrIF): Double {
        val w = mvr.hasMarkFor(contest.id, winner)
        return if (mvr.hasOneVote(contest.id, contest.info.candidateIds)) (w / (2 * minFraction)) else .5
    }

    override fun upperBound() = upperBound
    override fun desc() = "SuperMajorityAssorter winner=$winner minFraction=$minFraction"
    override fun winner() = winner
    override fun loser() = -1 // everyone else is a loser

    // TODO how to derive the assort mean for estimation ??
    override fun reportedAssorterMargin(): Double {
        val winnerVotes = contest.votes[winner] ?: 0
        val loserVotes = contest.votes.filter { it.key != winner }.values.sum()
        val nuetralVotes = contest.Nc - winnerVotes - loserVotes

        // TODO i think this works when theres only 1 vote allowed ??
        val weight = 1 / (2 * minFraction)
        val mean =  (winnerVotes * weight + nuetralVotes * 0.5) / contest.Nc.toDouble()
        return mean2margin(mean)
    }
}

/////////////////////////////////////////////////////////////////////////////////

fun comparisonAssorterCalc(assortAvgValue:Double, assortUpperBound: Double): Triple<Double, Double, Double> {
    val margin = 2.0 * assortAvgValue - 1.0 // reported assorter margin
    val noerror = 1.0 / (2.0 - margin / assortUpperBound)  // assort value when there's no error
    val upperBound = 2.0 * noerror  // maximum assort value
    return Triple(margin, noerror, upperBound)
}

/** See SHANGRLA Section 3.2 */
data class ComparisonAssorter(
    val contest: Contest,
    val assorter: AssorterFunction,   // A
    val avgCvrAssortValue: Double,    // Ā(c) = average CVR assort value
    val check: Boolean = true, // TODO get rid of
) {
    val margin = 2.0 * avgCvrAssortValue - 1.0 // reported assorter margin
    val noerror = 1.0 / (2.0 - margin / assorter.upperBound())  // assort value when there's no error
    val upperBound = 2.0 * noerror  // maximum assort value

    fun upperBound() = upperBound

    init {
        if (check) { // suspend checking for some tests that expect to fail TODO maybe bad idea
            if (avgCvrAssortValue <= 0.5) {
                println("avgCvrAssortValue")
            }
            require(avgCvrAssortValue > 0.5) { "($avgCvrAssortValue) avgCvrAssortValue must be > .5" }// the math requires this; otherwise divide by negative number flips the inequality
            require(noerror > 0.5) { "($noerror) noerror must be > .5" }
        }
    }

    // B(bi, ci)
    fun bassort(mvr: CvrIF, cvr:CvrIF): Double {
        // Let
        //     Ā(c) ≡ Sum(A(ci))/N be the average CVR assort value
        //     margin ≡ 2Ā(c) − 1, the _reported assorter margin_, (for 2 candidate plurality, aka the _diluted margin_).
        //
        //     ωi ≡ A(ci) − A(bi)   overstatementError
        //     τi ≡ (1 − ωi /upper) ≥ 0, since ωi <= upper
        //     B(bi, ci) ≡ τi / (2 − margin/upper) = (1 − ωi /upper) / (2 − margin/upper)
        //
        //     B assigns nonnegative numbers to ballots, and the outcome is correct iff Bavg > 1/2
        //     So, B is an assorter.

        val overstatement = overstatementError(mvr, cvr, contest.useStyle) // ωi
        val tau = (1.0 - overstatement / this.assorter.upperBound())
        val denom =  (2.0 - margin/this.assorter.upperBound())
        val result1 =  tau * noerror
        val result2 =  tau / denom
        require(doubleIsClose(result1, result2))
        return result1
    }

    //    overstatement error for a CVR compared to the human reading of the ballot.
    //    the overstatement error ωi for CVR i is at most the value the assorter assigned to CVR i.
    //
    //     ωi ≡ A(ci) − A(bi) ≤ A(ci) ≤ upper              ≡   overstatement error (SHANGRLA eq 2, p 9)
    //      bi is the manual voting record (MVR) for the ith ballot
    //      ci is the cast-vote record for the ith ballot
    //      A() is the assorter function
    //
    //        Phantom CVRs and MVRs are treated specially:
    //            A phantom CVR is considered a non-vote in every contest (assort()=1/2).
    //            A phantom MVR is considered a vote for the loser (i.e., assort()=0) in every contest.
    fun overstatementError(mvr: CvrIF, cvr: CvrIF, useStyle: Boolean): Double {


        //        # sanity check
        //        if use_style and not cvr.has_contest(self.contest.id):
        //            raise ValueError(
        //                f"use_style==True but {cvr=} does not contain contest {self.contest.id}"
        //            )
        if (useStyle and !cvr.hasContest(contest.id)) {
            throw RuntimeException("use_style==True but cvr=${cvr} does not contain contest ${contest.info.name} (${contest.id})")
        }

        //        If use_style, then if the CVR contains the contest but the MVR does
        //        not, treat the MVR as having a vote for the loser (assort()=0)
        //
        //        mvr_assort = (
        //            0
        //            if mvr.phantom or (use_style and not mvr.has_contest(self.contest.id))
        //            else self.assort(mvr)
        val mvr_assort = if (mvr.phantom || (useStyle && !mvr.hasContest(contest.id))) 0.0
                         else this.assorter.assort(mvr)

        //        If not use_style, then if the CVR contains the contest but the MVR does not,
        //        the MVR is considered to be a non-vote in the contest (assort()=1/2).
        //
        //        # assort the CVR
        //        cvr_assort = (
        //            self.tally_pool_means[cvr.tally_pool] // TODO cvr.tally_pool used for ONEAUDIT
        //            if cvr.pool and self.tally_pool_means is not None
        //            else int(cvr.phantom) / 2 + (1 - int(cvr.phantom)) * self.assort(cvr)
        //        )

        val cvr_assort = if (cvr.phantom) .5 else this.assorter.assort(cvr)
        return cvr_assort - mvr_assort
    }

    fun name() = assorter.desc()
}

///////////////////////////////////////////////////////////////////

open class Assertion(
    val contest: Contest,
    val assorter: AssorterFunction,
) {
    val winner = assorter.winner()
    val loser = assorter.loser()
    val margin = assorter.reportedAssorterMargin()

    // TODO is it ok to have this state ??
    var status = TestH0Status.NotStarted
    var proved = false
    var estSampleSize = 0  // estimated sample size; depends only on the margin, fromEstimateSampleSize
    var samplesNeeded = 0 // sample count when pvalue < riskLimit; from Audit
    var samplesUsed = 0 // sample count when testH0 terminates
    var pvalue = 0.0 // last pvalue when testH0 terminates
    var round = 0    // round when set to proved or disproved

    override fun toString() = "'${contest.info.name}' (${contest.id}) ${assorter.desc()} margin=${df(margin)}"
}

class ComparisonAssertion(
    contest: Contest,
    val cassorter: ComparisonAssorter,
): Assertion(contest, cassorter.assorter) {
    val avgCvrAssortValue = cassorter.avgCvrAssortValue
    val cmargin = cassorter.margin

    override fun toString() = "${cassorter.name()} cmargin=${df(cmargin)} estSampleSize=$estSampleSize"
}
