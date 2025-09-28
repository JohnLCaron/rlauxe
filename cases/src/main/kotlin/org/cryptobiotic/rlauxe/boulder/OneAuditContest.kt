package org.cryptobiotic.rlauxe.boulder

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.ContestTabulation
import org.cryptobiotic.rlauxe.core.AssorterIF
import org.cryptobiotic.rlauxe.core.ClcaAssertion
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.CvrExport.Companion.unpooled
import org.cryptobiotic.rlauxe.oneaudit.AssortAvg
import org.cryptobiotic.rlauxe.oneaudit.AssortAvgsInPools
import org.cryptobiotic.rlauxe.oneaudit.BallotPool
import org.cryptobiotic.rlauxe.oneaudit.OneAuditClcaAssorter
import org.cryptobiotic.rlauxe.util.cleanCsvString
import org.cryptobiotic.rlauxe.util.doubleIsClose
import org.cryptobiotic.rlauxe.util.margin2mean
import org.cryptobiotic.rlauxe.util.mergeReduce
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.util.roundUp
import org.cryptobiotic.rlauxe.util.trunc
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach
import kotlin.collections.get
import kotlin.collections.set
import kotlin.math.max

private val logger = KotlinLogging.logger("OneAuditContest2")

// in this form we calculate undervoets differenctly, dont use red.ncards which is unknown at the time of creation
class OneAuditContest(val info: ContestInfo, val sovoContest: BoulderContestVotes,
                      val cvr: ContestTabulation, val red: ContestTabulation, val cardPools: List<CardPoolB>
) {
    // there are no overvotes in the Cvrs; we treat them as blanks (not divided by voteForN)
    val sovoCards = (sovoContest.totalVotes + sovoContest.totalUnderVotes) / info.voteForN + sovoContest.totalOverVotes
    val phantoms = sovoContest.totalBallots - sovoCards

    val sovoUndervotes = sovoContest.totalUnderVotes + sovoContest.totalOverVotes * info.voteForN
    val redUndervotes = sovoUndervotes  - cvr.undervotes
    val redVotes = red.nvotes()
    val redNcards = (redVotes + redUndervotes) / info.voteForN
    val totalCards= redNcards + cvr.ncards

    fun candVoteTotals(): Map<Int, Int> {
        val sum = mutableMapOf<Int, Int>()
        sum.mergeReduce(listOf(cvr.votes, red.votes))
        return sum
    }

    override fun toString() = buildString {
        appendLine(info)
        appendLine(" sovoContest=$sovoContest")
    }

    fun details() = buildString {
        appendLine(info)
        appendLine(" sovoContest=$sovoContest")
        // appendLine(" allTabulation=$all3")
        appendLine(" cvrTabulation=$cvr")
        appendLine(" redTabulation=$red")

        appendLine("  sovoCards= $sovoCards = (sovoContest.totalVotes + sovoContest.totalUnderVotes) / info.voteForN + sovoContest.totalOverVotes")
        appendLine("  phantoms= $phantoms  = sovoContest.totalBallots - sovoCards")


        val redUnderPct = 100.0 * redUndervotes / (redVotes + redUndervotes)
        appendLine("  sovoUndervotes= ${sovoUndervotes} = sovoContest.totalUnderVotes + sovoContest.totalOverVotes * info.voteForN")
        appendLine("  cvrUndervotes= ${cvr.undervotes}")
        appendLine("  redUndervotes= $redUndervotes  = sovoUndervotes - cvr.undervotes")
        appendLine("  redVotes= $redVotes = redacted.votes.map { it.value }.sum()")
        appendLine("  redNcards= $redNcards = (redVotes + redUndervotes) / info.voteForN")
        appendLine("  totalCards= ${totalCards} = redNcards + cvr.ncards")
        appendLine("  diff= ${sovoContest.totalBallots - totalCards} = sovoContest.totalBallots - totalCards")
        appendLine("  redUnderPct= 100.0 * redUndervotes / redNcards  = ${redUnderPct.toInt()}%")
    }

    // on contest 20, sovo.totalVotes and sovo.totalBallots is wrong vs the cvrs. (only one where voteForN=3, but may not be related)

    // contestTitle, precinctCount, activeVoters, totalBallots, totalVotes, totalUnderVotes, totalOverVotes
    //'Town of Superior - Trustee' (20) candidates=[0, 1, 2, 3, 4, 5, 6] choiceFunction=PLURALITY nwinners=3 voteForN=3
    // sovoContest=Town of Superior - Trustee, 7, 9628, 8254, 16417, 8246, 33
    // cvrTabulation={0=3121, 1=3332, 2=3421, 3=2097, 4=805, 5=657, 6=3137} nvotes=16570 ncards=7865 undervotes=7025 overvotes=0 novote=1484 underPct= 29%
    // redTabulation={0=130, 1=87, 2=111, 3=50, 4=25, 5=36, 6=101} nvotes=540 ncards=180 undervotes=0 overvotes=0 novote=0 underPct= 0%
    //  sovoCards= 8254 = (sovoContest.totalVotes + sovoContest.totalUnderVotes) / info.voteForN + sovoContest.totalOverVotes
    //  phantoms= 0  = sovoContest.totalBallots - sovoCards
    //  sovoUndervotes= 8345 = sovoContest.totalUnderVotes + sovoContest.totalOverVotes * info.voteForN
    //  cvrUndervotes= 7025
    //  redUndervotes= 1320  = sovoUndervotes - cvr.undervotes
    //  redVotes= 540 = redacted.votes.map { it.value }.sum()
    //  redNcards= 620 = (redVotes + redUndervotes) / info.voteForN
    //  totalCards= 8485 = redNcards + cvr.ncards
    //  diff= -231 = sovoContest.totalBallots - totalCards
    //  redUnderPct= 100.0 * redUndervotes / redNcards  = 70%

    // assume sovo.totalBallots is wrong
    // so nballotes uses max(totalCards, sovoContest.totalBallots)

    fun Nc(): Int {
        return max(totalCards, sovoContest.totalBallots)
    }

    fun poolTotals(): Int {
        return cardPools.filter{ it.contains(info.id)}.sumOf { it.ncards() }
    }

    // ncards
    fun sumAllCards() : Int {
        return poolTotals() + cvr.ncards
    }

    fun checkCvrs(contestTab: ContestTabulation) {
        sovoContest.candidateVotes.forEach { (sovoCandidate, sovoVote) ->
            val candidateId = info.candidateNames[sovoCandidate]
            val contestVote = contestTab.votes[candidateId] ?: 0
            if (contestVote != sovoVote) {
                println("*** ${info.name} '$sovoCandidate' $contestVote != $sovoVote")
            }
            require(contestVote == sovoVote)
        }
    }

   fun checkNcards(contestTab: ContestTabulation) {
        println("  ${info.id}: sovoContest.totalBallots=${sovoContest.totalBallots} - contestTab.ncards=${contestTab.ncards} = ${sovoContest.totalBallots - contestTab.ncards}")
        println("  ${info.id}: sumAllCards=${sumAllCards()} - contestTab.ncards=${contestTab.ncards} = ${sumAllCards() - contestTab.ncards}")
        println()
    }
}

