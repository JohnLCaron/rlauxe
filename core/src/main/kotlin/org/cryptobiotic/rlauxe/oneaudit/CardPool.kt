package org.cryptobiotic.rlauxe.oneaudit

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.ContestTabulation
import org.cryptobiotic.rlauxe.audit.RegVotes
import org.cryptobiotic.rlauxe.audit.RegVotesImpl
import org.cryptobiotic.rlauxe.core.AssorterIF
import org.cryptobiotic.rlauxe.core.ClcaAssertion
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.util.cleanCsvString
import org.cryptobiotic.rlauxe.util.margin2mean
import org.cryptobiotic.rlauxe.util.mean2margin
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.util.roundUp
import org.cryptobiotic.rlauxe.util.trunc
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach
import kotlin.math.max


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
    val assortAvg: MutableMap<Int, MutableMap<AssorterIF, AssortAvg>>  // contest -> assorter -> average in the pool
    val poolId: Int
    fun regVotes() : Map<Int, RegVotes> // contestId -> RegVotes, regular contests only
    // fun ncards() : Int // total number of cards in the pool, including undervotes
    fun contains(contestId: Int) : Boolean // does the pool contain this contest ?
}

class CardPoolImpl(override val poolId: Int, val contestId: Int, val regVotes: RegVotes) : CardPoolIF {
    override val assortAvg = mutableMapOf<Int, MutableMap<AssorterIF, AssortAvg>>()  // contest -> assorter -> average
    override fun regVotes() = mapOf(contestId to regVotes)
    override fun contains(contestId: Int) = contestId == this.contestId

    fun toBallotPools(): List<BallotPool> {
        return listOf(BallotPool("poolName", poolId, contestId, regVotes.ncards(), regVotes.votes))
    }
}

// When the pools do not have CVRS, but just pool vote count totals.
// Assumes that all cards have the same BallotStyle.
class CardPool(
    poolNameIn: String,
    override val poolId: Int,
    val voteTotals: Map<Int, Map<Int, Int>>, // contestId -> candidateId -> nvotes from redacted group // TODO use ContestTabulation ??
    val infos: Map<Int, ContestInfo>, // all infos
) : CardPoolIF
{
    val poolName = cleanCsvString(poolNameIn)
    val minCardsNeeded = mutableMapOf<Int, Int>() // contestId -> minCardsNeeded
    val maxMinCardsNeeded: Int
    private var adjustCards = 0

    // a convenient place to keep this, used in addOAClcaAssortersFromCvrs()
    override val assortAvg = mutableMapOf<Int, MutableMap<AssorterIF, AssortAvg>>()  // contest -> assorter -> average

    init {
        voteTotals.forEach { (contestId, candidateCounts) ->
            val redVotesSum = candidateCounts.map { it.value }.sum()
            val info = infos[contestId]!!
            // need at least this many cards would you need for this contest?
            minCardsNeeded[contestId] = roundUp(redVotesSum.toDouble() / info.voteForN)
        }
        maxMinCardsNeeded = minCardsNeeded.values.max()
    }

    override fun contains(contestId: Int) = voteTotals.contains(contestId)
    override fun regVotes(): Map<Int, RegVotes> {
        return voteTotals.mapValues { (_, votes) -> RegVotesImpl(votes, ncards()) }
    }
    fun ncards() = maxMinCardsNeeded + adjustCards

    fun adjustCards(adjust: Int, contestId : Int) {
        if (!contains(contestId)) throw RuntimeException("NO CONTEST")
        adjustCards = max( adjust, adjustCards)
    }

    fun contests() = (voteTotals.map { it.key }).toSortedSet().toIntArray()

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
            Pair(id, maxMinCardsNeeded * info.voteForN - sum)
        }
        return undervote.toMap().toSortedMap()
    }

    fun toBallotPools(): List<BallotPool> {
        return voteTotals.map { (contestId, candCount) ->
            BallotPool(poolName, poolId, contestId, ncards(), candCount)
        }
    }
}

// When the pools have complete CVRS.
open class CardPoolFromCvrs(
    val poolName: String,
    override val poolId: Int,
    val infos: Map<Int, ContestInfo>) : CardPoolIF
{
    val contestTabs = mutableMapOf<Int, ContestTabulation>()  // contestId -> ContestTabulation
    var totalCards = 0

    // a convenient place to keep this, calculated in addOAClcaAssorters()
    override val assortAvg = mutableMapOf<Int, MutableMap<AssorterIF, AssortAvg>>()  // contest -> assorter -> average
    override fun contains(contestId: Int) = contestTabs.contains(contestId)
    override fun regVotes() = contestTabs.filter { !it.value.isIrv }
    // override fun ncards() = totalCards

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

    fun toBallotPools(): List<BallotPool> {
        val bpools = mutableListOf<BallotPool>()
        contestTabs.forEach { contestId, contestCount ->
            if (contestCount.ncards > 0) {
                bpools.add(BallotPool(poolName, poolId, contestId, contestCount.ncards, contestCount.votes))
            }
        }
        return bpools
    }

    // sfoans needs to add undervotes
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
}

