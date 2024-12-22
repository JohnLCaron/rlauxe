package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.doubleIsClose
import org.cryptobiotic.rlauxe.util.mean2margin

interface AssorterFunction {
    fun assort(mvr: CvrIF, usePhantoms: Boolean = false) : Double
    fun upperBound(): Double
    fun desc(): String
    fun winner(): Int
    fun loser(): Int
    fun reportedMargin(): Double

    // Calculate the assorter mean for all the CVRs,
    //    including the phantoms by treating the phantom CVRs as if they contain no valid vote in the contest
    //    (i.e., the assorter assigns the value 1/2 to phantom CVRs)
    fun calcAssorterMargin(contestId: Int, cvrs: Iterable<CvrIF>): Double {
        val mean = cvrs.filter{ it.hasContest(contestId) }
                        .map { assort(it, usePhantoms = false) }.average()
        return mean2margin(mean)
    }

    // from SuperSimple:
    // The number µ is the “diluted margin”: the smallest margin of victory in votes among the contests, divided by the
    // total number of ballots cast across all the contests. (p. 1)
    // The diluted margin µ is the smallest margin in votes among the contests under audit, divided by the total
    // number of ballots cast across all the contests under audit. (p. 4)
    // The reported margin of reported winner w ∈ Wc over reported loser l ∈ Lc in contest c is
    //    Vwl ≡ Sum (vpw − vpl), p=1..N = Sum (vpw) − Sum( vpl ) = votes(winner) - votes(loser) (p. 5)

    // Define v ≡ 2Āc − 1, the reported assorter margin. In a two-candidate plurality contest, v
    // is the fraction of ballot cards with valid votes for the reported winner, minus the fraction
    // with valid votes for the reported loser. This is the diluted margin of [22,12]. (Margins are
    // traditionally calculated as the difference in votes divided by the number of valid votes.
    // Diluted refers to the fact that the denominator is the number of ballot cards, which is
    // greater than or equal to the number of valid votes.) (SHANGRLA p. 10)

    // phantom ballots are re-animated as evil zombies: We suppose that they reflect whatever would
    // increase the P-value most: a 2-vote overstatement for a ballot-level comparison audit,
    // or a valid vote for every loser in a ballot-polling audit.
}

/** See SHANGRLA, section 2.1. */
data class PluralityAssorter(val contest: ContestIF, val winner: Int, val loser: Int, val reportedMargin: Double): AssorterFunction {
    // SHANGRLA section 2, p 4.
    override fun assort(mvr: CvrIF, usePhantoms: Boolean): Double {
        if (usePhantoms && mvr.phantom) return 0.0
        val w = mvr.hasMarkFor(contest.info.id, winner)
        val l = mvr.hasMarkFor(contest.info.id, loser)
        return (w - l + 1) * 0.5
    }
    override fun upperBound() = 1.0
    override fun desc() = "PluralityAssorter winner=$winner loser=$loser"
    override fun winner() = winner
    override fun loser() = loser
    override fun reportedMargin() = reportedMargin

    companion object {
        fun makeWithVotes(contest: ContestIF, winner: Int, loser: Int, votes: Map<Int, Int>?=null): PluralityAssorter {
            val useVotes = if (votes != null) votes else (contest as Contest).votes
            val winnerVotes = useVotes[winner] ?: 0
            val loserVotes = useVotes[loser] ?: 0
            val reportedMargin = (winnerVotes - loserVotes) / contest.Nc.toDouble()
            return PluralityAssorter(contest, winner, loser, reportedMargin)
        }
    }
}

/** See SHANGRLA, section 2.3. */
data class SuperMajorityAssorter(val contest: ContestIF, val winner: Int, val minFraction: Double, val reportedMargin: Double): AssorterFunction {
    val upperBound = 0.5 / minFraction

    // SHANGRLA eq (1), section 2.3, p 5.
    override fun assort(mvr: CvrIF, usePhantoms: Boolean): Double {
        if (usePhantoms && mvr.phantom) return 0.0
        val w = mvr.hasMarkFor(contest.info.id, winner)
        return if (mvr.hasOneVote(contest.info.id, contest.info.candidateIds)) (w / (2 * minFraction)) else .5
    }

    override fun upperBound() = upperBound
    override fun desc() = "SuperMajorityAssorter winner=$winner minFraction=$minFraction"
    override fun winner() = winner
    override fun loser() = -1 // everyone else is a loser
    override fun reportedMargin() = reportedMargin

