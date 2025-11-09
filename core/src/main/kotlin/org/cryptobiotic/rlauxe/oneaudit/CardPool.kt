package org.cryptobiotic.rlauxe.oneaudit

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.CardStyleIF
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.util.RegVotes
import org.cryptobiotic.rlauxe.util.RegVotesImpl
import org.cryptobiotic.rlauxe.core.AssorterIF
import org.cryptobiotic.rlauxe.core.ClcaAssertion
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.util.VotesAndUndervotes
import org.cryptobiotic.rlauxe.util.margin2mean
import org.cryptobiotic.rlauxe.util.mean2margin
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.util.roundToClosest
import org.cryptobiotic.rlauxe.util.roundUp
import org.cryptobiotic.rlauxe.util.trunc
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach
import kotlin.math.max
import kotlin.random.Random


private val logger = KotlinLogging.logger("CardPool")

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

interface CardPoolIF: CardStyleIF {
    val poolName: String
    val poolId: Int
    val assortAvg: MutableMap<Int, MutableMap<AssorterIF, AssortAvg>>  // contestId -> assorter -> average in the pool
    fun regVotes() : Map<Int, RegVotes> // contestId -> RegVotes, regular contests only
    fun ncards() : Int // total number of cards in the pool, including undervotes
    fun votesAndUndervotes(contestId: Int): VotesAndUndervotes

    override fun name() = poolName
    override fun id() = poolId
    override fun hasContest(contestId: Int) : Boolean // does the pool contain this contest ?
    override fun contests(): IntArray
}

