package org.cryptobiotic.rlauxe.oneaudit

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.*
import kotlin.math.min


// PS email 3/27/25:
// With ONEAudit, things get more complicated because you have to start by adding every contest that appears on any card
// in a tally batch to every card in that tally batch and increase the upper bound on the number of cards in
// the contest appropriately. That's in the SHANGRLA codebase.
//  (I think this is the case when theres no style info for the pooled cards)

class OneAuditContest (
    info: ContestInfo,
    voteInput: Map<Int, Int>,
    Nc: Int,
    Np: Int,

    val cvrVotes: Map<Int, Int>,   // candidateId -> nvotes (may be empty)
    val cvrNc: Int,                // may be 0
    val pools: Map<Int, BallotPool>, // pool id -> pool
) : Contest(info, voteInput, Nc, Np) {

    val minMargin: Double
    val poolNc = pools.values.sumOf { it.ncards }

    init {
        // TODO add SUPERMAJORITY. What about IRV ??
        require(choiceFunction == SocialChoiceFunction.PLURALITY) { "OneAuditContest requires PLURALITY"}

        // how many undervotes are there ?
        val poolVotes = pools.values.sumOf { it.votes.values.sum() }
        require (poolNc * info.voteForN >= poolVotes)
        val poolUndervotes = poolNc * info.voteForN - poolVotes

        val cvrVotesTotal = cvrVotes.values.sumOf { it }
        require (cvrNc * info.voteForN >= cvrVotesTotal)
        val cvrUndervotes = cvrNc * info.voteForN - cvrVotesTotal
        val undervotes2 = poolUndervotes + cvrUndervotes
        require (undervotes == undervotes2)

        // not sure about where undervotes and phantoms live
        require(Nc == cvrNc + poolNc + Np)

        minMargin = if (votes.size < 2) 0.0 else {
            val sortedVotes = votes.toList().sortedBy { it.second }.reversed()
            (sortedVotes[0].second - sortedVotes[1].second) / Nc.toDouble()
        }
    }

    fun makeContestUnderAudit() : OAContestUnderAudit {
        val contestUA = OAContestUnderAudit(this)
        contestUA.makeClcaAssertionsFromReportedMargin()
        return contestUA
    }

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

    fun cvrVotesAndUndervotes(): Map<Int, Int> {
        val cvrVotesTotal = cvrVotes.values.sumOf { it }
        val cvrUndervotes = cvrNc * info.voteForN - cvrVotesTotal
        return (cvrVotes.map { Pair(it.key, it.value)} + Pair(this.ncandidates, cvrUndervotes)).toMap()
    }

    fun poolVotesAndUnderVotes() : Map<Int, Int> { // contestId -> candidateId -> nvotes
        val poolVotes = mutableMapOf<Int, Int>()

        // TODO general Map merge
        pools.values.forEach { pool ->
            pool.votes.entries.forEach { (cand, vote) ->
                val candAccum = poolVotes.getOrPut(cand) { 0 }
                poolVotes[cand] = candAccum + vote
            }
        }

        val poolVotesTotal = poolVotes.values.sumOf { it }
        val poolUndervotes = poolNc * info.voteForN - poolVotesTotal

        val result =  (poolVotes.map { Pair(it.key, it.value) } + Pair(this.ncandidates, poolUndervotes)).toMap()
        return result
    }

    override fun toString() = buildString {
        appendLine("$name ($id) Nc=$Nc Np=$Np votesAndUnderVotes=${votesAndUndervotes()} minMargin=${df(minMargin)}")
        appendLine("  cvrNc=$cvrNc cvrVotesAndUndervotes()=${cvrVotesAndUndervotes()}")
        appendLine("  poolNc=$poolNc poolVotesAndUnderVotes= ${poolVotesAndUnderVotes()} npools= ${pools.size} pctInPools=${df(poolNc / Nc.toDouble())}")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as OneAuditContest

        if (cvrNc != other.cvrNc) return false
        if (minMargin != other.minMargin) return false
        if (poolNc != other.poolNc) return false
        if (cvrVotes != other.cvrVotes) return false
        if (pools != other.pools) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + cvrNc
        result = 31 * result + minMargin.hashCode()
        result = 31 * result + poolNc
        result = 31 * result + cvrVotes.hashCode()
        result = 31 * result + pools.hashCode()
        return result
    }

    companion object {
        fun make(info: ContestInfo,
                          cvrVotes: Map<Int, Int>,   // candidateId -> nvotes
                          cvrNc: Int,                // the diff from cvrVotes tells you the undervotes
                          pools: List<BallotPool>,   // pools for this contest
                          Np: Int): OneAuditContest {

            val poolNc = pools.sumOf { it.ncards }
            val Nc = poolNc + cvrNc + Np

            //// construct total votes
            val voteBuilder = mutableMapOf<Int, Int>()  // cand -> vote
            cvrVotes.forEach { (cand, votes) ->
                val tvote = voteBuilder[cand] ?: 0
                voteBuilder[cand] = tvote + votes
            }
            pools.forEach { pool ->
                require(pool.contest == info.id)
                pool.votes.forEach { (cand, votes) ->
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
            val voteInput = voteBuilder.toList().sortedBy{ it.second }.reversed().toMap()

            return OneAuditContest(info, voteInput, Nc, Np, cvrVotes, cvrNc, pools.associateBy { it.poolId })
        }
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
): ContestUnderAudit(contestOA, isComparison=true, hasStyle=hasStyle) {

    override fun makeClcaAssorter(assertion: Assertion, assortValueFromCvrs: Double?): ClcaAssorter {
        // dont use assortValueFromCvrs. TODO: consider different primitive assorter, that knows about pools.
        return OneAuditClcaAssorter(this.contestOA, assertion.assorter, null)
    }

    override fun show() = buildString {
        appendLine("${contestOA.javaClass.simpleName} $contestOA")
        appendLine(" margin=${df(minMargin())} recount=${df(recountMargin())} Nc=$Nc Np=$Np")
        appendLine(" choiceFunction=${choiceFunction} nwinners=${contestOA.info.nwinners}, winners=${contest.winners()}")
        append(showCandidates())
    }


    fun showPools(mvrs: List<Cvr>) {
        val minAllAssorter = minClcaAssertion()!!.assorter
        contestOA.pools.forEach { (id, pool) ->
            val poolCvrs = mvrs.filter{ it.poolId == id}
            val poolAssortAvg = margin2mean(minAllAssorter.calcAssorterMargin(contestOA.id, poolCvrs))
            val poolReportedAvg = margin2mean(minAllAssorter.calcReportedMargin(pool.votes, pool.ncards))
            if (!doubleIsClose(poolReportedAvg, poolAssortAvg)) print(" **** ")
            println(
                "pool-${id} votes = ${pool.votesAndUndervotes(contestOA.info.voteForN, contestOA.ncandidates)} " +
                        "poolAssortAvg = $poolAssortAvg reportedAvg = $poolReportedAvg"
            )
        }
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
    assorter: AssorterIF,   // A(mvr) Use this assorter for the CVRs
    assortValueFromCvrs: Double?,    // Ā(c) = average CVR assorter value. TODO wrong ??
) : ClcaAssorter(contestOA.info, assorter, assortValueFromCvrs) {
    private val doAffineTransform = (contestOA.cvrNc == 0) // this is a "batch level comparison audit" (BLCA)
    private val affineMin: Double
    private val affineIScale: Double

    // we need this to test the assorter average calculation
    // same calculation is embodied in overstatementPoolError. TODO test this more
    val oaAssorter = OaPluralityAssorter.makeFromContestVotes(contestOA, assorter.winner(), assorter.loser())

    init {
        // TODO test effect of this
        if (doAffineTransform) {
            var minAverage = Double.MAX_VALUE
            contestOA.pools.values.forEach { pool ->
                val poolAverage = margin2mean(pool.calcReportedMargin(assorter.winner(), assorter.loser()))
                minAverage = min(minAverage, (1.0 - poolAverage) / (2 - cvrAssortMargin))
            }
            affineMin = minAverage
            // make af(1/2) = 1/2; (1/2 - m)/scale = 1/2; scale = (1 - 2m)
            affineIScale = 1.0 / (1.0 - 2 * minAverage)
        } else {
            affineMin = 0.0
            affineIScale = 1.0
        }
    }

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
            require(!doAffineTransform)
            return super.bassort(mvr, cvr) // here we use the standard assorter
        }

        // if (hasStyle && !mvr.hasContest(contestOA.id)) return 0.5   TODO does this solve the undervote problem ??

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

        // eq 10 of OneAudit.
        return if (doAffineTransform) affineIScale * (result - affineMin) else result
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
}

// OneAuditComparisonAssorter for contest St. Vrain and Left Hand Water Conservancy District Ballot Issue 7C (64)
//  assorter= winner=0 loser=1 reportedMargin=0.6556 reportedMean=0.8278
//  cvrAssortMargin=0.6555896614618087 noerror=0.7438205221534695 upperBound=1.487641044306939 assortValueFromCvrs=null
//  mvrVotes = {0=51846, 1=8841, 2=6750} NC=67437
//     pAssorter reportedMargin=0.6555896614618087 reportedAvg=0.8277948307309044 assortAvg = 0.8188531518305975 ******
//     oaAssorter reportedMargin=0.6555896614618087 reportedAvg=0.8277948307309044 assortAvg = 0.827794830730896
// ****** passortAvg != oassortAvg
//
// OneAuditClcaAssorter.assorter = pAssorter reportedMargin agrees with oAassortAvg
// OaPluralityAssorter = oaAssorter reportedMargin agrees with OaPluralityAssorter.assort(cvrs).mean
// pAssorter reportedMargin does not agree with pAssortAvg, because pAssorter doesnt use the average pool values. ****
//
// why are we using reqular pAssort instead of OaPluralityAssorter in the OneAuditClcaAssorter?
// because its the assorter you have to use for the cvrs

// This is the primitive assorter whose average assort values agrees with the reportedMargin.
// TODO check against SHANGRLA
class OaPluralityAssorter(val contestOA: OneAuditContest, winner: Int, loser: Int, reportedMargin: Double):
    PluralityAssorter(contestOA.info, winner, loser, reportedMargin) {

    override fun assort(mvr: Cvr, usePhantoms: Boolean): Double {
        if (mvr.poolId == null) {
            return super.assort(mvr, usePhantoms = usePhantoms)
        }

        // TODO not sure of this
        //     if (hasStyle and !cvr.hasContest(contestOA.id)) {
        //            throw RuntimeException("use_style==True but cvr=${cvr} does not contain contest ${contestOA.name} (${contestOA.id})")
        //        }
        //        val mvr_assort = if (mvr.phantom || (hasStyle && !mvr.hasContest(contestOA.id))) 0.0
        //                         else this.assorter.assort(mvr, usePhantoms = false)

        val pool = contestOA.pools[mvr.poolId]
            ?: throw IllegalStateException("Dont have pool ${mvr.poolId} in contest ${contestOA.id}")
        val avgBatchAssortValue = margin2mean(pool.calcReportedMargin(winner, loser))

        return if (mvr.phantom) .5 else avgBatchAssortValue
    }

    companion object {
        fun makeFromContestVotes(contest: OneAuditContest, winner: Int, loser: Int): OaPluralityAssorter {
            val winnerVotes = contest.votes[winner] ?: 0
            val loserVotes = contest.votes[loser] ?: 0
            val reportedMargin = (winnerVotes - loserVotes) / contest.Nc.toDouble()
            return OaPluralityAssorter(contest, winner, loser, reportedMargin)
        }
    }
}

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

