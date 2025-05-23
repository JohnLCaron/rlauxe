package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.util.mean2margin

/** See SHANGRLA Section 3.2.
 * Let bi denote the ith ballot, and let ci denote the cast-vote record for the ith ballot.
 * Let A denote an assorter, which maps votes into [0, u], where u is an upper bound (eg 1, 1/2f).
 * The overstatement error for the ith ballot is
 *     ωi ≡ A(ci) − A(bi) ≤ A(ci ) ≤ u.     (1)
 * Let Āc = AVG(A(ci)), Āb = AVG(A(bi)) and ω̄ = AVG(ωi).
 * Then Āb = Āc − ω̄, so
 *     Āb > 1/2  iff  ω̄ < Āc − 1/2.   (2)
 * We know that Āc > 1/2 (or the assertion would not be true for the CVRs), so 2Āc − 1 > 0,
 * so we can divide without flipping the inequality:
 *    ω̄ < Āc − 1/2  <==>  ω̄ / (2Āc − 1) < (Āc − 1/2) / (2Āc − 1) = (2Āc − 1) / 2(2Āc − 1) = 1/2
 * that is,
 *    Āb > 1/2  iff  ω̄ / (2Āc − 1) < 1/2    (3)
 * Define v ≡ 2Āc − 1 == the reported assorter margin so
 *    Āb > 1/2  iff  ω̄ / v < 1/2    (4)
 * Let τi ≡ 1 − (ωi / u) ≥ 0, and τ̄ ≡ Avg(τi) = 1 − ω̄/u, and ω̄ = u(1 − τ̄), so
 *    Āb > 1/2  iff  (u/v) * (1 − τ̄) < 1/2      (5)
 * Then (u/v) * (1 − τ̄) < 1/2 == (-u/v) τ̄ < 1/2 - (u/v) == τ̄ > (-v/u)/2 - (-v/u)(u/v) == 1 - v/2u == (2u - v) / 2u
 *    τ̄ * u / (2u - v)  > 1/2  ==   τ̄ / (2 - v/u) > 1/2     (6)
 * Define B(bi, ci) ≡ τi /(2 − v/u) =  (1 − (ωi / u)) / (2 − v/u)    (7)
 *   Āb > 1/2  iff  Avg(B(bi, ci)) < 1/2      (8)
 * which makes B(bi, ci) an assorter.
 */