// When the pools do not have CVRS, but just pool vote count totals.
// Assumes that all cards have the same BallotStyle.
// TODO cant do IRVs?
class CardPoolWithBallotStyle(
    override val poolName: String,
    override val poolId: Int,
    val voteTotals: Map<Int, ContestTabulation>, // contestId -> candidateId -> nvotes //
    val infos: Map<Int, ContestInfo>, // all infos
) : CardPoolIF
{
    val minCardsNeeded = mutableMapOf<Int, Int>() // contestId -> minCardsNeeded
    val maxMinCardsNeeded: Int
    var adjustCards = 0 // TODO simplify relationship with undervotes

    // a convenient place to keep this, used in addOAClcaAssortersFromCvrs()
    override val assortAvg = mutableMapOf<Int, MutableMap<AssorterIF, AssortAvg>>()  // contest -> assorter -> average

    init {
        voteTotals.forEach { (contestId, contestTab) ->
            val voteSum = contestTab.nvotes()
            val info = infos[contestId]!!
            // need at least this many cards would you need for this contest?
            minCardsNeeded[contestId] = roundUp(voteSum.toDouble() / info.voteForN)
        }
        maxMinCardsNeeded = minCardsNeeded.values.max()
    }

    override fun hasContest(contestId: Int) = voteTotals.contains(contestId)
    override fun contests() = voteTotals.map { it.key }.toSortedSet().toIntArray()

    override fun regVotes(): Map<Int, RegVotes> {
        return voteTotals.mapValues { (id, contestTab) -> RegVotesImpl(contestTab.votes, ncards(), undervoteForContest(id)) }
    }
    override fun ncards() = maxMinCardsNeeded + adjustCards

    fun adjustCards(adjust: Int, contestId : Int) {
        if (!hasContest(contestId)) throw RuntimeException("NO CONTEST")
        adjustCards = max( adjust, adjustCards)
    }


    fun showVotes(contestIds: Collection<Int>, width: Int=4) = buildString {
        append("${trunc(poolName, 9)}:")
        contestIds.forEach { id ->
            val contestTab = voteTotals[id]
            if (contestTab == null)
                append("    |")
            else {
                val voteSum = contestTab.nvotes()
                append("${nfn(voteSum, width)}|")
            }
        }
        appendLine()

        val undervotes = undervotes()
        append("${trunc("", 9)}:")
        contestIds.forEach { id ->
            val contestVote = voteTotals[id]
            if (contestVote == null)
                append("    |")
            else {
                val undervote = undervotes[id]!!
                append("${nfn(undervote, width)}|")
            }
        }
        appendLine()
    }

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

    override fun votesAndUndervotes(contestId: Int): VotesAndUndervotes {
        val poolUndervotes = undervoteForContest(contestId)
        val votesForContest = voteTotals[contestId]!!
        return VotesAndUndervotes(votesForContest.votes, poolUndervotes, votesForContest.voteForN)
    }

    override fun toString(): String {
        return "CardPoolWithBallotStyle(poolName='$poolName', poolId=$poolId, voteTotals=$voteTotals, maxMinCardsNeeded=$maxMinCardsNeeded)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CardPoolWithBallotStyle

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

    companion object {
        fun showVotes(contestIds: List<Int>, cardPools: List<CardPoolWithBallotStyle>, width:Int = 4) {
            println("votes, undervotes")
            print("${trunc("poolName", 9)}:")
            contestIds.forEach {  print("${nfn(it, width)}|") }
            println()

            cardPools.forEach {
                println(it.showVotes(contestIds, width))
            }
        }
    }
}

// When the pools have complete CVRS.
open class CardPoolFromCvrs(
    override val poolName: String,
    override val poolId: Int,
    val infos: Map<Int, ContestInfo>) : CardPoolIF
{
    // TODO mutable
    val contestTabs = mutableMapOf<Int, ContestTabulation>()  // contestId -> ContestTabulation
    var totalCards = 0

    // a convenient place to keep this, calculated in addOAClcaAssorters()
    override val assortAvg = mutableMapOf<Int, MutableMap<AssorterIF, AssortAvg>>()  // contest -> assorter -> average

    override fun hasContest(contestId: Int) = contestTabs.contains(contestId)
    override fun contests() = (contestTabs.map { it.key }).toSortedSet().toIntArray()

    override fun regVotes() = contestTabs
    override fun ncards() = totalCards

    // this is when you have CVRs. (sfoa, sfoans)
    open fun accumulateVotes(cvr : Cvr) {
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


    override fun votesAndUndervotes(contestId: Int): VotesAndUndervotes {
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
            val contestSumTab = sumTab.getOrPut(contestId) { ContestTabulation(infos[contestId]!!) }
            contestSumTab.sum(contestTab)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CardPoolFromCvrs) return false

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
        return "CardPoolFromCvrs(poolName='$poolName', poolId=$poolId, contestTabs=$contestTabs, totalCards=$totalCards)"
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

// use dilutedMargin to set the pool assorter averages. can only use for non-IRV contests
fun addOAClcaAssortersFromMargin(
    oaContests: List<ContestUnderAudit>,
    cardPools: List<CardPoolIF>, // poolId -> pool
    hasStyle: Boolean,
) {
    // ClcaAssorter already has the contest-wide reported margin. We just have to add the pool assorter averages
    // create the clcaAssertions and add then to the oaContests
    oaContests.filter { !it.isIrv}. forEach { oaContest ->
        val contestId = oaContest.id
        val clcaAssertions = oaContest.pollingAssertions.map { assertion ->
            val assortAverages = mutableMapOf<Int, Double>() // poolId -> average assort value
            cardPools.forEach { cardPool ->
                if (cardPool.hasContest(contestId)) {
                    val regVotes = cardPool.regVotes()[oaContest.id]!!
                    if (regVotes.ncards() > 0) {
                        val poolMargin = assertion.assorter.calcMargin(regVotes.votes, regVotes.ncards())
                        assortAverages[cardPool.poolId] = margin2mean(poolMargin)
                    }
                }
            }
            val clcaAssorter = OneAuditClcaAssorter(assertion.info, assertion.assorter, hasStyle, poolAverages = AssortAvgsInPools(assortAverages),
                dilutedMargin = oaContest.makeDilutedMargin(assertion.assorter))
            ClcaAssertion(assertion.info, clcaAssorter)
        }
        oaContest.clcaAssertions = clcaAssertions
    }
}

//////////////////////////////////////////////////////////////////

interface OneAuditContestIF {
    val contestId: Int
    fun poolTotalCards(): Int // total cards in all pools for this contest
    fun expectedPoolNCards(): Int // expected total pool cards for this contest, making assumptions about missing undervotes
    fun adjustPoolInfo(cardPools: List<CardPoolIF>)
}

fun distributeExpectedOvervotes(oaContest: OneAuditContestIF, cardPools: List<CardPoolWithBallotStyle>) {
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
