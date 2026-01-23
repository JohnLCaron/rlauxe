package org.cryptobiotic.rlauxe.oneaudit

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.PopulationIF
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.util.ContestVotesIF
import org.cryptobiotic.rlauxe.util.ContestVotes
import org.cryptobiotic.rlauxe.core.AssorterIF
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.util.Vunder2
import org.cryptobiotic.rlauxe.util.mean2margin
import org.cryptobiotic.rlauxe.util.roundUp
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach
import kotlin.math.max


private val logger = KotlinLogging.logger("OneAuditPool")

const val unpooled = "unpooled"

// for calculating average from running total, see addOAClcaAssorters
class AssortAvg() {
    var ncards = 0
    var totalAssort = 0.0
    fun avg() : Double = if (ncards == 0) 0.0 else totalAssort / ncards
    fun margin() : Double = mean2margin(avg())

    override fun toString(): String {
        return "AssortAvg(ncards=$ncards, totalAssort=$totalAssort avg=${avg()} margin=${margin()})"
    }
}

interface OneAuditPoolIF: PopulationIF {
    val poolName: String
    val poolId: Int
    fun assortAvg(): MutableMap<Int, MutableMap<AssorterIF, AssortAvg>>  // contestId -> assorter -> average in the pool
    fun regVotes(): Map<Int, ContestVotesIF> // contestId -> RegVotes, regular contests only, not IRV
    // fun votesAndUndervotes(contestId: Int): Vunder // , voteForN: Int): Vunder
    fun votesAndUndervotes2(contestId: Int): Vunder2 // , voteForN: Int): Vunder
    // fun contestTab(contestId: Int): ContestTabulation? need this for IRV

    fun show() = buildString {
        appendLine("OneAuditPool(poolName='$poolName', poolId=$poolId, ncards=${ncards()} hasSingleCardStyle=${hasSingleCardStyle()},")
        regVotes().toSortedMap().forEach{
            appendLine("    contest ${it.key} votes= ${it.value.votes}, ncards= ${it.value.ncards()}, undervotes= ${it.value.undervotes()} ")
        }
        appendLine(")")
    }

    // OneAuditPool(override val poolName: String, override val poolId: Int, val exactContests: Boolean,
    //  val ncards: Int, val regVotes: Map<Int, RegVotes>)
    fun toOneAuditPool() = OneAuditPool(poolName, poolId, hasSingleCardStyle(), ncards(), regVotes())
}

// TODO keeping regVotes but not irvVotes. Because VoteConsolidator can be large
data class OneAuditPool(override val poolName: String, override val poolId: Int, val hasSingleCardStyle: Boolean,
                        val ncards: Int, val regVotes: Map<Int, ContestVotesIF>) : OneAuditPoolIF {
    val assortAvg = mutableMapOf<Int, MutableMap<AssorterIF, AssortAvg>>()  // contest -> assorter -> average
    override fun name() = poolName
    override fun id() = poolId
    override fun hasSingleCardStyle() = hasSingleCardStyle

    override fun regVotes() = regVotes
    override fun hasContest(contestId: Int) = regVotes[contestId] != null
    override fun ncards() = ncards

    override fun contests() = regVotes.keys.toList().sorted().toIntArray()
    override fun assortAvg() = assortAvg

    /* override fun votesAndUndervotes(contestId: Int,): Vunder {
        val regVotes = regVotes[contestId]!!         // empty for IRV ...
        return Vunder.fromNpop(contestId, regVotes.undervotes(), ncards(), regVotes.votes, regVotes.voteForN)
        // val poolUndervotes = ncards * voteForN - regVotes.votes.values.sum()
        // return Vunder(contestId, regVotes.votes, regVotes.undervotes(), 0, voteForN) // old way
    } */

    override fun votesAndUndervotes2(contestId: Int,): Vunder2 {
        val regVotes = regVotes[contestId]!!         // empty for IRV ...
        return Vunder2.fromNpop(contestId, regVotes.undervotes(), ncards(), regVotes.votes, regVotes.voteForN)
    }
}

