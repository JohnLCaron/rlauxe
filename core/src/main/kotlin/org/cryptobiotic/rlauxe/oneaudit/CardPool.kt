package org.cryptobiotic.rlauxe.oneaudit

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.ContestTabulation
import org.cryptobiotic.rlauxe.audit.RegVotes
import org.cryptobiotic.rlauxe.audit.RegVotesImpl
import org.cryptobiotic.rlauxe.core.AssorterIF
import org.cryptobiotic.rlauxe.core.ClcaAssertion
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.util.Prng
import org.cryptobiotic.rlauxe.util.cleanCsvString
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

interface CardPoolIF {
    val poolName: String
    val poolId: Int
    val assortAvg: MutableMap<Int, MutableMap<AssorterIF, AssortAvg>>  // contestId -> assorter -> average in the pool
    fun regVotes() : Map<Int, RegVotes> // contestId -> RegVotes, regular contests only
    fun ncards() : Int // total number of cards in the pool, including undervotes
    fun contains(contestId: Int) : Boolean // does the pool contain this contest ?
    fun toBallotPools(): List<BallotPool>
    fun contests(): IntArray
}

// single contest, for testing
class CardPoolImpl(override val poolName: String, override val poolId: Int, val contestId: Int, val regVotes: RegVotes) : CardPoolIF {
    override val assortAvg = mutableMapOf<Int, MutableMap<AssorterIF, AssortAvg>>()  // contest -> assorter -> average
    override fun regVotes() = mapOf(contestId to regVotes)
    override fun contains(contestId: Int) = contestId == this.contestId
    override fun ncards() = regVotes.ncards()

    override fun contests() = intArrayOf(contestId)

    override fun toBallotPools(): List<BallotPool> {
        return listOf(BallotPool("poolName", poolId, contestId, regVotes.ncards(), regVotes.votes))
    }
}

