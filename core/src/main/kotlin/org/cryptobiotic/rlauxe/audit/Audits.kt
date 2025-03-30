package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.tabulateVotesFromCvrsUA

// runs audit rounds until finished. return last audit round
// Can only use this if the MvrManager implements MvrManagerTest
// otherwise run one round at a time with PersistentAudit
fun runAudit(name: String, workflow: RlauxAuditIF, quiet: Boolean=true): AuditRound? {
    val stopwatch = Stopwatch()

    var nextRound: AuditRound? = null
    var complete = false
    while (!complete) {
        nextRound = workflow.startNewRound(quiet=quiet)
        if (nextRound.sampleNumbers.isEmpty()) {
            complete = true

        } else {
            stopwatch.start()

            // workflow MvrManager must implement MvrManagerTest, else Exception
            (workflow.mvrManager() as MvrManagerTest).setMvrsBySampleNumber(nextRound.sampleNumbers)

            if (!quiet) println("\nrunAudit $name ${nextRound.roundIdx}")
            complete = workflow.runAuditRound(nextRound, quiet)
            nextRound.auditWasDone = true
            nextRound.auditIsComplete = complete
            if (!quiet) println(" runAudit $name ${nextRound.roundIdx} done=$complete samples=${nextRound.sampleNumbers.size}")
        }
    }

    return nextRound
}

fun checkContestsCorrectlyFormed(auditConfig: AuditConfig, contestsUA: List<ContestUnderAudit>) {

    contestsUA.forEach { contestUA ->
        if (contestUA.status == TestH0Status.InProgress && contestUA.choiceFunction != SocialChoiceFunction.IRV) {
            checkWinners(contestUA)

            // see if margin is too small
            val minAssertion = contestUA.minAssertion()
            if (minAssertion == null) {
                println("*** no assertions for contest ${contestUA}")
                contestUA.status = TestH0Status.ContestMisformed

            } else {
                val minMargin = minAssertion.assorter.reportedMargin()
                if (minMargin <= auditConfig.minMargin) {
                    println("***MinMargin contest ${contestUA} margin ${minMargin} <= ${auditConfig.minMargin}")
                    contestUA.status = TestH0Status.MinMargin
                }

                // see if too many phantoms
                val adjustedMargin = minMargin - contestUA.contest.phantomRate()
                if (auditConfig.removeTooManyPhantoms && adjustedMargin <= 0.0) {
                    println("***TooManyPhantoms contest ${contestUA} adjustedMargin ${adjustedMargin} == $minMargin - ${contestUA.contest.phantomRate()} < 0.0")
                    contestUA.status = TestH0Status.TooManyPhantoms
                }
            }
        }
        // println("contest ${contestUA} minMargin ${minMargin} + phantomRate ${contestUA.contest.phantomRate()} = adjustedMargin ${adjustedMargin}")
    }
}

// check winners are correctly formed
fun checkWinners(contestUA: ContestUnderAudit, ) {
    val sortedVotes: List<Map.Entry<Int, Int>> = (contestUA.contest as Contest).votes.entries.sortedByDescending { it.value } // TODO wtf?
    val contest = contestUA.contest
    val nwinners = contest.winners.size

    // make sure that the winners are unique
    val winnerSet = mutableSetOf<Int>()
    winnerSet.addAll(contest.winners)
    if (winnerSet.size != contest.winners.size) {
        println("*** winners in contest ${contest} have duplicates")
        contestUA.status = TestH0Status.ContestMisformed
        return
    }

    // see if theres a tie TODO check this
    val winnerMin: Int = sortedVotes.take(nwinners).map{ it.value }.min()
    if (sortedVotes.size > nwinners) {
        val firstLoser = sortedVotes[nwinners]
        if (firstLoser.value == winnerMin ) {
            println("*** tie in contest ${contest}")
            contestUA.status = TestH0Status.MinMargin
            return
        }
    }

    // check that the top nwinners are in winners list
    sortedVotes.take(nwinners).forEach { (candId, vote) ->
        if (!contest.winners.contains(candId)) {
            println("*** winners ${contest.winners} does not contain candidateId $candId")
            contestUA.status = TestH0Status.ContestMisformed
            return
        }
    }
}

fun checkContestsWithCvrs(contestsUA: List<ContestUnderAudit>, cvrs: Iterator<CvrUnderAudit>) {
    val votes = tabulateVotesFromCvrsUA(cvrs)
    contestsUA.filter { it.status == TestH0Status.InProgress && it.choiceFunction != SocialChoiceFunction.IRV }.forEach { contestUA ->
        val contestVotes = (contestUA.contest as Contest).votes
        val cvrVotes = votes[contestUA.id]
        if (cvrVotes == null) {
            println("*** contest ${contestUA.contest} not found in tabulatedVotesFromCvrsUA")
            contestUA.status = TestH0Status.ContestMisformed
        } else {
            if (!checkEquivilentVotes(contestVotes, cvrVotes)) {
                println("*** contest ${contestUA.contest} votes ${contestVotes} cvrVotes = $cvrVotes")
                contestUA.status = TestH0Status.ContestMisformed
            }
            require(checkEquivilentVotes((contestUA.contest as Contest).votes, contestVotes))
        }
    }
}

// ok if one has zero votes and the other doesnt
fun checkEquivilentVotes(votes1: Map<Int, Int>, votes2: Map<Int, Int>, ) : Boolean {
    if (votes1 == votes2) return true
    val votes1z = votes1.filter{ (_, vote) -> vote != 0 }
    val votes2z = votes2.filter{ (_, vote) -> vote != 0 }
    return votes1z == votes2z
}

// not used
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
