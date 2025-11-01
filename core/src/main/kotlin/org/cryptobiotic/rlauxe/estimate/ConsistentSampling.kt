package org.cryptobiotic.rlauxe.estimate

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.roundToClosest

private val debugConsistent = false
private val debugUniform = false
private val debugSizeNudge = true
private val logger = KotlinLogging.logger("ConsistentSampling")

/**
 * Select the samples to audit.
 * 2. _Choosing sample sizes_: the Auditor decides which contests and how many samples will be audited.
 * 3. _Random sampling_: The actual ballots to be sampled are selected randomly based on a carefully chosen random seed.
 * Iterates on createSampleIndices, checking for auditRound.sampleNumbers.size <= auditConfig.sampleLimit, removing contests until satisfied.
 * Also called from rlauxe_viewer
 */
fun sampleWithContestCutoff(
    auditConfig: AuditConfig,
    mvrManager : MvrManager,
    auditRound: AuditRound,
    previousSamples: Set<Long>,
    quiet: Boolean
) {
    val stopwatch = Stopwatch()
    val contestsNotDone = auditRound.contestRounds.filter { !it.done }.toMutableList()

    while (contestsNotDone.isNotEmpty()) {
        sample(auditConfig, mvrManager, auditRound, previousSamples, quiet = quiet)

        //// the rest of this implements contestSampleCutoff
        if (!auditConfig.removeCutoffContests || auditConfig.contestSampleCutoff == null || auditRound.samplePrns.size <= auditConfig.contestSampleCutoff) {
            break
        }

        // find the contest with the largest estimation size eligible for removal, remove it
        val maxEstimation = contestsNotDone.maxOf { it.estSampleSizeEligibleForRemoval() }
        val maxContest = contestsNotDone.first { it.estSampleSizeEligibleForRemoval() == maxEstimation }
        logger.warn{" ***too many samples= ${maxEstimation}, remove contest ${maxContest.id} with status FailMaxSamplesAllowed"}

        /// remove maxContest from the audit
        // information we want in the persisted record
        maxContest.done = true
        maxContest.status = TestH0Status.FailMaxSamplesAllowed
        contestsNotDone.remove(maxContest)
    }
    logger.info{" sample() success on ${auditRound.contestRounds.count { !it.done }} contests: ready to audit; took ${stopwatch}"}
}

/** Choose what cards to sample */
fun sample(
    auditConfig: AuditConfig,
    mvrManager : MvrManager,
    auditRound: AuditRound,
    previousSamples: Set<Long> = emptySet(),
    quiet: Boolean = true
) {
    if (auditConfig.hasStyles) {
        if (!quiet) logger.info{"consistentSampling round ${auditRound.roundIdx} auditorSetNewMvrs=${auditRound.auditorWantNewMvrs}"}
        consistentSampling(auditRound, mvrManager, previousSamples)
        if (!quiet) logger.info{" consistentSamplingSize= ${auditRound.samplePrns.size}"}
    } else {
        if (!quiet) logger.info{"\nuniformSampling round ${auditRound.roundIdx}"}
        uniformSampling(auditRound, mvrManager, previousSamples, auditConfig, auditRound.roundIdx)
        if (!quiet) logger.info{" uniformSamplingSize= ${auditRound.samplePrns.size}"}
    }
}

