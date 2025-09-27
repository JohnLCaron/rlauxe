package org.cryptobiotic.rlauxe.boulder

import org.cryptobiotic.rlauxe.audit.ContestTabulation
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.dominion.RedactedGroup
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.util.roundToClosest
import org.cryptobiotic.rlauxe.util.trunc
import kotlin.collections.get
import kotlin.math.max

// intermediate form before we make Contest
class OneAuditContest(val info: ContestInfo, val sovoContest: BoulderContestVotes,
                      val cvr: ContestTabulation, val red: ContestTabulation
) {
    // there are no overvotes in the Cvrs; we treat them as undervotes and add to redacted pools.
    val redUndervotes = sovoContest.totalUnderVotes + sovoContest.totalOverVotes - cvr.undervotes

    // allocated missing undervotes across pools. In a real audit, this must be provided for each pool and contest.
    val allocUndervotes = mutableMapOf<String, Int>() // pool id -> undervote

    fun nballots(): Int {
        val nvotes = cvr.nvotes() + red.nvotes()
        val nballots = (nvotes + sovoContest.totalUnderVotes) / info.voteForN + sovoContest.totalOverVotes
        return max(nballots, sovoContest.totalBallots)
    }

    fun votesCast(): Int {
        val nvotes = cvr.nvotes() + red.nvotes()
        val sovoCards = (nvotes + sovoContest.totalUnderVotes) / info.voteForN
        val votesCast = sovoCards + sovoContest.totalOverVotes
        return votesCast
    }

    // allocate missing undervotes across pools
    fun allocateUndervotes(groups: List<RedactedGroup>) {
        val totalCards = red.ncards // TODO where do we get this when we dont have CVRs?
        var used = 0

        groups.forEach { group ->
            val votes = group.contestVotes[info.id]
            if (votes != null) {
                // distribute undervotes as proportion of totalVotes
                val groupUndervotes = roundToClosest(redUndervotes * (votes.values.sum() / totalCards.toDouble()))
                used += groupUndervotes
                allocUndervotes[group.ballotType] = groupUndervotes
            }
        }
        // adjust the last group so sum undervotes = redUndervotes
        if (redUndervotes - used != 0) {
            val adjustGroup = groups.first { allocUndervotes[it.ballotType] != null && allocUndervotes[it.ballotType]!! > (used - redUndervotes) }.ballotType
            val lastUnder = allocUndervotes[adjustGroup]!!
            allocUndervotes[adjustGroup] = lastUnder + (redUndervotes - used)
        }
        // check
        require (allocUndervotes.values.sum() == redUndervotes)
    }

    override fun toString() = buildString {
        appendLine(info)
        appendLine(" sovoContest=$sovoContest")
    }

    fun showSummary() {
        val ncvrs = cvr.ncards
        println("  ${info.id}: sovoContest.totalBallots=${sovoContest.totalBallots} ncvrs=${cvr.ncards}  ${(100.0 * ncvrs)/sovoContest.totalBallots} % ")
    }

    fun details() = buildString {
        appendLine(info)
        appendLine(" sovoContest=$sovoContest")
        // appendLine(" allTabulation=$all3")
        appendLine(" cvrTabulation=$cvr")
        appendLine(" redTabulation=$red")

        val sovoCards = (sovoContest.totalVotes + sovoContest.totalUnderVotes) / info.voteForN
        val phantoms = sovoContest.totalBallots - sovoCards - sovoContest.totalOverVotes
        appendLine("  sovoCards= $sovoCards = (sovoContest.totalVotes + sovoContest.totalUnderVotes) / info.voteForN")
        appendLine("  phantoms= $phantoms  = sovoContest.totalBallots - sovoCards - sovoContest.totalOverVotes")

        val redUndervotes = sovoContest.totalUnderVotes - cvr.undervotes
        val redVotes = red.nvotes()
        val redNcards = (redVotes + redUndervotes) / info.voteForN
        val redUnderPct = 100.0 * redUndervotes / (redVotes + redUndervotes)
        appendLine("  redUndervotes= $redUndervotes  = sovoContest.totalUnderVotes - cvr.undervotes")
        appendLine("  redVotes= $redVotes = redacted.votes.map { it.value }.sum()")
        appendLine("  redNcards= $redNcards = (redVotes + redUndervotes) / info.voteForN")
        appendLine("  redUnderPct= 100.0 * redUndervotes / redNcards  = ${redUnderPct.toInt()}%")
    }

    fun problems() = buildString {
        appendLine(info)
        val nvotes = cvr.nvotes() + red.nvotes()
        val no = if (nvotes == sovoContest.totalVotes) "" else "***"
        appendLine("  nvotes= $nvotes, sovoContest.totalVotes = ${sovoContest.totalVotes} $no")

        val nballots = (nvotes + sovoContest.totalUnderVotes) / info.voteForN + sovoContest.totalOverVotes
        val no2 = if (nballots == sovoContest.totalBallots) "" else "***"
        appendLine("  nballots= $nballots, sovoContest.totalBallots = ${sovoContest.totalBallots} $no2")

        val ncards = cvr.ncards + red.ncards
        val no3 = if (ncards == votesCast()) "" else "***"
        appendLine("  ncards= $ncards, votesCast() = ${votesCast()} $no3")
    }
    // on contest 20, sovo.totalVotes and sovo.totalBallots is wrong vs the cvrs. (only one where voteForN=3, but may not be related)
    //  'Town of Superior - Trustee' (20) candidates=[0, 1, 2, 3, 4, 5, 6] choiceFunction=PLURALITY nwinners=3 voteForN=3
    //   nvotes= 17110, sovoContest.totalVotes = 16417
    //   nballots= 8485, sovoContest.totalBallots = 8254
    // assume sovo is wrong
    // so nballotes uses max(nballots, sovoContest.totalBallots)


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
        println("  ${info.id}: nballots=${nballots()} - contestTab.ncards=${contestTab.ncards} = ${nballots() - contestTab.ncards}")
        println()
    }

}

