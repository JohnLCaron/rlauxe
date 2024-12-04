package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.Prng
import org.cryptobiotic.rlauxe.util.df

//// Adapted from SHANGRLA Audit.py


///////////////////////////////////////////////////////////////////////

// TODO not clear yet how to limit the sample size. maxFirstRoundSampleSize? maxPercent? show pvalue, let user intervene?

fun consistentCvrSampling(
    contests: List<ContestUnderAudit>, // must have sampleSizes set
    cvrList: List<CvrUnderAudit>, // must have sampleNums assigned
): List<Int> {
    if (cvrList.isEmpty()) return emptyList()

    val currentSizes = mutableMapOf<Int, Int>()
    fun contestInProgress(c: ContestUnderAudit) = (currentSizes[c.id] ?: 0) < c.estSampleSize

    // get list of cvr indexes sorted by sampleNum
    val sortedCvrIndices = cvrList.indices.sortedBy { cvrList[it].sampleNum }

    val sampledIndices = mutableListOf<Int>()
    var inx = 0
    // while we need more samples
    while (contests.any { contestInProgress(it) } && inx < sortedCvrIndices.size) {
        // get the next sorted cvr
        val sidx = sortedCvrIndices[inx]
        val cvr = cvrList[sidx]
        // does this cvr contribute to one or more contests that need more samples?
        if (contests.any { contestInProgress(it) && cvr.hasContest(it.id) }) {
            // then use it
            sampledIndices.add(sidx)
            cvr.sampled = true
            contests.forEach { contest ->
                if (cvr.hasContest(contest.id)) {
                    currentSizes[contest.id] = currentSizes[contest.id]?.plus(1) ?: 1
                }
            }
        }
        inx++
    }
    if (inx == sortedCvrIndices.size) {
        println("ran out of samples!!")
    }
    currentSizes.forEach { (contestId, size) ->
        val contest = contests.find { it.id == contestId }!!
        contest.availableInSample = size
        if (show) println(" ${contest} availableInSample=${contest.availableInSample}")
    }
    return sampledIndices
}

fun consistentPollingSampling(
    contests: List<ContestUnderAudit>, // all the contests you want to sample
    ballots: List<BallotUnderAudit>, // all the ballots available to sample
    ballotManifest: BallotManifest,
): List<Int> {
    if (ballots.isEmpty()) return emptyList()

    val currentSizes = mutableMapOf<Int, Int>()
    fun contestInProgress(c: ContestUnderAudit) = (currentSizes[c.id] ?: 0) < c.estSampleSize

    // get list of ballot indexes sorted by sampleNum
    val sortedCvrIndices = ballots.indices.sortedBy { ballots[it].sampleNum }

    val sampledIndices = mutableListOf<Int>()
    var inx = 0
    // while we need more samples
    while (contests.any { contestInProgress(it) }  && (inx < sortedCvrIndices.size)) {
        // get the next sorted cvr
        val sidx = sortedCvrIndices[inx]
        val ballot = ballots[sidx]
        val ballotStyle = ballotManifest.getBallotStyleFor(ballot.ballot.ballotStyleId!!)!!
        // does this cvr contribute to one or more contests that need more samples?
        if (contests.any { contestInProgress(it) && ballotStyle.hasContest(it.id) }) {
            // then use it
            sampledIndices.add(sidx)
            ballot.sampled = true
            ballotStyle.contestIds.forEach {
                currentSizes[it] = currentSizes[it]?.plus(1) ?: 1
            }
        }
        inx++
    }
    if (inx == sortedCvrIndices.size) {
        println("ran out of samples!!")
    }
    contests.forEach { contest ->
        contest.availableInSample = currentSizes[contest.id]!!
        if (show) println(" ${contest} availableInSample=${contest.availableInSample}")
    }
    return sampledIndices
}

fun uniformPollingSampling(
    contests: List<ContestUnderAudit>,
    ballots: List<BallotUnderAudit>, // all the ballots available to sample
    samplePctCutoff: Double,
    N: Int,
    roundIdx: Int,
): List<Int> {
    if (ballots.isEmpty()) return emptyList()

    // est = rho / dilutedMargin
    // dilutedMargin = (vw - vl)/ Nc
    // est = rho * Nc / (vw - vl)
    // totalEst = est * N / Nc = rho * N / (vw - vl) = rho / fullyDilutedMargin
    // fullyDilutedMargin = (vw - vl)/ N

    // scale by proportion of ballots that have this contest
    contests.forEach {
        val fac = N / it.Nc.toDouble()
        val est = (it.estSampleSize * fac).toInt()
        val estPct = (it.estSampleSize / it.Nc.toDouble())
        println("  $it: scale=${df(fac)} estTotalNeeded=${est.toInt()}")
        it.estTotalSampleSize = est
        if (estPct > samplePctCutoff) {
            it.done = true
            it.status = TestH0Status.LimitReached
            println("  ***$it estPct $estPct > samplePctCutoff $samplePctCutoff round $roundIdx")
            val minAssert = it.minPollingAssertion()
            if (minAssert != null)
                minAssert.round = roundIdx
        }
    }
    val estTotalSampleSizes = contests.filter { !it.done }.map { it.estTotalSampleSize }
    if (estTotalSampleSizes.isEmpty()) return emptyList()

    // get list of ballot indexes sorted by sampleNum
    val sortedCvrIndices = ballots.indices.sortedBy { ballots[it].sampleNum }

    // take the first estSampleSize of the sorted ballots
    // val simple = roundIdx * N / 10.0
    // val sampledIndices = sortedCvrIndices.take(simple.toInt())
    val sampledIndices = sortedCvrIndices.take(estTotalSampleSizes.max().toInt())

    return sampledIndices
}

private val show = true


