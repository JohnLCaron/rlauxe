package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.roundToInt
import org.cryptobiotic.rlauxe.workflow.AuditRound
import org.cryptobiotic.rlauxe.workflow.BallotOrCvr
import org.cryptobiotic.rlauxe.workflow.ContestRound
import org.cryptobiotic.rlauxe.workflow.RlauxWorkflowProxy

private val debug = false
private val debugConsistent = false
private val debugUniform = true
private val debugSizeNudge = true

/**
 * iterates on createSampleIndices, checking for pct <= auditConfig.samplePctCutoff,
 * removing contests until satisfied. */
fun sample(workflow: RlauxWorkflowProxy, auditRound: AuditRound, previousSamples: Set<Int>, quiet: Boolean): List<Int> {
    val auditConfig = workflow.auditConfig()
    val sortedBorc = workflow.sortedBallotsOrCvrs()

    // count the number of cvrs that have at least one contest under audit.
    // TODO this is wrong for samplePctCutoff, except maybe the first round ??
    val N = if (!auditConfig.hasStyles) sortedBorc.size
                  else sortedBorc.filter { it.hasOneOrMoreContest(auditRound.contestRounds) }.count()

    var sampleIndices: List<Int> = emptyList()
    val contestsNotDone = auditRound.contestRounds.filter { !it.done }.toMutableList()

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
        println("consistentSampling round ${auditRound.roundIdx} auditorSetNewMvrs=${auditRound.auditorWantNewMvrs}")
        val sampleIndices = consistentSampling(auditRound, workflow.sortedBallotsOrCvrs(), previousSamples)
        println(" consistentSamplingSize= ${sampleIndices.size}")
        sampleIndices
    } else {
        if (!quiet) println("\nuniformSampling round ${auditRound.roundIdx}")
        val sampleIndices =
            uniformSampling(auditRound, workflow.sortedBallotsOrCvrs(), previousSamples.size, auditConfig.samplePctCutoff, auditRound.roundIdx)
        if (!quiet) println(" consistentSamplingSize= ${sampleIndices.size}")
        sampleIndices
    }
}

// for audits with hasStyles
fun consistentSampling(
    auditRound: AuditRound,
    sortedBorc: List<BallotOrCvr>,
    previousSamples: Set<Int> = emptySet(),
): List<Int> {
    val contestsNotDone = auditRound.contestRounds.filter { !it.done }
    if (contestsNotDone.isEmpty()) return emptyList()
    if (sortedBorc.isEmpty()) return emptyList()

    // calculate how many samples are wanted for each contest
    val wantSampleSize = wantSampleSize(contestsNotDone, previousSamples, sortedBorc)

    val haveSampleSize = mutableMapOf<Int, Int>() // contestId -> nmvrs in sample
    val haveNewSamples = mutableMapOf<Int, Int>() // contestId -> nmvrs in sample
    fun contestWantsMoreSamples(c: ContestRound): Boolean {
        if (c.auditorWantNewMvrs > 0 && (haveNewSamples[c.id] ?: 0) >= c.auditorWantNewMvrs) return false
        return (haveSampleSize[c.id] ?: 0) < (wantSampleSize[c.id] ?: 0)
    }

    val contestsIncluded = contestsNotDone.filter { it.included }
    val haveActualMvrs = mutableMapOf<Int, Int>() // contestId -> new nmvrs in sample

    // get list of cvr indexes sorted by sampleNum TODO these should already be sorted
    // val sortedBocIndices = ballotOrCvrs.indices.sortedBy { ballotOrCvrs[it].sampleNumber() }

    var newMvrs = 0
    val sampledIndices = mutableListOf<Int>()

    // while we need more samples
    var inx = 0
    while (
        ((auditRound.auditorWantNewMvrs < 0) || (newMvrs < auditRound.auditorWantNewMvrs)) &&
        contestsIncluded.any { contestWantsMoreSamples(it) } &&
        inx < sortedBorc.size) {

        // get the next sorted cvr
        val boc = sortedBorc[inx]
        val sampleIdx = boc.index()
        // does this contribute to one or more contests that need more samples?
        if (contestsIncluded.any { contestRound -> contestWantsMoreSamples(contestRound) && boc.hasContest(contestRound.id) }) {
            // then use it
            sampledIndices.add(sampleIdx)
            if (!previousSamples.contains(sampleIdx)) {
                newMvrs++
            }

            // only if included
            contestsIncluded.forEach { contest ->
                if (boc.hasContest(contest.id)) {
                    haveSampleSize[contest.id] = haveSampleSize[contest.id]?.plus(1) ?: 1
                }
            }
            // track actual for all contests not done
            contestsNotDone.forEach { contest ->
                if (boc.hasContest(contest.id)) {
                    haveActualMvrs[contest.id] = haveActualMvrs[contest.id]?.plus(1) ?: 1
                    if (!previousSamples.contains(sampleIdx))
                        haveNewSamples[contest.id] = haveNewSamples[contest.id]?.plus(1) ?: 1
                }
            }
        }
        inx++
    }
    if (inx > sortedBorc.size) {
        throw RuntimeException("ran out of samples!!")
    }

    if (debugConsistent) println("**consistentSampling haveActualMvrs = $haveActualMvrs, haveNewSamples = $haveNewSamples, newMvrs=$newMvrs")
    val contestIdMap = contestsNotDone.associate { it.id to it }
    contestIdMap.values.forEach { // defaults to 0
        it.actualMvrs = 0
        it.actualNewMvrs = 0
    }
    haveActualMvrs.forEach { (contestId, nmvrs) ->
        contestIdMap[contestId]?.actualMvrs = nmvrs
    }
    haveNewSamples.forEach { (contestId, nnmvrs) ->
        contestIdMap[contestId]?.actualNewMvrs = nnmvrs
    }
    auditRound.nmvrs = sampledIndices.size
    auditRound.newmvrs = newMvrs
    auditRound.sampledIndices = sampledIndices

    return sampledIndices
}

