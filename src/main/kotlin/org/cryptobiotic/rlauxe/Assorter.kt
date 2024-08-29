package org.cryptobiotic.rlauxe

abstract class Assorter(
    val contest: AuditContest,
    val upperBound: Double, // a priori upper bound on the value the assorter can take
    val winner: String,
    val loser: String
): AssorterFunction {
    abstract override fun assort(mvr: Mvr): Double

    override fun toString(): String {
        return "(upperBound=$upperBound, winner='$winner', loser='$loser')"
    }
}

class PluralityAssorter(contest: AuditContest, winner: String, loser: String): Assorter(contest, 1.0, winner, loser) {
    // SHANGRLA section 2, p 4.
    override fun assort(mvr: Mvr): Double {
        val w = mvr.hasMarkFor(contest.id, winner)
        val l = mvr.hasMarkFor(contest.id, loser)
        return (w - l + 1) * 0.5
    }
}

class SupermajorityAssorter(contest: AuditContest, winner: String, loser: String):
        Assorter(contest, 1.0 / (2 * contest.minFraction!!), winner, loser) {

    // SHANGRLA eq (1), section 2.3, p 5.
    override fun assort(mvr: Mvr): Double {
        val w = mvr.hasMarkFor(contest.id, winner)
        return if (mvr.hasOneVote(contest.id, contest.candidates)) (w / (2 * contest.minFraction!!)) else .5
    }
}

/////////////////////////////////////////////////////////////////////////////////

class ComparisonAssorter(
    val assorter: Assorter,
    avgCvrAssortValue: Double // Ā(c)
): ComparisonAssorterFunction {
    val v = 2.0 * avgCvrAssortValue - 1.0

    override fun assort(mvr: Mvr, cvr:Cvr): Double {
        // Let
        //     Ā(c) ≡ Sum(A(ci))/N be the average CVR assort value
        //     ω̄ ≡ Sum(ωi)/N = Sum(A(ci) − A(bi))/N be the average overstatement error
        //     v ≡ 2Ā(c) − 1, the _reported assorter margin_, (for 2 c ondidate plurality, the _diluted margin_.
        //     τi ≡ (1 − ωi /upper) ≥ 0
        //     B(bi, ci) ≡ τi /(2 − v/upper) = (1 − ωi /upper) / (2 − v/upper)

        val overstatement = overstatementError(mvr, cvr) // ωi
        val tau = (1 - overstatement / this.assorter.upperBound)
        return tau / (2 - v/this.assorter.upperBound)
    }

    // Compute the arithmetic mean of the assort value over the cvrs that have this contest, // eq 2
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

    //     ωi ≡ A(ci) − A(bi) ≤ A(ci ) ≤ upper                 overstatement error (SHANGRLA eq 2, p 9)
    //      bi is the manual voting record (MVR) for the ith ballot
    //      ci is the cast-vote record for the ith ballot
    //      A() is the assorter function

    fun overstatementError(mvr: Mvr, cvr: Cvr): Double {
        val mvr_assort = this.assorter.assort(mvr)

        val phantomValue = if (cvr.phantom) 1.0 else 0.0
        val temp = phantomValue / 2 + (1 - phantomValue) * this.assorter.assort(cvr)
        val cvr_assort = if (cvr.phantom) .5 else this.assorter.assort(cvr)
        require(temp == cvr_assort)

        return cvr_assort - mvr_assort
    }

    override fun toString(): String {
        return "(assorter=$assorter, reportedMargin=$v)"
    }

}