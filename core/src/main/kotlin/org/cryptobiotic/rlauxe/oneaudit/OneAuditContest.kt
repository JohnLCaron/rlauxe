package org.cryptobiotic.rlauxe.oneaudit

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.*
import kotlin.math.min

data class OneAuditContest (
    override val info: ContestInfo,
    val cvrVotes: Map<Int, Int>,   // candidateId -> nvotes;  sum is nvotes or V_c
    val cvrNc: Int,
    val pools: Map<Int, BallotPool>, // pool id -> pool
) : ContestIF {
    override val id = info.id
    val name = info.name
    override val choiceFunction = info.choiceFunction
    override val ncandidates = info.candidateIds.size

    val votes: Map<Int, Int>  // cand -> vote
    override val winnerNames: List<String>
    override val winners: List<Int>
    override val losers: List<Int>

    override val Nc: Int  // upper limit on number of ballots for all strata for this contest
    override val Np: Int  // number of phantom ballots for all strata for this contest

    override val undervotes: Int
    val minMargin: Double

    init {
        val poolNc = pools.values.sumOf { it.ncards }
        Nc = poolNc + cvrNc
        Np = 0 // TODO

        //// construct total votes, adding 0 votes if needed
        val voteBuilder = mutableMapOf<Int, Int>()  // cand -> vote
        cvrVotes.forEach { cand, votes ->
            val tvote = voteBuilder[cand] ?: 0
            voteBuilder[cand] = tvote + votes
        }
        pools.values.forEach { pool ->
            require(pool.contest == info.id)
            pool.votes.forEach { cand, votes ->
                val tvote = voteBuilder[cand] ?: 0
                voteBuilder[cand] = tvote + votes
            }
        }
        info.candidateIds.forEach {
            if (!voteBuilder.contains(it)) {
                voteBuilder[it] = 0
            }
        }
        votes = voteBuilder.toMap()

        //// find winners, check that the minimum value is satisfied
        // This works for PLURALITY, APPROVAL, SUPERMAJORITY.  IRV not supported I think
        val useMin = info.minFraction ?: 0.0
        val nvotes = votes.values.sum()
        val overTheMin = votes.toList().filter{ it.second.toDouble()/nvotes >= useMin }.sortedBy{ it.second }.reversed()
        val useNwinners = min(overTheMin.size, info.nwinners)
        winners = overTheMin.subList(0, useNwinners).map { it.first }
        val mapIdToName: Map<Int, String> = info.candidateNames.toList().associate { Pair(it.second, it.first) } // invert the map
        winnerNames = winners.map { mapIdToName[it]!! }

        // find losers
        val mlosers = mutableListOf<Int>()
        info.candidateNames.forEach { (_, id) ->
            if (!winners.contains(id)) mlosers.add(id)
        }
        losers = mlosers.toList()
        undervotes = Nc * info.nwinners - nvotes - Np

        val sortedVotes = votes.toList().sortedBy{ it.second }.reversed()
        minMargin = (sortedVotes[0].second - sortedVotes[1].second) / Nc.toDouble()
    }

    fun makeContestUnderAudit() : OAContestUnderAudit {
        val contestUA = OAContestUnderAudit(this)
        contestUA.makeClcaAssertions()
        return contestUA
    }

    fun makeContest() = Contest(info, votes, Nc, Np)

    fun reportedMargin(winner: Int, loser:Int): Double {
        val winnerVotes = votes[winner] ?: 0
        val loserVotes = votes[loser] ?: 0
        return (winnerVotes - loserVotes) / Nc.toDouble()
    }

    override fun toString() = buildString {
        appendLine("$name ($id) Nc=$Nc Np=$Np votes=${votes} minMargin=${df(minMargin)}")
    }
}

/////////////////////////////////////////////////////////////////////////////////////////////

