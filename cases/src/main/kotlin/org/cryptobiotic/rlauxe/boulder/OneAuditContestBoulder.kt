package org.cryptobiotic.rlauxe.boulder

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.oneaudit.OneAuditContestBuilderIF
import org.cryptobiotic.rlauxe.oneaudit.OneAuditPoolIF
import org.cryptobiotic.rlauxe.oneaudit.OneAuditPoolWithBallotStyle
import org.cryptobiotic.rlauxe.util.mergeReduce
import org.cryptobiotic.rlauxe.util.roundToClosest
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach
import kotlin.collections.get
import kotlin.random.Random

private val logger = KotlinLogging.logger("OneAuditContestBoulder")

class OneAuditContestBoulder(val info: ContestInfo,
                             val sovoContest: BoulderContestVotes,
                             val cvrTab: ContestTabulation,
                             val redTab: ContestTabulation): OneAuditContestBuilderIF {

    // there are no overvotes in the Cvrs; we treat them as blanks (not divided by voteForN)
    val sovoCards = (sovoContest.totalVotes + sovoContest.totalUnderVotes) / info.voteForN + sovoContest.totalOverVotes
    val phantoms = sovoContest.totalBallots - sovoCards

    // sovo gives us an expected undervote for each contest
    val sovoUndervotes = sovoContest.totalUnderVotes + sovoContest.totalOverVotes * info.voteForN
    // missing undervotes we assume are in the redacted pools
    val redUndervotes = sovoUndervotes  - cvrTab.undervotes
    val redVotes = redTab.nvotes()
    // then this is the total cards in the pools
    val redNcards = (redVotes + redUndervotes) / info.voteForN
    // then this is the total cards in the cvrs and the pools
    val totalCards= redNcards + cvrTab.ncardsTabulated
    override val contestId: Int = info.id

    var poolTotalCards: Int = 0

    fun candVoteTotals(): Map<Int, Int> {
        val sum = mutableMapOf<Int, Int>()
        sum.mergeReduce(listOf(cvrTab.votes, redTab.votes))
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
        appendLine(" cvrTabulation=$cvrTab")
        appendLine(" redTabulation=$redTab")

        appendLine("  sovoCards= $sovoCards = (sovoContest.totalVotes + sovoContest.totalUnderVotes) / info.voteForN + sovoContest.totalOverVotes")
        appendLine("  phantoms= $phantoms  = sovoContest.totalBallots - sovoCards")


        val redUnderPct = 100.0 * redUndervotes / (redVotes + redUndervotes)
        appendLine("  sovoUndervotes= ${sovoUndervotes} = sovoContest.totalUnderVotes + sovoContest.totalOverVotes * info.voteForN")
        appendLine("  cvrUndervotes= ${cvrTab.undervotes}")
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

    // take 2
    //
    // cvrTab.undervotes = 7025
    // redUndervotes= 1320 so contest should be 8345, which is what sumWithPools has
    // sumVotes = 17110, ncast = (17110 + 8345) / 3 = 8485, correct

    fun Nc(): Int {
        return sovoContest.totalBallots
        // return max(totalCards, sovoContest.totalBallots)
    }

    fun ncards(): Int {
        // for contest 20, correct the ncards, ignore undervote count
        return if (info.id == 20)
            8256
        else return sumAllCards()
    }

    // total number of cards for this contest in the pools. this is dynamic because the pools get adjusted
    override fun poolTotalCards() = poolTotalCards

    override fun adjustPoolInfo(cardPools: List<OneAuditPoolIF>) {
        poolTotalCards = cardPools.filter{ it.hasContest(info.id)}.sumOf { it.ncards() }
    }

    // calculated total cards in the pools
    override fun expectedPoolNCards() = redNcards

    // ncards
    fun sumAllCards() : Int {
        return poolTotalCards() + cvrTab.ncardsTabulated
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
        println("  ${info.id}: sovoContest.totalBallots=${sovoContest.totalBallots} - contestTab.ncards=${contestTab.ncardsTabulated} = ${sovoContest.totalBallots - contestTab.ncardsTabulated}")
        println("  ${info.id}: sumAllCards=${sumAllCards()} - contestTab.ncards=${contestTab.ncardsTabulated} = ${sumAllCards() - contestTab.ncardsTabulated}")
        println()
    }
}

//////////////////////////////////////////////////////////////////

fun distributeExpectedOvervotes(oaContest: OneAuditContestBuilderIF, cardPools: List<OneAuditPoolWithBallotStyle>) {
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


