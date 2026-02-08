package org.cryptobiotic.rlauxe.betting

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.oneaudit.OneAuditClcaAssorter
import org.cryptobiotic.rlauxe.util.Welford
import org.cryptobiotic.rlauxe.util.doubleIsClose
import org.cryptobiotic.rlauxe.util.doublePrecision
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import kotlin.random.Random

private val logger = KotlinLogging.logger("SamplerTracker")

interface Tracker {
    fun numberOfSamples(): Int    // total number of samples so far
    fun sum(): Double
    fun mean(): Double
    fun variance(): Double
}

interface ErrorTracker: Tracker {
    fun measuredClcaErrorCounts(): ClcaErrorCounts
    fun noerror(): Double
}

//// abstraction for creating a sequence of assort values
interface SamplerTracker: Tracker, Iterator<Double> {
    // Sampler : abstraction for creating a sequence of assort values
    fun sample(): Double // get next in sample
    fun maxSamples(): Int  // max samples available, needed by testFn
    fun countCvrsUsedInAudit(): Int // number of cvrs used in the audit
    fun nmvrs(): Int // total number mvrs

    fun reset()   // start over again with different permutation (may be prohibited)

    /// Tracker : keeps track of the latest sample, number of samples, and the sample sum, mean and variance.
    //  doesnt update the statistics until the next sample is called. Must call done() when finished.
    override fun numberOfSamples(): Int    // total number of samples so far
    fun measuredClcaErrorCounts(): ClcaErrorCounts
    fun done()    // end of sampling, update statistics to final form
    fun welford(): Welford   // running mean, variance, stddev

    override fun sum() = welford().sum()
    override fun mean() = welford().mean
    override fun variance() = welford().variance()
}

// Note that we are stuffing the sampling logic into card.hasContest(contestId)

//// For polling audits. Production runPollingAuditRound
class PollingSamplerTracker(
    val contestId: Int,
    val assorter: AssorterIF,
    val cvrPairs: List<Pair<CvrIF, CvrIF>>, // Pair(mvr, card)
    maxSampleIndex: Int? = null,
): SamplerTracker {
    val useMaxSampleIndex = maxSampleIndex ?: cvrPairs.size
    val permutedIndex = MutableList(cvrPairs.size) { it }
    val allowReset: Boolean = maxSampleIndex == null

    var maxSamples: Int = 0
    private var idx = 0
    private var welford = Welford()

    init {
        cvrPairs.forEach { (mvr, card) ->
            require(mvr.location() == card.location())  { "mvr location ${mvr.location()} != card.location ${card.location()}"}  }
        maxSamples = cvrPairs.take(useMaxSampleIndex).count { it.second.hasContest(contestId) }
    }

    override fun sample(): Double {
        while (idx < cvrPairs.size && hasNext()) {
            val (mvr, card) = cvrPairs[permutedIndex[idx]]
            idx++
            if (card.hasContest(contestId)) {
                if (lastVal != null) welford.update(lastVal!!)
                lastVal =  assorter.assort(mvr, usePhantoms = true)
                return lastVal!!
            }
        }
        logger.error{"PollingSamplerTracker no samples left for contest ${contestId} and Assorter ${assorter.shortName()}"}
        throw RuntimeException("PollingSamplerTracker no samples left for contest ${contestId} and assorter ${assorter.shortName()}")
    }

    override fun reset() {
        if (!allowReset) {
            logger.error {"PollingSamplerTracker reset not allowed; contest ${contestId} assorter ${assorter.shortName()}\""}
            throw RuntimeException("PollingSamplerTracker reset not allowed")
        }
        permutedIndex.shuffle(Random)
        idx = 0
        maxSamples = cvrPairs.take(useMaxSampleIndex).count { it.second.hasContest(contestId) }
        welford = Welford()
    }

    override fun maxSamples() = maxSamples
    override fun countCvrsUsedInAudit() = idx
    override fun nmvrs() = cvrPairs.size

    override fun hasNext() = (welford.count + 1 < maxSamples)
    override fun next() = sample()

    // tracker reflects "previous sequence"
    var lastVal: Double? = null
    override fun numberOfSamples() = welford.count
    override fun measuredClcaErrorCounts() = ClcaErrorCounts.empty(0.0, 0.0)

    override fun welford() = welford
    override fun done() {
        if (lastVal != null) welford.update(lastVal!!)
        lastVal = null
    }
}

