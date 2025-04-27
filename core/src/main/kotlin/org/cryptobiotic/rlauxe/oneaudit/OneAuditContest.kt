package org.cryptobiotic.rlauxe.oneaudit

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.*
import kotlin.math.min


// PS email 3/27/25:
// With ONEAudit, things get more complicated because you have to start by adding every contest that appears on any card
// in a tally batch to every card in that tally batch and increase the upper bound on the number of cards in
// the contest appropriately. That's in the SHANGRLA codebase.
//  (I think this is the case when theres no style info for the pooled cards)

data class OneAuditContest (
    override val info: ContestInfo,
    val cvrVotes: Map<Int, Int>,   // candidateId -> nvotes
    val cvrNc: Int,                // the diff from cvrVotes tells you the undervotes
    val pools: Map<Int, BallotPool>, // pool id -> pool
    override val Np: Int,
) : ContestIF {
    // TODO why not subclass Contest ? hard to get the voteInput into the constructor
    // Contest(
    //        override val info: ContestInfo,
    //        voteInput: Map<Int, Int>,   // candidateId -> nvotes;  sum is nvotes or V_c
    //        override val Nc: Int,
    //        override val Np: Int,
    //    )

    override val id = info.id
    val name = info.name
    override val choiceFunction = info.choiceFunction
    override val ncandidates = info.candidateIds.size

    val votes: Map<Int, Int>  // cand -> vote
    override val winnerNames: List<String>
    override val winners: List<Int>
    override val losers: List<Int>

    override val Nc: Int  // upper limit on number of ballots for all strata for this contest
    val minMargin: Double
    val poolNc: Int
    val pctInPools: Double
    val undervotes: Int

    init {
        // TODO add SUPERMAJORITY. What about IRV ??
        require(choiceFunction == SocialChoiceFunction.PLURALITY) { "OneAuditContest requires PLURALITY"}

        poolNc = pools.values.sumOf { it.ncards }

        Nc = poolNc + cvrNc + Np
        pctInPools = poolNc / Nc.toDouble()

        // how many undervotes are there ?
        val poolVotes = pools.values.sumOf { it.votes.values.sum() }
        require (poolNc * info.voteForN >= poolVotes)
        val poolUndervotes = poolNc - poolVotes

        val cvrVotesTotal = cvrVotes.values.sumOf { it }
        require (cvrNc * info.voteForN >= cvrVotesTotal)
        val cvrUndervotes = cvrNc - cvrVotesTotal
        undervotes = poolUndervotes + cvrUndervotes

        //// construct total votes
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
        // add 0 candidate votes if needed
        info.candidateIds.forEach {
            if (!voteBuilder.contains(it)) {
                voteBuilder[it] = 0
            }
        }
        votes = voteBuilder.toList().sortedBy{ it.second }.reversed().toMap()

        //// find winners, check that the minimum value is satisfied
        // This works for PLURALITY, APPROVAL, SUPERMAJORITY.  IRV not supported
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

        val sortedVotes = votes.toList().sortedBy{ it.second }.reversed()
        minMargin = (sortedVotes[0].second - sortedVotes[1].second) / Nc.toDouble()
    }

    fun makeContestUnderAudit() : OAContestUnderAudit {
        val contestUA = OAContestUnderAudit(this)
        contestUA.makeClcaAssertionsFromReportedMargin()
        return contestUA
    }

    fun makeContest() = Contest(info, votes, Nc, Np)

    // candidate for removal
    fun reportedMargin(winner: Int, loser:Int): Double {
        val winnerVotes = votes[winner] ?: 0
        val loserVotes = votes[loser] ?: 0
        return (winnerVotes - loserVotes) / Nc.toDouble()
    }

    fun reportedMarginNonPooled(winner: Int, loser:Int): Double {
        val winnerVotes = cvrVotes[winner] ?: 0
        val loserVotes = cvrVotes[loser] ?: 0
        return (winnerVotes - loserVotes) / cvrNc.toDouble()
    }

    override fun toString() = buildString {
        appendLine("$name ($id) Nc=$Nc Np=$Np votes=${votes} minMargin=${df(minMargin)}")
        appendLine("  cvrNc=$cvrNc npools= ${pools.size} poolNc=$poolNc pctInPools=${df(pctInPools)}")
    }
}

