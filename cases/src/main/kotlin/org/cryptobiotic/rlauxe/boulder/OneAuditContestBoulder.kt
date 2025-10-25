package org.cryptobiotic.rlauxe.boulder

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.oneaudit.CardPoolIF
import org.cryptobiotic.rlauxe.oneaudit.OneAuditContestIF
import org.cryptobiotic.rlauxe.util.mergeReduce
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach
import kotlin.collections.get
import kotlin.math.max

private val logger = KotlinLogging.logger("OneAuditContestBoulder")

class OneAuditContestBoulder(val info: ContestInfo, val sovoContest: BoulderContestVotes,
                             val cvr: ContestTabulation, val red: ContestTabulation): OneAuditContestIF {

    // there are no overvotes in the Cvrs; we treat them as blanks (not divided by voteForN)
    val sovoCards = (sovoContest.totalVotes + sovoContest.totalUnderVotes) / info.voteForN + sovoContest.totalOverVotes
    val phantoms = sovoContest.totalBallots - sovoCards

    // sovo gives us an expected undervote for each contest
    val sovoUndervotes = sovoContest.totalUnderVotes + sovoContest.totalOverVotes * info.voteForN
    // missing undervotes we assume are in the redacted pools
    val redUndervotes = sovoUndervotes  - cvr.undervotes
    val redVotes = red.nvotes()
    // then this is the total cards in the pools
    val redNcards = (redVotes + redUndervotes) / info.voteForN
    // then this is the total cards in the cvrs and the pools
    val totalCards= redNcards + cvr.ncards
    override val contestId: Int = info.id

    var poolTotalCards: Int = 0

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

    override fun adjustPoolInfo(cardPools: List<CardPoolIF>) {
        poolTotalCards = cardPools.filter{ it.contains(info.id)}.sumOf { it.ncards() }
    }

    // calculated total cards in the pools
    override fun expectedPoolNCards() = redNcards

    // ncards
    fun sumAllCards() : Int {
        return poolTotalCards() + cvr.ncards
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



