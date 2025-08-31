package org.cryptobiotic.rlauxe.oneaudit

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.*

open class OAContestUnderAudit(
    contest: ContestIF,
    hasStyle: Boolean = true
): ContestUnderAudit(contest, isComparison=true, hasStyle=hasStyle) {

    // TODO did override     open fun makeClcaAssorter(assertion: Assertion, assortValueFromCvrs: Double?): ClcaAssorter {
    fun makeClcaAssorter(assertion: Assertion, poolAverages: AssortAvgsInPools): ClcaAssorter {
        return OneAuditClcaAssorter(contest.info(), assertion.assorter, hasStyle = true, poolAverages)
    }

    override fun show() = buildString {
        appendLine("${contest.javaClass.simpleName} $contest")
        appendLine(" margin=${df(minMargin())} recount=${df(recountMargin())} Nc=$Nc Np=$Np")
        appendLine(" choiceFunction=${choiceFunction} nwinners=${contest.info().nwinners}, winners=${contest.winners()}")
        append(showCandidates())
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
        val overstatement = overstatementPoolError(mvr, cvr, poolAverage) // ωi
        val tau = (1.0 - overstatement / this.assorter.upperBound())

        // for pooled data (i in Gg):
        //   A(ci) = 1/2 or poolAvg = [pa, pa-1/2, pa-1]
        //   A(bi) in [0, .5, u],
        //   so ωi ≡ A(ci) − A(bi) ranges from -u to u
        //   so (1 − (ωi / u)) ranges from 0 to 2, and B ranges from [0, 2] * noerror

        //// TODO which? had error using the pool margin (pool.calcReportedMargin) instead of the assorter margin (in noerror)
        //     ONEAUDIT eq 6 has a denominator = 2u − v, which is the same for all ballots, so it cant be pool dependent.
        //     v := 2Āc − 1
        //     noerror = 1.0 / (2.0 - v / assorter.upperBound())  // assort value when there's no error

        val result =  tau * noerror()
        if (result > upperBound()) {
            throw RuntimeException("OneAuditClcaAssorter result $result > upper ${upperBound()}")
        }

        // eq 10 of OneAudit.
        return result
    }

    fun overstatementPoolError(mvr: Cvr, cvr: Cvr, avgBatchAssortValue: Double, hasStyle: Boolean = true): Double {
        if (hasStyle and !cvr.hasContest(info.id)) {
            // TODO log error
            throw RuntimeException("use_style==True but cvr=${cvr} does not contain contest ${info.name} (${info.id})")
        }
        val mvr_assort = if (mvr.phantom || (hasStyle && !mvr.hasContest(info.id))) 0.0
                         else this.assorter.assort(mvr, usePhantoms = false)

        // for pooled data (i in Gg):
        //   A(ci) = 1/2 or poolAvg in [0..u]
        //   A(bi) in [0, .5, u],
        //   so ωi ≡ A(ci) − A(bi) ranges from -u to u

        // let u = 1, let poolAvg = pa, and pool margin = pv = 2*pa - 1
        // so ωi ≡ A(ci) − A(bi)
        //   1/2 - 0, 1/2 - 1/2, 1/2 - 1 = [1/2, 0, -1/2] (or)
        //   poolAvg - 0, poolAvg - 1/2, poolAvg - 1 in [pa, pa-1/2,pa-1]

        val cvr_assort = if (cvr.phantom) .5 else avgBatchAssortValue
        return cvr_assort - mvr_assort
    }

    override fun toString() = buildString {
        appendLine("OneAuditClcaAssorter for contest ${info.name} (${info.id})")
        appendLine("  assorter=${assorter.desc()}")
        appendLine("  cvrAssortMargin=$cvrAssortMargin noerror=$noerror upperBound=$upperBound assortValueFromCvrs=$assortAverageFromCvrs")
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