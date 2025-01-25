package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.workflow.BallotOrCvr

//// Adapted from SHANGRLA Audit.py

// TODO not clear yet how to limit the sample size. maxFirstRoundSampleSize? maxPercent? show pvalue, let user intervene?

fun consistentSampling(
    contests: List<ContestUnderAudit>, // must have sampleSizes set
    ballotOrCvrs: List<BallotOrCvr>, // must have sampleNums assigned
): List<Int> {
    if (ballotOrCvrs.isEmpty()) return emptyList()

    // set all sampled to false, so each round is independent
    ballotOrCvrs.forEach{ it.setIsSampled(false)}

    val currentSizes = mutableMapOf<Int, Int>() // contestId -> ncvrs in sample
    fun contestInProgress(c: ContestUnderAudit) = (currentSizes[c.id] ?: 0) < c.estSampleSize

    // get list of cvr indexes sorted by sampleNum
    val sortedBocIndices = ballotOrCvrs.indices.sortedBy { ballotOrCvrs[it].sampleNumber() }

    val sampledIndices = mutableListOf<Int>()
    var inx = 0
    // while we need more samples
    while (contests.any { contestInProgress(it) } && inx < sortedBocIndices.size) {
        // get the next sorted cvr
        val sidx = sortedBocIndices[inx]
        val boc = ballotOrCvrs[sidx]
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

private val debug = false
fun uniformSampling(
    contests: List<ContestUnderAudit>,
    ballotOrCvrs: List<BallotOrCvr>,
    samplePctCutoff: Double,
    Nb: Int,
    roundIdx: Int,
): List<Int> {
    if (ballotOrCvrs.isEmpty()) return emptyList()

    // set all sampled to false, so each round is independent TODO not needed
    ballotOrCvrs.forEach{ it.setIsSampled(false)}

    // scale by proportion of ballots that have this contest
    contests.forEach { contestUA ->
        val fac = Nb / contestUA.Nc.toDouble()
        val est = (contestUA.estSampleSize * fac).toInt()
        // println("  $contestUA: scale=${df(fac)} estSampleSizeNoStyles=${est} Nb=$Nb")
        contestUA.estSampleSizeNoStyles = est
        val estPct = est / Nb.toDouble()
        /* if (estPct > samplePctCutoff) {
            contestUA.done = true
            contestUA.status = TestH0Status.LimitReached
            if (debug) println("uniformSampling samplePctCutoff: $contestUA estPct $estPct > $samplePctCutoff round $roundIdx")
            val minAssert = contestUA.minAssertion()!!

            val roundResult = AuditRoundResult(roundIdx,
                estSampleSize=est,
                samplesNeeded = -1,
                samplesUsed = -1,
                pvalue = 0.0,
                status = TestH0Status.LimitReached,
            )
            minAssert.roundResults.add(roundResult)
        } */
    }
    val estTotalSampleSizes = contests.filter { !it.done }.map { it.estSampleSizeNoStyles }
    if (estTotalSampleSizes.isEmpty()) return emptyList()
    val nmvrs = estTotalSampleSizes.max().toInt()

    // get list of ballot indexes sorted by sampleNum
    val sortedCvrIndices = ballotOrCvrs.indices.sortedBy { ballotOrCvrs[it].sampleNumber() }

    // take the first estSampleSize of the sorted ballots
    return sortedCvrIndices.take(nmvrs)
}