//// For clca audits. Production RunClcaContestTask
class ClcaSamplerErrorTracker(
    val contestId: Int,
    val cassorter: ClcaAssorter,
    val samples: List<Pair<CvrIF, AuditableCard>>, // Pair(mvr, card)
    val allowReset: Boolean = true,  // needed ?
): SamplerTracker, ErrorTracker {
    val permutedIndex = MutableList(samples.size) { it }
    val clcaErrorTracker = ClcaErrorTracker2(cassorter.noerror, cassorter.assorter.upperBound())
    val oaTracker = if (cassorter is OneAuditClcaAssorter) OneAuditTracker2(contestId, cassorter) else null

    private var idx = 0
    private var welford = Welford()

    init {
        samples.forEach { (mvr, card) ->
            require(mvr.location() == card.location())  { "mvr location ${mvr.location()} != card.location ${card.location()}"}
            require(card.hasContest(contestId))  { " card.location ${card.location()} does not have contest $contestId" }
        }
    }

    fun dump(outputFilename: String) {
        val writer: OutputStreamWriter = FileOutputStream(outputFilename).writer()
        samples.forEachIndexed { idx, pair ->
            writer.write("mvr $idx: ${pair.first}\n")
            writer.write("cvr $idx: ${pair.second}\n")
        }
        writer.close()
    }

    override fun sample(): Double {
        while (idx < samples.size) {
            val (mvr, card) = samples[permutedIndex[idx]]
            idx++
            val nextVal = cassorter.bassort(mvr, card, hasStyle=card.exactContests())

            clcaErrorTracker.addSample(nextVal, card.poolId == null) // dont track errors from oa pools
            if (oaTracker != null && card.poolId != null) oaTracker.addSample(mvr, card)

            if (lastVal != null) welford.update(lastVal!!)
            lastVal = nextVal
            return nextVal
        }
        logger.error{"ClcaSamplerErrorTracker no samples left for contest ${contestId} and ComparisonAssorter ${cassorter.shortName()}"}
        throw RuntimeException("ClcaSamplerErrorTracker no samples left for ${contestId} and ComparisonAssorter ${cassorter.shortName()}")
    }

    override fun reset() {
        if (!allowReset) {
            logger.error{"ClcaSamplerErrorTracker reset not allowed ; contest ${contestId} cassorter ${cassorter.shortName()}"}
            throw RuntimeException("ClcaSamplerErrorTracker reset not allowed")
        }
        permutedIndex.shuffle(Random)
        welford = Welford()
        clcaErrorTracker.reset()
        idx = 0
        lastVal = null
    }

    override fun maxSamples() = samples.size
    override fun countCvrsUsedInAudit() = idx  // count of cvrs used in the audit
    override fun nmvrs() = samples.size

    override fun hasNext() = (idx < samples.size)
    override fun next() = sample()

    // tracker reflects "previous sequence"
    var lastVal: Double? = null
    override fun numberOfSamples() = welford.count
    override fun welford() = welford

    override fun done() {
        if (lastVal != null) welford.update(lastVal!!)
        lastVal = null
    }

    override fun measuredClcaErrorCounts(): ClcaErrorCounts = clcaErrorTracker.measuredClcaErrorCounts()
    override fun noerror(): Double = clcaErrorTracker.noerror

    companion object {
        fun fromIndexList(
            contestId: Int,
            cassorter: ClcaAssorter,
            pairs: List<Pair<AuditableCard, AuditableCard>>,
            wantIndices: List<Int>,
        ): ClcaSamplerErrorTracker {
            val extract = mutableListOf<Pair<AuditableCard, AuditableCard>>()
            var wantIdx = 0
            pairs.forEachIndexed { idx, pair ->
                if (wantIdx < wantIndices.size && idx == wantIndices[wantIdx]) {
                    extract.add(pair)
                    wantIdx++
                }
            }
            return ClcaSamplerErrorTracker(contestId, cassorter, extract)
        }

        fun withMaxSample(
            contestId: Int,
            cassorter: ClcaAssorter,
            cvrPairs: List<Pair<CvrIF, AuditableCard>>, // Pair(mvr, card)
            maxSampleIndex: Int? = null,  // if maxSampleIndexIn != null then reset = false)
        ): ClcaSamplerErrorTracker {
            val maxIndex = maxSampleIndex ?: Int.MAX_VALUE
            val extract = mutableListOf<Pair<CvrIF, AuditableCard>>()
            cvrPairs.forEachIndexed { idx, pair ->
                if (pair.second.hasContest(contestId) && idx < maxIndex) {
                    extract.add(pair)
                }
            }
            return ClcaSamplerErrorTracker(contestId, cassorter, extract)
        }

        fun withNoErrors(
            contestId: Int,
            cassorter: ClcaAssorter,
            cvrIterator: Iterator<AuditableCard>,
        ): ClcaSamplerErrorTracker {
            val extract = mutableListOf<Pair<CvrIF, AuditableCard>>()
            while (cvrIterator.hasNext()) {
                val cvr = cvrIterator.next()
                if (cvr.hasContest(contestId)) {
                    extract.add(Pair(cvr, cvr))
                }
            }
            return ClcaSamplerErrorTracker(contestId, cassorter, extract)
        }
    }
}

