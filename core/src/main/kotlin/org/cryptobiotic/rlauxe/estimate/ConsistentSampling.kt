package org.cryptobiotic.rlauxe.estimate

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.betting.TestH0Status
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.workflow.MvrManager

private val debugConsistent = false
private val logger = KotlinLogging.logger("ConsistentSampling")

// called from auditWorkflow.startNewRound
// also called by rlauxe-viewer
fun sampleWithContestCutoff(
    config: AuditConfig,
    mvrManager : MvrManager,
    auditRound: AuditRoundIF,
    previousSamples: Set<Long>,
    quiet: Boolean
) {
    val stopwatch = Stopwatch()
    val contestsNotDone = auditRound.contestRounds.filter { !it.done }.toMutableList()

    while (contestsNotDone.isNotEmpty()) {
        consistentSampling( auditRound, mvrManager, previousSamples)

        //// the rest of this implements contestSampleCutoff
        if (!config.removeCutoffContests || config.contestSampleCutoff == null || auditRound.samplePrns.size <= config.contestSampleCutoff) {
            break
        }

        // TODO test
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
    logger.debug{"sampleWithContestCutoff success on ${auditRound.contestRounds.count { !it.done }} contests: round ${auditRound.roundIdx} took ${stopwatch}"}
}

// From Consistent Sampling with Replacement, Ronald Rivest, August 31, 2018
// main side effects:
//    auditRound.nmvrs = sampledCards.size
//    auditRound.newmvrs = newMvrs
//    auditRound.samplePrns = sampledCards.map { it.prn }
//    contestRound.maxSampleAllowed = sampledCards.size
fun consistentSampling(
    auditRound: AuditRoundIF,
    mvrManager: MvrManager,
    previousSamples: Set<Long> = emptySet(),
) {
    val contestsIncluded = auditRound.contestRounds.filter { !it.done && it.included}
    if (contestsIncluded.isEmpty()) return

    // calculate how many samples are wanted for each contest.
    // TODO was val wantSampleSizeMap = wantSampleSize(contestsNotDone, previousSamples, mvrManager.sortedCards().iterator())
    val wantSampleSize = wantSampleSizeSimple(contestsIncluded)
    require(wantSampleSize.values.all { it >= 0 }) { "wantSampleSize must be >= 0" }

    val skippedContests = mutableSetOf<Int>()
    val haveSampleSize = mutableMapOf<Int, Int>() // contestId -> nmvrs in sample
    val haveNewSamples = mutableMapOf<Int, Int>() // contestId -> nmvrs in sample
    var newMvrs = 0 // count when this card not in previous samples

    val sampledCards = mutableListOf<AuditableCard>()
    var cardIndex = 0  // track maximum index (not done yet)

    val sortedCardIter = mvrManager.sortedCards().iterator()
    while (
        sortedCardIter.hasNext() &&
        // ((auditRound.auditorWantNewMvrs < 0) || (newMvrs < auditRound.auditorWantNewMvrs)) && // TODO REDO or delete?
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
            } // has contest
        }

        if (include) {
            sampledCards.add(card)
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
}

// try running without complexity
private fun wantSampleSizeSimple(contestsNotDone: List<ContestRound>): Map<Int, Int> {
     return contestsNotDone.associate { it.id to it.estMvrs }
}


////////////////////////////////////////////////////////////////////////////
//val minSamples = -ln(.05) / ln(2 * minAssorter.noerror())

// (1 - lam * noerror)^n < alpha
// n * ln(1 - maxLam * noerror) < ln(alpha)

// ttj is how much you win or lose
// ttj = 1 + lamj * (xj - mj)
// ttj = 1 + lamj * (noerror - mj)
// ttj = 1 + 2 * noerror - 1        // lamj ~ 2, mj ~ 1/2
// ttj = 2 * noerror

// (2 * noerror)^n > 1/alpha
// n * log(2 * noerror) > -log(alpha)
// n ~ -log(alpha) / log(2 * noerror)

// how about when lamj = maxBet < 2 ?
// maxBet = maxRisk / mj

// ttj = 1 + lamj * (noerror - mj)
// ttj = 1 + maxRisk / mj * (noerror - mj)
// ttj = 1 + maxRisk * noerror / mj - maxRisk
// ttj = 1 - maxRisk + 2 * maxRisk * noerror       // mj ~ 1/2

// (1 - maxRisk + 2 * maxRisk * noerror)^n > 1/alpha
// n * log(1 - maxRisk + 2 * maxRisk * noerror) > -log(alpha)
// n ~ -log(alpha) / log(1 - maxRisk + 2 * maxRisk * noerror)
// n ~ -log(alpha) / log(2 * noerror)  // when maxRisk = 1.0

// ?????
// 1 - maxRisk + 2 * maxRisk * noerror > 2 * noerror when maxRisk < 1.0 ?
// 1 - maxRisk > 0
// maxRisk * noerror < noerror
// (1 - maxRisk) * noerror < noerror


// n ~ -log(alpha) / log(1 - maxRisk + 2 * maxRisk * noerror)
// n ~ -log(alpha) / log(1 + maxRisk * (2 * noerror - 1))
// noerror > 1/2, so (2 * noerror - 1) > 0, so ???


//// TODO  this is a lot of trouble to calculate prevContestCounts; we only need it if contest.auditorWantNewMvrs has been set
// for each contest, return map contestId -> wantSampleSize. used in ConsistentSampling
private fun wantSampleSize(contestsNotDone: List<ContestRound>, previousSamples: Set<Long>, sortedCards : CloseableIterator<AuditableCard>, debug: Boolean = false): Map<Int, Int> {
    //// count how many samples each contest already has
    val prevContestCounts = mutableMapOf<ContestRound, Int>()
    contestsNotDone.forEach { prevContestCounts[it] = 0 }

    // Note this iterates through sortedCards only until all previousSamples have been found and counted
    sortedCards.use { cardIter ->
        previousSamples.forEach { prevNumber ->
            while (cardIter.hasNext()) {
                val card = cardIter.next() // previousSamples must be in same order as sortedBorc
                if (card.prn == prevNumber) {
                    contestsNotDone.forEach { contest ->
                        if (card.hasContest(contest.id)) {
                            prevContestCounts[contest] = prevContestCounts[contest]?.plus(1) ?: 1
                        }
                    }
                    break
                }
            }
        }
    }
    if (debug) {
        val prevContestCountsById = prevContestCounts.entries.associate { it.key.id to it.value }
        logger.debug{"**wantSampleSize prevContestCountsById = $prevContestCountsById"}
    }
    // we need prevContestCounts in order to calculate wantSampleSize if contest.auditorWantNewMvrs has been set
    val wantSampleSizeMap = prevContestCounts.entries.associate { it.key.id to it.key.wantSampleSize(it.value) }
    if (debug) logger.debug{"wantSampleSize = $wantSampleSizeMap"}

    return wantSampleSizeMap
}



