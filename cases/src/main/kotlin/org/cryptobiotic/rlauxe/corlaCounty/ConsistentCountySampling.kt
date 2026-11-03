package org.cryptobiotic.rlauxe.corlaCounty

import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.audit.SamplingCardIF
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.CountyAuditRecord
import org.cryptobiotic.rlauxe.persist.Publisher
import kotlin.random.Random

// Used by CvCountySampling in Colorado viewer.
// Sample within one county's CVRS, to estimate how many samples would be needed within that county.
fun sampleCountyCvrs(countyAudit: CountyAuditRecord, county: String, wantNmvrs: Map<Int, Int>, ntrials: Int): List<Int>? {
    val publisher = Publisher(countyAudit.topdir)
    val result = AuditRecord.readPartialResult(publisher.countyAuditRecordTopdir(county))
    if (result.isErr) return null

    val sampleDist = sampleFastCvrsFromRecord(result.unwrap(),  wantNmvrs, ntrials)
    if (sampleDist != null) return sampleDist

    val cvrs = countyAudit.readCountyCvrs(county, null) // this only reads ~maxSamples if set
    return sampleCountyCvrs(cvrs, wantNmvrs, ntrials)
}

fun sampleStateCvrs(auditRecord: CountyAuditRecord, wantNmvrs: Map<Int, Int>, ntrials: Int): List<Int>? {
    return sampleFastCvrsFromRecord(auditRecord, wantNmvrs, ntrials)
}

fun sampleFastCvrsFromRecord(auditRecord: AuditRecord, wantNmvrs: Map<Int, Int>, ntrials: Int): List<Int>? {
    val styles = auditRecord.readCardStyles()
    val cardIter = auditRecord.readSamplingCards(styles)
    if (cardIter == null) return null

    val fastCards = mutableListOf<SamplingCardIF>()
    var count = 0
    cardIter.iterator().use { iter ->
        while( iter.hasNext()) {
            fastCards.add(iter.next())
            count++
        }
    }
    return sampleCountyCvrs(fastCards, wantNmvrs, ntrials)
}

// wantNmvrs: contestId -> wantCards
// run ntrials to get a distribution
fun sampleCountyCvrs(cvrs: List<SamplingCardIF>, wantNmvrs: Map<Int, Int>, ntrials: Int): List<Int> {
    val random = Random.Default
    val mcvrs = cvrs.toMutableList()
    val result = mutableListOf<Int>()
    repeat (ntrials) {
        mcvrs.shuffle(random)
        val nmvrs = consistentSampling(wantNmvrs, mcvrs.iterator())
        result.add(nmvrs)
    }
    return result
}

// return how many were needed
fun consistentSampling(
    wantNmvrs: Map<Int, Int>,
    samplingCards: Iterator<SamplingCardIF>,
): Int {

    val needNmvrs = wantNmvrs.toMutableMap().withDefault{ 0 }
    var countCards = 0
    var countInclude = 0

    val samplingCardIter = samplingCards.iterator()
    while (
        samplingCardIter.hasNext() &&
        needNmvrs.any { it.value > 0 }
    ) {
        // get the next card in sorted order
        val card: SamplingCardIF = samplingCardIter.next()

        // do we want it ?
        var include = false
        for (contestId in needNmvrs.keys) {
            // does this contest want this card ?
            if (card.hasContest(contestId) && (needNmvrs[contestId]!! > 0)) {
                include = true
                countInclude++
                break
            }
        }

        if (include) {
            countCards++
            for (contestId in needNmvrs.keys) {
                // println((card as AuditableCard).possibleContests().contentToString())
                if (card.hasContest(contestId)) {
                    needNmvrs[contestId] = needNmvrs[contestId]!! - 1
                }
            }
        }
    }

    needNmvrs.filter{ it.value > 0 }.forEach {
        println(it)
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