package org.cryptobiotic.rlauxe.oneaudit

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.core.AssorterIF
import org.cryptobiotic.rlauxe.core.ClcaAssertion
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.util.VotesAndUndervotes
import org.cryptobiotic.rlauxe.util.cleanCsvString
import org.cryptobiotic.rlauxe.util.doubleIsClose
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

// this is really CardPoolForContest
data class BallotPool(
    val name: String,
    val poolId: Int,
    val contestId :Int,
    val ncards: Int,          // ncards for this contest in this pool; TODO hasStyles = false?
    val votes: Map<Int, Int>, // candid -> nvotes, for plurality. umm do we really need ?
) {

    fun calcReportedMargin(winner: Int, loser: Int): Double {
        if (ncards == 0) return 0.0
        val winnerVote = votes[winner] ?: 0
        val loserVote = votes[loser] ?: 0
        return (winnerVote - loserVote) / ncards.toDouble()
    }

    fun votesAndUndervotes(voteForN: Int, ncandidates: Int): Map<Int, Int> {
        val poolVotes = votes.values.sum()
        val poolUndervotes = ncards * voteForN - poolVotes
        return (votes.map { Pair(it.key, it.value)} + Pair(ncandidates, poolUndervotes)).toMap()
    }

    fun votesAndUndervotes(voteForN: Int): VotesAndUndervotes {
        val poolUndervotes = ncards * voteForN - votes.values.sum()
        return VotesAndUndervotes(votes, poolUndervotes, voteForN)
    }

    fun reportedAverage(winner: Int, loser: Int): Double {
        val winnerVotes = votes[winner] ?: 0
        val loserVotes = votes[loser] ?: 0
        val reportedMargin = (winnerVotes - loserVotes) / ncards.toDouble() // TODO dont know Nc
        return margin2mean(reportedMargin)
    }
}


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