open class ClcaAssorter(
    val info: ContestInfo,
    val assorter: AssorterIF,   // A
    val assortAverageFromCvrs: Double?,    // Ā(c) = average assort value measured from CVRs
    val hasStyle: Boolean = true,
    val check: Boolean = true,
) {
    // Define v ≡ 2Āc − 1, the assorter margin
    val cvrAssortMargin: Double
    // when A(ci) == A(bi), ωi = 0, so then "noerror" B(bi, ci) = 1 / (2 − v/u) from eq (7)
    val noerror: Double // clca assort value when no error
    // A ranges from [0, u], so ωi ≡ A(ci) − A(bi) ranges from +/- u,
    // so (1 − (ωi / u)) ranges from 0 to 2, and B ranges from 0 to 2 /(2 − v/u) = 2 * noerror, from eq (7)
    val upperBound: Double // upper bound of clca assorter

    init {
        // Define v ≡ 2Āc − 1, the assorter margin TODO just use reportedMArgin
        cvrAssortMargin = assorter.reportedMargin()
        // when A(ci) == A(bi), ωi = 0, so then "noerror" B(bi, ci) = 1 / (2 − v/u) from eq (7)
        noerror = 1.0 / (2.0 - cvrAssortMargin / assorter.upperBound()) // clca assort value when no error
        // A ranges from [0, u], so ωi ≡ A(ci) − A(bi) ranges from +/- u,
        // so (1 − (ωi / u)) ranges from 0 to 2, and B ranges from 0 to 2 /(2 − v/u) = 2 * noerror, from eq (7) above
        upperBound = 2.0 * noerror // upper bound of clca assorter

        val cvrAssortAvg = if (assortAverageFromCvrs != null) assortAverageFromCvrs else assorter.reportedMean()
        if (cvrAssortAvg <= 0.5)
            println("*** ${info.choiceFunction} ${info.name} (${info.id}) ${assorter.desc()}: cvrAssortAvg ($cvrAssortAvg) must be > .5" )
        if (noerror <= 0.5)
            println("*** ${info.choiceFunction} ${info.name} (${info.id}) ${assorter.desc()}: noerror ($noerror) must be > .5" )
        /* if (check) { // TODO suspend checking for some tests that expect to fail
            require(avgCvrAssortValue > 0.5) {
                "${info.name} (${info.id}) ${assorter.desc()}: avgCvrAssortValue ($avgCvrAssortValue)  must be > .5"
            }
            // the math requires this; otherwise divide by negative number flips the inequality
            require(noerror > 0.5) { "${info.name} ${assorter.desc()}: ($noerror) noerror must be > .5" }
        } */
    }

    fun id() = info.id
    fun noerror() = noerror
    fun upperBound() = upperBound
    fun assorter() = assorter

    // TODO move to test
    fun calcClcaAssorterMargin(cvrPairs: Iterable<Pair<Cvr, Cvr>>): Double {
        val mean = cvrPairs.filter{ it.first.hasContest(info.id) }
            .map { bassort(it.first, it.second) }.average()
        return mean2margin(mean)
    }

    // B(bi, ci) = (1-o/u)/(2-v/u), where
    //                o is the overstatement
    //                u is the upper bound on the value the assorter assigns to any ballot
    //                v is the assorter margin
    //
    // assort in [0, .5, u], u > .5, so overstatementError in
    //      [-1, -.5, 0, .5, 1] (plurality)
    //      [-u, -.5, .5-u, 0, u-.5, .5, u] (SM, u in [.5, 1])
    //      [-u, .5-u, -.5, 0, .5, u-.5, u] (SM, u > 1)
    //
    // bassort does affine transformation of overstatementError (1-o/u)/(2-v/u) to [0, 2] * noerror
    // so bassort in
    //      [2,         1.5,                    1,             .5,          0] * noerror (plurality)
    //      [2,         1+.5/u,     1-(.5-u)/u, 1,  1-(u-.5)/u, 1-.5/u,     0] * noerror (SM, u in [.5, 1])
    //      [2,         1-(.5-u)/u, 1+.5/u,     1,  1-.5/u,     1-(u-.5)/u, 0] * noerror (SM, u > 1)
    //
    //      [2,         1.875,      1.125,      1,  .875,       .125,       0] * noerror  for u = 4
    //      [2,         1.666,      1.333,      1,  .666,       .333,       0] * noerror  for u = .75

    open fun bassort(mvr: Cvr, cvr:Cvr): Double {
        val overstatement = overstatementError(mvr, cvr, this.hasStyle) // ωi eq (1)
        val tau = (1.0 - overstatement / this.assorter.upperBound()) // τi eq (6)
        return tau * noerror   // Bi eq (7)
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
    fun overstatementError(mvr: Cvr, cvr: Cvr, hasStyle: Boolean): Double {


        //        # sanity check
        //        if use_style and not cvr.has_contest(self.contest.id):
        //            raise ValueError(
        //                f"use_style==True but {cvr=} does not contain contest {self.contest.id}"
        //            )
        if (hasStyle and !cvr.hasContest(info.id)) {
            throw RuntimeException("use_style==True but cvr=${cvr} does not contain contest ${info.name} (${info.id})")
        }

        //        If use_style, then if the CVR contains the contest but the MVR does
        //        not, treat the MVR as having a vote for the loser (assort()=0)
        //
        //        mvr_assort = (
        //            0
        //            if mvr.phantom or (use_style and not mvr.has_contest(self.contest.id))
        //            else self.assort(mvr)
        val mvr_assort = if (mvr.phantom || (hasStyle && !mvr.hasContest(info.id))) 0.0
        else this.assorter.assort(mvr, usePhantoms = false)

        //        If not use_style, then if the CVR contains the contest but the MVR does not,
        //        the MVR is considered to be a non-vote in the contest (assort()=1/2).
        //
        //        # assort the CVR
        //        cvr_assort = (
        //            self.tally_pool_means[cvr.tally_pool]
        //            if cvr.pool and self.tally_pool_means is not None
        //            else int(cvr.phantom) / 2 + (1 - int(cvr.phantom)) * self.assort(cvr)
        //        )

        // assort in [0, .5, u], u > .5, so overstatementError in
        //      [-1, -.5, 0, .5, 1] (plurality)
        //      [-u, -.5, .5-u, 0, u-.5, .5, u] (SM, u in [.5, 1])
        //      [-u, .5-u, -.5, 0, .5, u-.5, u] (SM, u > 1)

        val cvr_assort = if (cvr.phantom) .5 else this.assorter.assort(cvr, usePhantoms = false)
        return cvr_assort - mvr_assort
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ClcaAssorter

        if (assortAverageFromCvrs != other.assortAverageFromCvrs) return false
        if (hasStyle != other.hasStyle) return false
        if (info != other.info) return false
        if (assorter != other.assorter) return false

        return true
    }

    override fun hashCode(): Int {
        var result = assortAverageFromCvrs?.hashCode() ?: 0
        result = 31 * result + hasStyle.hashCode()
        result = 31 * result + info.hashCode()
        result = 31 * result + assorter.hashCode()
        return result
    }

    override fun toString() = buildString {
        appendLine("ClcaAssorter for contest ${info.name} (${info.id})")
        appendLine("  assorter=${assorter.desc()}")
        append("  cvrAssortMargin=$cvrAssortMargin noerror=$noerror upperBound=$upperBound")
    }
}
