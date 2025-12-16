package org.cryptobiotic.rlauxe.oneaudit

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.CardStyleIF
import org.cryptobiotic.rlauxe.audit.PopulationIF
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.util.RegVotesIF
import org.cryptobiotic.rlauxe.util.RegVotes
import org.cryptobiotic.rlauxe.core.AssorterIF
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.util.Vunder
import org.cryptobiotic.rlauxe.util.mean2margin
import org.cryptobiotic.rlauxe.util.roundToClosest
import org.cryptobiotic.rlauxe.util.roundUp
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach
import kotlin.math.max
import kotlin.random.Random


private val logger = KotlinLogging.logger("OneAuditPool")

const val unpooled = "unpooled"

// for calculating average from running total, see addOAClcaAssorters
class AssortAvg() {
    var ncards = 0
    var totalAssort = 0.0
    fun avg() : Double = if (ncards == 0) 0.0 else totalAssort / ncards
    fun margin() : Double = mean2margin(avg())

    override fun toString(): String {
        return "AssortAvg(ncards=$ncards, totalAssort=$totalAssort avg=${avg()})"
    }
}

interface OneAuditPoolIF: PopulationIF {
    val poolName: String
    val poolId: Int
    fun assortAvg(): MutableMap<Int, MutableMap<AssorterIF, AssortAvg>>  // contestId -> assorter -> average in the pool
    fun regVotes(): Map<Int, RegVotesIF> // contestId -> RegVotes, regular contests only
    fun votesAndUndervotes(contestId: Int, voteForN: Int): Vunder  // candidate for removal
    // fun contestTab(contestId: Int): ContestTabulation?

    fun show() = buildString {
        appendLine("OneAuditPool(poolName=$poolName, poolId=$poolId, ncards=${ncards()}")
        regVotes().forEach{
            appendLine("    contest ${it.key} votes= ${it.value.votes}, ncards= ${it.value.ncards()}, undervotes= ${it.value.undervotes()} ")
        }
        appendLine(")")
    }


    // OneAuditPool(override val poolName: String, override val poolId: Int, val exactContests: Boolean,
    //  val ncards: Int, val regVotes: Map<Int, RegVotes>)
    fun toOneAuditPool() = OneAuditPool(poolName, poolId, exactContests(), ncards(), regVotes())

    /* fun showVotes(contestIds: Collection<Int>, width: Int=4) = buildString {
        append("${trunc(name(), 9)}:")

        contestIds.forEach { id ->
            // (val candVotes: Map<Int, Int>, val undervotes: Int, val voteForN: Int)
            val tab = contestTab(id)
            if (tab == null)
                append("    |")
            else {
                append("${nfn(tab.nvotes(), width)}|")
            }
        }
        appendLine()

        append("${trunc("", 9)}:")
        contestIds.forEach { id ->
            val tab = contestTab(id)
            if (tab == null)
                append("    |")
            else {
                append("${nfn(tab.undervotes, width)}|")
            }
        }
        appendLine()
    } */
}

data class OneAuditPool(override val poolName: String, override val poolId: Int, val exactContests: Boolean,
                        val ncards: Int, val regVotes: Map<Int, RegVotesIF>) : OneAuditPoolIF {
    val assortAvg = mutableMapOf<Int, MutableMap<AssorterIF, AssortAvg>>()  // contest -> assorter -> average
    override fun name() = poolName
    override fun id() = poolId
    override fun exactContests() = exactContests

    override fun regVotes() = regVotes
    override fun hasContest(contestId: Int) = regVotes[contestId] != null
    override fun ncards() = ncards

    override fun contests() = regVotes.keys.toList().toIntArray()
    override fun assortAvg() = assortAvg

    // candidate for removal, assumes voteForN == 1, perhaps we need to save that ??
    override fun votesAndUndervotes(contestId: Int, voteForN: Int): Vunder {
        val regVotes = regVotes[contestId]!!
        val poolUndervotes = ncards * voteForN - regVotes.votes.values.sum()
        return Vunder(regVotes.votes, poolUndervotes, voteForN)
    }
}