class CardPool(
    poolNameIn: String,
    val poolId: Int,
    val poolVotes: Map<Int, Map<Int, Int>>, // contestId -> candidateId -> nvotes from redacted group // TODO use ContestTabulation ??
    infos: Map<Int, ContestInfo>) // all infos
{
    val poolName = cleanCsvString(poolNameIn)
    val voteForNmap = infos.values.associate { it.id to it.voteForN }

    val minCardsNeeded = mutableMapOf<Int, Int>() // contestId -> minCardsNeeded
    val maxMinCardsNeeded: Int
    private var adjustCards = 0

    // a convenient place to keep this, used in addOAClcaAssortersFromCvrs()
    val assortAvg = mutableMapOf<Int, MutableMap<AssorterIF, AssortAvg>>()  // contest -> assorter -> average

    init {
        poolVotes.forEach { (contestId, candidateCounts) ->
            val redVotesSum = candidateCounts.map { it.value }.sum()
            // need at leat this many cards would you need for this contest?
            minCardsNeeded[contestId] = roundUp(redVotesSum.toDouble() / voteForNmap[contestId]!!)
        }
        maxMinCardsNeeded = minCardsNeeded.values.max()
    }

    fun contains(contestId: Int) = poolVotes.contains(contestId)

    fun adjustCards(adjust: Int, contestId : Int) {
        if (!contains(contestId)) throw RuntimeException("NO CONTEST")
        adjustCards = max( adjust, adjustCards)
    }

    fun contests() = (poolVotes.map { it.key }).toSortedSet().toIntArray()

    fun showVotes(contestIds: Collection<Int>, width: Int=4) = buildString {
        append("${trunc(poolName, 9)}:")
        contestIds.forEach { id ->
            val contestVote = poolVotes[id]
            if (contestVote == null)
                append("    |")
            else {
                val sum = contestVote.map { it.value } .sum()
                append("${nfn(sum, width)}|")
            }
        }
        appendLine()

        val undervotes = undervotes2()
        append("${trunc("", 9)}:")
        contestIds.forEach { id ->
            val contestVote = poolVotes[id]
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
    fun undervotes2(): Map<Int, Int> {  // contest -> undervote
        val undervote = poolVotes.map { (id, cands) ->
            val sum = cands.map { it.value }.sum()
            Pair(id, maxMinCardsNeeded * voteForNmap[id]!! - sum)
        }
        return undervote.toMap().toSortedMap()
    }

    fun ncards() = maxMinCardsNeeded + adjustCards

    fun toBallotPools(): List<BallotPool> {
        if (poolId == 10)
            print("heh")
        return poolVotes.map { (contestId, candCount) ->
            BallotPool(poolName, poolId, contestId, ncards(), candCount)
        }
    }
}

fun addOAClcaAssortersFromMargin(
    oaContests: List<ContestUnderAudit>,
    cardPools: Map<Int, CardPool>
) {
    // ClcaAssorter already has the contest-wide reported margin. We justy have to add the poolAvgs
    // create the clcaAssertions and add then to the oaContests
    oaContests.forEach { oaContest ->
        val contestId = oaContest.id
        val clcaAssertions = oaContest.pollingAssertions.map { assertion ->
            val assortAverages = mutableMapOf<Int, Double>() // poolId -> average assort value
            cardPools.values.forEach { cardPool ->
                if (cardPool.contains(contestId)) {
                    val poolMargin = assertion.assorter.calcReportedMargin(cardPool.poolVotes[oaContest.id]!!, cardPool.ncards())
                    assortAverages[cardPool.poolId] = margin2mean(poolMargin)
                }
            }
            val clcaAssorter = OneAuditClcaAssorter(assertion.info, assertion.assorter, true, poolAverages = AssortAvgsInPools(assortAverages))
            ClcaAssertion(assertion.info, clcaAssorter)
        }
        oaContest.clcaAssertions = clcaAssertions
    }
}

fun addOAClcaAssortersFromCvrs(
    oaContests: List<ContestUnderAudit>,
    cardIter: Iterator<Cvr>,
    cardPools: Map<Int, CardPool>
) {
    // sum all the assorters values in one pass across all the cvrs
    while (cardIter.hasNext()) {
        val card: Cvr = cardIter.next()
        if (card.poolId == null) continue

        val assortAvg = cardPools[card.poolId]!!.assortAvg
        oaContests.forEach { contest ->
            val avg = assortAvg.getOrPut(contest.id) { mutableMapOf() }
            contest.pollingAssertions.forEach { assertion ->
                val passorter = assertion.assorter
                val assortAvg = avg.getOrPut(passorter) { AssortAvg() } // TODO could have a hash collision ?
                if (card.hasContest(contest.id)) {
                    assortAvg.ncards++
                    assortAvg.totalAssort += passorter.assort(card, usePhantoms = false) // TODO usePhantoms correct ??
                }
            }
        }
    }

    // create the clcaAssertions and add then to the oaContests
    oaContests.forEach { oaContest ->
        val clcaAssertions = oaContest.pollingAssertions.map { assertion ->
            val assortAverageTest = mutableMapOf<Int, Double>() // poolId -> average assort value
            cardPools.values.forEach { cardPool ->
                val contestAA = cardPool.assortAvg[oaContest.id]
                if (contestAA != null) {
                    val assortAvg = contestAA[assertion.assorter]
                    if (assortAvg != null) {
                        assortAverageTest[cardPool.poolId] = assortAvg.avg()
                    } else {
                        logger.warn { "cardPool ${cardPool.poolId} missing assertion ${assertion.assorter}" }
                    }
                } else if (cardPool.poolName != unpooled) {
                    logger.warn { "cardPool ${cardPool.poolId} missing contest ${oaContest.id}" }
                }
            }

            val poolAvgs = AssortAvgsInPools(assortAverageTest)
            val clcaAssorter = OneAuditClcaAssorter(assertion.info, assertion.assorter, true, poolAvgs)
            ClcaAssertion(assertion.info, clcaAssorter)
        }
        oaContest.clcaAssertions = clcaAssertions
    }

    // compare the assortAverage with the value computed from the contestTabulation (non-IRV only)
    cardPools.values.forEach { cardPool ->
        cardPool.poolVotes.forEach { (contestId, candVotes) ->
            val avg = cardPool.assortAvg[contestId]
            if (avg != null) {
                avg.forEach { (assorter, assortAvg) ->
                    val calcReportedMargin =
                        assorter.calcReportedMargin(candVotes, cardPool.ncards())
                    val calcReportedMean = margin2mean(calcReportedMargin)
                    val cvrAverage = assortAvg.avg()

                    if (!doubleIsClose(calcReportedMean, cvrAverage)) {
                        println("pool ${cardPool.poolId} means not agree for contest $contestId assorter $assorter ")
                        println("     calcReportedMean= ${calcReportedMean} cvrAverage= $cvrAverage ")
                        println("     ${assortAvg} candVotes= $candVotes ")
                        println()
                    }
                }
            } else {
                logger.warn { "cardPool ${cardPool.poolId} missing contest ${contestId}" }
            }
        }
    }
}


