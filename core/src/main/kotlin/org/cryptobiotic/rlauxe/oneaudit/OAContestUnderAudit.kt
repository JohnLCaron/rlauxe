package org.cryptobiotic.rlauxe.oneaudit

import org.cryptobiotic.rlauxe.core.*

open class OAContestUnderAudit(
    contest: ContestIF,
    hasStyle: Boolean = true
): ContestUnderAudit(contest, isComparison=true, hasStyle=hasStyle) {

    // TODO did override     open fun makeClcaAssorter(assertion: Assertion, assortValueFromCvrs: Double?): ClcaAssorter {
    fun makeClcaAssorter(assertion: Assertion, poolAverages: AssortAvgsInPools): ClcaAssorter {
        return OneAuditClcaAssorter(contest.info(), assertion.assorter, hasStyle = true, poolAverages)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as OAContestUnderAudit

        return contest == other.contest
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + contest.hashCode()
        return result
    }
}

/** See OneAudit Section 2.3.
 * Suppose we have a CVR ci for every ballot card whose index i is in C. The cardinality of C is |C|.
 * Ballot cards not in C are partitioned into G ≥ 1 disjoint groups {Gg} for which reported assorter subtotals are available.
 * Let
 *    Āc = AVG_N(A(ci)), Āc_C = AVG_C(A(ci)), Āc_Gg = AVG_Gg(A(ci))
 *    Āb = AVG_N(A(bi)), Āb_C = AVG_C(A(bi)), Āb_Gg = AVG_Gg(A(bi))
 * The assertion is the claim Āb > 1/2.
 * The reported assorter mean Āc > 1/2.
 *
 * We dont actually have ci for i in Gg, so we declare A(ci) := Âc_Gg, the average value in group Gg.
 * From eq 7 define B(bi, ci) ≡ (1 − (ωi / u)) / (2 − v/u) = (u - A(ci) + A(bi)) / (2u - v) in [0, 2u/(2u-v)]] (OA 6) TODO HEY [0, 3u/(2u-v)]] ?
 *   B̄b = AVG_N((u - A(ci) + A(bi)) / (2u - v) =  u - AVG_N(A(ci)) + AVG_N(A(bi)) / (2u - v) =
 *        (u - Āc + Āb)  / (2u - v)
 *  define v := 2Āc − 1 ≤ 2u − 1 < 2u     [show that (2u - 2Āc + 1) > 0, so dont flip the sign]
 *   Bb = (u - Āc + Āb)  / (2u - 2Āc + 1)       (OA 7)
 * So B̄b > 1/2  iff  (u - Āc + Āb) / (2u - 2Āc + 1) > 1/2  iff  (u - Āc + Āb) > (u - Āc + 1/2) iff  Āb > 1/2   (OA 8)
 * If the reported tallies are correct, i.e., if Āc = Āb = (v + 1)/2, then
 *  B̄b = u /(2u − v)       (OA 9)
 */