fun showPct(what: String, votes: Map<Int, Int>, Nc: Int) {
    val winnerVotes = votes[0] ?: 0
    val loserVotes = votes[1] ?: 0
    val hasMargin = (winnerVotes - loserVotes) / Nc.toDouble()
    println("$what winnerVotes = $winnerVotes loserVotes = $loserVotes diff=${winnerVotes-loserVotes} Nc=${Nc} hasMargin=$hasMargin ")
}

/////////////////////////////////////////////////////////////////////////////////////////////

class OAContestUnderAudit(
    val contestOA: OneAuditContest,
    hasStyle: Boolean = true
): ContestUnderAudit(contestOA.makeContest(), isComparison=true, hasStyle=hasStyle) {

    override fun makeClcaAssorter(assertion: Assertion, assortValueFromCvrs: Double?): ClcaAssorter {
        // dont use assortValueFromCvrs. TODO: consider different primitive assorter, that knows about pools.
        return OneAuditClcaAssorter(this.contestOA, assertion.assorter, null)
    }

    override fun show() = buildString {
        val votes = if (contest is Contest) contest.votes else emptyMap()
        appendLine("${contestOA.javaClass.simpleName} $contestOA")
        appendLine(" margin=${df(minMargin())} recount=${df(recountMargin())} Nc=$Nc Np=$Np")
        appendLine(" choiceFunction=${choiceFunction} nwinners=${contest.info.nwinners}, winners=${contest.winners}")
        append(showCandidates())
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

class OneAuditClcaAssorter(
    val contestOA: OneAuditContest,
    assorter: AssorterIF,   // A(mvr)
    assortValueFromCvrs: Double?,    // Ā(c) = average CVR assorter value
) : ClcaAssorter(contestOA.info, assorter, assortValueFromCvrs) {
    var countAssort = 0

    // TODO this is not accurate because not using Nc as the denominator
    // We assume we have a reported assorter total = Sum(i∈Gg A(ci))
    // from the voting system for the cards in the group Gg (e.g., reported precinct subtotals)
    // this agrees with the reportedMean from the total
    // however, we might want to do an affine transform to set range to [0, max]
    fun calcAssortMeanFromPools(): Double {
        val welford = Welford()
        val cvrMargin = contestOA.reportedMarginNonPooled(assorter.winner(), assorter.loser())
        welford.update(margin2mean(cvrMargin), contestOA.cvrNc)
        contestOA.pools.values.forEach { pool ->
            val poolMargin = pool.calcReportedMargin(assorter.winner(), assorter.loser())
            welford.update(margin2mean(poolMargin), pool.ncards)
        }
        return welford.mean
    }

    // B(bi, ci)
    override fun bassort(mvr: Cvr, cvr: Cvr): Double {
        if (cvr.poolId == null) {
            val result =  super.bassort(mvr, cvr) // TODO doAffineTransform ??
            if (countAssort < countAssortMax) {
                println("bassort-cvr ${cvr.id} = $result")
                countAssort++
            }
            return result
        }
        val pool = contestOA.pools[cvr.poolId]
            ?: throw IllegalStateException("Dont have pool ${cvr.poolId} in contest ${contestOA.id}")

        val poolAverage = margin2mean(pool.calcReportedMargin(assorter.winner(), assorter.loser()))
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
        if (countAssort < countAssortMax) {
            println("bassort-pool ${cvr.id} = $result")
            countAssort++
        }

        // supposedly eq 10 of OneAudit. checking with PS.
        return if (doAffineTransform) {
            val min = (1.0 - poolAverage) / (2 - cvrAssortMargin)
            val max = (2.0 - poolAverage) / (2 - cvrAssortMargin)
            return (result - min) / (max - min)
        }
        else result
    }

    fun overstatementPoolError(mvr: Cvr, cvr: Cvr, avgBatchAssortValue: Double, hasStyle: Boolean = true): Double {
        if (hasStyle and !cvr.hasContest(contestOA.id)) {
            throw RuntimeException("use_style==True but cvr=${cvr} does not contain contest ${contestOA.name} (${contestOA.id})")
        }
        val mvr_assort = if (mvr.phantom || (hasStyle && !mvr.hasContest(contestOA.id))) 0.0
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

        // println("   cvr_assort=$cvr_assort mvr_assort=$mvr_assort")
        return cvr_assort - mvr_assort
    }

    override fun toString() = buildString {
        appendLine("OneAuditComparisonAssorter for contest ${contestOA.name} (${contestOA.id})")
        appendLine("  assorter=${assorter.desc()}")
        append("  cvrAssortMargin=$cvrAssortMargin noerror=$noerror upperBound=$upperBound assortValueFromCvrs=$assortValueFromCvrs")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as OneAuditClcaAssorter

        return contestOA == other.contestOA
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + contestOA.hashCode()
        return result
    }

    companion object {
        var countAssortMax = 0
        var doAffineTransform = false
    }
}

/* Experimental. need to make PluralityAssorter open, not data class
class OaPluralityAssorter(val contestOA: OneAuditContest, winner: Int, loser: Int, reportedMargin: Double):
    PluralityAssorter(contestOA.info, winner, loser, reportedMargin) {

    override fun assort(cvr: Cvr, usePhantoms: Boolean): Double {
        if (cvr.poolId == null) {
            return super.assort(cvr, usePhantoms = usePhantoms)
        }

        // TODO not sure of this
        //     if (hasStyle and !cvr.hasContest(contestOA.id)) {
        //            throw RuntimeException("use_style==True but cvr=${cvr} does not contain contest ${contestOA.name} (${contestOA.id})")
        //        }
        //        val mvr_assort = if (mvr.phantom || (hasStyle && !mvr.hasContest(contestOA.id))) 0.0
        //                         else this.assorter.assort(mvr, usePhantoms = false)

        val pool = contestOA.pools[cvr.poolId]
            ?: throw IllegalStateException("Dont have pool ${cvr.poolId} in contest ${contestOA.id}")
        val avgBatchAssortValue = margin2mean(pool.calcReportedMargin(winner, loser))

        return if (cvr.phantom) .5 else avgBatchAssortValue
    }

    companion object {
        fun makeFromContestVotes(contest: OneAuditContest, winner: Int, loser: Int): OaPluralityAssorter {
            val winnerVotes = contest.votes[winner] ?: 0
            val loserVotes = contest.votes[loser] ?: 0
            val reportedMargin = (winnerVotes - loserVotes) / contest.Nc.toDouble()
            return OaPluralityAssorter(contest, winner, loser, reportedMargin)
        }
    }
} */

// ONEAUDIT p 9
// This algorithm be made more efficient statistically and logistically in a variety
// of ways, for instance, by making an affine translation of the data so that the
// minimum possible value is 0 (by subtracting the minimum of the possible over-
// statement assorters across batches and re-scaling so that the null mean is still 1/2)
//
// Also see Section 5.1 and fig 1. "The original assorter values will generally be closer to the endpoints of [0, u]
// than the transformed values are to the endpoints of [0, 2u/(2u − v)]"
// TODO investigate: "An affine transformation of the over-statement assorter values can move them back to the endpoints
//    of the support constraint".
//    where does this happen? The constraint is "the null mean is 1/2".
//
// A test that uses the prior information xj ∈ [0, u] may not be as efficient for
// populations for which xj ∈ [a, b] with a > 0 and b < u as it is for populations
// where the values 0 and u actually occur. An affine transformation of the over-
// statement assorter values can move them back to the endpoints of the support
// constraint by subtracting the minimum possible value then re-scaling so that the
// null mean is 1/2 once again, which reproduces the original assorter, A. (see eq 10)
//
// I think the claim is that you can find a better bounds based on avgAssortValue?
// is SHANGRLA already doing that??

