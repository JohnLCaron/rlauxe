package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.roundToInt
import org.cryptobiotic.rlauxe.workflow.BallotOrCvr
import org.cryptobiotic.rlauxe.workflow.RlauxWorkflowIF

private val debug = false

/** must have contest.estSampleSize set. must have borc.sampleNumber assigned. */
fun sample(workflow: RlauxWorkflowIF, roundIdx: Int, quiet: Boolean): List<Int> {
    val auditConfig = workflow.auditConfig()
    val borc = workflow.getBallotsOrCvrs()

    // count the number of cvrs that have at least one contest under audit.
    // TODO this is wrong for samplePctCutoff, except maybe the first round ??
    val N = if (!auditConfig.hasStyles) borc.size
                  else borc.filter { it.hasOneOrMoreContest(workflow.getContests()) }.count()

    var sampleIndices: List<Int> = emptyList()
    val contestsNotDone = workflow.getContests().filter { !it.done }.toMutableList()

    while (contestsNotDone.isNotEmpty()) {
        sampleIndices = createSampleIndices(workflow, roundIdx, quiet)
        val pct = sampleIndices.size / N.toDouble()
        if (debug) println(" createSampleIndices size = ${sampleIndices.size} N=$N pct= $pct max=${auditConfig.samplePctCutoff}")
        if (pct <= auditConfig.samplePctCutoff) {
            break
        }
         // find the contest with the largest estimation size, remove it
        val maxEstimation = contestsNotDone.maxOf { it.estSampleSize }
        val maxContest = contestsNotDone.first { it.estSampleSize == maxEstimation }
        println(" ***too many samples, remove contest ${maxContest} with status FailMaxSamplesAllowed")

        // information we want in the persisted record
        val minAssertion = maxContest.minAssertion()!!
        minAssertion.status = TestH0Status.FailMaxSamplesAllowed
        minAssertion.round = roundIdx
        maxContest.done = true
        maxContest.status = TestH0Status.FailMaxSamplesAllowed

        contestsNotDone.remove(maxContest)
        sampleIndices = emptyList() // if theres no more contests, this says were done
    }

    return sampleIndices
}

fun createSampleIndices(workflow: RlauxWorkflowIF, roundIdx: Int, quiet: Boolean): List<Int> {
    val auditConfig = workflow.auditConfig()
    val contestsNotDone = workflow.getContests().filter { !it.done }
    val maxContestSize = contestsNotDone.filter { !it.done }.maxOfOrNull { it.estSampleSize }

    if (contestsNotDone.isEmpty()) return emptyList()

    return if (auditConfig.hasStyles) {
        if (!quiet) println("\nconsistentSampling round $roundIdx")
        val sampleIndices = consistentSampling(contestsNotDone, workflow.getBallotsOrCvrs())
        if (!quiet) println(" maxContestSize=$maxContestSize consistentSamplingSize= ${sampleIndices.size}")
        sampleIndices
    } else {
        if (!quiet) println("\nuniformSampling round $roundIdx")
        val sampleIndices =
            uniformSampling(contestsNotDone, workflow.getBallotsOrCvrs(), auditConfig.samplePctCutoff, roundIdx)
        if (!quiet) println(" maxContestSize=$maxContestSize consistentSamplingSize= ${sampleIndices.size}")
        sampleIndices
    }
}

// for audits with hasStyles
// TODO samplePctCutoff: Double,
fun consistentSampling(
    contests: List<ContestUnderAudit>,
    ballotOrCvrs: List<BallotOrCvr>,
): List<Int> {
    if (ballotOrCvrs.isEmpty()) return emptyList()

    // set all sampled to false, so each round is independent
    ballotOrCvrs.forEach{ it.setIsSampled(false) }

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

// for audits with !hasStyles
fun uniformSampling(
    contests: List<ContestUnderAudit>,
    ballotOrCvrs: List<BallotOrCvr>,
    samplePctCutoff: Double,  // TODO
    roundIdx: Int,
): List<Int> {
    if (ballotOrCvrs.isEmpty()) return emptyList()
    val Nb = ballotOrCvrs.size // TODO is it always all ballots ?? could it be contest specific ??

    // set all sampled to false, so each round is independent TODO not needed
    ballotOrCvrs.forEach{ it.setIsSampled(false)}

    // scale by proportion of ballots that have this contest
    contests.forEach { contestUA ->
        val fac = Nb / contestUA.Nc.toDouble()
        val estWithFactor = roundToInt((contestUA.estSampleSize * fac))
        contestUA.estSampleSizeNoStyles = estWithFactor
        val estPct = estWithFactor / Nb.toDouble()
        if (estPct > samplePctCutoff) {
            if (debug) println("uniformSampling samplePctCutoff: $contestUA estPct $estPct > $samplePctCutoff round $roundIdx")
            contestUA.done = true
            contestUA.status = TestH0Status.FailMaxSamplesAllowed
        }
    }
    val estTotalSampleSizes = contests.filter { !it.done }.map { it.estSampleSizeNoStyles }
    if (estTotalSampleSizes.isEmpty()) return emptyList()
    val nmvrs = estTotalSampleSizes.max()

    // get list of ballot indexes sorted by sampleNum
    val sortedCvrIndices = ballotOrCvrs.indices.sortedBy { ballotOrCvrs[it].sampleNumber() }

    // take the first estSampleSize of the sorted ballots
    return sortedCvrIndices.take(nmvrs)
}


