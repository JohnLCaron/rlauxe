package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.roundToInt
import org.cryptobiotic.rlauxe.workflow.AuditRound
import org.cryptobiotic.rlauxe.workflow.BallotOrCvr
import org.cryptobiotic.rlauxe.workflow.ContestRound
import org.cryptobiotic.rlauxe.workflow.RlauxWorkflowProxy

private val debug = false

/** iterates on createSampleIndices, checking for pct <= auditConfig.samplePctCutoff,
 * removing contests until satisfied. */
fun sample(workflow: RlauxWorkflowProxy, auditRound: AuditRound, quiet: Boolean): List<Int> {
    val auditConfig = workflow.auditConfig()
    val borc = workflow.getBallotsOrCvrs()
    val roundIdx = auditRound.roundIdx

    // count the number of cvrs that have at least one contest under audit.
    // TODO this is wrong for samplePctCutoff, except maybe the first round ??
    val N = if (!auditConfig.hasStyles) borc.size
                  else borc.filter { it.hasOneOrMoreContest(auditRound.contests) }.count()

    var sampleIndices: List<Int> = emptyList()
    val contestsNotDone = auditRound.contests.filter { !it.done }.toMutableList()

    while (contestsNotDone.isNotEmpty()) {
        sampleIndices = createSampleIndices(workflow, auditRound, -1, quiet)

        // the rest of this implements samplePctCutoff TODO refactor this
        val pct = sampleIndices.size / N.toDouble()
        if (debug) println(" createSampleIndices size = ${sampleIndices.size} N=$N pct= $pct max=${auditConfig.samplePctCutoff}")
        if (pct <= auditConfig.samplePctCutoff) {
            break
        }
         // find the contest with the largest estimation size, remove it
        val maxEstimation = contestsNotDone.maxOf { it.estMvrs }
        val maxContest = contestsNotDone.first { it.estMvrs == maxEstimation }
        println(" ***too many samples, remove contest ${maxContest} with status FailMaxSamplesAllowed")

        // information we want in the persisted record
        val minAssertion = maxContest.minAssertion()
        minAssertion.status = TestH0Status.FailMaxSamplesAllowed
        minAssertion.round = roundIdx
        maxContest.done = true
        maxContest.status = TestH0Status.FailMaxSamplesAllowed

        contestsNotDone.remove(maxContest)
        sampleIndices = emptyList() // if theres no more contests, this says were done
    }

    return sampleIndices
}

/** must have contest.estSampleSize set. must have borc.sampleNumber assigned. */
fun createSampleIndices(workflow: RlauxWorkflowProxy, auditRound: AuditRound, wantNewMvrs: Int, quiet: Boolean): List<Int> {
    val auditConfig = workflow.auditConfig()
    val contestsNotDone = auditRound.contests.filter { !it.done }
    if (contestsNotDone.isEmpty()) return emptyList()

    val maxContestSize = contestsNotDone.maxOf { it.estMvrs }
    return if (auditConfig.hasStyles) {
        println("consistentSampling round ${auditRound.roundIdx} wantNewMvrs=$wantNewMvrs")
        val sampleIndices = consistentSampling(contestsNotDone, workflow.getBallotsOrCvrs(), wantNewMvrs)
        println(" maxContestSize=$maxContestSize consistentSamplingSize= ${sampleIndices.size}")
        sampleIndices
    } else {
        if (!quiet) println("\nuniformSampling round ${auditRound.roundIdx}")
        val sampleIndices =
            uniformSampling(contestsNotDone, workflow.getBallotsOrCvrs(), auditConfig.samplePctCutoff, auditRound.roundIdx)
        if (!quiet) println(" maxContestSize=$maxContestSize consistentSamplingSize= ${sampleIndices.size}")
        sampleIndices
    }
}

// for audits with hasStyles
// TODO samplePctCutoff: Double,
fun consistentSampling(
    contestsNotDone: List<ContestRound>,
    ballotOrCvrs: List<BallotOrCvr>,
    wantNewMvrs: Int = -1, // target newMvrs may be set manually, else -1
    ): List<Int> {
    if (ballotOrCvrs.isEmpty()) return emptyList()

    // set all sampled to false, so each round is independent
    // ballotOrCvrs.forEach{ it.setIsSampled(false) }

    val contestsIncluded = contestsNotDone.filter { it.included }

    val currentSizes = mutableMapOf<Int, Int>() // contestId -> ncvrs in sample
    fun contestInProgress(c: ContestRound) = (currentSizes[c.id] ?: 0) < c.estMvrs

    // get list of cvr indexes sorted by sampleNum
    val sortedBocIndices = ballotOrCvrs.indices.sortedBy { ballotOrCvrs[it].sampleNumber() }

    var newMvrs = 0
    val sampledIndices = mutableListOf<Int>()
    var inx = 0
    // while we need more samples
    while (
        ((wantNewMvrs < 0) || (newMvrs < wantNewMvrs)) &&
        contestsIncluded.any { contestInProgress(it) } &&
        inx < sortedBocIndices.size) {

        // get the next sorted cvr
        val sidx = sortedBocIndices[inx]
        val boc = ballotOrCvrs[sidx]
        // does this contribute to one or more contests that need more samples?
        if (contestsIncluded.any { contestInProgress(it) && boc.hasContest(it.id) }) {
            // then use it
            sampledIndices.add(sidx)
            if (boc.isSampled()) newMvrs++
            boc.setIsSampled(true)
            contestsIncluded.forEach { contest ->
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
    contests: List<ContestRound>,
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
        val estWithFactor = roundToInt((contestUA.estMvrs * fac))
        contestUA.estSampleSizeNoStyles = estWithFactor
        val estPct = estWithFactor / Nb.toDouble()
        if (estPct > samplePctCutoff) {
            if (debug) println("uniformSampling samplePctCutoff: $contestUA estPct $estPct > $samplePctCutoff round $roundIdx")
            contestUA.done = true // TODO dont do this here
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


