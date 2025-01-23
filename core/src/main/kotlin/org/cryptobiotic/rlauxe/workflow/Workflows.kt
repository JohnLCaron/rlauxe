package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.TestH0Status
import org.cryptobiotic.rlauxe.util.Stopwatch
import java.util.concurrent.TimeUnit

interface RlauxWorkflow {
    fun chooseSamples(prevMvrs: List<Cvr>, roundIdx: Int, show: Boolean = false): List<Int>
    fun runAudit(sampleIndices: List<Int>, mvrs: List<Cvr>, roundIdx: Int): Boolean
    fun showResults()
    fun getContests() : List<ContestUnderAudit>
}

// runs test workflow with fake mvrs already generated, and the cvrs are variants of those
fun runWorkflow(name: String, workflow: RlauxWorkflow, testMvrs: List<Cvr>, quiet: Boolean=false) {
    val stopwatch = Stopwatch()

    val previousSamples = mutableSetOf<Int>()
    var rounds = mutableListOf<Round>()
    var roundIdx = 1

    var prevMvrs = emptyList<Cvr>()
    var done = false
    while (!done) {
        val indices = workflow.chooseSamples(prevMvrs, roundIdx, show=false)

        val currRound = Round(roundIdx, indices, previousSamples.toSet())
        rounds.add(currRound)
        previousSamples.addAll(indices)

        if (!quiet) println("estimateSampleSizes round $roundIdx took ${stopwatch.elapsed(TimeUnit.MILLISECONDS)} ms\n")
        stopwatch.start()

        val sampledMvrs = indices.map {
            testMvrs[it]
        }

        done = workflow.runAudit(indices, sampledMvrs, roundIdx)
        if (!quiet) println("runAudit $roundIdx done=$done took ${stopwatch.elapsed(TimeUnit.MILLISECONDS)} ms\n")
        prevMvrs = sampledMvrs
        roundIdx++
    }

    if (!quiet) {
        rounds.forEach { println(it) }
        workflow.showResults()
    }
}

data class Round(val round: Int, val sampledIndices: List<Int>, val previousSamples: Set<Int>) {
    var newSamples: Int = 0
    init {
        newSamples = sampledIndices.count { it !in previousSamples }
    }
    override fun toString(): String {
        return "Round(round=$round, newSamples=$newSamples)"
    }
}

data class WorkflowResult(val N: Int,
                          val margin: Double,
                          val status: TestH0Status,
                          val nrounds: Double,
                          val samplesUsed: Double,
                          val samplesNeeded: Double,
                          val parameters: Map<String, Double>,
                          val failPct: Double = 0.0, // from avgWorkflowResult()
)

// 2.a) Check that the winners according to the CVRs are the reported winners on the Contest.
fun checkWinners(contestUA: ContestUnderAudit, sortedVotes: List<Map.Entry<Int, Int>>) {
    val contest = contestUA.contest
    val nwinners = contest.winners.size

    // make sure that the winners are unique
    val winnerSet = mutableSetOf<Int>()
    winnerSet.addAll(contest.winners)
    if (winnerSet.size != contest.winners.size) {
        println("winners in contest ${contest} have duplicates")
        contestUA.done = true
        contestUA.status = TestH0Status.ContestMisformed
        return
    }

    // see if theres a tie
    val winnerMin: Int = sortedVotes.take(nwinners).map{ it.value }.min()
    if (sortedVotes.size > nwinners) {
        val firstLoser = sortedVotes[nwinners]
        if (firstLoser.value == winnerMin ) {
            println("tie in contest ${contest}")
            contestUA.done = true
            contestUA.status = TestH0Status.MinMargin
            return
        }
    }

    // check that the top nwinners are in winners list
    sortedVotes.take(nwinners).forEach { (candId, vote) ->
        if (!contest.winners.contains(candId)) {
            println("winners ${contest.winners} does not contain candidateId $candId")
            contestUA.done = true
            contestUA.status = TestH0Status.ContestMisformed
            return
        }
    }
}