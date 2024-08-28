package org.cryptobiotic.rlauxe

abstract class Assorter(
    val contest: AuditContest,
    val upperBound: Double, // a priori upper bound on the value the assorter can take
    val winner: String,
    val loser: String
): AssorterFunction {
    abstract override fun assort(cvr: Cvr): Double



    // Compute the arithmetic mean of the assort value over the cvrs that have this contest, // eq 2
    fun mean(cvrs: List<Cvr>, use_style: Boolean = true): Double {
        //           val result = cvr_list.filter { cvr -> if (use_style) cvr.has_contest(this.contest.id) else true }
        return cvrs.filter { cvr ->  if (use_style) cvr.hasContest(this.contest.id) else true }
            .map { this.assort(it) }
            .average()
    }

    // TODO paper reference
    fun overstatement(mvr: Cvr, cvr: Cvr, use_style: Boolean = true): Double {
        // sanity check
        if (use_style && !cvr.hasContest(contest.id)) {
            throw Exception("use_style==True but Cvr '${cvr.id}' does not contain contest '${this.contest.id}'")
        }
        // assort the MVR
        val mvr_assort = if (mvr.phantom || (use_style && !mvr.hasContest(this.contest.id))) 0.0
        else this.assort(mvr)

        // assort the CVR
        //        cvr_assort = (
        //            self.tally_pool_means[cvr.tally_pool]
        //            if cvr.pool and self.tally_pool_means is not None
        //            else int(cvr.phantom) / 2 + (1 - int(cvr.phantom)) * self.assort(cvr)
        //        )
        // val cvr_assort: Double = if (cvr.pool && this.tally_pool_means != null) this.tally_pool_means!![cvr.tally_pool]!!
        // else phantomValue / 2 + (1 - phantomValue) * this.assort(cvr)
        val phantomValue = if (cvr.phantom) 1.0 else 0.0 // TODO really ? int(cvr.phantom)
        val temp = phantomValue / 2 + (1 - phantomValue) * this.assort(cvr)
        val cvr_assort = if (cvr.phantom) .5 else this.assort(cvr)
        require(temp == cvr_assort)

        return cvr_assort - mvr_assort
    }

    override fun toString(): String {
        return "(upperBound=$upperBound, winner='$winner', loser='$loser')"
    }
}

class PluralityAssorter(contest: AuditContest, winner: String, loser: String): Assorter(contest, 1.0, winner, loser) {

    // SHANGRLA section 2, p 4.
    override fun assort(cvr: Cvr): Double {
        val w = cvr.hasMarkFor(contest.id, winner)
        val l = cvr.hasMarkFor(contest.id, loser)
        return (w - l + 1) * 0.5
    }
}

class SupermajorityAssorter(contest: AuditContest, winner: String, loser: String)
    : Assorter(contest, 1.0 / (2 * contest.minFraction!!), winner, loser) {

    // SHANGRLA eq (1), section 2.3, p 5.
    override fun assort(cvr: Cvr): Double {
        val w = cvr.hasMarkFor(contest.id, winner)
        return if (cvr.hasOneVote(contest.id, contest.candidates)) (w / (2 * contest.minFraction!!)) else .5
    }
}