package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.workflow.BallotOrCard

//// Adapted from SHANGRLA Audit.py

// TODO not clear yet how to limit the sample size. maxFirstRoundSampleSize? maxPercent? show pvalue, let user intervene?

fun consistentSampling(
    contests: List<ContestUnderAudit>, // must have sampleSizes set
    ballotOrCards: List<BallotOrCard>, // must have sampleNums assigned
): List<Int> {
    if (ballotOrCards.isEmpty()) return emptyList()

    // set all sampled to false, so each round is independent
    ballotOrCards.forEach{ it.setIsSampled(false)}

    val currentSizes = mutableMapOf<Int, Int>() // contestId -> ncvrs in sample
    fun contestInProgress(c: ContestUnderAudit) = (currentSizes[c.id] ?: 0) < c.estSampleSize

    // get list of cvr indexes sorted by sampleNum
    val sortedBocIndices = ballotOrCards.indices.sortedBy { ballotOrCards[it].sampleNumber() }

    val sampledIndices = mutableListOf<Int>()
    var inx = 0
    // while we need more samples
    while (contests.any { contestInProgress(it) } && inx < sortedBocIndices.size) {
        // get the next sorted cvr
        val sidx = sortedBocIndices[inx]
        val boc = ballotOrCards[sidx]
        // does this contribute to one or more contests that need more samples?
        if (contests.any { contestInProgress(it) && boc.hasContest(it.id) }) {
            // then use it
            sampledIndices.add(sidx)
            boc.setIsSampled(true)
            contests.forEach { contest ->
                if (boc.hasContest(contest.id)) {
                    currentSizes[contest.id] = currentSizes[contest.id]?.plus(1) ?: 1
                }
            }
        }
        inx++
    }
    if (inx > sortedBocIndices.size) {
        throw RuntimeException("ran out of samples!!")
    }

    return sampledIndices
}

fun uniformSampling(
    contests: List<ContestUnderAudit>,
    ballotOrCards: List<BallotOrCard>,
    samplePctCutoff: Double,
    N: Int,
    roundIdx: Int,
): List<Int> {
    if (ballotOrCards.isEmpty()) return emptyList()

    // set all sampled to false, so each round is independent
    ballotOrCards.forEach{ it.setIsSampled(false)}

    // scale by proportion of ballots that have this contest
    contests.forEach {
        val fac = N / it.Nc.toDouble()
        val est = (it.estSampleSize * fac).toInt()
        val estPct = (it.estSampleSize / it.Nc.toDouble())
        println("  $it: scale=${df(fac)} estSampleSizeNoStyles=${est.toInt()}")
        it.estSampleSizeNoStyles = est
        if (estPct > samplePctCutoff) {
            it.done = true
            it.status = TestH0Status.LimitReached
            println("  ***$it estPct $estPct > samplePctCutoff $samplePctCutoff round $roundIdx")
            val minAssert = it.minAssertion()!!

            val roundResult = AuditRoundResult(roundIdx,
                estSampleSize=est,
                samplesNeeded = -1,
                samplesUsed = -1,
                pvalue = 0.0,
                status = TestH0Status.LimitReached,
            )
            minAssert.roundResults.add(roundResult)
        }
    }
    val estTotalSampleSizes = contests.filter { !it.done }.map { it.estSampleSizeNoStyles }
    if (estTotalSampleSizes.isEmpty()) return emptyList()

    // get list of ballot indexes sorted by sampleNum
    val sortedCvrIndices = ballotOrCards.indices.sortedBy { ballotOrCards[it].sampleNumber() }

    // take the first estSampleSize of the sorted ballots
    // val simple = roundIdx * N / 10.0
    // val sampledIndices = sortedCvrIndices.take(simple.toInt())
    val sampledIndices = sortedCvrIndices.take(estTotalSampleSizes.max().toInt())

    return sampledIndices
}


