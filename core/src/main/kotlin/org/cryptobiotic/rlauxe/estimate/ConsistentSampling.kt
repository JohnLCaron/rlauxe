package org.cryptobiotic.rlauxe.estimate

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.betting.TestH0Status
import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.strata.Strata
import org.cryptobiotic.rlauxe.strata.setHaveSampleSize
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.CloseableIterable
import kotlin.text.drop

private val verifyMaxIndex = false
private val logger = KotlinLogging.logger("ConsistentSampling")

// called from auditWorkflow.startNewRound
// also called by rlauxe-viewer
fun removeContestsAndSample(
    sampling: ContestSampleControl,
    samplingCards: CloseableIterable<SamplingCardIF>,
    auditRound: AuditRoundIF,
    previousSamples: Set<Long>, // all previous prns ever sampled
) {
    val stopwatch = Stopwatch()

    // remove first "removeMaxContests" - to generate plot comparisions
    val removeMaxContests: Int? = sampling.removeMaxContests()
    if (auditRound.roundIdx == 1 && removeMaxContests != null && removeMaxContests > 0) {
        val sortedByEstMvrs : List<ContestRound> = auditRound.contestRounds.sortedByDescending { it.estMvrs }
        repeat(removeMaxContests) { idx ->
            val maxContest = sortedByEstMvrs[idx]
            maxContest.status = TestH0Status.FailMaxSamplesAllowed
            maxContest.included = false
            maxContest.done = true // this will remove from contestsNotDone
            logger.info{"*** removeMaxContests contest ${maxContest.id} estimated Mvrs ${maxContest.estMvrs}"}
        }
    }

    // var lastCardsUsed : List<AuditableCardIF> = emptyList()
    val contestsNotDone = auditRound.contestRounds.filter { !it.done }.toMutableList()
    while (contestsNotDone.isNotEmpty()) {
        // create a strawman sample
        chooseSamples(sampling, auditRound, samplingCards, previousSamples)

        // enforce sample limits
        val removeContests = checkSampleLimits(sampling, auditRound, contestsNotDone)
        if (removeContests.isEmpty()) break
        logger.info{"*** remove ${removeContests.size} contests and resample"}
        removeContests.forEach { contestsNotDone.remove(it) }
        // do it again
    }

    logger.debug{"sampleAndRemoveContests success on ${auditRound.contestRounds.count { !it.done }} contests: round ${auditRound.roundIdx} took ${stopwatch}"}
}

// TODO find balance with allowing EA to control what gets removed
private fun checkSampleLimits(
    sampleControl: ContestSampleControl,
    auditRound: AuditRoundIF,
    contestsNotDone: MutableList<ContestRound>,
): List<ContestRound> {
    val removeContests = mutableListOf<ContestRound>()

    // limit contest samples to maxSamplePct
    if (sampleControl.maxSamplePct > 0.0) {
        contestsNotDone.filter{ it.status == TestH0Status.InProgress }.forEach { contestRound ->
            val pct = contestRound.estMvrs / contestRound.contestUA.population().toDouble()
            if (pct > sampleControl.maxSamplePct) {
                contestRound.status = TestH0Status.FailMaxSamplesAllowed
                contestRound.included = false
                contestRound.done = true
                logger.info{"*** remove contest ${contestRound.id} with status FailMaxSamplesAllowed: maxSamplePct ${pct} > ${sampleControl.maxSamplePct}"}
                removeContests.add(contestRound)
            }
        }
    }

    // limit each contest sample to be less than contestSampleCutoff
    if (sampleControl.contestSampleCutoff != null && sampleControl.contestSampleCutoff > 0) {
        contestsNotDone.filter{ it.status == TestH0Status.InProgress }.forEach { contestRound ->
            if (contestRound.estMvrs > sampleControl.contestSampleCutoff) {
                contestRound.status = TestH0Status.FailMaxSamplesAllowed
                contestRound.included = false
                contestRound.done = true
                logger.info{" *** too many samples for contest ${contestRound.id}: ${contestRound.estMvrs} > ${sampleControl.contestSampleCutoff}, "}
                removeContests.add(contestRound)
            }
        }
    }

    // if above checks remove contests, rerun consistentSampling before checking for auditSampleCutoff
    if (removeContests.isEmpty()) {
        // check if overall auditSampleCutoff is exceeded
        if (sampleControl.auditSampleCutoff != null && auditRound.samplePrns.size > sampleControl.auditSampleCutoff) {
            // find the contest with the largest estimation size eligible for removal, remove it
            val maxEstimation = contestsNotDone.maxOf { it.estMvrs }
            val maxContest = contestsNotDone.first { it.estMvrs == maxEstimation }

            /// remove contest with largest estimation from the audit
            maxContest.status = TestH0Status.FailMaxSamplesAllowed
            maxContest.included = false
            maxContest.done = true
            logger.info {
                "*** too many samples in audit: ${auditRound.samplePrns.size} > ${sampleControl.auditSampleCutoff}, " +
                        "remove contest ${maxContest.id} with largest sample size = ${maxContest.estMvrs}; set to FailMaxSamplesAllowed"
            }
            removeContests.add(maxContest)
        }
    }

    return removeContests
}

