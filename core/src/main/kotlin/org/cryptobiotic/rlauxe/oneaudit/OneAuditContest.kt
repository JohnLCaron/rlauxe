package org.cryptobiotic.rlauxe.oneaudit

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.*
import kotlin.math.min

// OneAuditContest no longer isa Contest, but hasa ContestIF (which might be Contest or RaireContest)
// was class OneAuditContest (
//    info: ContestInfo,
//    voteInput: Map<Int, Int>,
//    Nc: Int,
//    Np: Int,
//
//    val cvrVotes: Map<Int, Int>,   // candidateId -> nvotes (may be empty)
//    val cvrNc: Int,                // may be 0
//    val pools: Map<Int, BallotPool>, // pool id -> pool
//) : Contest(info, voteInput, Nc, Np)

class OneAuditContest (
    val contest: ContestIF,
    val cvrVotes: Map<Int, Int>,   // candidateId -> nvotes (may be empty) from the crvs
    val cvrNcards: Int,
    val pools: Map<Int, BallotPool>, // pool id -> pool
) : ContestIF {
    val info = contest.info()

    // val minMargin: Double
    val poolNc = pools.values.sumOf { it.ncards }
    // val cvrUndervotes: Int

    init {
        // TODO add SUPERMAJORITY.
        // require(choiceFunction == SocialChoiceFunction.PLURALITY) { "OneAuditContest requires PLURALITY"}

        /* TODO how many undervotes are there ?
        if (choiceFunction == SocialChoiceFunction.PLURALITY) {
            val poolVotes = pools.values.sumOf { it.votes.values.sum() }
            require(poolNc * info.voteForN >= poolVotes) { "OneAuditContest ${poolNc * info.voteForN} < $poolVotes" }
            val poolUndervotes = poolNc * info.voteForN - poolVotes

            val cvrVotesTotal = cvrVotes.values.sumOf { it }
            //require(cvrNc * info.voteForN >= cvrVotesTotal)
            cvrUndervotes = cvrNc * info.voteForN - cvrVotesTotal
            val undervotes2 = poolUndervotes + cvrUndervotes
            // require(undervotes == undervotes2)
        } else {
            cvrUndervotes = 0
        } */

        // not sure about where undervotes and phantoms live
        // TODO require(Nc == cvrNc + poolNc + Np())

        /* minMargin = if (votes.size < 2) 0.0 else {
            val sortedVotes = votes.toList().sortedBy { it.second }.reversed()
            (sortedVotes[0].second - sortedVotes[1].second) / Nc.toDouble()
        } */
    }

    override fun Nc() = contest.Nc()
    override fun Np() = contest.Np()
    override fun Nundervotes() = contest.Nundervotes()
    override fun info() = contest.info()
    override fun winnerNames() = contest.winnerNames()
    override fun winners() = contest.winners()
    override fun losers() = contest.losers()

    fun makeContestUnderAudit() : OAContestUnderAudit {
        val contestUA = OAContestUnderAudit(this)
        contestUA.makeClcaAssertionsFromReportedMargin()
        return contestUA
    }

    /*
    fun reportedMarginNonPooled(winner: Int, loser:Int): Double {
        val winnerVotes = cvrVotes[winner] ?: 0
        val loserVotes = cvrVotes[loser] ?: 0
        return (winnerVotes - loserVotes) / cvrNc.toDouble()
    } */

    fun cvrVotesAndUndervotes(): Map<Int, Int> {
        return (cvrVotes.map { Pair(it.key, it.value)} + Pair(this.ncandidates, contest.Nundervotes())).toMap()
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
        appendLine("$contest") //  votesAndUnderVotes=${votesAndUndervotes()} minMargin=${df(minMargin)}")
        appendLine("  cvrVotesAndUndervotes()=${cvrVotesAndUndervotes()}")
        appendLine("  poolNc=$poolNc poolVotesAndUnderVotes= ${poolVotesAndUnderVotes()} npools= ${pools.size} " +
                "pctInPools=${df(poolNc / Nc().toDouble())}")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as OneAuditContest

        if (!contest.equals(other.contest)) return false
        if (cvrVotes != other.cvrVotes) return false
        if (pools != other.pools) return false

        return true
    }

    override fun hashCode(): Int {
        var result = 31
        result = 31 * result + contest.hashCode()
        result = 31 * result + cvrVotes.hashCode()
        result = 31 * result + pools.hashCode()
        return result
    }

    companion object {
        fun make(info: ContestInfo,
                 cvrVotes: Map<Int, Int>,   // candidateId -> nvotes
                 cvrNcards: Int,
                 pools: List<BallotPool>,   // pools for this contest
                 Nc: Int,
                 Ncast: Int): OneAuditContest {

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

            val contest = Contest(info, voteInput, Nc = Nc, Ncast = Ncast)
            return OneAuditContest(contest,  cvrVotes, cvrNcards, pools.associateBy { it.poolId })
        }

        fun make(contest: ContestIF,
                  cvrVotes: Map<Int, Int>,   // candidateId -> nvotes
                 cvrNcards: Int,
                 pools: List<BallotPool>,   // pools for this contest
                  ): OneAuditContest {

            // val poolNc = pools.sumOf { it.ncards }
            // val Nc = poolNc + cvrNc + Np

            //// construct total votes
            val voteBuilder = mutableMapOf<Int, Int>()  // cand -> vote
            cvrVotes.forEach { (cand, votes) ->
                val tvote = voteBuilder[cand] ?: 0
                voteBuilder[cand] = tvote + votes
            }
            pools.forEach { pool ->
                require(pool.contest == contest.id)
                pool.votes.forEach { (cand, votes) ->
                    val tvote = voteBuilder[cand] ?: 0
                    voteBuilder[cand] = tvote + votes
                }
            }
            // add 0 candidate votes if needed
            contest.info().candidateIds.forEach {
                if (!voteBuilder.contains(it)) {
                    voteBuilder[it] = 0
                }
            }
            val voteInput = voteBuilder.toList().sortedBy{ it.second }.reversed().toMap()

            return OneAuditContest(contest,  cvrVotes, cvrNcards, pools.associateBy { it.poolId })
        }
    }
}