class OAContestUnderAudit(
    val contestOA: OneAuditContest,
    isComparison: Boolean = true,
    hasStyle: Boolean = true
): ContestUnderAudit(contestOA.makeContest(), isComparison=isComparison, hasStyle=hasStyle) {

    override fun makeClcaAssertions(): ContestUnderAudit {
        // TODO assume its plurality for now
        val assertions = mutableListOf<Assertion>()
        contest.winners.forEach { winner ->
            contest.losers.forEach { loser ->
                val baseAssorter = PluralityAssorter.makeWithVotes(this.contest, winner, loser, contestOA.votes)
                assertions.add( Assertion( this.contest.info, baseAssorter))
            }
        }
        // turn into comparison assertions
        this.clcaAssertions = assertions.map { assertion ->
            val margin = assertion.assorter.reportedMargin()
            ClcaAssertion(contest.info, OneAuditAssorter(this.contestOA, assertion.assorter, margin2mean(margin)))
        }
        return this
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as OAContestUnderAudit

        return contestOA == other.contestOA
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + contestOA.hashCode()
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
class OneAuditAssorter(
    val contestOA: OneAuditContest,
    assorter: AssorterIF,   // A(mvr)
    avgCvrAssortValue: Double,    // Ā(c) = average CVR assorter value
) : ClcaAssorter(contestOA.info, assorter, avgCvrAssortValue) {

    // B(bi, ci)
    override fun bassort(mvr: Cvr, cvr: Cvr): Double {
        if (cvr.poolId == null) {
            return super.bassort(mvr, cvr)
        }
        val pool = contestOA.pools[cvr.poolId]
        if (pool == null) { throw IllegalStateException("Dont have pool ${cvr.poolId} in contest ${contestOA.id}") }

        // TODO do you believe this is the same as if you ran the assorter on the cvrs? But we dont have the cvrs....
        val avgBatchAssortValue = margin2mean(pool.calcReportedMargin(assorter.winner(), assorter.loser()))
        val overstatement = overstatementPoolError(mvr, cvr, avgBatchAssortValue) // ωi
        val tau = (1.0 - overstatement / this.assorter.upperBound())

        //// had error using the pool margin (pool.calcReportedMargin) instead of the assorter margin (in noerror)
        //val margin = 2.0 * avgBatchAssortValue - 1.0 // reported pool margin
        //val noerror = 1.0 / (2.0 - margin / assorter.upperBound())  // assort value when there's no error

        // for pooled data (i in Gg):
        //   A(ci) = 1/2 or poolAvg = 0 to 1 TODO assume 0 to u ??
        //   A(bi) in [0, .5, u],
        //   so ωi ≡ A(ci) − A(bi) ranges from -u to u
        //   so (1 − (ωi / u)) ranges from 0 to 2, and B ranges from [0, 2] * noerror
        val result =  tau * noerror()
        if (result > upperBound()) {
            println("how?")
            val poolMargin = pool.calcReportedMargin(assorter.winner(), assorter.loser())
            val mean = margin2mean(poolMargin)
            overstatementPoolError(mvr, cvr, avgBatchAssortValue)
        }
        return result
    }

    fun overstatementPoolError(mvr: Cvr, cvr: Cvr, avgBatchAssortValue: Double, hasStyle: Boolean = true): Double {
        if (hasStyle and !cvr.hasContest(contestOA.id)) {
            throw RuntimeException("use_style==True but cvr=${cvr} does not contain contest ${contestOA.name} (${contestOA.id})")
        }
        val mvr_assort = if (mvr.phantom || (hasStyle && !mvr.hasContest(contestOA.id))) 0.0
                         else this.assorter.assort(mvr, usePhantoms = false)

        // for pooled data (i in Gg):
        //   A(ci) = 1/2 or poolAvg = 0 to 1 TODO assume 0 to u ?? Note poolAvg may be less than 1/2
        //   A(bi) in [0, .5, u],
        //   so ωi ≡ A(ci) − A(bi) ranges from -u to u

        val cvr_assort = if (cvr.phantom) .5 else avgBatchAssortValue // TODO phantom not ever set?
        return cvr_assort - mvr_assort
    }

    override fun toString() = buildString {
        appendLine("OneAuditComparisonAssorter for contest ${contestOA.name} (${contestOA.id})")
        appendLine("  assorter=${assorter.desc()}")
        append("  avgCvrAssortValue=$avgCvrAssortValue")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as OneAuditAssorter

        return contestOA == other.contestOA
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + contestOA.hashCode()
        return result
    }
}