class OneAuditClcaAssorter(
    info: ContestInfo,
    assorter: AssorterIF,   // A(mvr) Use this assorter for the CVRs: plurality or IRV
    hasStyle: Boolean = true,
    val poolAverages: AssortAvgsInPools,
) : ClcaAssorter(info, assorter, null, hasStyle = hasStyle) {

    // B(bi, ci)
    override fun bassort(mvr: Cvr, cvr: Cvr, hasStyle: Boolean): Double {
        // println("mvr = $mvr cvr = $cvr")
        if (cvr.poolId == null) {
            return super.bassort(mvr, cvr, hasStyle) // here we use the standard assorter
        }

        val poolAverage = poolAverages.assortAverage[cvr.poolId] ?: throw IllegalStateException("Dont have pool ${cvr.poolId} in contest ${info.id} assorter")
        val overstatement = overstatementPoolError(mvr, poolAverage) // ωi
        val tau = (1.0 - overstatement / this.assorter.upperBound())

        // for pooled data (i in Gg):
        //   ωi ≡ A(ci) − A(bi) = [pa, pa-1/2, pa-1] = (loser, other, winner); pa in [0, 1]
        // taui = 1.0 - ωi =  [1 - pa, 1.5 - pa, pa] = (loser, other, winner) = ([1:0], [3/2..1/2], [0..1])
        //   B = taui * noerror = [1 - pa, 1.5 - pa, pa] * noerror;  (loser, other, winner)
        // B < 1 decreases testStat, B > 1 increases testStat
        //

        val result =  tau * noerror()
        if (result > upperBound()) {
            throw RuntimeException("OneAuditClcaAssorter result $result > upper ${upperBound()}")
        }

        // eq 10 of OneAudit.
        return result
    }

    fun overstatementPoolError(mvr: Cvr, poolAvgAssortValue: Double): Double {
        val mvr_assort = if (mvr.phantom || (hasStyle && !mvr.hasContest(info.id))) 0.0
                         else this.assorter.assort(mvr, usePhantoms = false)

        // for pooled data (i in Gg):
        //   A(ci) = poolAvg in [0..u]
        //   A(bi) in [0, .5, u],  (loser, other, winner)
        //   so ωi ≡ A(ci) − A(bi) ranges from -u to u

        // let u = 1, let poolAvg = pa, and pool margin = pv = 2*pa - 1
        // so ωi ≡ A(ci) − A(bi)
        //   poolAvg - 0, poolAvg - 1/2, poolAvg - 1 in [pa, pa-1/2, pa-1] = (loser, other, winner)

        // val cvr_assort = if (cvr.phantom) .5 else poolAvgAssortValue
        val cvr_assort = poolAvgAssortValue
        return cvr_assort - mvr_assort
    }

    override fun toString() = buildString {
        appendLine("OneAuditClcaAssorter for contest ${info.name} (${info.id})")
        appendLine("  assorter=${assorter.desc()}")
        appendLine("  cvrAssortMargin=$reportedAssortMargin noerror=$noerror upperBound=$upperBound assortValueFromCvrs=$assortAverageFromCvrs")
    }

    fun showPools() = buildString {
        appendLine("  cvrAssortMargin=$reportedAssortMargin noerror=$noerror upperBound=$upperBound assortValueFromCvrs=$assortAverageFromCvrs")
        poolAverages.assortAverage.forEach {
            appendLine("  pool=${it.key} average=${it.value}")
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as OneAuditClcaAssorter

        return info == other.info
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + info.hashCode()
        return result
    }
}

// for a specific assorter, all the averages in each pool
data class AssortAvgsInPools (
    val assortAverage: Map<Int, Double>, // poolId -> average assort value
)

/*
Audit.py line 2584

    def overstatement(self, mvr, cvr, use_style=True):
        """
        overstatement error for a CVR compared to the human reading of the ballot

        If use_style, then if the CVR contains the contest but the MVR does
        not, treat the MVR as having a vote for the loser (assort()=0)

        If not use_style, then if the CVR contains the contest but the MVR does not,
        the MVR is considered to be a non-vote in the contest (assort()=1/2).

        Phantom CVRs and MVRs are treated specially:
            A phantom CVR is considered a non-vote in every contest (assort()=1/2).
            A phantom MVR is considered a vote for the loser (i.e., assort()=0) in every
            contest.

        Parameters
        ----------
        mvr: Cvr
            the manual interpretation of voter intent
        cvr: Cvr
            the machine-reported cast vote record

        Returns
        -------
        overstatement: float
            the overstatement error
        """
        # sanity check

        # TODO there is no cvr; assume that SHANGRLA doesnt deal with use_style = true (?)
        if use_style and not cvr.has_contest(self.contest.id):
            raise ValueError(
                f"use_style==True but {cvr=} does not contain contest {self.contest.id}"
            )
        # assort the MVR
        mvr_assort = (
            0
            if
                mvr.phantom or (use_style and not mvr.has_contest(self.contest.id))
            else
                self.assort(mvr)
        )
        # assort the CVR
        cvr_assort = (
           # TODO in case theres phantoms in the pool, I think this should be
           # TODO int(cvr.phantom) / 2 + (1 - int(cvr.phantom)) * self.tally_pool_means[cvr.tally_pool]
            self.tally_pool_means[cvr.tally_pool]
            if
                cvr.pool and self.tally_pool_means is not None
            else
                int(cvr.phantom) / 2 + (1 - int(cvr.phantom)) * self.assort(cvr)
        )

        #if cvr.pool and self.tally_pool_means is not None:
        #    print(f"tally_pool {cvr.tally_pool} means: {self.tally_pool_means[cvr.tally_pool]} ")
        # print(f" mvr_assort: {mvr_assort}, cvr_assort: {cvr_assort}")
        return cvr_assort - mvr_assort
 */