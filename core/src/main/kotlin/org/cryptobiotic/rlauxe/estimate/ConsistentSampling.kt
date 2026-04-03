package org.cryptobiotic.rlauxe.estimate

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.betting.TestH0Status
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.persist.CardManifest
import kotlin.collections.set

private val debugConsistent = false
private val verifyMaxIndex = false
private val logger = KotlinLogging.logger("ConsistentSampling")

// called from auditWorkflow.startNewRound
// also called by rlauxe-viewer
fun removeContestsAndSample(
    sampling: ContestSampleControl,
    sortedManifest: CardManifest,
    auditRound: AuditRoundIF,
    previousSamples: Set<Long>, // all previous prns ever sampled
) {
    val stopwatch = Stopwatch()

    // remove first "removeMaxContests" - to generate plot comparisions
    val removeMaxContests: Int? = sampling.removeMaxContests()
    if (auditRound.roundIdx == 1 && removeMaxContests != null && removeMaxContests > 0) {
        val sortedByMargin : List<ContestRound> = auditRound.contestRounds.sortedByDescending { it.estMvrs }
        repeat(removeMaxContests) { idx ->
            val maxContest = sortedByMargin[idx]
            maxContest.status = TestH0Status.FailMaxSamplesAllowed
            maxContest.included = false
            maxContest.done = true // this will remove from contestsNotDone
            logger.info{"*** removeMaxContests contest ${maxContest.id} estimated Mvrs ${maxContest.estMvrs}"}
        }
    }

    var lastCardsUsed : List<AuditableCard> = emptyList()
    val contestsNotDone = auditRound.contestRounds.filter { !it.done }.toMutableList()
    while (contestsNotDone.isNotEmpty()) {
        // create a strawman sample
        lastCardsUsed = consistentSampling( auditRound, sortedManifest, previousSamples)

        // enforce sample limits
        val removeContests = checkSampleLimits(sampling, auditRound, contestsNotDone)
        if (removeContests.isEmpty()) break
        removeContests.forEach { contestsNotDone.remove(it) }
        // do it again
    }

    if (verifyMaxIndex) { // debugging, probably dont need this anymore
        val countSamples = mutableMapOf<Int, Int>()
        contestsNotDone.forEach { countSamples[it.id] = 0}
        lastCardsUsed.forEach { cardUsed ->
            contestsNotDone.forEach { contest ->
                val count = countSamples[contest.id]!!
                if (cardUsed.hasContest(contest.id) && count < contest.maxSampleAllowed!!) {
                    countSamples[contest.id] = count + 1
                }
            }
        }
        contestsNotDone.forEach {
            println("contest ${it.id} countInSample=${countSamples[it.id]} maxSampleAllowed=${it.maxSampleAllowed} est=${it.estMvrs} estNew=${it.estNewMvrs}")
            require (countSamples[it.id]!! >= it.estMvrs )
        }
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

    // limit contest samples to minRecountMargin, minMargin
    if (sampleControl.minRecountMargin > 0.0 || sampleControl.minMargin > 0.0) {
        contestsNotDone.forEach { contestRound ->
            val contestUA = contestRound.contestUA
            if ((contestUA.minRecountMargin() ?: 0.0) <= sampleControl.minRecountMargin) {
                logger.warn { "*** MinMargin contest ${contestUA.id} recountMargin ${contestUA.minRecountMargin()} <= ${sampleControl.minRecountMargin}" }
                contestUA.preAuditStatus = TestH0Status.MinMargin
            }
            if ((contestUA.minDilutedMargin() ?: 0.0) <= sampleControl.minMargin) {
                logger.warn { "*** MinMargin contest ${contestUA.id} minMargin ${contestUA.minDilutedMargin()} <= ${sampleControl.minMargin}" }
                contestUA.preAuditStatus = TestH0Status.MinMargin
            }
        }
    }

    // limit contest samples to maxSamplePct
    if (sampleControl.maxSamplePct > 0.0) {
        contestsNotDone.forEach { contestRound ->
            val pct = contestRound.estMvrs / contestRound.contestUA.Npop.toDouble()
            if (pct > sampleControl.maxSamplePct) {
                contestRound.status = TestH0Status.FailMaxSamplesAllowed
                contestRound.included = false
                contestRound.done = true
                logger.warn{"*** remove contest ${contestRound.id} with status FailMaxSamplesAllowed: maxSamplePct ${pct} > ${sampleControl.maxSamplePct}"}
                removeContests.add(contestRound)
            }
        }
    }

    // limit each contest sample to be less than contestSampleCutoff
    if (sampleControl.contestSampleCutoff != null && sampleControl.contestSampleCutoff > 0) {
        contestsNotDone.forEach { contestRound ->
            if (contestRound.estMvrs > sampleControl.contestSampleCutoff) {
                contestRound.status = TestH0Status.FailMaxSamplesAllowed
                contestRound.included = false
                contestRound.done = true
                logger.warn{" *** too many samples for contest ${contestRound.id}: ${contestRound.estMvrs} > ${sampleControl.contestSampleCutoff}, "}
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
            logger.warn {
                "*** too many samples in audit: ${auditRound.samplePrns.size} > ${sampleControl.auditSampleCutoff}, " +
                        "remove contest ${maxContest.id} with largest sample size = ${maxContest.estMvrs}; set to FailMaxSamplesAllowed"
            }
            removeContests.add(maxContest)
        }
    }

    return removeContests
}

// From Consistent Sampling with Replacement, Ronald Rivest, August 31, 2018
// main side effects:
//    auditRound.nmvrs = sampledCards.size
//    auditRound.newmvrs = newMvrs
//    auditRound.samplePrns = sampledCards.map { it.prn }
//    contestRound.maxSampleAllowed = sampledCards.size
fun consistentSampling(
    auditRound: AuditRoundIF,
    sortedManifest: CardManifest,
    previousSamples: Set<Long> = emptySet(), // all previous prns ever sampled
): List<AuditableCard>  // debugging
{
    val contestsIncluded = auditRound.contestRounds.filter { !it.done && it.included}
    if (contestsIncluded.isEmpty()) return emptyList()

    // how many samples are wanted for each contest.
    val wantSampleSize = contestsIncluded.associate { it.id to it.estMvrs }
    require(wantSampleSize.values.all { it >= 0 }) { "wantSampleSize must be >= 0" }

    val skippedContests = mutableSetOf<Int>()
    val haveSampleSize = mutableMapOf<Int, Int>() // contestId -> nmvrs in sample
    val haveNewSamples = mutableMapOf<Int, Int>() // contestId -> nmvrs in sample
    var newMvrs = 0 // count when this card not in previous samples

    val sampledCards = mutableListOf<AuditableCard>()
    var cardIndex = 0  // track maximum index (not done yet)

    val sortedCardIter = sortedManifest.cards.iterator()
    while (
        sortedCardIter.hasNext() &&
        contestsIncluded.any { (haveSampleSize[it.id] ?: 0) < (wantSampleSize[it.id] ?: 0) }
    ) {
        // get the next card in sorted order
        val card = sortedCardIter.next()

        // do we want it ?
        var include = false
        contestsIncluded.forEach { contest ->
            // does this contest want this card ?
            if (card.hasContest(contest.id)) {
                if ((haveSampleSize[contest.id] ?: 0) < (wantSampleSize[contest.id] ?: 0)) {
                    include = true
                }
            }
        }

        if (include) {
            sampledCards.add(card)
            //   If you assume that previousSamples had all contests audited, then previousSamples reflects ballots already audited,
            //   (even if not used for this contest), so you dont need to sample them again, so theyre not new.
            if (!previousSamples.contains(card.prn))
                newMvrs++
        }

        // track how many contiguous mvrs each contest has
        contestsIncluded.forEach { contest ->
            if (card.hasContest(contest.id)) {
                if (include && !skippedContests.contains(contest.id)) {
                    haveSampleSize[contest.id] = haveSampleSize[contest.id]?.plus(1) ?: 1
                    if (!previousSamples.contains(card.prn)) {
                        haveNewSamples[contest.id] = haveNewSamples[contest.id]?.plus(1) ?: 1
                    }
                    // ok to use if we havent skipped any cards for this contest in its sequence
                    contest.maxSampleAllowed = sampledCards.size
                } else {
                    // if card has contest but its not included in the sample, then continuity has been broken
                    skippedContests.add(contest.id)
                }
            }
        }

        cardIndex++
    }

    // TODO why would this happen ??
    val wantMore = contestsIncluded.any { (haveSampleSize[it.id] ?: 0) < (wantSampleSize[it.id] ?: 0) }
    if (wantMore) {
        contestsIncluded.forEach {
            if ((haveSampleSize[it.id] ?: 0) < (wantSampleSize[it.id] ?: 0))
                logger.warn { "contest ${it.id}:  (have) ${(haveSampleSize[it.id] ?: 0)} < ${(wantSampleSize[it.id] ?: 0)} (want)" }
        }
    }

    if (debugConsistent) logger.info{"**consistentSampling haveSampleSize = $haveSampleSize, haveNewSamples = $haveNewSamples, newMvrs=$newMvrs"}

    // set the results into the auditRound direclty
    auditRound.nmvrs = sampledCards.size
    auditRound.newmvrs = newMvrs
    auditRound.samplePrns = sampledCards.map { it.prn }
    return sampledCards
}

