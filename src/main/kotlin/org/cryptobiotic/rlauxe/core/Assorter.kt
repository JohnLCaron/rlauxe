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
        return if (mvr.hasOneVote(contest.idx)) (w / (2 * minFraction)) else .5
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
    fun assort(mvr: Mvr, cvr: Cvr) : Double
}

/** See SHANGRLA Section 3.2 */
data class ComparisonAssorter(
    val contest: AuditContest,
    val assorter: AssorterFunction,   // A
    val avgCvrAssortValue: Double // Ā(c) = average CVR assort value
): ComparisonAssorterFunction {
    val v = 2.0 * avgCvrAssortValue - 1.0 // reported assorter margin

    override fun assort(mvr: Mvr, cvr:Cvr): Double {
        // Let
        //     Ā(c) ≡ Sum(A(ci))/N be the average CVR assort value
        //     ω̄ ≡ Sum(ωi)/N = Sum(A(ci) − A(bi))/N be the average overstatement error
        //     v ≡ 2Ā(c) − 1, the _reported assorter margin_, (for 2 candidate plurality, the _diluted margin_).
        //     τi ≡ (1 − ωi /upper) ≥ 0
        //     B(bi, ci) ≡ τi /(2 − v/upper) = (1 − ωi /upper) / (2 − v/upper)

        val overstatement = overstatementError(mvr, cvr) // ωi
        val tau = (1.0 - overstatement / this.assorter.upperBound())
        return tau / (2.0 - v/this.assorter.upperBound())
    }

    //     ωi ≡ A(ci) − A(bi) ≤ A(ci ) ≤ upper                 overstatement error (SHANGRLA eq 2, p 9)
    //      bi is the manual voting record (MVR) for the ith ballot
    //      ci is the cast-vote record for the ith ballot
    //      A() is the assorter function
    fun overstatementError(mvr: Mvr, cvr: Cvr): Double {
        val mvr_assort = this.assorter.assort(mvr)

        val phantomValue = if (cvr.phantom) 1.0 else 0.0
        val temp = phantomValue / 2.0 + (1.0 - phantomValue) * this.assorter.assort(cvr)
        val cvr_assort = if (cvr.phantom) .5 else this.assorter.assort(cvr)
        require(temp == cvr_assort)

        return cvr_assort - mvr_assort
    }

    // ported from SHANGRLA but probably not needed here
    /* Compute the arithmetic mean of the assort value over the cvrs that have this contest, // eq 2
    fun mean(mvrs: List<Mvr>, use_style: Boolean = true): Double {
        //           val result = cvr_list.filter { cvr -> if (use_style) cvr.has_contest(this.contest.id) else true }
        return mvrs.filter { mvr ->  if (use_style) mvr.hasContest(this.assorter.contest.id) else true }
            .map { this.assorter.assort(it) }
            .average()
    }

    // ωi ≡ A(ci) − A(bi) ≤ A(ci ) ≤ upper                 overstatement error: SHANGRLA 3.2 eq 2, p 9
    // plus "Zombie Bounds" SHANGRLA 3.4 p 12
    fun overstatementError(mvr: Mvr, cvr: Cvr, use_style: Boolean = true): Double {
        // sanity check
        if (use_style && !cvr.hasContest(this.assorter.contest.id)) {
            throw Exception("use_style==True but Cvr '${cvr.id}' does not contain contest '${this.assorter.contest.id}'")
        }
        // assort the MVR
        val mvr_assort = this.assorter.assort(mvr)

        // assort the CVR
        //        cvr_assort = (
        //            self.tally_pool_means[cvr.tally_pool]
        //            if cvr.pool and self.tally_pool_means is not None
        //            else int(cvr.phantom) / 2 + (1 - int(cvr.phantom)) * self.assort(cvr)
        //        )
        // val cvr_assort: Double = if (cvr.pool && this.tally_pool_means != null) this.tally_pool_means!![cvr.tally_pool]!!
        // else phantomValue / 2 + (1 - phantomValue) * this.assort(cvr)
        val phantomValue = if (cvr.phantom) 1.0 else 0.0
        val temp = phantomValue / 2 + (1 - phantomValue) * this.assorter.assort(cvr)
        val cvr_assort = if (cvr.phantom) .5 else this.assorter.assort(cvr)
        require(temp == cvr_assort)

        return cvr_assort - mvr_assort
    }
     */

    fun desc() = "ComparisonAssorter has assorter=${assorter.desc()}"
}

class ComparisonAssertion(
    val contest: AuditContest,
    val assorter: ComparisonAssorter,
) {
    override fun toString() = "ComparisonAssertion for ${contest.id} assorter=${assorter.desc()}"
}