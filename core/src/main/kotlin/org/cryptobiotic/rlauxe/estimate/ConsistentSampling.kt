package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.roundToInt
import org.cryptobiotic.rlauxe.workflow.AuditRound
import org.cryptobiotic.rlauxe.workflow.BallotOrCvr
import org.cryptobiotic.rlauxe.workflow.ContestRound
import org.cryptobiotic.rlauxe.workflow.RlauxWorkflowProxy

private val debug = false

/**
 * iterates on createSampleIndices, checking for pct <= auditConfig.samplePctCutoff,
 * removing contests until satisfied. */
fun sample(workflow: RlauxWorkflowProxy, auditRound: AuditRound, previousSamples: Set<Int>, quiet: Boolean): List<Int> {
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
        sampleIndices = createSampleIndices(workflow, auditRound, previousSamples, quiet = quiet)

        // the rest of this implements samplePctCutoff TODO refactor this
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
        /* val minAssertion = maxContest.minAssertion()
        if (minAssertion != null) {
            minAssertion.status = TestH0Status.FailMaxSamplesAllowed
            minAssertion.round = roundIdx
        } */
        maxContest.done = true
        maxContest.status = TestH0Status.FailMaxSamplesAllowed

        contestsNotDone.remove(maxContest)
        sampleIndices = emptyList() // if theres no more contests, this says were done
    }

    return sampleIndices
}

/** must have contest.estSampleSize set. must have borc.sampleNumber assigned. */
fun createSampleIndices(
    workflow: RlauxWorkflowProxy,
    auditRound: AuditRound,
    previousSamples: Set<Int> = emptySet(),
    quiet: Boolean = true
): List<Int> {

    val auditConfig = workflow.auditConfig()
    return if (auditConfig.hasStyles) {
        println("consistentSampling round ${auditRound.roundIdx} auditorSetNewMvrs=${auditRound.auditorSetNewMvrs}")
        val sampleIndices = consistentSampling(auditRound, workflow.getBallotsOrCvrs(), previousSamples)
        println(" consistentSamplingSize= ${sampleIndices.size}")
        sampleIndices
    } else {
        if (!quiet) println("\nuniformSampling round ${auditRound.roundIdx}")
        val sampleIndices =
            uniformSampling(auditRound, workflow.getBallotsOrCvrs(), auditConfig.samplePctCutoff, auditRound.roundIdx)
        if (!quiet) println(" consistentSamplingSize= ${sampleIndices.size}")
        sampleIndices
    }
}