// this might be specialized for Boulder, perhaps shouldnt be in the general code ??
data class OneAuditPoolWithBallotStyle(
    override val poolName: String,
    override val poolId: Int,
    val exactContests: Boolean,
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
    override fun exactContests() = exactContests

    override fun assortAvg() = assortAvg
    override fun hasContest(contestId: Int) = voteTotals.contains(contestId)
    override fun contests() = voteTotals.map { it.key }.toSortedSet().toIntArray()

    override fun regVotes(): Map<Int, RegVotesIF> {
        return voteTotals.mapValues { (id, contestTab) -> RegVotes(contestTab.votes, ncards(), undervoteForContest(id)) }
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

    override fun votesAndUndervotes(contestId: Int, voteForN: Int): Vunder {
        val poolUndervotes = undervoteForContest(contestId)
        val votesForContest = voteTotals[contestId]!!
        return Vunder(votesForContest.votes, poolUndervotes, votesForContest.voteForN)
    }

    override fun toString(): String {
        return "OneAuditPoolWithBallotStyle(poolName='$poolName', poolId=$poolId, voteTotals=$voteTotals, maxMinCardsNeeded=$maxMinCardsNeeded)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as OneAuditPoolWithBallotStyle

        if (poolId != other.poolId) return false
        if (adjustCards != other.adjustCards) return false
        if (poolName != other.poolName) return false
        if (voteTotals != other.voteTotals) return false

        return true
    }

    override fun hashCode(): Int {
        var result = poolId
        result = 31 * result + adjustCards
        result = 31 * result + poolName.hashCode()
        result = 31 * result + voteTotals.hashCode()
        return result
    }
}

// class CardPoolFromCvrs(
//    override val poolName: String,
//    override val poolId: Int,
//    val infos: Map<Int, ContestInfo>) : CardPoolIF
data class OneAuditPoolFromCvrs(
    override val poolName: String,
    override val poolId: Int,
    val exactContests: Boolean,
    val infos: Map<Int, ContestInfo>,
): OneAuditPoolIF {

    val contestTabs = mutableMapOf<Int, ContestTabulation>()  // contestId -> ContestTabulation
    var totalCards = 0

    // a convenient place to keep this, calculated in addOAClcaAssorters()
    val assortAvg = mutableMapOf<Int, MutableMap<AssorterIF, AssortAvg>>()  // contest -> assorter -> average

    override fun name() = poolName
    override fun id() = poolId
    override fun exactContests() = exactContests

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


    override fun votesAndUndervotes(contestId: Int, voteForN: Int): Vunder {
        val contestTab = contestTabs[contestId]!!
        return contestTab.votesAndUndervotes() // good reason for cardPool to always have contestTabs
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

    override fun toString(): String {
        return "OneAuditPoolFromCvrs(poolName='$poolName', poolId=$poolId, totalCards=$totalCards contests=${contests().contentToString()})"
    }

    companion object {
        // poolId -> CardPoolIF
        fun makeCardPools(cvrs: Iterator<Cvr>, infos: Map<Int, ContestInfo>): Map<Int, CardPoolFromCvrs> {
            val cardPools: MutableMap<Int, CardPoolFromCvrs> = mutableMapOf()
            cvrs.forEach { cvr ->
                if (cvr.poolId != null) {
                    val pool = cardPools.getOrPut(cvr.poolId) {
                        CardPoolFromCvrs( "pool${cvr.poolId}", cvr.poolId, infos)
                    }
                    pool.accumulateVotes(cvr)
                }
            }
           return cardPools
        }
    }
}

fun calcOneAuditPoolsFromMvrs(
    infos: Map<Int, ContestInfo>,
    cardStyles: List<CardStyleIF>,
    mvrs: List<Cvr>,
): List<CardPoolFromCvrs> {  // poolId -> CardPoolFromCvrs

    // The styles have the name, poolId, and contest list
    val poolsFromCvrs = cardStyles.map { style ->
        val poolFromCvr = CardPoolFromCvrs(style.name(), style.poolId()!!, infos)
        style.contests().forEach { poolFromCvr.contestTabs[it]  = ContestTabulation( infos[it]!!) }
        poolFromCvr
    }.associateBy { it.poolId }

    // populate the pool counts from the mvrs
    mvrs.filter{ it.poolId != null }.forEach {
        val pool = poolsFromCvrs[it.poolId]
        if (pool != null) pool.accumulateVotes(it)
    }
    if (false) {
        println("tabulatePooledMvrs")
        poolsFromCvrs.forEach { (id, pool) ->
            println(pool)
            pool.contestTabs.forEach {
                println(" $it")
            }
            println()
        }
    }
    return poolsFromCvrs.values.toList()
}

//////////////////////////////////////////////////////////////////

fun distributeExpectedOvervotes(oaContest: OneAuditContestIF, cardPools: List<OneAuditPoolWithBallotStyle>) {
    val contestId = oaContest.contestId
    val poolCards = oaContest.poolTotalCards()
    val expectedCards = oaContest.expectedPoolNCards()
    val diff = expectedCards - poolCards

    var used = 0
    val allocDiffPool = mutableMapOf<Int, Int>()
    cardPools.forEach { pool ->
        val minCardsNeeded = pool.minCardsNeeded[contestId]
        if (minCardsNeeded != null) {
            // distribute cards as proportion of totalVotes
            val allocDiff = roundToClosest(diff * (pool.maxMinCardsNeeded / poolCards.toDouble()))
            used += allocDiff
            allocDiffPool[pool.poolId] = allocDiff
        }
    }

    // adjust some pool so sum undervotes = redUndervotes
    if (used < diff) {
        val keys = allocDiffPool.keys.toList()
        while (used < diff) {
            val chooseOne = keys[Random.nextInt(allocDiffPool.size)]
            val prev = allocDiffPool[chooseOne]!!
            allocDiffPool[chooseOne] = prev + 1
            used++
        }
    }
    if (used > diff) {
        val keys = allocDiffPool.keys.toList()
        while (used > diff) {
            val chooseOne = keys[Random.nextInt(allocDiffPool.size)]
            val prev = allocDiffPool[chooseOne]!!
            if (prev > 0) {
                allocDiffPool[chooseOne] = prev - 1
                used--
            }
        }
    }

    // check
    require(allocDiffPool.values.sum() == diff)

    // adjust
    val cardPoolMap = cardPools.associateBy { it.poolId }
    allocDiffPool.forEach { (poolId, adjust) ->
        cardPoolMap[poolId]!!.adjustCards(adjust, contestId)
    }
}