// From Consistent Sampling with Replacement, Ronald Rivest, August 31, 2018
// for audits with hasStyles = true
fun consistentSampling(
    auditRound: AuditRound,
    mvrManager: MvrManager,
    previousSamples: Set<Long> = emptySet(),
) {
    val contestsNotDone = auditRound.contestRounds.filter { !it.done }
    if (contestsNotDone.isEmpty()) return

    // calculate how many samples are wanted for each contest
    val wantSampleSize = wantSampleSize(contestsNotDone, previousSamples, mvrManager.sortedCards().iterator())
    require(wantSampleSize.values.all { it >= 0 }) { "wantSampleSize must be >= 0" }

    val haveSampleSize = mutableMapOf<Int, Int>() // contestId -> nmvrs in sample
    val haveNewSamples = mutableMapOf<Int, Int>() // contestId -> nmvrs in sample
    fun contestWantsMoreSamples(c: ContestRound): Boolean {
        if (c.auditorWantNewMvrs > 0 && (haveNewSamples[c.id] ?: 0) >= c.auditorWantNewMvrs) return false
        return (haveSampleSize[c.id] ?: 0) < (wantSampleSize[c.id] ?: 0)
    }

    val contestsIncluded = contestsNotDone.filter { it.included }
    val haveActualMvrs = mutableMapOf<Int, Int>() // contestId -> new nmvrs in sample

    var newMvrs = 0
    val sampledCards = mutableListOf<AuditableCard>()

    // the cards come in order of the prn, aka "ticket number" (Rivest)
    var countSamples = 0
    val sortedBorcIter = mvrManager.sortedCards().iterator()
    while (
        ((auditRound.auditorWantNewMvrs < 0) || (newMvrs < auditRound.auditorWantNewMvrs)) &&
            contestsIncluded.any { contestWantsMoreSamples(it) } &&
            sortedBorcIter.hasNext()) {

        // get the next card in sorted order
        val boc = sortedBorcIter.next()
        // does this contribute to one or more contests that need more samples?
        if (contestsIncluded.any { contestRound -> contestWantsMoreSamples(contestRound) && boc.hasContest(contestRound.id) }) {
            // then use it
            sampledCards.add(boc)
            if (!previousSamples.contains(boc.prn)) {
                newMvrs++
            }
            // count only if included
            contestsIncluded.forEach { contest ->
                if (boc.hasContest(contest.id)) {
                    haveSampleSize[contest.id] = haveSampleSize[contest.id]?.plus(1) ?: 1
                }
            }
            // track actual for all contests not done
            contestsNotDone.forEach { contest ->
                if (boc.hasContest(contest.id)) {
                    haveActualMvrs[contest.id] = haveActualMvrs[contest.id]?.plus(1) ?: 1
                    if (!previousSamples.contains(boc.prn))
                        haveNewSamples[contest.id] = haveNewSamples[contest.id]?.plus(1) ?: 1
                }
            }
        }
        countSamples++
    }

    if (debugConsistent) logger.info{"**consistentSampling haveActualMvrs = $haveActualMvrs, haveNewSamples = $haveNewSamples, newMvrs=$newMvrs"}
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
    // set the results into the auditRound direclty
    auditRound.nmvrs = sampledCards.size
    auditRound.newmvrs = newMvrs
    auditRound.samplePrns = sampledCards.map { it.prn }
    auditRound.sampledBorc = sampledCards
}

// for audits with hasStyles = false
fun uniformSampling(
    auditRound: AuditRound,
    mvrManager: MvrManager,
    previousSamples: Set<Long>,
    auditConfig: AuditConfig,
    roundIdx: Int,
) {
    val contestsNotDone = auditRound.contestRounds.filter { !it.done }
    if (contestsNotDone.isEmpty()) return

    // scale by proportion of ballots that have this contest
    contestsNotDone.forEach { contestRound ->
        val Nb = mvrManager.Nballots(contestRound.contestUA)
        val fac = Nb / contestRound.Nc.toDouble()
        val estWithFactor = roundToClosest((contestRound.estSampleSize * fac))
        contestRound.estSampleSizeNoStyles = estWithFactor
        // val estPct = estWithFactor / Nb.toDouble()
        if (auditConfig.removeCutoffContests && auditConfig.contestSampleCutoff != null && estWithFactor > auditConfig.contestSampleCutoff) {
            if (debugUniform) logger.info{"uniformSampling contestSampleCutoff for contest ${contestRound.id} estWithFactor $estWithFactor > ${auditConfig.contestSampleCutoff} round $roundIdx"}
            contestRound.done = true // TODO dont do this here?
            contestRound.status = TestH0Status.FailMaxSamplesAllowed
        }
    }
    val estTotalSampleSizes = contestsNotDone.filter { !it.done }.map { it.estSampleSizeNoStyles }
    if (estTotalSampleSizes.isEmpty()) return
    var nmvrs = estTotalSampleSizes.max()

    if (auditRound.roundIdx > 2) { // TODO check this
        val prevSampleSize = previousSamples.size
        val prevNudged = (1.25 * prevSampleSize).toInt()
        if (prevNudged > nmvrs) {
            if (debugSizeNudge) logger.info{" ** uniformSampling prevNudged $prevNudged > $nmvrs; round=${auditRound.roundIdx}"}
            nmvrs = prevNudged
        }
    }

    // take the first nmvrs of the sorted ballots
    val sampledCards = mvrManager.takeFirst(nmvrs)
    val newMvrs = sampledCards.count { !previousSamples.contains(it.prn) }

    // set the results into the auditRound directly
    auditRound.nmvrs = nmvrs
    auditRound.newmvrs = newMvrs
    auditRound.samplePrns = sampledCards.map { it.prn }
    auditRound.sampledBorc = sampledCards
}


