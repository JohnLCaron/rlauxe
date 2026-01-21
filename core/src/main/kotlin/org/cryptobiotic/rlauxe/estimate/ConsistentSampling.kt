package org.cryptobiotic.rlauxe.estimate

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.betting.ClcaErrorCounts
import org.cryptobiotic.rlauxe.betting.ClcaErrorTracker
import org.cryptobiotic.rlauxe.betting.GeneralAdaptiveBetting
import org.cryptobiotic.rlauxe.betting.TestH0Status
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.oneaudit.ClcaAssorterOneAudit
import org.cryptobiotic.rlauxe.util.CloseableIterable
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.roundUp
import org.cryptobiotic.rlauxe.workflow.MvrManager

private val debugConsistent = false
private val logger = KotlinLogging.logger("ConsistentSampling")

// TODO
// for each contest record first card prn not taken due to have >= want.
// can continue the audit up to that prn.


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
        sample(config, mvrManager, auditRound, previousSamples, quiet = quiet)

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

/** Choose what cards to sample */
private fun sample(
    config: AuditConfig,
    mvrManager : MvrManager,
    auditRound: AuditRoundIF,
    previousSamples: Set<Long> = emptySet(),
    quiet: Boolean = true
) {
    if (!quiet) logger.info{"consistentSampling round ${auditRound.roundIdx} auditorSetNewMvrs=${auditRound.auditorWantNewMvrs}"}
    consistentSampling(auditRound, mvrManager, previousSamples)
    if (!quiet) logger.info{" consistentSamplingSize= ${auditRound.samplePrns.size} newmvrs= ${auditRound.newmvrs} "}
}

// From Consistent Sampling with Replacement, Ronald Rivest, August 31, 2018
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

        // track how many continguous mvrs each contest has
        contestsIncluded.forEach { contest ->
            if (card.hasContest(contest.id)) {
                if (include && !skippedContests.contains(contest.id)) {
                    haveSampleSize[contest.id] = haveSampleSize[contest.id]?.plus(1) ?: 1
                    if (!previousSamples.contains(card.prn)) {
                        haveNewSamples[contest.id] = haveNewSamples[contest.id]?.plus(1) ?: 1
                    }
                    // ok to use if we havent skipped any cards for this contest in its sequence
                    contest.maxSampleIndex = sampledCards.size
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
    val contestIdMap = contestsIncluded.associate { it.id to it }
    contestIdMap.values.forEach { // defaults to 0
        it.actualMvrs = 0
        it.actualNewMvrs = 0
    }
    haveSampleSize.forEach { (contestId, nmvrs) ->
        contestIdMap[contestId]?.actualMvrs = nmvrs
    }
    haveNewSamples.forEach { (contestId, nnmvrs) ->
        contestIdMap[contestId]?.actualNewMvrs = nnmvrs
    }

    // set the results into the auditRound direclty
    auditRound.nmvrs = sampledCards.size
    auditRound.newmvrs = newMvrs
    auditRound.samplePrns = sampledCards.map { it.prn }
}

// try running without complexity
fun wantSampleSizeSimple(contestsNotDone: List<ContestRound>): Map<Int, Int> {
     return contestsNotDone.associate { it.id to it.estMvrs }
}

////////////////////////////////////////////////////////////////////////////////
// called from estimateSampleSizes to choose N cards to reduce simulation cost
fun estimationSubset(
    config: AuditConfig,
    contests: List<ContestRound>,
    cards: CloseableIterable<AuditableCard>,
    previousSamples: Set<Long>,
): List<AuditableCard> {
    val contestsNotDone = contests.filter { !it.done }
    if (contestsNotDone.isEmpty()) return emptyList() // may not quite be right...

    // calculate how many samples are wanted for each contest.
    val wantSamples: Map<Int, Int> = contestsNotDone.associate { it.id to estSamplesNeeded(config, it) }
    val haveSampleSize = mutableMapOf<Int, Int>() // contestId -> nmvrs in sample

    fun contestWantsMoreSamples(c: ContestRound): Boolean {
        return (haveSampleSize[c.id] ?: 0) < (wantSamples[c.id] ?: 0)
    }

    val contestsIncluded = contestsNotDone.filter { it.included }

    // var newMvrs = 0 // count when this card not in previous samples
    val sampledCards = mutableListOf<AuditableCard>()

    // skip previously used cards, grab next batch if needed by a contest thats not done...
    // because we are using real cards, we dont need to add phantoms to match....
    var countSamples = 0
    val sortedCardIter = cards.iterator()
    while (contestsIncluded.any { contestWantsMoreSamples(it) } && sortedCardIter.hasNext()) {
        val card = sortedCardIter.next()
        if (previousSamples.contains(card.prn)) continue

        // does this contribute to one or more contests that need more samples?
        if (contestsIncluded.any { contestWantsMoreSamples(it) && card.hasContest(it.id) }) {
            // then use it
            sampledCards.add(card)

            // count only if included
            contestsIncluded.forEach { contest ->
                if (card.hasContest(contest.id)) {
                    haveSampleSize[contest.id] = haveSampleSize[contest.id]?.plus(1) ?: 1
                }
            }
        }
        countSamples++
    }

    return sampledCards
}

// CLCA and OneAudit TODO POLLING
// we dont use this for the actual estimation....
private fun estSamplesNeeded(config: AuditConfig, contestRound: ContestRound): Int {
    val minAssertionRound = contestRound.minAssertion()
    if (minAssertionRound == null) {
        contestRound.minAssertion()
        throw RuntimeException()
    }

    val lastPvalue = minAssertionRound.auditResult?.plast ?: config.riskLimit
    val minAssertion = minAssertionRound.assertion
    val cassorter = (minAssertion as ClcaAssertion).cassorter

    if (config.isClca) {
        return roundUp(2.0 * cassorter.sampleSizeNoErrors(maxRisk = config.clcaConfig.maxRisk, lastPvalue))
    }

    // maybe just something inverse to margin ??

    // one audit - calc optimal bet, use it as maxRisk
    val contest = contestRound.contestUA
    val oaass = minAssertion.cassorter as ClcaAssorterOneAudit
    val assorter = minAssertion.cassorter.assorter
    val upper = assorter.upperBound()
    val betFn = GeneralAdaptiveBetting(
        contest.Npop,
        ClcaErrorCounts.empty(oaass.noerror(), upper),
        contest.Nphantoms,
        oaass.oaAssortRates,
        maxRisk = config.clcaConfig.maxRisk,
        debug=false,
    )
    val bet = betFn.bet(ClcaErrorTracker(oaass.noerror(), upper))
    val maxRisk = bet / 2
    val est = roundUp(5.0 * cassorter.sampleSizeNoErrors(maxRisk = maxRisk, lastPvalue))
    if (assorter.dilutedMargin() < 0.07) {
        logger.info { "estimationSubset ${contest.id}-${assorter.winLose()}  sumRates = ${oaass.oaAssortRates.sumRates()} maxRisk= $maxRisk, est = $est, margin=${assorter.dilutedMargin()}" }
    }
    return est
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
fun wantSampleSize(contestsNotDone: List<ContestRound>, previousSamples: Set<Long>, sortedCards : CloseableIterator<AuditableCard>, debug: Boolean = false): Map<Int, Int> {
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