    companion object {
        fun makeWithVotes(contest: ContestIF, winner: Int, minFraction: Double, votes: Map<Int, Int>?=null): SuperMajorityAssorter {
            val useVotes = if (votes != null) votes else (contest as Contest).votes

            val winnerVotes = useVotes[winner] ?: 0
            val loserVotes = useVotes.filter { it.key != winner }.values.sum()
            val nuetralVotes = contest.Nc - winnerVotes - loserVotes

            // TODO i think this works when theres only 1 vote allowed ??
            val weight = 1 / (2 * minFraction)
            val mean =  (winnerVotes * weight + nuetralVotes * 0.5) / contest.Nc.toDouble()
            val reportedMargin = mean2margin(mean)
            return SuperMajorityAssorter(contest, winner, minFraction, reportedMargin)
        }
    }
}

/////////////////////////////////////////////////////////////////////////////////

/** See SHANGRLA Section 3.2 */
data class ComparisonAssorter(
    val contest: ContestIF,
    val assorter: AssorterFunction,   // A
    val avgCvrAssortValue: Double,    // Ā(c) = average CVR assort value != reportedMargin
    val hasStyle: Boolean = true, // TODO could be on the Contest ??
    val check: Boolean = true, // TODO get rid of
) {
    val margin = 2.0 * avgCvrAssortValue - 1.0 // reported assorter margin
    val noerror = 1.0 / (2.0 - margin / assorter.upperBound())  // assort value when there's no error
    val upperBound = 2.0 * noerror  // maximum assort value

    init {
        if (check) { // suspend checking for some tests that expect to fail TODO maybe bad idea
            require(avgCvrAssortValue > 0.5) { "$contest: ($avgCvrAssortValue) avgCvrAssortValue must be > .5" }// the math requires this; otherwise divide by negative number flips the inequality
            require(noerror > 0.5) { "$contest: ($noerror) noerror must be > .5" }
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

        val overstatement = overstatementError(mvr, cvr, hasStyle) // ωi
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
    fun overstatementError(mvr: CvrIF, cvr: CvrIF, hasStyle: Boolean): Double {


        //        # sanity check
        //        if use_style and not cvr.has_contest(self.contest.id):
        //            raise ValueError(
        //                f"use_style==True but {cvr=} does not contain contest {self.contest.id}"
        //            )
        if (hasStyle and !cvr.hasContest(contest.info.id)) {
            throw RuntimeException("use_style==True but cvr=${cvr} does not contain contest ${contest.info.name} (${contest.info.id})")
        }

        //        If use_style, then if the CVR contains the contest but the MVR does
        //        not, treat the MVR as having a vote for the loser (assort()=0)
        //
        //        mvr_assort = (
        //            0
        //            if mvr.phantom or (use_style and not mvr.has_contest(self.contest.id))
        //            else self.assort(mvr)
        val mvr_assort = if (mvr.phantom || (hasStyle && !mvr.hasContest(contest.info.id))) 0.0
                         else this.assorter.assort(mvr, usePhantoms = false)

        //        If not use_style, then if the CVR contains the contest but the MVR does not,
        //        the MVR is considered to be a non-vote in the contest (assort()=1/2).
        //
        //        # assort the CVR
        //        cvr_assort = (
        //            self.tally_pool_means[cvr.tally_pool] // TODO cvr.tally_pool used for ONEAUDIT
        //            if cvr.pool and self.tally_pool_means is not None
        //            else int(cvr.phantom) / 2 + (1 - int(cvr.phantom)) * self.assort(cvr)
        //        )

        val cvr_assort = if (cvr.phantom) .5 else this.assorter.assort(cvr, usePhantoms = false)
        return cvr_assort - mvr_assort
    }
}

///////////////////////////////////////////////////////////////////

open class Assertion(
    val contest: ContestIF,
    val assorter: AssorterFunction,
) {
    val winner = assorter.winner()
    val loser = assorter.loser()
    val margin = assorter.reportedMargin()

    var status = TestH0Status.NotStarted
    var proved = false
    var estSampleSize = 0  // estimated sample size; depends only on the margin, fromEstimateSampleSize
    var samplesNeeded = 0 // first sample when pvalue < riskLimit; from runAudit
    var samplesUsed = 0 // sample count when testH0 terminates
    var pvalue = 0.0 // last pvalue when testH0 terminates
    var round = 0    // round when set to proved or disproved

    override fun toString() = "'${contest.info.name}' (${contest.info.id}) ${assorter.desc()} margin=${df(margin)}"
}

class ComparisonAssertion(
    contest: ContestIF,
    val cassorter: ComparisonAssorter,
): Assertion(contest, cassorter.assorter) {
    val avgCvrAssortValue = cassorter.avgCvrAssortValue
    val cmargin = cassorter.margin

    override fun toString() = "${cassorter.assorter.desc()} cmargin=${df(cmargin)} estSampleSize=$estSampleSize"
}
