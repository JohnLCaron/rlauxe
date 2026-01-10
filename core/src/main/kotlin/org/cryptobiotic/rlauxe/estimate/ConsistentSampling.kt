package org.cryptobiotic.rlauxe.estimate

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.CloseableIterable
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.workflow.MvrManager
import org.cryptobiotic.rlauxe.workflow.wantSampleSize
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.use

private val debugConsistent = false
private val logger = KotlinLogging.logger("ConsistentSampling")

// TODO
// for each contest record first card prn not taken due to have >= want.
// can continue the audit up to that prn.


// called from auditWorkflow
// also called by rlauxe-viewer
fun sampleWithContestCutoff(
    config: AuditConfig,
    mvrManager : MvrManager,
    auditRound: AuditRound,
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
    auditRound: AuditRound,
    previousSamples: Set<Long> = emptySet(),
    quiet: Boolean = true
) {
        if (!quiet) logger.info{"consistentSampling round ${auditRound.roundIdx} auditorSetNewMvrs=${auditRound.auditorWantNewMvrs}"}
        consistentSampling(auditRound, mvrManager, previousSamples)
        if (!quiet) logger.info{" consistentSamplingSize= ${auditRound.samplePrns.size}"}
}

// From Consistent Sampling with Replacement, Ronald Rivest, August 31, 2018
fun consistentSampling(
    auditRound: AuditRound,
    mvrManager: MvrManager,
    previousSamples: Set<Long> = emptySet(),
) {
    val contestsNotDone = auditRound.contestRounds.filter { !it.done }
    if (contestsNotDone.isEmpty()) return

    // calculate how many samples are wanted for each contest.
    // TODO try simple?
    val wantSampleSizeMap = wantSampleSizeSimple(contestsNotDone, previousSamples, mvrManager.sortedCards().iterator())
    require(wantSampleSizeMap.values.all { it >= 0 }) { "wantSampleSize must be >= 0" }

    val haveSampleSize = mutableMapOf<Int, Int>() // contestId -> nmvrs in sample
    val haveNewSamples = mutableMapOf<Int, Int>() // contestId -> nmvrs in sample
    fun contestWantsMoreSamples(c: ContestRound): Boolean {
        if (c.auditorWantNewMvrs > 0 && (haveNewSamples[c.id] ?: 0) >= c.auditorWantNewMvrs) return false
        return (haveSampleSize[c.id] ?: 0) < (wantSampleSizeMap[c.id] ?: 0)
    }

    val contestsIncluded = contestsNotDone.filter { it.included }
    val haveActualMvrs = mutableMapOf<Int, Int>() // contestId -> new nmvrs in sample

    var newMvrs = 0 // count when this card not in previous samples
    val sampledCards = mutableListOf<AuditableCard>()

    // makes only one partial iteration over sortedCards, until wantSamples foreach contest are fouond
    var countSamples = 0
    val sortedCardIter = mvrManager.sortedCards().iterator()
    while (
        ((auditRound.auditorWantNewMvrs < 0) || (newMvrs < auditRound.auditorWantNewMvrs)) &&
        contestsIncluded.any { contestWantsMoreSamples(it) } &&
        sortedCardIter.hasNext()
    ) {
        // get the next card in sorted order
        val card = sortedCardIter.next()
        /* if (card.location == "card1659") {
            contestsIncluded.forEach {
                if (card.hasContest(it.id)) {
                    val want = contestWantsMoreSamples(it) && card.hasContest(it.id)
                    println("  ${it.id} $want have=${haveSampleSize[it.id]} want=${wantSampleSizeMap[it.id]} autor=${it.auditorWantNewMvrs}")
                }
            }
            val anywant = contestsIncluded.any { contestRound -> contestWantsMoreSamples(contestRound) && card.hasContest(contestRound.id) }
            println(" $card anywant $anywant")
        } */

        // does this contribute to one or more contests that need more samples?
        if (contestsIncluded.any { contestRound -> contestWantsMoreSamples(contestRound) && card.hasContest(contestRound.id) }) {
            // then use it
            sampledCards.add(card)
            if (!previousSamples.contains(card.prn)) {
                newMvrs++
            }
            // count only if included
            contestsIncluded.forEach { contest ->
                if (card.hasContest(contest.id)) {
                    haveSampleSize[contest.id] = haveSampleSize[contest.id]?.plus(1) ?: 1
                }
            }
            // track actual for all contests not done
            contestsNotDone.forEach { contest ->
                if (card.hasContest(contest.id)) {
                    haveActualMvrs[contest.id] = haveActualMvrs[contest.id]?.plus(1) ?: 1
                    if (!previousSamples.contains(card.prn))
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
}

// try running without complexity
fun wantSampleSizeSimple(contestsNotDone: List<ContestRound>, previousSamples: Set<Long>, sortedCards : CloseableIterator<AuditableCard>, debug: Boolean = false): Map<Int, Int> {
     return contestsNotDone.associate { it.id to it.estSampleSize }
}

// called from estimateSampleSizes to choose N cards to reduce simulation cost
fun consistentSampling(
    config: AuditConfig,
    contests: List<ContestRound>,
    cards: CloseableIterable<AuditableCard>,
    previousSamples: Set<Long>,
): List<AuditableCard> {
    val contestsNotDone = contests.filter { !it.done }
    if (contestsNotDone.isEmpty()) return emptyList() // may not quite be right...

    // calculate how many samples are wanted for each contest.
    val wantSamples: Map<Int, Int> = contestsNotDone.associate { it.id to estSamplesNeeded(it, config.riskLimit, 2.2) }
    val haveSampleSize = mutableMapOf<Int, Int>() // contestId -> nmvrs in sample

    fun contestWantsMoreSamples(c: ContestRound): Boolean {
        return (haveSampleSize[c.id] ?: 0) < (wantSamples[c.id] ?: 0)
    }

    val contestsIncluded = contestsNotDone.filter { it.included }

    // var newMvrs = 0 // count when this card not in previous samples
    val sampledCards = mutableListOf<AuditableCard>()

    // makes only one partial iteration over sortedCards, until wantSamples foreach contest are fouond
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

// CLCA and OneAudit
fun estSamplesNeeded(contestRound: ContestRound, alpha: Double, fac: Double): Int {
    val minAssertionRound = contestRound.minAssertion()
    if (minAssertionRound == null) {
        contestRound.minAssertion()
        throw RuntimeException()
    }

    val lastPvalue = minAssertionRound.auditResult?.pvalue ?: alpha
    val minAssertion = minAssertionRound.assertion

    val cassorter = (minAssertion as ClcaAssertion).cassorter
    // val expected = ln(1 / alpha) / ln(2 * cassorter.noerror())
    val estSamplesNoErrors = ln(1 / lastPvalue) / ln(2 * cassorter.noerror())

    val estNeeded =  (fac * estSamplesNoErrors).roundToInt()
    // contestRound.estCardsNeeded = estNeeded
    return estNeeded
}

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



