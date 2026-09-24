package org.cryptobiotic.rlauxe.corlaCounty

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.SamplingCardIF
import org.cryptobiotic.rlauxe.persist.CountyAuditRecord
import kotlin.random.Random


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