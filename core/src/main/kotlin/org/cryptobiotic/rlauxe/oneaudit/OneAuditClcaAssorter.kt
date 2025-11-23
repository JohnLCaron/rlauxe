package org.cryptobiotic.rlauxe.oneaudit

import org.cryptobiotic.rlauxe.audit.CardIF
import org.cryptobiotic.rlauxe.core.*

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

/* TODO OneAudit p.9
This algorithm be made more efficient statistically and logistically in a variety
of ways, for instance, by making an affine translation of the data so that the
minimum possible value is 0 (by subtracting the minimum of the possible over-
statement assorters across batches and re-scaling so that the null mean is still
1/2) and by starting with a sample size that is expected to be large enough to
confirm the contest outcome if the reported results are correct.

"Your intuition is right: if you don't use the same affine transformation for the entire pool of cards, you'd have to
introduce weights and/or use stratified sampling to make the null mean for the whole contest 1/2. But a single affine
transformation could be based on the minimum possible value across all batches and the resulting implicit scaling
needed to get back to 1/2. So it could be used, but might not help much if the ONEAudit cvrs are for batches
(rather than for the whole election) and the batches are heterogeneous.

Ive been thinking only about "hybrid" audits, where you have one "batch" that has Cvrs, and one or more pools where you only have the batch totals,
as in the San Francisco example for OneAudit in SHANGRLA.
In that case,  the CVR batch values are in the range [0, 2 * noerror], and the "minimum possible value across all batches" is 0, so there's nothing to be gained.

But, eq (10) would be useful in the case where all the batches are pooled data, and the minimum possible value across
all batches is non-zero. This use case I havent started to work with, but it sounds like I should, since
OneAudit will be "far more efficient than BLCA"." 4/27/25 email

TODO p.10-12
Moving from tests about raw assorter values to tests about overstatements rel-
ative to ONE CVRs derived from overall contest totals is just an affine trans-
formation: no information is gained or lost. Thus, if we audited using an affine
equivariant statistical test, the sample size should be the same whether the data
are the original assorter values (i.e., BPA) or overstatements from ONE CVRs.
However, the statistical tests used in RLAs are not affine equivariant because
they rely on a priori bounds on the assorter values. The original assorter values
will generally be closer to the endpoints of [0, u] than the transformed values
are to the endpoints of [0, 2u/(2u − v)].

To see why, suppose that there are
no reported CVRs (C = ∅) and that only contest totals are reported from the
system—so every cast ballot card is in G1 . For a BPA, the population values
from which the sample is drawn are the original assorter values {A(bi )}, which
for many social choice functions can take only the values 0, 1/2, and u. For
instance, consider a two-candidate plurality contest, Alice v. Bob, where Alice
is the reported winner. This can be audited using a single assorter that assigns
the value 0 to a card with a vote for Bob, the value u = 1 to a card with a vote
for Alice, and the value 1/2 to other cards. In contrast, for a comparison audit,
the possible population values {B(bi )} are

...

Unless v = 1—i.e., unless every card was reported to have a vote for Alice—
the minimum value of the overstatement assorter is greater than 0 and the
maximum is less than u.

A test that uses the prior information xj ∈ [0, u] may not be as efficient for
populations for which xj ∈ [a, b] with a > 0 and b < u as it is for populations
where the values 0 and u actually occur. An affine transformation of the over-
statement assorter values can move them back to the endpoints of the support
constraint by subtracting the minimum possible value then re-scaling so that the
null mean is 1/2 once again, which reproduces the original assorter, A:
 */

class OneAuditClcaAssorter(
    info: ContestInfo,
    assorter: AssorterIF,   // A(mvr) Use this assorter for the CVRs: plurality or IRV
    hasStyle: Boolean = true,
    dilutedMargin: Double,
    val poolAverages: AssortAvgsInPools,
) : ClcaAssorter(info, assorter, hasStyle = hasStyle, dilutedMargin=dilutedMargin) {

    // B(bi, ci)
    override fun bassort(mvr: CardIF, cvr: CardIF, hasStyle: Boolean): Double {
        // println("mvr = $mvr cvr = $cvr")
        if (cvr.poolId() == null) {
            return super.bassort(mvr, cvr, hasStyle) // here we use the standard assorter
        }

        val poolAverage = poolAverages.assortAverage[cvr.poolId()] ?: throw IllegalStateException("Dont have pool ${cvr.poolId()} in contest ${info.id} assorter")
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

    fun overstatementPoolError(mvr: CardIF, poolAvgAssortValue: Double): Double {
        val mvr_assort = if (mvr.isPhantom() || (hasStyle && !mvr.hasContest(info.id)))
            0.0
        else
            this.assorter.assort(mvr, usePhantoms = false)

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
        appendLine("  dilutedMargin=$dilutedMargin noerror=$noerror upperBound=$upperBound")
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