fun wantSampleSize(contestsNotDone: List<ContestRound>, previousSamples: Set<Int>, sortedBorc: List<BallotOrCvr>): Map<Int, Int> {
    // count how many samples each contest already has
    val prevContestCounts = mutableMapOf<ContestRound, Int>()
    contestsNotDone.forEach { prevContestCounts[it] = 0 }
    previousSamples.forEach { sampleIdx ->
        val boc = sortedBorc[sampleIdx] // TODO WRONG!
        contestsNotDone.forEach { contest ->
            if (boc.hasContest(contest.id)) {
                prevContestCounts[contest] = prevContestCounts[contest]?.plus(1) ?: 1
            }
        }
    }
    if (debugConsistent) {
        val contestSampleSizesById = prevContestCounts.entries.map { it.key.id to it.value }.toMap()
        println("**prevContestCounts = ${contestSampleSizesById}")
    }

    // we need that in order to calculate wantedSampleSizes
    val wantSampleSize = prevContestCounts.entries.map { it.key.id to it.key.sampleSize(it.value) }.toMap()
    if (debugConsistent) println("**wantSampleSize = $wantSampleSize")

    return wantSampleSize
}

// for audits with !hasStyles
fun uniformSampling(
    auditRound: AuditRound,
    sortedBorc: List<BallotOrCvr>,
    prevSampleSize: Int,
    samplePctCutoff: Double,  // TODO
    roundIdx: Int,
): List<Int> {
    val contestsNotDone = auditRound.contestRounds.filter { !it.done }
    if (contestsNotDone.isEmpty()) return emptyList()
    if (sortedBorc.isEmpty()) return emptyList()
    val Nb = sortedBorc.size // TODO is it always all ballots ?? could it be contest specific ??

    // set all sampled to false, so each round is independent TODO not needed
    // ballotOrCvrs.forEach{ it.setIsSampled(false)}

    // scale by proportion of ballots that have this contest
    contestsNotDone.forEach { contestUA ->
        val fac = Nb / contestUA.Nc.toDouble()
        val estWithFactor = roundToInt((contestUA.estSampleSize * fac))
        contestUA.estSampleSizeNoStyles = estWithFactor
        val estPct = estWithFactor / Nb.toDouble()
        if (estPct > samplePctCutoff) {
            if (debugUniform) println("uniformSampling samplePctCutoff: $contestUA estPct $estPct > $samplePctCutoff round $roundIdx")
            contestUA.done = true // TODO dont do this here?
            contestUA.status = TestH0Status.FailMaxSamplesAllowed
        }
    }
    val estTotalSampleSizes = contestsNotDone.filter { !it.done }.map { it.estSampleSizeNoStyles }
    if (estTotalSampleSizes.isEmpty()) return emptyList()
    var nmvrs = estTotalSampleSizes.max()

    if (auditRound.roundIdx > 2) {
        val prevNudged = (1.25 * prevSampleSize).toInt()
        if (prevNudged > nmvrs) {
            if (debugSizeNudge) println(" ** uniformSampling prevNudged $prevNudged > $nmvrs; round=${auditRound.roundIdx}")
            nmvrs = prevNudged
        }
    }

    // get list of ballot indexes sorted by sampleNum
    val sortedCvrIndices = sortedBorc.map { it.index() }

    // take the first nmvrs of the sorted ballots
    return sortedCvrIndices.take(nmvrs)
}