/////////////////////////////////////////////////////////////////////////////////////////////

open class OAContestUnderAudit(
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
    assorter: AssorterIF,   // A(mvr) Use this assorter for the CVRs: plurality or IRV
    assortValueFromCvrs: Double?,    // Ā(c) = average CVR assorter value. TODO wrong ??
    hasStyle: Boolean = true,
) : ClcaAssorter(contestOA.info, assorter, assortValueFromCvrs, hasStyle) {
    private val doAffineTransform = false // (contestOA.cvrNc == 0) // this is a "batch level comparison audit" (BLCA)
    private val affineMin: Double
    private val affineIScale: Double

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
    /* however, we might want to do an affine transform to set range to [0, max]
    fun calcAssortMeanFromPools(): Double {
        val welford = Welford()
        val cvrMargin = contestOA.reportedMarginNonPooled(assorter.winner(), assorter.loser())
        welford.update(margin2mean(cvrMargin), contestOA.cvrNc)
        contestOA.pools.values.forEach { pool ->
            val poolMargin = pool.calcReportedMargin(assorter.winner(), assorter.loser())
            welford.update(margin2mean(poolMargin), pool.ncards)
        }
        return welford.mean
    } */

    // B(bi, ci)
    override fun bassort(mvr: Cvr, cvr: Cvr, hasStyle: Boolean): Double {
        if (cvr.poolId == null) {
            require(!doAffineTransform)
            return super.bassort(mvr, cvr, hasStyle) // here we use the standard assorter
        }

        // if (hasStyle && mvr.hasContest(contestOA.id)) return 0.5   TODO does this solve the undervote problem ??

        val pool = contestOA.pools[cvr.poolId]
            ?: throw IllegalStateException("Dont have pool ${cvr.poolId} in contest ${contestOA.id}")

        // seems like you could just replace pool.calcReportedMargin with pre calculated avg
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
        append("  cvrAssortMargin=$cvrAssortMargin noerror=$noerror upperBound=$upperBound assortValueFromCvrs=$assortAverageFromCvrs")
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