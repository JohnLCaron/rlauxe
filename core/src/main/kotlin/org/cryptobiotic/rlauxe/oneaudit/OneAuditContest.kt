package org.cryptobiotic.rlauxe.oneaudit

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.dhondt.DHondtContest
import org.cryptobiotic.rlauxe.util.margin2mean

import kotlin.collections.get

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

/* TODO OneAudit p.9-12 affine transform
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


interface OneAuditContestBuilderIF {
    val contestId: Int
    fun poolTotalCards(): Int // total cards in all pools for this contest
    fun expectedPoolNCards(): Int // expected total pool cards for this contest, making assumptions about missing undervotes
    fun adjustPoolInfo(cardPools: List<OneAuditPoolIF>)
}

private const val debug = false

// the contests share the audit pools, so its convenient to process them all at once?
fun makeOneAuditContests(
    wantContests: List<ContestIF>, // the contests you want to audit
    npopMap: Map<Int,Int>,  // contestId -> Npop
    cardPools: List<OneAuditPoolIF>,
): List<ContestWithAssertions> {

    val contestsUA = wantContests.filter{ !it.isIrv() }.map { contest ->
        val cua = ContestWithAssertions(contest, true, NpopIn=npopMap[contest.id]).addStandardAssertions()
        if (contest is DHondtContest) {
            cua.addAssertionsFromAssorters(contest.assorters)
        } else {
            cua.addStandardAssertions()
        }
        cua
    }

    // Its the OA assorters that make this a OneAudit contest
    setPoolAssorterAverages(contestsUA, cardPools)
    return contestsUA
}

// use dilutedMargin to set the pool assorter averages. can only use for non-IRV contests because calcMargin(regVotes)
// this also repalces the clcaAssertions with ones that use ClcaAssorterOneAudit which contain the pool assorter averages
fun setPoolAssorterAverages(
    oaContests: List<ContestWithAssertions>,
    pools: List<OneAuditPoolIF>, // poolId -> pool
) {
    val oneAuditErrorsFromPools = OneAuditRatesFromPools(pools)

    // ClcaAssorter already has the contest-wide reported margin. We just have to add the pool assorter averages
    // create the clcaAssertions and add then to the oaContests
    oaContests.filter { !it.isIrv}. forEach { oaContest ->
        val contestId = oaContest.id
        val clcaAssertions = oaContest.assertions.map { assertion ->
            val assortAverages = mutableMapOf<Int, Double>() // poolId -> average assort value
            pools.forEach { cardPool ->
                if (cardPool.hasContest(contestId)) {
                    val regVotes = cardPool.regVotes()[oaContest.id]!! // TODO assumes not IRV
                    if (cardPool.ncards() > 0) {
                        // note: using cardPool.ncards(), this is the diluted count
                        val poolMargin = assertion.assorter.calcMarginFromRegVotes(regVotes.votes, cardPool.ncards())
                        assortAverages[cardPool.poolId] = margin2mean(poolMargin)
                    }
                }
            }
            val oaAssorter = ClcaAssorterOneAudit(assertion.info, assertion.assorter,
                dilutedMargin = assertion.assorter.dilutedMargin(),
                poolAverages = AssortAvgsInPools(assortAverages))

            oaAssorter.oaAssortRates = oneAuditErrorsFromPools.oaErrorRates(oaContest, oaAssorter)

            ClcaAssertion(assertion.info, oaAssorter)
        }
        oaContest.clcaAssertions = clcaAssertions
    }
}

class ClcaAssorterOneAudit(
    info: ContestInfo,
    assorter: AssorterIF,   // A(mvr) Use this assorter for the CVRs
    dilutedMargin: Double,
    val poolAverages: AssortAvgsInPools,
) : ClcaAssorter(info, assorter, dilutedMargin=dilutedMargin) {
    override fun classname() = this::class.simpleName

    // convenient place to put this; set from outside
    var oaAssortRates: OneAuditAssortValueRates = OneAuditAssortValueRates(emptyMap(), 0)

    fun bassort1(mvr: CvrIF, cvr:CvrIF, hasStyle:Boolean=true): Double {
        val overstatement = overstatementError(mvr, cvr, hasStyle) // ωi eq (1)
        val tau = (1.0 - overstatement / this.assorter.upperBound()) // τi eq (6)
        return tau * noerror   // Bi eq (7)
    }

    // B(bi, ci)
    override fun bassort(mvr: CvrIF, cvr: CvrIF, hasStyle: Boolean): Double {
        if (cvr.poolId() == null) {
            return super.bassort(mvr, cvr, hasStyle) // here we use the standard assorter
        }

        // TODO add verifier of poolAvg existence
        val poolAverage = poolAverages.assortAverage[cvr.poolId()]
        if (poolAverage == null) {
            throw RuntimeException("OneAuditClcaAssorter couldnt find pool Avg for pool ${cvr.poolId()}")
        }

        // TODO im surprised that we dont return 0.5 when !mvr.hasContest(info.id)
        // if (!mvr.hasContest(info.id)) { if (hasStyle) 0.0 else 0.5 }

        val overstatement = overstatementPoolError(mvr, poolAverage, hasStyle) // ωi
        val tau = (1.0 - overstatement / this.assorter.upperBound())

        val result =  tau * noerror()
        if (result > upperBound()) {
            throw RuntimeException("OneAuditClcaAssorter result $result > upper ${upperBound()}")
        }

        // eq 10 of OneAudit.
        return result
    }

    fun overstatementError1(mvr: CvrIF, cvr: CvrIF, hasStyle: Boolean): Double {

        if (hasStyle and !cvr.hasContest(info.id)) {
            val trace = Throwable().stackTraceToString()
            // logger.error { "hasCompleteCvrs==True but cvr=${cvr} does not contain contest ${info.name} (${info.id})\n$trace" }
            // TODO core dump not a good option.
            //    if we were using hasStyle in assorter.assort(), it would return 0.0 for cvr_assort
            throw RuntimeException("hasCompleteCvrs==True but cvr=${cvr} does not contain contest ${info.name} (${info.id})")
        }

        val mvr_assort =
            if (mvr.isPhantom()) 0.0
            else if (!mvr.hasContest(info.id)) { if (hasStyle) 0.0 else 0.5 }
            else this.assorter.assort(mvr, usePhantoms = false)

        val cvr_assort = if (cvr.isPhantom()) .5 else this.assorter.assort(cvr, usePhantoms = false)

        return cvr_assort - mvr_assort
    }

    fun overstatementPoolError(mvr: CvrIF, poolAvgAssortValue: Double, hasStyle: Boolean): Double {
        val mvr_assort =
            if (mvr.isPhantom()) 0.0
            else if (!mvr.hasContest(info.id)) { if (hasStyle) 0.0 else 0.5 }
            else this.assorter.assort(mvr, usePhantoms = false)

        // val cvr_assort = if (cvr.phantom) .5 else poolAvgAssortValue
        val cvr_assort = poolAvgAssortValue
        return cvr_assort - mvr_assort
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as ClcaAssorterOneAudit

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
Audit.py line 1604

    def mvrs_to_data(
        self, mvr_sample: list = None, cvr_sample: list = None, use_all: bool=False
    ) -> np.array:
        """
        Process mvrs (and, for comparison audits, cvrs) to create data for the assertion's test
        and for sample size simulations.

        Creates assorter values for the mvrs, or overstatement assorter values using the mvrs and cvrs,
        according to whether the audit uses ballot polling or card-level comparison

        The margin should be set before calling this function.

        mvr_sample and cvr_sample should be ordered using CVR.prep_comparison_sample() or
           CVR.prep_polling_sample() before calling this routine

        Parameters
        ----------
        mvr_sample: list of CVR objects
            corresponding MVRs
        cvr_sample: list of CVR objects
            sampled CVRs
        use_all: bool
            if True, ignore contest sample_num in determining which pairs to include

        Returns
        -------
        d: np.array
            either assorter values or overstatement assorter values, depending on the audit method
        u: upper bound for the test
        """
        margin = self.margin
        upper_bound = self.assorter.upper_bound
        con = self.contest
        use_style = con.use_style
        if con.audit_type in [
            Audit.AUDIT_TYPE.CARD_COMPARISON,
            Audit.AUDIT_TYPE.ONEAUDIT,
        ]:
            d = np.array(
                [
                    self.overstatement_assorter(
                        mvr_sample[i], cvr_sample[i], use_style=use_style
                    )
                    for i in range(len(mvr_sample))
                    if (
                        (not use_style)
                        or (
                            cvr_sample[i].has_contest(con.id)
                            and (use_all or (cvr_sample[i].sample_num <= con.sample_threshold))
                        )
                    )
                ]
            )
            u = 2 / (2 - margin / upper_bound)

Audit.py line 1450

    def overstatement_assorter(
        self, mvr: CVR = None, cvr: CVR = None, use_style=True
    ) -> float:
        """
        assorter that corresponds to normalized overstatement error for an assertion

        If `use_style == True`, then if the CVR contains the contest but the MVR does not,
        that is considered to be an overstatement, because the ballot is presumed to contain
        the contest.

        If `use_style == False`, then if the CVR contains the contest but the MVR does not,
        the MVR is considered to be a non-vote in the contest.

        Parameters
        -----------
        mvr: CVR
            the manual interpretation of voter intent
        cvr: CVR
            the machine-reported cast vote record.

        Returns
        --------
        over: float
            (1-o/u)/(2-v/u), where
                o is the overstatement
                u is the upper bound on the value the assorter assigns to any ballot
                v is the assorter margin
        """
        return (
            1
            - self.assorter.overstatement(mvr, cvr, use_style)
            / self.assorter.upper_bound
        ) / (2 - self.margin / self.assorter.upper_bound)

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
            self.tally_pool_means[cvr.tally_pool]
            if
                cvr.pool and self.tally_pool_means is not None
            else
                int(cvr.phantom) / 2 + (1 - int(cvr.phantom)) * self.assort(cvr)
        )
        return cvr_assort - mvr_assort
 */