class ClcaErrorTracker2(val noerror: Double, val upper: Double) {
    val taus = Taus(upper)
    private var count = 0
    private var countTrackError = 0

    val valueCounter = mutableMapOf<Double, Int>()
    var noerrorCount = 0

    // when the sample is from an OA pool, the value is not an CLCA error
    fun addSample(sample : Double, trackError: Boolean) {
        count++
        if (trackError) countTrackError++

        if (trackError && noerror != 0.0) {
            if (doubleIsClose(sample, noerror, doublePrecision))
                noerrorCount++
            else if (taus.isClcaError(sample / noerror)) {
                val counter = valueCounter.getOrPut(sample) { 0 }
                valueCounter[sample] = counter + 1
            }
        }
    }

    fun reset() {
        valueCounter.clear()
        count = 0
        countTrackError = 0
        noerrorCount = 0
    }

    fun measuredClcaErrorCounts(): ClcaErrorCounts {
        val clcaErrors = valueCounter.toList().filter { (key, _) -> taus.isClcaError(key / noerror) }.toMap().toSortedMap()
        return ClcaErrorCounts(clcaErrors, count, noerror, upper)
    }

    override fun toString(): String {
        return "ClcaErrorTracker(noerror=$noerror, noerrorCount=$noerrorCount, valueCounter=${valueCounter.toSortedMap()}, count=$count countTrackError=$countTrackError)"
    }
}

class OneAuditTracker2(val contestId: Int, val cassorter : OneAuditClcaAssorter) {
    private var count = 0
    private var countTrackError = 0

    val welford = Welford()
    var noerrorCount = 0

    // not really sure what im doing here
    fun addSample(mvr : CvrIF, card: AuditableCard) {
        count++
        val poolAvg = cassorter.poolAverages.assortAverage[card.poolId]
        if (poolAvg != null) {
            val nextVal = cassorter.bassort(mvr, card, hasStyle=card.exactContests())
            welford.update(nextVal)

            val mvr_assort =
                if (mvr.isPhantom()) 0.0
                else if (!mvr.hasContest(contestId)) { if (card.exactContests()) 0.0 else 0.5 }
                else cassorter.assorter.assort(mvr, usePhantoms = false)

            // TODO val cvr_assort = if (cvr.phantom) .5 else poolAvgAssortValue
            val overstatement = poolAvg - mvr_assort
        }

    }

}