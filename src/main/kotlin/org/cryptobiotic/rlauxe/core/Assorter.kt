package org.cryptobiotic.rlauxe.core

interface AssorterFunction {
    fun assort(mvr: Mvr) : Double
    fun upperBound(): Double
    fun desc(): String
}

/** See SHANGRLA, section 2.1. */
data class PluralityAssorter(val contest: AuditContest, val winner: Int, val loser: Int): AssorterFunction {
    // SHANGRLA section 2, p 4.
    override fun assort(mvr: Mvr): Double {
        val w = mvr.hasMarkFor(contest.idx, winner)
        val l = mvr.hasMarkFor(contest.idx, loser)
        return (w - l + 1) * 0.5
    }
    override fun upperBound() = 1.0
    override fun desc() = "PluralityAssorter winner=$winner loser=$loser"
}

/** See SHANGRLA, section 2.3. */
data class SuperMajorityAssorter(val contest: AuditContest, val winner: Int, val minFraction: Double): AssorterFunction {
        val upperBound = 1.0 / (2 * minFraction)

    // SHANGRLA eq (1), section 2.3, p 5.
    override fun assort(mvr: Mvr): Double {
        val w = mvr.hasMarkFor(contest.idx, winner)
        return if (mvr.hasOneVote(contest.idx, contest.candidates)) (w / (2 * minFraction)) else .5
    }

    override fun upperBound() = upperBound
    override fun desc() = "SuperMajorityAssorter winner=$winner minFraction=$minFraction"
}

data class Assertion(
    val contest: AuditContest,
    val assorter: AssorterFunction,
) {
    override fun toString() = "Assertion for ${contest.id} assorter=${assorter.desc()}"
}

/////////////////////////////////////////////////////////////////////////////////


interface ComparisonAssorterFunction {
    fun assort(mvr: Cvr, cvr: Cvr) : Double // TODO should it be Mvr, Cvr??
}

/** See SHANGRLA Section 3.2 */
data class ComparisonAssorter(
    val contest: AuditContest,
    val assorter: AssorterFunction,   // A
    val avgCvrAssortValue: Double // Ā(c) = average CVR assort value
): ComparisonAssorterFunction {
    val margin = 2.0 * avgCvrAssortValue - 1.0 // reported assorter margin
    val Bzero = 1.0 / (2.0 - margin)

    // B(bi, ci)
    override fun assort(mvr: Cvr, cvr:Cvr): Double {
        // Let
        //     Ā(c) ≡ Sum(A(ci))/N be the average CVR assort value
        //     margin ≡ 2Ā(c) − 1, the _reported assorter margin_, (for 2 candidate plurality, the _diluted margin_).
        //
        //     ωi ≡ A(ci) − A(bi)   overstatementError
        //     τi ≡ (1 − ωi /upper) ≥ 0, since ωi <= upper
        //     B(bi, ci) ≡ τi /(2 − margin/upper) = (1 − ωi /upper) / (2 − margin/upper)

        val overstatement = overstatementError(mvr, cvr) // ωi
        val tau = (1.0 - overstatement / this.assorter.upperBound())
        val denom =  (2.0 - margin/this.assorter.upperBound())
        return tau / denom
    }

    //        overstatement error for a CVR compared to the human reading of the ballot
    //
    //        If use_style, then if the CVR contains the contest but the MVR does
    //        not, treat the MVR as having a vote for the loser (assort()=0)
    //
    //        If not use_style, then if the CVR contains the contest but the MVR does not,
    //        the MVR is considered to be a non-vote in the contest (assort()=1/2).
    //
    //        Phantom CVRs and MVRs are treated specially:
    //            A phantom CVR is considered a non-vote in every contest (assort()=1/2).
    //            A phantom MVR is considered a vote for the loser (i.e., assort()=0) in every
    //            contest.
    fun overstatementError(mvr: Cvr, cvr: Cvr, useStyle: Boolean = true): Double {
        //     ωi ≡ A(ci) − A(bi) ≤ A(ci) ≤ upper                 overstatement error (SHANGRLA eq 2, p 9)
        //      bi is the manual voting record (MVR) for the ith ballot
        //      ci is the cast-vote record for the ith ballot
        //      A() is the assorter function

        //        # sanity check
        //        if use_style and not cvr.has_contest(self.contest.id):
        //            raise ValueError(
        //                f"use_style==True but {cvr=} does not contain contest {self.contest.id}"
        //            )
        if (useStyle and !cvr.hasContest(contest.idx)) {
            throw RuntimeException("use_style==True but cvr=${cvr} does not contain contest ${contest.id}")
        }

        //        mvr_assort = (
        //            0
        //            if mvr.phantom or (use_style and not mvr.has_contest(self.contest.id))
        //            else self.assort(mvr)
        // TODO how can mvr be a phantom?
        val mvr_assort = if (mvr.phantom || (useStyle && !mvr.hasContest(contest.idx))) 0.0
                         else this.assorter.assort(mvr)

        //        # assort the CVR
        //        cvr_assort = (
        //            self.tally_pool_means[cvr.tally_pool]
        //            if cvr.pool and self.tally_pool_means is not None
        //            else int(cvr.phantom) / 2 + (1 - int(cvr.phantom)) * self.assort(cvr)
        //        )
        // TODO what is cvr.pool ?

        val cvr_assort = if (cvr.phantom) .5 else this.assorter.assort(cvr)
        return cvr_assort - mvr_assort
    }

    fun desc() = "ComparisonAssorter has assorter=${assorter.desc()}"
}

class ComparisonAssertion(
    val contest: AuditContest,
    val assorter: ComparisonAssorter,
) {
    override fun toString() = "ComparisonAssertion for ${contest.id} assorter=${assorter.desc()}"
}