// there are no cvrs, use reportedMargin to set the pool assorter averages.  can only use for non-IRV contests
fun addOAClcaAssortersFromMargin(
    oaContests: List<OAContestUnderAudit>,
    cardPools: Map<Int, CardPoolIF>
) {
    // ClcaAssorter already has the contest-wide reported margin. We just have to add the pool assorter averages
    // create the clcaAssertions and add then to the oaContests
    oaContests.forEach { oaContest ->
        val contestId = oaContest.id
        val clcaAssertions = oaContest.pollingAssertions.map { assertion ->
            val assortAverages = mutableMapOf<Int, Double>() // poolId -> average assort value
            cardPools.values.forEach { cardPool ->
                if (cardPool.contains(contestId)) {
                    val regVotes = cardPool.regVotes()[oaContest.id]!!
                    val poolMargin = assertion.assorter.calcReportedMargin(regVotes.votes, regVotes.ncards())
                    assortAverages[cardPool.poolId] = margin2mean(poolMargin)
                }
            }
            val clcaAssorter = OneAuditClcaAssorter(assertion.info, assertion.assorter, true, poolAverages = AssortAvgsInPools(assortAverages))
            ClcaAssertion(assertion.info, clcaAssorter)
        }
        oaContest.clcaAssertions = clcaAssertions
    }
}


/* add pool assort averages to the OneAuditClcaAssorters
fun addOAClcaAssortersFromCvrs(
    oaContests: List<OAContestUnderAudit>,
    cardIter: Iterator<Cvr>,
    cardPools: Map<Int, CardPoolIF> // poolId -> CardPoolIF
) {
    // sum all the assorters values in one pass across all the cvrs. works for both Irvs and Reg.
    while (cardIter.hasNext()) {
        val card: Cvr = cardIter.next()
        if (card.poolId == null) continue

        val assortAvg = cardPools[card.poolId]!!.assortAvg
        oaContests.forEach { contest ->
            if (card.hasContest(contest.id)) { // TODO assumes has_styles = true
                val avg = assortAvg.getOrPut(contest.id) { mutableMapOf() }
                contest.pollingAssertions.forEach { assertion ->
                    val passorter = assertion.assorter
                    val assortAvg = avg.getOrPut(passorter) { AssortAvg() } // TODO could have a hash collision ?
                    assortAvg.ncards++
                    assortAvg.totalAssort += passorter.assort(card, usePhantoms = false)
                }
            }
        }
    }

    // create the clcaAssertions and add poolAverages to them
    oaContests.forEach { oaContest ->
        val clcaAssertions = oaContest.pollingAssertions.map { assertion ->
            val poolAvgMap = mutableMapOf<Int, Double>() // poolId -> average assort value
            cardPools.values.forEach { cardPool ->
                val contestAA = cardPool.assortAvg[oaContest.id]
                if (contestAA != null) {
                    val assortAvg = contestAA[assertion.assorter]
                    if (assortAvg != null) {
                        poolAvgMap[cardPool.poolId] = assortAvg.avg()
                    } else {
                        logger.warn { "cardPool ${cardPool.poolId} missing assertion ${assertion.assorter}" }
                    }
                }
            }
            val poolAvgs = AssortAvgsInPools(poolAvgMap)
            val clcaAssorter = OneAuditClcaAssorter(assertion.info, assertion.assorter, true, poolAvgs)
            ClcaAssertion(assertion.info, clcaAssorter)
        }
        oaContest.clcaAssertions = clcaAssertions
    }

    // compare the pool's assortAverage with the value computed from the contestTabulation (non-IRV only)
    cardPools.values.forEach { cardPool ->
        cardPool.regVotes().forEach { (contestId, regVotes) ->
            val avg = cardPool.assortAvg[contestId]
            if (avg != null) {
                avg.forEach { (assorter, assortAvg) ->
                    // calculated margin based on winner/loser counts in the pool
                    val calcReportedMargin = assorter.calcReportedMargin(regVotes.votes, regVotes.ncards())
                    val calcReportedMean = margin2mean(calcReportedMargin)
                    // average assort value in the pool
                    val cvrAssortAverage = assortAvg.avg()

                    if (!doubleIsClose(calcReportedMean, cvrAssortAverage)) {
                        println("pool ${cardPool.poolId} means not agree for contest $contestId assorter $assorter ")
                        println("     calcReportedMean= ${calcReportedMean} cvrAssortAverage= $cvrAssortAverage ")
                        println("     ${assortAvg} regVotes= $regVotes ")
                        println()
                    }
                }
            }
        }
    }

} */


