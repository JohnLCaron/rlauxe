package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlaux.core.raire.RaireCvr
import org.cryptobiotic.rlauxe.core.raire.RaireAssertion
import org.cryptobiotic.rlauxe.core.raire.RaireContestAudit

interface AssorterFunction {
    fun assort(mvr: Cvr) : Double
    fun upperBound(): Double
    fun desc(): String
}

/** See SHANGRLA, section 2.1. */
data class PluralityAssorter(val contest: Contest, val winner: Int, val loser: Int): AssorterFunction {
    // SHANGRLA section 2, p 4.
    override fun assort(mvr: Cvr): Double {
        val w = mvr.hasMarkFor(contest.idx, winner)
        val l = mvr.hasMarkFor(contest.idx, loser)
        return (w - l + 1) * 0.5
    }
    override fun upperBound() = 1.0
    override fun desc() = "PluralityAssorter winner=$winner loser=$loser"
}

/** See SHANGRLA, section 2.3. */
data class SuperMajorityAssorter(val contest: Contest, val winner: Int, val minFraction: Double): AssorterFunction {
    val upperBound = 0.5 / minFraction

    // SHANGRLA eq (1), section 2.3, p 5.
    override fun assort(mvr: Cvr): Double {
        val w = mvr.hasMarkFor(contest.idx, winner)
        return if (mvr.hasOneVote(contest.idx, contest.candidates)) (w / (2 * minFraction)) else .5
    }

    override fun upperBound() = upperBound
    override fun desc() = "SuperMajorityAssorter winner=$winner minFraction=$minFraction"
}

data class Assertion(
    val contest: Contest,
    val assorter: AssorterFunction,
) {
    override fun toString() = "Assertion for ${contest.id} assorter=${assorter.desc()}"
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
    val check: Boolean = true,
) {
    val margin = 2.0 * avgCvrAssortValue - 1.0 // reported assorter margin
    val noerror = 1.0 / (2.0 - margin / assorter.upperBound())  // assort value when there's no error
    val upperBound = 2.0 * noerror  // maximum assort value

    fun upperBound() = upperBound

    init {
        if (check) { // suspend checking for some tests that expect to fail TODO maybe bad idea
            require(avgCvrAssortValue > 0.5) { "($avgCvrAssortValue) avgCvrAssortValue must be > .5" }// the math requires this; otherwise divide by negative number flips the inequality
            require(noerror > 0.5) { "($noerror) noerror must be > .5" }
        }
    }

    // B(bi, ci)
    fun bassort(mvr: Cvr, cvr:Cvr): Double {
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

        val overstatement = overstatementError(mvr, cvr) // ωi
        val tau = (1.0 - overstatement / this.assorter.upperBound())
        val denom =  (2.0 - margin/this.assorter.upperBound())
        val result1 =  tau * noerror
        val result2 =  tau /denom
        require(result1 == result2)
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
    fun overstatementError(mvr: Cvr, cvr: Cvr, useStyle: Boolean = true): Double {


        //        # sanity check
        //        if use_style and not cvr.has_contest(self.contest.id):
        //            raise ValueError(
        //                f"use_style==True but {cvr=} does not contain contest {self.contest.id}"
        //            )
        if (useStyle and !cvr.hasContest(contest.idx)) {
            throw RuntimeException("use_style==True but cvr=${cvr} does not contain contest ${contest.id}")
        }

        //        If use_style, then if the CVR contains the contest but the MVR does
        //        not, treat the MVR as having a vote for the loser (assort()=0)
        //
        //        mvr_assort = (
        //            0
        //            if mvr.phantom or (use_style and not mvr.has_contest(self.contest.id))
        //            else self.assort(mvr)
        val mvr_assort = if (mvr.phantom || (useStyle && !mvr.hasContest(contest.idx))) 0.0
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

    fun desc() = "ComparisonAssorter has assorter=${assorter.desc()}"
}

class ComparisonAssertion(
    val contest: Contest,
    val assorter: ComparisonAssorter,
) {
    override fun toString() = "ComparisonAssertion for ${contest.id} assorter=${assorter.desc()}"
}

//////////////////////////////////////////////////////////////////////////////////////////////////

class RaireAssorter(contest: RaireContestAudit, val assertion: RaireAssertion) {
    val contestName = contest.contest
    // I believe this doesnt change in the course of the audit
    val remaining = contest.candidates.filter { !assertion.alreadyEliminated.contains(it) }

    fun upperBound() = 1.0
    fun desc() = "RaireAssorter contest ${contestName} type= ${assertion.assertionType} winner=${assertion.winner} loser=${assertion.loser}"

    fun assort(rcvr: RaireCvr): Double {
        return if (assertion.assertionType == "WINNER_ONLY") assortWinnerOnly(rcvr)
        else  if (assertion.assertionType == "IRV_ELIMINATION") assortIrvElimination(rcvr)
        else throw RuntimeException("unknown assertionType = $(this.assertionType")
    }

    fun assortWinnerOnly(rcvr: RaireCvr): Double {
        // CVR is a vote for the winner only if it has the winner as its first preference
        val awinner = if (rcvr.get_vote_for(assertion.winner) == 1) 1 else 0
        // CVR is a vote for the loser if they appear and the winner does not, or they appear before the winner
        val aloser = rcvr.rcv_lfunc_wo( assertion.winner, assertion.loser)

        //     An assorter must either have an `assort` method or both `winner` and `loser` must be defined
        //    (in which case assort(c) = (winner(c) - loser(c) + 1)/2. )
        return (awinner - aloser + 1) * 0.5
    }

    fun assortIrvElimination(rcvr: RaireCvr): Double {
        // Context is that all candidates in "already_eliminated" have been
        // eliminated and their votes distributed to later preferences
        val awinner = rcvr.rcv_votefor_cand(assertion.winner, remaining)
        val aloser = rcvr.rcv_votefor_cand(assertion.loser, remaining)

        return (awinner - aloser + 1) * 0.5
    }

}