// used by Boulder and Corla
data class OneAuditPoolWithBallotStyle(
    override val poolName: String,
    override val poolId: Int,
    val hasSingleCardStyle: Boolean,
    val voteTotals: Map<Int, ContestTabulation>, // contestId -> candidateId -> nvotes; must include contests with no votes
    val infos: Map<Int, ContestInfo>, // all infos
): OneAuditPoolIF {

    val minCardsNeeded = mutableMapOf<Int, Int>() // contestId -> minCardsNeeded
    val maxMinCardsNeeded: Int
    var adjustCards = 0 // TODO simplify relationship with undervotes

    // a convenient place to keep this, used in addOAClcaAssortersFromCvrs()
    val assortAvg = mutableMapOf<Int, MutableMap<AssorterIF, AssortAvg>>()  // contest -> assorter -> average

    init {
        voteTotals.forEach { (contestId, contestTab) ->
            val voteSum = contestTab.nvotes()
            val info = infos[contestId]!!
            // need at least this many cards would you need for this contest?
            minCardsNeeded[contestId] = roundUp(voteSum.toDouble() / info.voteForN)
        }
        maxMinCardsNeeded = minCardsNeeded.values.max()
    }

    override fun name() = poolName
    override fun id() = poolId
    override fun hasSingleCardStyle() = hasSingleCardStyle

    override fun assortAvg() = assortAvg
    override fun hasContest(contestId: Int) = voteTotals.contains(contestId)
    override fun contests() = voteTotals.map { it.key }.toSortedSet().toIntArray()

    override fun regVotes(): Map<Int, ContestVotesIF> {
        return voteTotals.mapValues { (id, contestTab) ->
            ContestVotes(id, contestTab.voteForN, contestTab.votes, ncards(), undervoteForContest(id)) }
    }

    override fun ncards() = maxMinCardsNeeded + adjustCards

    fun adjustCards(adjust: Int, contestId : Int) {
        if (!hasContest(contestId)) throw RuntimeException("NO CONTEST")
        adjustCards = max( adjust, adjustCards)
    }

    fun contestTab(contestId: Int) = voteTotals[contestId]

    // undervotes per contest when single BallotStyle, no blanks
    fun undervotes(): Map<Int, Int> {  // contest -> undervote
        val undervote = voteTotals.map { (id, contestTab) ->
            val voteSum = contestTab.nvotes()
            val info = infos[id]!!
            Pair(id, ncards() * info.voteForN - voteSum)
        }
        return undervote.toMap().toSortedMap()
    }

    fun undervoteForContest(contestId: Int): Int {
        val contestTab = voteTotals[contestId] ?: return 0
        val voteSum = contestTab.nvotes()
        val info = infos[contestId]!!
        return ncards() * info.voteForN - voteSum
    }

    /* override fun votesAndUndervotes(contestId: Int): Vunder {
        val poolUndervotes = undervoteForContest(contestId)
        val votesForContest = voteTotals[contestId]!!
        return Vunder.fromNpop(contestId, poolUndervotes, ncards(), votesForContest.votes, votesForContest.voteForN)
    } */

    override fun votesAndUndervotes2(contestId: Int): Vunder2 {
        val poolUndervotes = undervoteForContest(contestId) // TODO why is this different from  whats in the contestTab ?
        val contestTab = voteTotals[contestId]!!
        // return contestTab.votesAndUndervotes2(poolId)
        return Vunder2.fromNpop(contestId, poolUndervotes, ncards(), contestTab.votes, contestTab.voteForN)
    }

    override fun toString(): String {
        return "OneAuditPoolWithBallotStyle(poolName='$poolName', poolId=$poolId, voteTotals=$voteTotals, maxMinCardsNeeded=$maxMinCardsNeeded)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OneAuditPoolWithBallotStyle) return false

        if (poolId != other.poolId) return false
        if (hasSingleCardStyle != other.hasSingleCardStyle) return false
        if (maxMinCardsNeeded != other.maxMinCardsNeeded) return false
        if (adjustCards != other.adjustCards) return false
        if (poolName != other.poolName) return false
        if (voteTotals != other.voteTotals) return false
        if (minCardsNeeded != other.minCardsNeeded) return false

        return true
    }

    override fun hashCode(): Int {
        var result = poolId
        result = 31 * result + hasSingleCardStyle.hashCode()
        result = 31 * result + maxMinCardsNeeded
        result = 31 * result + adjustCards
        result = 31 * result + poolName.hashCode()
        result = 31 * result + voteTotals.hashCode()
        result = 31 * result + minCardsNeeded.hashCode()
        return result
    }
}

