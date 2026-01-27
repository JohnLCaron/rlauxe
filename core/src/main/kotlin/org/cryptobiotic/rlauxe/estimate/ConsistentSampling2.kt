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
import org.cryptobiotic.rlauxe.util.Closer
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.roundUp
import org.cryptobiotic.rlauxe.workflow.MvrManager
import kotlin.math.min

private val debugConsistent = false
private val logger = KotlinLogging.logger("ConsistentSampling")

// TODO
// for each contest record first card prn not taken due to have >= want.
// can continue the audit up to that prn.


// called from auditWorkflow.startNewRound
// also called by rlauxe-viewer
fun sampleWithContestCutoff2(
    config: AuditConfig,
    mvrManager : MvrManager,
    auditRound: AuditRoundIF,
    previousSamples: Set<Long>,
    quiet: Boolean
) {
    val stopwatch = Stopwatch()
    val contestsNotDone = auditRound.contestRounds.filter { !it.done }.toMutableList()

    while (contestsNotDone.isNotEmpty()) {
        sample2(config, mvrManager, auditRound, previousSamples, quiet = quiet)

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
private fun sample2(
    config: AuditConfig,
    mvrManager : MvrManager,
    auditRound: AuditRoundIF,
    previousSamples: Set<Long> = emptySet(),
    quiet: Boolean = true
) {
    if (!quiet) logger.info{"consistentSampling round ${auditRound.roundIdx} auditorSetNewMvrs=${auditRound.auditorWantNewMvrs}"}
    consistentSampling2(auditRound, mvrManager, previousSamples)
    if (!quiet) logger.info{" consistentSamplingSize= ${auditRound.samplePrns.size} newmvrs= ${auditRound.newmvrs} "}
}

// From Consistent Sampling with Replacement, Ronald Rivest, August 31, 2018
fun consistentSampling2(
    auditRound: AuditRoundIF,
    mvrManager: MvrManager,
    previousSamples: Set<Long> = emptySet(),  // TODO skip previous samples I think?? what about sampleCutoff ??
) {
    val contestsIncluded = auditRound.contestRounds.filter { !it.done && it.included}
    if (contestsIncluded.isEmpty()) return

    //println("consistentSampling all cards")
    //val allInfo = tabulateDebugInfo(mvrManager.sortedCards().iterator(), contestsIncluded, emptyMap())
    //allInfo.forEach { println("  $it") }

    // calculate how many samples are wanted for each contest.
    // TODO was val wantSampleSizeMap = wantSampleSize(contestsNotDone, previousSamples, mvrManager.sortedCards().iterator())
    val wantSampleSize = contestsIncluded.associate { it.id to it.estMvrs }
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

    //println("consistentSampling sampled cards ncards = ${sampledCards.size}")
    //val debugInfo = tabulateDebugInfo(Closer(sampledCards.iterator()), contestsIncluded)
    //debugInfo.forEach { (contestId, debugInfo) ->
    //    val allInfo = allInfo[contestId]!!
    //    println("  $debugInfo allPct=${df(allInfo.pct())}")
    //}

    if (debugConsistent) logger.info{"**consistentSampling haveSampleSize = $haveSampleSize, haveNewSamples = $haveNewSamples, newMvrs=$newMvrs"}
    /* val contestIdMap = contestsIncluded.associate { it.id to it }
    contestIdMap.values.forEach { // defaults to 0
        it.actualMvrs = 0
        it.actualNewMvrs = 0
    }
    haveSampleSize.forEach { (contestId, nmvrs) ->
        contestIdMap[contestId]?.actualMvrs = nmvrs
    }
    haveNewSamples.forEach { (contestId, nnmvrs) ->
        contestIdMap[contestId]?.actualNewMvrs = nnmvrs
    } */

    // set the results into the auditRound direclty
    auditRound.nmvrs = sampledCards.size
    auditRound.newmvrs = newMvrs
    auditRound.samplePrns = sampledCards.map { it.prn }  // TODO WHY ?
}

//////////////////////////////////////////////
// bit simpler than consistentSampling, but tempting to try to just use it

data class CardSamples(val cards: List<AuditableCard>, val usedByContests: Map<Int, List<Int>>) {

    fun extractSubsetByIndex(contestId: Int): List<AuditableCard> {
        val extract = mutableListOf<AuditableCard>()
        val want = usedByContests[contestId]!!
        var wantIdx = 0
        cards.forEachIndexed { idx, it ->
            if (wantIdx < want.size && idx == want[wantIdx]) {
                extract.add(it)
                wantIdx++
            }
        }
        return extract
    }

    fun extractSubsetByIndex(contestId: Int, pairs: List<Pair<CvrIF, AuditableCard>>): List<Pair<CvrIF, AuditableCard>> {
        val extract = mutableListOf<Pair<CvrIF, AuditableCard>>()
        val want = usedByContests[contestId]!!
        var wantIdx = 0
        pairs.forEachIndexed { idx, it ->
            if (wantIdx < want.size && idx == want[wantIdx]) {
                extract.add(it)
                wantIdx++
            }
        }
        return extract
    }

}

fun getSubsetForEstimation2(
    config: AuditConfig,
    contests: List<ContestRound>,
    cards: CloseableIterable<AuditableCard>,
    previousSamples: Set<Long>,  // TODO skip previous samples maybe?
): CardSamples
{
    val contestsIncluded = contests.filter { !it.done && it.included }
    if (contestsIncluded.isEmpty()) return CardSamples(emptyList(), emptyMap())

    println("getSubsetForEstimation all cards")
    val allInfo = tabulateDebugInfo(cards.iterator(), contestsIncluded, null)

    // calculate how many samples are wanted for each contest.
    val wantSampleSize: Map<Int, Int> = contestsIncluded.associate { it.id to estSamplesNeeded2(config, it) }
    val haveSampleSize = mutableMapOf<Int, Int>() // contestId -> nmvrs in sample
    val skippedContests = mutableSetOf<Int>()
    val usedByContests = mutableMapOf<Int, MutableList<Int>>()

    val sampledCards = mutableListOf<AuditableCard>()
    var cardIndex = 0  // track maximum index (not done yet)

    fun contestWantsMoreSamples(c: ContestRound): Boolean {
        return (haveSampleSize[c.id] ?: 0) < (wantSampleSize[c.id] ?: 0)
    }

    var countCardsLookedAt = 0
    val sortedCardIter = cards.iterator()
    while (
        sortedCardIter.hasNext()) {
        if (!contestsIncluded.any { contestWantsMoreSamples(it)} ) break

        // get the next card in sorted order
        val card = sortedCardIter.next()
        countCardsLookedAt++

        /* if (countCardsLookedAt % 10000 == 0) {
            contestsIncluded.forEach {
                val need = (haveSampleSize[it.id]?: 0) < (wantSampleSize[it.id]?: 0)
                if (need) println(" ${it.id}: have=${haveSampleSize[it.id]} < want=${wantSampleSize[it.id]}")
            }
            println()
        } */

        // does anyone want this card ?
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
        }

        // update the haveSampleSize count and maxSampleIndexAllowed
        contestsIncluded.forEach { contest ->
            if (card.hasContest(contest.id)) {
                if (include && !skippedContests.contains(contest.id)) {
                    haveSampleSize[contest.id] = haveSampleSize[contest.id]?.plus(1) ?: 1
                    // ok to use if we havent skipped any cards for this contest in its sequence
                    val usedByContest = usedByContests.getOrPut(contest.id) { mutableListOf() }
                    usedByContest.add(sampledCards.size-1)
                } else {
                    // if card has contest but its not included in the sample, then continuity has been broken
                    skippedContests.add(contest.id)
                }
            }
        }

        cardIndex++
    }

    println("getSubsetForEstimation sampled cards  ncards = ${sampledCards.size} countCardsLookedAt = $countCardsLookedAt")
    val debugInfo = tabulateDebugInfo(Closer(sampledCards.iterator()), contestsIncluded, usedByContests)
    debugInfo.forEach { (contestId, debugInfo) ->
        val allInfo = allInfo[contestId]!!
        println("  $debugInfo allPct=${df(allInfo.pct())} wantSampleSize=${wantSampleSize[contestId]}")
    }

    return CardSamples(sampledCards, usedByContests)
}

class ContestDebugInfo(val contestId: Int, var count: Int, var countNotPooled: Int) {
    fun pct() = countNotPooled/count.toDouble()
    override fun toString(): String {
        return "ContestDebugInfo(contestId=$contestId, count=$count, countNotPooled=$countNotPooled pct=${df(pct())}"
    }
}

fun tabulateDebugInfo(cards: CloseableIterator<AuditableCard>, contests: List<ContestRound>, usedByContests: Map<Int, List<Int>>? = null): Map<Int, ContestDebugInfo> {
    val tabs = mutableMapOf<Int, ContestDebugInfo>()
    var sampleIndex = 0
    cards.use { cardIter ->
        while (cardIter.hasNext()) {
            val card = cardIter.next()
            sampleIndex++
            contests.forEach { contest ->
                if (usedByContests != null && usedByContests[contest.id] == null)
                    print("how")
                val useCard = usedByContests == null || usedByContests[contest.id]!!.contains(sampleIndex)
                if (useCard && card.hasContest(contest.id)) {
                    val tab = tabs.getOrPut(contest.id) { ContestDebugInfo(contest.id, 0, 0) }
                    tab.count++
                    if (card.poolId == null)
                        tab.countNotPooled++
                }
            }
        }
    }
    return tabs.toSortedMap()
}

// CLCA and OneAudit TODO POLLING
// we dont use this for the actual estimation....
fun estSamplesNeeded2(config: AuditConfig, contestRound: ContestRound): Int {
    val minAssertionRound = contestRound.minAssertion()
    if (minAssertionRound == null) {
        contestRound.minAssertion()
        throw RuntimeException()
    }

    val lastPvalue = minAssertionRound.auditResult?.plast ?: config.riskLimit
    val minAssertion = minAssertionRound.assertion
    val cassorter = (minAssertion as ClcaAssertion).cassorter

    val contest = contestRound.contestUA
    val assorter = cassorter.assorter
    val estAndBet = cassorter.estWithOptimalBet(contest, maxRisk = config.clcaConfig.maxRisk, lastPvalue)
    val dd = if (cassorter is ClcaAssorterOneAudit) {
        val sum = cassorter.oaAssortRates.sumOneAuditTerm(estAndBet.second)
        val sumneg = if (sum < 0) "**" else ""
        "sumOneAuditTerm=${dfn(sum, 6)} $sumneg"
    } else ""
    logger.info { "getSubsetForEstimation ${contest.id}-${assorter.winLose()} margin=${assorter.dilutedMargin()} estAndBet=$estAndBet $dd" }
    var est =  min( contest.Npop, 3 * estAndBet.first)// arb factor of 3
    if (config.contestSampleCutoff != null) est = min(config.contestSampleCutoff, est)
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

