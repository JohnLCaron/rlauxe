package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.Stopwatch

// runs audit rounds until finished
// return last audit round
fun runAudit(name: String, workflow: RlauxAuditIF, quiet: Boolean=true): AuditRound? {
    val stopwatch = Stopwatch()

    var nextRound: AuditRound? = null
    var done = false
    while (!done) {
        nextRound = workflow.startNewRound(quiet=quiet)
        if (nextRound.sampleNumbers.isEmpty()) {
            done = true

        } else {
            stopwatch.start()
            workflow.setMvrsBySampleNumber(nextRound.sampleNumbers )
            if (!quiet) println("\nrunAudit ${nextRound.roundIdx}")
            done = workflow.runAuditRound(nextRound, quiet)
            if (!quiet) println(" runAudit ${nextRound.roundIdx} done=$done samples=${nextRound.sampleNumbers.size}")
        }
    }

    /*
    if (!quiet && rounds.isNotEmpty()) {
        rounds.forEach { println(it) }
        workflow.showResults(rounds.last().sampledIndices.size)
    } */

    return nextRound
}

fun check(auditConfig: AuditConfig, contests: List<ContestRound>) {

    contests.forEach { contestRound ->
        val contestUA = contestRound.contestUA
        if (contestUA.choiceFunction != SocialChoiceFunction.IRV) {
            checkWinners(
                contestRound,
                (contestUA.contest as Contest).votes.entries.sortedByDescending { it.value })  // 2.a)

            // see if margin is too small
            val minMargin = contestUA.minAssertion()!!.assorter.reportedMargin()
            if (minMargin <= auditConfig.minMargin) {
                println("***MinMargin contest ${contestUA} margin ${minMargin} <= ${auditConfig.minMargin}")
                contestRound.done = true
                contestRound.status = TestH0Status.MinMargin
            }

            // see if too many phantoms
            val adjustedMargin = minMargin - contestUA.contest.phantomRate()
            if (auditConfig.removeTooManyPhantoms && adjustedMargin <= 0.0) {
                println("***TooManyPhantoms contest ${contestUA} adjustedMargin ${adjustedMargin} == $minMargin - ${contestUA.contest.phantomRate()} < 0.0")
                contestRound.done = true
                contestRound.status = TestH0Status.TooManyPhantoms
            }
        }
        // println("contest ${contestUA} minMargin ${minMargin} + phantomRate ${contestUA.contest.phantomRate()} = adjustedMargin ${adjustedMargin}")
    }
}

// 2.a) Check that the winners according to the CVRs are the reported winners on the Contest.
fun checkWinners(contestRound: ContestRound, sortedVotes: List<Map.Entry<Int, Int>>) {
    val contest = contestRound.contestUA.contest
    val nwinners = contest.winners.size

    // make sure that the winners are unique
    val winnerSet = mutableSetOf<Int>()
    winnerSet.addAll(contest.winners)
    if (winnerSet.size != contest.winners.size) {
        println("winners in contest ${contest} have duplicates")
        contestRound.done = true
        contestRound.status = TestH0Status.ContestMisformed
        return
    }

    // see if theres a tie TODO check this
    val winnerMin: Int = sortedVotes.take(nwinners).map{ it.value }.min()
    if (sortedVotes.size > nwinners) {
        val firstLoser = sortedVotes[nwinners]
        if (firstLoser.value == winnerMin ) {
            println("tie in contest ${contest}")
            contestRound.done = true
            contestRound.status = TestH0Status.MinMargin
            return
        }
    }

    // check that the top nwinners are in winners list
    sortedVotes.take(nwinners).forEach { (candId, vote) ->
        if (!contest.winners.contains(candId)) {
            println("winners ${contest.winners} does not contain candidateId $candId")
            contestRound.done = true
            contestRound.status = TestH0Status.ContestMisformed
            return
        }
    }
}

// find first index where pvalue < riskLimit, and stays below the riskLimit for the rest of the sequence
fun samplesNeeded(pvalues: List<Double>, riskLimit: Double): Int {
    var firstIndex = -1
    pvalues.forEachIndexed { idx, pvalue ->
        if (pvalue <= riskLimit && firstIndex < 0) {
            firstIndex = idx
        } else if (pvalue >= riskLimit) {
            firstIndex = -1
        }
    }
    return firstIndex
}