// When the pools do not have CVRS, but just pool vote count totals.
// Assumes that all cards have the same BallotStyle.
class CardPoolWithBallotStyle(
    override val poolName: String,
    override val poolId: Int,
    val voteTotals: Map<Int, Map<Int, Int>>, // contestId -> candidateId -> nvotes // TODO use ContestTabulation ??
    val infos: Map<Int, ContestInfo>, // all infos
) : CardPoolIF
{
    val minCardsNeeded = mutableMapOf<Int, Int>() // contestId -> minCardsNeeded
    val maxMinCardsNeeded: Int
    private var adjustCards = 0

    // a convenient place to keep this, used in addOAClcaAssortersFromCvrs()
    override val assortAvg = mutableMapOf<Int, MutableMap<AssorterIF, AssortAvg>>()  // contest -> assorter -> average

    init {
        voteTotals.forEach { (contestId, candidateCounts) ->
            val voteSum = candidateCounts.map { it.value }.sum()
            val info = infos[contestId]!!
            // need at least this many cards would you need for this contest?
            minCardsNeeded[contestId] = roundUp(voteSum.toDouble() / info.voteForN)
        }
        maxMinCardsNeeded = minCardsNeeded.values.max()
    }

    override fun contains(contestId: Int) = voteTotals.contains(contestId)
    override fun regVotes(): Map<Int, RegVotes> {
        return voteTotals.mapValues { (_, votes) -> RegVotesImpl(votes, ncards()) }
    }
    override fun ncards() = maxMinCardsNeeded + adjustCards

    fun adjustCards(adjust: Int, contestId : Int) {
        if (!contains(contestId)) throw RuntimeException("NO CONTEST")
        adjustCards = max( adjust, adjustCards)
    }

    override fun contests() = (voteTotals.map { it.key }).toSortedSet().toIntArray()

    fun showVotes(contestIds: Collection<Int>, width: Int=4) = buildString {
        append("${trunc(poolName, 9)}:")
        contestIds.forEach { id ->
            val contestVote = voteTotals[id]
            if (contestVote == null)
                append("    |")
            else {
                val sum = contestVote.map { it.value } .sum()
                append("${nfn(sum, width)}|")
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
        val undervote = voteTotals.map { (id, cands) ->
            val sum = cands.map { it.value }.sum()
            val info = infos[id]!!
            Pair(id, ncards() * info.voteForN - sum)
        }
        return undervote.toMap().toSortedMap()
    }

    fun undervoteForContest(contestId: Int): Int {
        val votesForContest = voteTotals[contestId] ?: return 0
        val sum = votesForContest.map { it.value }.sum()
        val info = infos[contestId]!!
        return ncards() * info.voteForN - sum
    }

    override fun toBallotPools(): List<BallotPool> {
        return voteTotals.map { (contestId, candCount) ->
            BallotPool(poolName, poolId, contestId, ncards(), candCount)
        }
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
    val contestTabs = mutableMapOf<Int, ContestTabulation>()  // contestId -> ContestTabulation
    var totalCards = 0

    // a convenient place to keep this, calculated in addOAClcaAssorters()
    override val assortAvg = mutableMapOf<Int, MutableMap<AssorterIF, AssortAvg>>()  // contest -> assorter -> average
    override fun contains(contestId: Int) = contestTabs.contains(contestId)
    override fun regVotes() = contestTabs.filter { !it.value.isIrv }
    override fun ncards() = totalCards

    // this is when you have CVRs. (sfoa, sfoans)
    open fun accumulateVotes(cvr : Cvr) {
        cvr.votes.forEach { (contestId, candIds) ->
            if (infos[contestId] == null) {
                logger.error { "cvr has unknown contest $contestId" }
            } else {
                val contestTab = contestTabs.getOrPut(contestId) { ContestTabulation(infos[contestId]!!) }
                contestTab.addVotes(candIds)
            }
        }
        totalCards++
    }

    override fun contests() = (contestTabs.map { it.key }).toSortedSet().toIntArray()

    override fun toBallotPools(): List<BallotPool> {
        val bpools = mutableListOf<BallotPool>()
        contestTabs.forEach { contestId, contestCount ->
            if (contestCount.ncards > 0) {
                bpools.add(BallotPool(poolName, poolId, contestId, contestCount.ncards, contestCount.votes))
            }
        }
        return bpools
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
        contestTab.undervotes++
        contestTab.ncards++
        totalCards++
    }

    fun sum(sumTab: MutableMap<Int, ContestTabulation>) {
        this.contestTabs.forEach { (contestId, poolContestTab) ->
            val contestSum = sumTab.getOrPut(contestId) { ContestTabulation(infos[contestId]!!) }
            contestSum.sum(poolContestTab)
        }
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

// use reportedMargin to set the pool assorter averages. can only use for non-IRV contests
fun addOAClcaAssortersFromMargin(
    oaContests: List<OAContestUnderAudit>,
    cardPools: Map<Int, CardPoolIF>
) {
    // ClcaAssorter already has the contest-wide reported margin. We just have to add the pool assorter averages
    // create the clcaAssertions and add then to the oaContests
    oaContests.filter { !it.isIrv}. forEach { oaContest ->
        val contestId = oaContest.id
        val clcaAssertions = oaContest.pollingAssertions.map { assertion ->
            val assortAverages = mutableMapOf<Int, Double>() // poolId -> average assort value
            cardPools.values.forEach { cardPool ->
                if (cardPool.contains(contestId)) {
                    val regVotes = cardPool.regVotes()[oaContest.id]!!
                    if (regVotes.ncards() > 0) {
                        val poolMargin = assertion.assorter.calcReportedMargin(regVotes.votes, regVotes.ncards())
                        assortAverages[cardPool.poolId] = margin2mean(poolMargin)
                    }
                }
            }
            val clcaAssorter = OneAuditClcaAssorter(assertion.info, assertion.assorter, true, poolAverages = AssortAvgsInPools(assortAverages))
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