// used by SF; necessary for doing OneAudit IRV
data class OneAuditPoolFromCvrs(
    override val poolName: String,
    override val poolId: Int,
    val hasSingleCardStyle: Boolean,
    val infos: Map<Int, ContestInfo>,
): OneAuditPoolIF {

    val contestTabs = mutableMapOf<Int, ContestTabulation>()  // contestId -> ContestTabulation
    var totalCards = 0

    // a convenient place to keep this, calculated in addOAClcaAssorters()
    val assortAvg = mutableMapOf<Int, MutableMap<AssorterIF, AssortAvg>>()  // contest -> assorter -> average

    override fun name() = poolName
    override fun id() = poolId
    override fun hasSingleCardStyle() = hasSingleCardStyle

    override fun assortAvg() = assortAvg
    override fun hasContest(contestId: Int) = contestTabs.contains(contestId)
    override fun contests() = (contestTabs.map { it.key }).toSortedSet().toIntArray()
    fun contestTab(contestId: Int) = contestTabs[contestId]

    override fun regVotes() = contestTabs
    override fun ncards() = totalCards

    // this is when you have CVRs. (sfoa, sfoans)
    fun accumulateVotes(cvr : Cvr) {
        cvr.votes.forEach { (contestId, candIds) ->
            if (infos[contestId] == null) {
                logger.error { "cvr has unknown contest $contestId" }
            } else {
                val contestTab = contestTabs.getOrPut(contestId) { ContestTabulation(infos[contestId]!!) }
                contestTab.addVotes(candIds, cvr.phantom)
            }
        }
        totalCards++
    }

    /* override fun votesAndUndervotes(contestId: Int): Vunder {
        val contestTab = contestTabs[contestId]!!
        return contestTab.votesAndUndervotes() // good reason for cardPool to always have contestTabs
    } */

    override fun votesAndUndervotes2(contestId: Int): Vunder2 {
        val contestTab = contestTabs[contestId]!!
        return contestTab.votesAndUndervotes2(poolId) // good reason for cardPool to always have contestTabs
    }

    // every cvr has to have every contest in the pool
    fun addUndervotes(cvr: Cvr): Cvr {
        var wasAmended = false
        val votesM= cvr.votes.toMutableMap()
        val needContests = this.contestTabs.keys
        needContests.forEach { contestId ->
            if (!votesM.containsKey(contestId)) {
                votesM[contestId] = IntArray(0)
                wasAmended = true
                addUndervote(contestId)
            }
        }
        return if (!wasAmended) cvr else cvr.copy(votes = votesM)
    }

    fun addUndervote(contestId: Int) {
        val contestTab = contestTabs[contestId]!!
        contestTab.undervotes += if (contestTab.isIrv) 1 else contestTab.voteForN
        contestTab.ncards++
        contestTab.novote++
    }

    fun addTo(sumTab: MutableMap<Int, ContestTabulation>) {
        this.contestTabs.forEach { (contestId, contestTab) ->
            val info = infos[contestId]
            if (info != null) { // skip IRV
                val contestSumTab = sumTab.getOrPut(contestId) { ContestTabulation(info) }
                contestSumTab.sum(contestTab)
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OneAuditPoolFromCvrs) return false

        if (poolId != other.poolId) return false
        if (totalCards != other.totalCards) return false
        if (poolName != other.poolName) return false
        if (contestTabs != other.contestTabs) return false

        return true
    }

    override fun hashCode(): Int {
        var result = poolId
        result = 31 * result + totalCards
        result = 31 * result + poolName.hashCode()
        result = 31 * result + contestTabs.hashCode()
        return result
    }

    override fun toString() = buildString {
        appendLine("OneAuditPoolFromCvrs(poolName='$poolName', poolId=$poolId, totalCards=$totalCards")
        contestTabs.values.forEach { appendLine("  $it")}
    }
}

fun calcOneAuditPoolsFromMvrs(
    infos: Map<Int, ContestInfo>,
    populations: List<PopulationIF>,
    mvrs: List<Cvr>,
): List<OneAuditPoolFromCvrs> {  // poolId -> CardPoolFromCvrs

    // The styles have the name, poolId, and contest list
    val poolsFromCvrs = populations.map { style ->
        val poolFromCvr = OneAuditPoolFromCvrs(style.name(), style.id(), style.hasSingleCardStyle(), infos)
        style.contests().forEach { poolFromCvr.contestTabs[it]  = ContestTabulation( infos[it]!!) }
        poolFromCvr
    }.associateBy { it.poolId }

    // populate the pool counts from the mvrs
    mvrs.filter{ it.poolId != null }.forEach {
        val pool = poolsFromCvrs[it.poolId]
        if (pool != null) pool.accumulateVotes(it)
    }
    return poolsFromCvrs.values.toList()
}
