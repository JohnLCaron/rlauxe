package org.cryptobiotic.rlauxe.core

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.util.dfn

private val logger = KotlinLogging.logger("ClcaAssorter")

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
 *   Āb > 1/2  iff  Avg(B(bi, ci)) > 1/2      (8)
 * which makes B(bi, ci) an assorter.
 */
open class ClcaAssorter(
    val info: ContestInfo,
    val assorter: AssorterIF,   // A
    val hasStyle: Boolean = true,
    val check: Boolean = true,
) {
    // Define v ≡ 2Āc − 1, the assorter margin
    val reportedAssortMargin: Double
    // when A(ci) == A(bi), ωi = 0, so then "noerror" B(bi, ci) = 1 / (2 − v/u) from eq (7)
    val noerror: Double // clca assort value when no error
    // A ranges from [0, u], so ωi ≡ A(ci) − A(bi) ranges from +/- u,
    // so (1 − (ωi / u)) ranges from 0 to 2, and B ranges from 0 to 2 /(2 − v/u) = 2 * noerror, from eq (7)
    val upperBound: Double // upper bound of clca assorter; betting functions may need to know this

    init {
        /* if (info.choiceFunction == SocialChoiceFunction.THRESHOLD) {
            require(assorter is ThresholdAssorter) { "assorter must be Threshold" }
        } else if (info.choiceFunction == SocialChoiceFunction.DHONDT) {
            require(assorter is DHondtAssorter) { "assorter must be DHondt" }
        }  else if (info.choiceFunction == SocialChoiceFunction.IRV) {
            require(assorter is RaireAssorter) { "assorter must be Raire" }
        } */

        // Define v ≡ 2Ā − 1, the assorter margin
        reportedAssortMargin = assorter.reportedMargin() // (0, 1)
        // when A(ci) == A(bi), ωi = 0, so then "noerror" B(bi, ci) = 1 / (2 − v/u) from eq (7)
        noerror = 1.0 / (2.0 - reportedAssortMargin / assorter.upperBound()) // clca assort value when no error (.5, 1)
        // A ranges from [0, u], so ωi ≡ A(ci) − A(bi) ranges from +/- u,
        // so (1 − (ωi / u)) ranges from 0 to 2, and B ranges from 0 to 2 /(2 − v/u) = 2 * noerror, from eq (7) above
        upperBound = 2.0 * noerror // upper bound of clca assorter;

        val reportedAssortAvg = assorter.reportedMean()
        if (check) { // TODO suspend checking for some tests that expect to fail
            require(reportedAssortAvg > 0.5) {
                "*** ${info.choiceFunction} ${info.name} (${info.id}) ${assorter.desc()}: cvrAssortAvg ($reportedAssortAvg) must be > .5"
            }
            // the math requires this; otherwise divide by negative number flips the inequality
            require(noerror > 0.5) { "${info.name} ${assorter.desc()}: ($noerror) noerror must be > .5" }
        }
    }

    fun id() = info.id
    fun noerror() = noerror
    fun upperBound() = upperBound
    fun assorter() = assorter

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

    // SHANGRLA overstatement_assorter()
    open fun bassort(mvr: Cvr, cvr:Cvr, hasStyle: Boolean = this.hasStyle): Double {
        val overstatement = overstatementError(mvr, cvr, hasStyle) // ωi eq (1)
        val tau = (1.0 - overstatement / this.assorter.upperBound()) // τi eq (6)
        return tau * noerror   // Bi eq (7)
    }


    // see Audit.py Assertion.overstatement_assorter()
    //         assorter that corresponds to normalized overstatement error for an assertion
    //
    //        If `use_style == True`, then if the CVR contains the contest but the MVR does not,
    //        that is considered to be an overstatement, because the ballot is presumed to contain
    //        the contest.
    //
    //        If `use_style == False`, then if the CVR contains the contest but the MVR does not,
    //        the MVR is considered to be a non-vote in the contest.
    //
    // see Audit.py Assorter.overstatement()
    //    overstatement error for a CVR compared to the human reading of the ballot.
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
    //
    //    the overstatement error ωi for CVR i is at most the value the assorter assigned to CVR i.
    //
    //     ωi ≡ A(ci) − A(bi) ≤ A(ci) ≤ upper              ≡   overstatement error (SHANGRLA eq 2, p 9)
    //      bi is the manual voting record (MVR) for the ith ballot
    //      ci is the cast-vote record for the ith ballot
    //      A() is the assorter function
    //
    // assort in [0, .5, u], u > .5, so overstatementError = cvr_assort - mvr_assort is in
    //      [-1, -.5, 0, .5, 1] (u == 1)
    //      [-u, -.5, .5-u, 0, u-.5, .5, u] (SM, u in [.5, 1])
    //      [-u, .5-u, -.5, 0, .5, u-.5, u] (SM, u > 1)

    fun overstatementError(mvr: Cvr, cvr: Cvr, hasStyle: Boolean): Double {


        //        # sanity check
        //        if use_style and not cvr.has_contest(self.contest.id):
        //            raise ValueError(
        //                f"use_style==True but {cvr=} does not contain contest {self.contest.id}"
        //            )
        if (hasStyle and !cvr.hasContest(info.id)) { // TODO SHANGRLA throws exception
            logger.error { "use_style==True but cvr=${cvr} does not contain contest ${info.name} (${info.id})" }
            throw RuntimeException("use_style==True but cvr=${cvr} does not contain contest ${info.name} (${info.id})")
        }

        //        If use_style, then if the CVR contains the contest but the MVR does
        //        not, treat the MVR as having a vote for the loser (assort()=0)

        //        # assort the MVR
        //        mvr_assort = (
        //            0
        //            if mvr.phantom or (use_style and not mvr.has_contest(self.contest.id))
        //            else self.assort(mvr)
        //
        val mvr_assort = if (mvr.phantom || (hasStyle && !mvr.hasContest(info.id))) 0.0
            else this.assorter.assort(mvr, usePhantoms = false)

        //        If not use_style, then if the CVR contains the contest but the MVR does not,
        //        the MVR is considered to be a non-vote in the contest (assort()=1/2).
        //
        //        # assort the CVR
        //        cvr_assort = (
        //            self.tally_pool_means[cvr.tally_pool]
        //            if
        //                cvr.pool and self.tally_pool_means is not None
        //            else
        //                int(cvr.phantom) / 2 + (1 - int(cvr.phantom)) * self.assort(cvr)
        //        )
        //        return cvr_assort - mvr_assort

        val cvr_assort = if (cvr.phantom) .5 else this.assorter.assort(cvr, usePhantoms = false)
        return cvr_assort - mvr_assort
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ClcaAssorter

        if (hasStyle != other.hasStyle) return false
        if (info != other.info) return false
        if (assorter != other.assorter) return false

        return true
    }

    override fun hashCode(): Int {
        var result = 0
        result = 31 * result + hasStyle.hashCode()
        result = 31 * result + info.hashCode()
        result = 31 * result + assorter.hashCode()
        return result
    }

    override fun toString() = buildString {
        appendLine("ClcaAssorter for contest ${info.name} (${info.id})")
        appendLine("  assorter=${assorter.desc()}")
        append("  cvrAssortMargin=${dfn(reportedAssortMargin, 8)} noerror=${dfn(noerror, 8)} upperBound=${dfn(upperBound, 8)}")
    }

    fun shortName() = assorter.shortName()
}

