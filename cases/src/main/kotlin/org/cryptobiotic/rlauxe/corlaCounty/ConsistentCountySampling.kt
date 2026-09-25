package org.cryptobiotic.rlauxe.corlaCounty

import org.cryptobiotic.rlauxe.audit.AuditRoundIF
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.ContestRound
import org.cryptobiotic.rlauxe.audit.SamplingCardIF
import org.cryptobiotic.rlauxe.persist.CountyAuditRecord
import org.cryptobiotic.rlauxe.util.CloseableIterable
import org.cryptobiotic.rlauxe.util.Stopwatch
import kotlin.random.Random

// Used by CountyAudit UI. Sample within one county's CVRS, to estimate how many samples would be needed within that county.

fun sampleCountyCvrs(countyAudit: CountyAuditRecord, county: String, wantNmvrs: Map<Int, Int>, maxSamples: Int, ntrials: Int): List<Int> {
    val cvrs = countyAudit.readCountyCvrs(county) // , limit = 100_000)
    return sampleCountyCvrs(cvrs, wantNmvrs, maxSamples, ntrials)
}

// wantNmvrs: contestId -> wantCards
// truncate cvrs if necessary; could use FastSamplingCard
fun sampleCountyCvrs(cvrs: List<AuditableCard>, wantNmvrs: Map<Int, Int>, maxSamples: Int, ntrials: Int): List<Int> {
    val random = Random.Default
    val mcvrs = cvrs.toMutableList()
    val result = mutableListOf<Int>()
    repeat (ntrials) {
        mcvrs.shuffle(random)
        val nmvrs = consistentSampling(wantNmvrs, maxSamples, mcvrs.iterator())
        result.add(nmvrs)
    }
    return result
}

// return how many were needed
fun consistentSampling(
    wantNmvrs: Map<Int, Int>,
    maxSamples: Int,
    samplingCards: Iterator<SamplingCardIF>,
): Int {

    val needNmvrs = wantNmvrs.toMutableMap().withDefault{ 0 }

    var countCards = 0

    val samplingCardIter = samplingCards.iterator()
    while (
        samplingCardIter.hasNext() &&
        countCards < maxSamples &&
        needNmvrs.any { it.value > 0 }
    ) {
        // get the next card in sorted order
        val card = samplingCardIter.next()

        // do we want it ?
        var include = false
        for (contestId in needNmvrs.keys) {
            // does this contest want this card ?
            if (card.hasContest(contestId) && (needNmvrs[contestId]!! > 0)) {
                include = true
                break
            }
        }

        if (include) {
            countCards++
            for (contestId in needNmvrs.keys) {
                if (card.hasContest(contestId)) {
                    needNmvrs[contestId] = needNmvrs[contestId]!! - 1
                }
            }
        }
    }

    return countCards
}

// simplified example for consistent sampling with styles
// return list of Pair(index, prn) that is the canonical sequence
fun consistentSamplingByStyle(
    wantNmvrs: Map<Int, Int>, // contest id -> number of samples wanted. contests not in this are ignored
    maxSamples: Int,
    sortedCards: Iterator<SamplingCardIF>, // sorted by prn
): List<Pair<Int, Long>> {
    val canonSequence = mutableListOf<Pair<Int,Long>>()

    // how many we still need
    val needNmvrs = wantNmvrs.toMutableMap().withDefault{ 0 }
    var cardIndex = 0
    val samplingCardIter = sortedCards.iterator()
    while (
        samplingCardIter.hasNext() &&
        canonSequence.size < maxSamples &&
        needNmvrs.any { it.value > 0 }
    ) {
        // get the next card in sorted order
        val card = samplingCardIter.next()

        // do we want it?
        var include = false
        for (contestId in needNmvrs.keys) {
            if (card.hasContest(contestId) && (needNmvrs[contestId]!! > 0)) {
                include = true
                break
            }
        }

        if (include) {
            canonSequence.add(Pair(cardIndex, card.prn()))

            // decrement needNmvrs for any contests on the card
            for (contestId in needNmvrs.keys) {
                if (card.hasContest(contestId)) {
                    needNmvrs[contestId] = needNmvrs[contestId]!! - 1
                }
            }
        }

        cardIndex++
    }

    return canonSequence
}