class CardPoolB(
    poolNameIn: String,
    val poolId: Int,
    val redVotes: Map<Int, Map<Int, Int>>, // contestId -> candidateId -> nvotes from redacted group // TODO use ContestTabulation ??
    val infos: Map<Int, ContestInfo>) // all infos
{
    val poolName = cleanCsvString(poolNameIn)

    val minCardsNeeded = mutableMapOf<Int, Int>() // contestId -> minCardsNeeded
    val maxMinCardsNeeded: Int
    private var adjustCards = 0

    // a convenient place to keep this, used in addOAClcaAssortersFromCvrs()
    val assortAvg = mutableMapOf<Int, MutableMap<AssorterIF, AssortAvg>>()  // contest -> assorter -> average

    init {
        redVotes.forEach { (contestId, candidateCounts) ->
            val info = infos[contestId]!!
            val redVotesSum = candidateCounts.map { it.value }.sum()
            // need at leat this many cards would you need for this contest?
            minCardsNeeded[contestId] = roundUp(redVotesSum.toDouble() / info.voteForN)
        }
        maxMinCardsNeeded = minCardsNeeded.values.max()
    }

    fun contains(contestId: Int) = redVotes.contains(contestId)

    fun adjustCards(adjust: Int, contestId : Int) {
        if (!contains(contestId)) throw RuntimeException("NO CONTEST")
        adjustCards = max( adjust, adjustCards)
    }

    fun contests() = (redVotes.map { it.key }).toSortedSet().toIntArray()

    fun showVotes(contestIds: Collection<Int>, width: Int=4) = buildString {
        append("${trunc(poolName, 9)}:")
        contestIds.forEach { id ->
            val contestVote = redVotes[id]
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
            val contestVote = redVotes[id]
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
        val undervote = redVotes.map { (id, cands) ->
            val sum = cands.map { it.value }.sum()
            val info = infos[id]!!
            Pair(id, maxMinCardsNeeded * info.voteForN - sum)
        }
        return undervote.toMap().toSortedMap()
    }

    fun ncards() = maxMinCardsNeeded + adjustCards

    fun toBallotPools(): List<BallotPool> {
        if (poolId == 10)
            print("heh")
        return redVotes.map { (contestId, candCount) ->
            BallotPool(poolName, poolId, contestId, ncards(), candCount)
        }
    }
}

fun addOAClcaAssortersFromMargin(
    oaContests: List<ContestUnderAudit>,
    cardPools: Map<Int, CardPoolB>
) {
    // ClcaAssorter already has the contest-wide reported margin. We justy have to add the poolAvgs
    // create the clcaAssertions and add then to the oaContests
    oaContests.forEach { oaContest ->
        val contestId = oaContest.id
        val clcaAssertions = oaContest.pollingAssertions.map { assertion ->
            val assortAverages = mutableMapOf<Int, Double>() // poolId -> average assort value
            cardPools.values.forEach { cardPool ->
                if (cardPool.contains(contestId)) {
                    val poolMargin = assertion.assorter.calcReportedMargin(cardPool.redVotes[oaContest.id]!!, cardPool.ncards())
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
    cardPools: Map<Int, CardPoolB>
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
        cardPool.redVotes.forEach { (contestId, candVotes) ->
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