fun chooseSamples(
    sampling: ContestSampleControl,
    auditRound: AuditRoundIF,
    samplingCards: CloseableIterable<SamplingCardIF>,
    previousSamples: Set<Long> = emptySet(), // all previous prns ever sampled
): List<Long> {
    return if (sampling.sampling == Sampling.uniform)
        uniformSampling(auditRound, samplingCards, previousSamples)
    else
        consistentSampling(auditRound, samplingCards, previousSamples)
}

// From Consistent Sampling with Replacement, Ronald Rivest, August 31, 2018
// main side effects:
//    auditRound.nmvrs = sampledCards.size
//    auditRound.newmvrs = newMvrs
//    auditRound.samplePrns = sampledCards.map { it.prn }
//    contestRound.haveSampleSize = contest cards in sample
//    contestRound.haveNewSampleSize = new contest cards in sample
// does no disk writing

fun consistentSampling(
    auditRound: AuditRoundIF,
    samplingCards: CloseableIterable<SamplingCardIF>,
    previousSamples: Set<Long> = emptySet(),
): List<Long>  // debugging
{
    val stopwatch = Stopwatch()
    val skippedContests = mutableListOf<ContestRound>()

    // included means these are included in the sampling
    // we still want to estimate risks for not-done contests
    val contestsNotDone = auditRound.contestRounds.filter { !it.done }.toMutableList()
    val contestsIncluded = auditRound.contestRounds.filter { !it.done && it.included }
    if (contestsIncluded.isEmpty()) return emptyList()

    // how many samples are wanted for each contest
    // contestsIncluded.forEach { if (it.auditorWantNewMvrs != null && it.auditorWantNewMvrs!! < 0) it.auditorWantNewMvrs = null } // TODO fix in viewerr
    // val wantSampleSize = contestsIncluded.associate { it.id to (it.auditorWantNewMvrs ?: it.estMvrs) }
    // require(wantSampleSize.values.all { it >= 0 }) { "wantSampleSize must be >= 0" }

    var newMvrs = 0 // count when this card not in previous samples
    auditRound.contestRounds.forEach {
        it.haveSampleSize = 0
        it.haveNewSampleSize = 0
    }

    val sampledPrns = mutableListOf<Long>()
    var cardIndex = 0  // track maximum index (not done yet)

    var maxNewSamples = auditRound.auditorMaxNewMvrs // TODO test
    if (maxNewSamples == null || maxNewSamples < 0) maxNewSamples = Int.MAX_VALUE

    val samplingCardIter = samplingCards.iterator()
    while (
        samplingCardIter.hasNext() &&
        sampledPrns.size < maxNewSamples &&
        contestsIncluded.any { it.haveSampleSize < it.estMvrs }
    ) {
        // get the next card in sorted order
        val card = samplingCardIter.next()

        // do we want it ?
        var include = false
        contestsIncluded.forEach { contest ->
            // does this contest want this card ?
            if (card.hasContest(contest.id) && contest.haveSampleSize < contest.estMvrs) {
                include = true
            }
        }

        if (include) {
            sampledPrns.add(card.prn())
            //   If you assume that previousSamples had all contests audited, then previousSamples reflects ballots already audited,
            //   (even if not used for this contest), so you dont need to sample them again, so theyre not new.
            // TODO do all at once at the end for speed ??
            if (!previousSamples.contains(card.prn()))
                newMvrs++
        }

        // track how many contiguous mvrs each not-done contest has
        contestsNotDone.forEach { contestRound ->
            if (card.hasContest(contestRound.id)) {
                if (include) {
                    contestRound.haveSampleSize++
                    // TODO do all at once at the end for speed ??
                    if (!previousSamples.contains(card.prn())) {
                        contestRound.haveNewSampleSize++
                    }
                    // ok to use if we havent skipped any cards for this contest in its sequence
                    contestRound.maxSampleAllowed = sampledPrns.size
                } else {
                    // if card has contest but its not included in the sample, then continuity has been broken
                    skippedContests.add(contestRound)
                }
            }
        }
        // remove from contestsNotDone for speed
        if (skippedContests.isNotEmpty()) {
            contestsNotDone.removeAll(skippedContests)
            skippedContests.clear() // reuse for speed
        }

        cardIndex++
    }

    // set the results into the auditRound direclty
    auditRound.nmvrs = sampledPrns.size
    auditRound.newmvrs = newMvrs
    auditRound.samplePrns = sampledPrns

    logger.info{" consistentSampling read $cardIndex and chose ${sampledPrns.size} cards; took $stopwatch"}
    return sampledPrns
}