// for audits with hasStyles
fun consistentSampling(
    auditRound: AuditRound,
    ballotOrCvrs: List<BallotOrCvr>,
    previousSamples: Set<Int> = emptySet(),
): List<Int> {
    val contestsNotDone = auditRound.contests.filter { !it.done }
    if (contestsNotDone.isEmpty()) return emptyList()
    if (ballotOrCvrs.isEmpty()) return emptyList()

    // set all sampled to false, so each round is independent
    // ballotOrCvrs.forEach{ it.setIsSampled(false) } // cvrs arent serialized.

    val contestsIncluded = contestsNotDone.filter { it.included }

    val contestActualMvrs = mutableMapOf<Int, Int>() // contestId -> new nmvrs in sample
    val contestActualNewMvrs = mutableMapOf<Int, Int>() // contestId -> new nmvrs in sample

    val contestSamples = mutableMapOf<Int, Int>() // contestId -> nmvrs in sample
    fun contestWantsMoreSamples(c: ContestRound) = (contestSamples[c.id] ?: 0) < c.estSampleSize

    // get list of cvr indexes sorted by sampleNum
    val sortedBocIndices = ballotOrCvrs.indices.sortedBy { ballotOrCvrs[it].sampleNumber() }

    var newMvrs = 0
    val sampledIndices = mutableListOf<Int>()
    var inx = 0
    // while we need more samples
    while (
        ((auditRound.auditorSetNewMvrs < 0) || (newMvrs < auditRound.auditorSetNewMvrs)) &&
        contestsIncluded.any { contestWantsMoreSamples(it) } &&
        inx < sortedBocIndices.size) {

        // get the next sorted cvr
        val sampleIdx = sortedBocIndices[inx]
        val boc = ballotOrCvrs[sampleIdx]
        // does this contribute to one or more contests that need more samples?
        if (contestsIncluded.any { contestRound -> contestWantsMoreSamples(contestRound) && boc.hasContest(contestRound.id) }) {
            // then use it
            sampledIndices.add(sampleIdx)
            if (!previousSamples.contains(sampleIdx)) newMvrs++
            boc.setIsSampled(true) // not needed?

            // only if included
            contestsIncluded.forEach { contest ->
                if (boc.hasContest(contest.id)) {
                    contestSamples[contest.id] = contestSamples[contest.id]?.plus(1) ?: 1
                }
            }
            // track actual for all contests not donw
            contestsNotDone.forEach { contest ->
                if (boc.hasContest(contest.id)) {
                    contestActualMvrs[contest.id] = contestActualMvrs[contest.id]?.plus(1) ?: 1
                    if (!previousSamples.contains(sampleIdx))
                        contestActualNewMvrs[contest.id] = contestActualNewMvrs[contest.id]?.plus(1) ?: 1
                }
            }
        }
        inx++
    }
    if (inx > sortedBocIndices.size) {
        throw RuntimeException("ran out of samples!!")
    }

    println("**consistentSampling contestActualMvrs = $contestActualMvrs, contestActualNewMvrs = $contestActualNewMvrs, newMvrs=$newMvrs")
    val contestIdMap = contestsNotDone.associate { it.id to it }
    contestIdMap.values.forEach { // defaults to 0
        it.actualMvrs = 0
        it.actualNewMvrs = 0
    }
    contestActualMvrs.forEach { (contestId, nmvrs) ->
        contestIdMap[contestId]?.actualMvrs = nmvrs
    }
    contestActualNewMvrs.forEach { (contestId, nnmvrs) ->
        contestIdMap[contestId]?.actualNewMvrs = nnmvrs
    }
    auditRound.nmvrs = sampledIndices.size
    auditRound.newmvrs = newMvrs
    auditRound.sampledIndices = sampledIndices

    return sampledIndices
}

// for audits with !hasStyles
fun uniformSampling(
    auditRound: AuditRound,
    ballotOrCvrs: List<BallotOrCvr>,
    samplePctCutoff: Double,  // TODO
    roundIdx: Int,
): List<Int> {
    val contestsNotDone = auditRound.contests.filter { !it.done }
    if (contestsNotDone.isEmpty()) return emptyList()
    if (ballotOrCvrs.isEmpty()) return emptyList()
    val Nb = ballotOrCvrs.size // TODO is it always all ballots ?? could it be contest specific ??

    // set all sampled to false, so each round is independent TODO not needed
    ballotOrCvrs.forEach{ it.setIsSampled(false)}

    // TODO included

    // scale by proportion of ballots that have this contest
    contestsNotDone.forEach { contestUA ->
        val fac = Nb / contestUA.Nc.toDouble()
        val estWithFactor = roundToInt((contestUA.estSampleSize * fac))
        contestUA.estSampleSizeNoStyles = estWithFactor
        val estPct = estWithFactor / Nb.toDouble()
        if (estPct > samplePctCutoff) {
            if (debug) println("uniformSampling samplePctCutoff: $contestUA estPct $estPct > $samplePctCutoff round $roundIdx")
            contestUA.done = true // TODO dont do this here
            contestUA.status = TestH0Status.FailMaxSamplesAllowed
        }
    }
    val estTotalSampleSizes = contestsNotDone.filter { !it.done }.map { it.estSampleSizeNoStyles }
    if (estTotalSampleSizes.isEmpty()) return emptyList()
    val nmvrs = estTotalSampleSizes.max()

    // get list of ballot indexes sorted by sampleNum
    val sortedCvrIndices = ballotOrCvrs.indices.sortedBy { ballotOrCvrs[it].sampleNumber() }

    // take the first estSampleSize of the sorted ballots
    return sortedCvrIndices.take(nmvrs)
}