/*
 margin = (Sum(w) - Sum(l)) / Nc
dmargin = (Sum(w) - Sum(l)) / Nb
     Nb = Nc + Nu

   1. assorter = (w - l + 1)/2 = (w-l)/2 + 1/2

    sum = Sum_Nb((w - l + 1)/2)
        = Sum_Nc((w - l + 1)/2) + Sum_Nu(1/2)
        = (Sum_Nc(w) - Sum_Nc(l) + Sum_Nc(1))/2 + Nu/2
        = (Sum_Nc(w) - Sum_Nc(l)) / 2 + Nc/2 + Nu/2
        = (Sum_Nc(w) - Sum_Nc(l)) / 2 + Nb/2
 sum/Nb = (Sum_Nc(w) - Sum_Nc(l)) / 2 / Nb + 1/2

 mean2margin(mean) = 2.0 * mean - 1.0 = (Sum_Nc(w) - Sum_Nc(l))/Nb + 1 - 1
        = (Sum_Nc(w) - Sum_Nc(l))/Nb
        = diluted margin

  Note that (Sum_Nc(w) - Sum_Nc(l)) = (Sum_Nb(w) - Sum_Nb(l)), since Nc have all the votes.

  2. cassorter = (1.0 - overstatement) * noerror; overstatement = cvr_assort - mvr_assort
          Sum / noerror  = Sum(1.0) - Sum(cvr_assort) + Sum(mvr_assort)
          Sum / noerror  = Nb - Sum_c((w - l)/2 - Sum(1/2) + Sum_m((w - l)/2) + Sum(1/2)
          Sum / noerror  = Nb - Sum_c(w - l)/2 + Sum_m(w - l)/2
          Sum / noerror / Nb  = 1 - Sum_c(w - l)/2*Nb + Sum_m(w - l)/2*Nb
          Avg / noerror  = 1 - Sum_c(w - l)/2*Nb + Sum_m(w - l)/2*Nb
          Avg / noerror  = 1 - Avg_c(w - l)/2 + Avg_m(w - l)/2
          Avg / noerror  = 1 + (Avg_m(w - l)/2 + 1/2) - (Avg_c(w - l)/2 + 1/2)

mean = (Avg(w - l)/2 + 1/2)
margin = (Avg_c(w - l)/2 + 1/2)

 mean2margin(mean)/noerror  = 2.0 * mean - 1.0 = 2*(1 - Sum_c(w - l)/2*Nb + Sum_m(w - l)/2*Nb) - 1
                        = 2 - Sum_c(w - l)/Nb + Sum_m(w - l)/Nb - 1
                        = 1 - Sum_c(w - l)/Nb + Sum_m(w - l)/Nb
                        = 1 + dmargin_m - dmargin_c
                        = (1 + dmargin_m) - (1 + dmargin_c) + 1

margin2mean(margin) = (margin + 1) / 2
mean2margin(mean) = 2.0 * mean - 1.0


 */