fun uniformSampling(
    auditRound: AuditRoundIF,
    samplingCards: CloseableIterable<SamplingCardIF>,
    previousSamples: Set<Long> = emptySet(), // TODO
): List<Long>  // debugging
{
    val stopwatch = Stopwatch()
    if (auditRound.countyStrata == null)  return emptyList()
    val countyStrataWant: Map<String, Strata> = auditRound.countyStrata!!.associateBy { it.strataName }
    if (countyStrataWant.isEmpty()) return emptyList()

    // included means these are included in the sampling
    // we still want to estimate risks for not-done contests
    //val contestsNotDone = auditRound.contestRounds.filter { !it.done }.toMutableList()
    //val contestsIncluded = auditRound.contestRounds.filter { !it.done && it.included }
    //if (contestsIncluded.isEmpty()) return emptyList()

    var newMvrs = 0 // TODO count when this card not in previous samples
    auditRound.contestRounds.forEach {
        it.haveSampleSize = 0
        it.haveNewSampleSize = 0
    }

    val wantFromPools = countyStrataWant.mapValues { it.value.nmvrs }
    val haveFromPools = mutableMapOf<String, Int>()
    val sampledPrns = mutableListOf<Long>()
    var cardIndex = 0  // track maximum index (not done yet)

    var maxNewSamples = auditRound.auditorMaxNewMvrs // TODO test
    if (maxNewSamples == null || maxNewSamples < 0) maxNewSamples = Int.MAX_VALUE
    val useAll = auditRound.auditorMaxNewMvrs != null
    val samplingCardIter = samplingCards.iterator()
    while (
        samplingCardIter.hasNext() &&
        sampledPrns.size < maxNewSamples &&
        wantFromPools.any { it.value > (haveFromPools[it.key] ?: 0) } // have < want
    ) {
        // get the next card in sorted order
        val card = samplingCardIter.next()
        val countyName = card.poolName()

        // do we want it ?
        val haveFromPool = haveFromPools.getOrDefault(countyName, 0)
        val include = useAll || (haveFromPool < (wantFromPools[countyName] ?: 0))  // have < want

        if (include) {
            haveFromPools[countyName] = haveFromPool + 1
            sampledPrns.add(card.prn())
        }
        cardIndex++

        // debug
        if (cardIndex % 5000 == 0) {
            val need = mutableMapOf<String, Int>()
            wantFromPools.forEach { (name, want) ->
                val have = haveFromPools[countyName] ?: 0
                val still = want - have
                if (still > 0) need[name] = still
            }
            logger.info { " after $cardIndex cards, have ${sampledPrns.size} and still need = $need"}
        }
    }

    // set the results into the contestRound
    val countyStrata = mutableMapOf<String, Strata>()
    countyStrataWant.forEach { (name, strata) ->
        val have = haveFromPools[name] ?: 0
        countyStrata[name] = Strata(name, have, strata.population)
    }
    setHaveSampleSize(auditRound.contestRounds.filter { !it.done }, countyStrata, useAll)

    // set the results into the auditRound directly
    auditRound.nmvrs = sampledPrns.size
    auditRound.newmvrs = newMvrs
    auditRound.samplePrns = sampledPrns

    logger.info{" uniformSampling read $cardIndex and chose ${sampledPrns.size} cards; took $stopwatch"}
    return sampledPrns
}

fun counties(contestUA: ContestWithAssertions): List<String> {
    val CORLAcounties = contestUA.contest.info().metadata.get("CORLAcounties")
    if (CORLAcounties == null) return emptyList()
    val stripped = CORLAcounties.drop(1).dropLast(1)
    return stripped.split(",".toRegex()).dropLastWhile { it.isEmpty() }
}
