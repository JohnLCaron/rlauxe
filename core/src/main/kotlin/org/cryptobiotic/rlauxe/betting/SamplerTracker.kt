package org.cryptobiotic.rlauxe.betting

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.Welford
import org.cryptobiotic.rlauxe.util.doubleIsClose
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import kotlin.random.Random

private val logger = KotlinLogging.logger("SamplerTracker")

// main purpose is for BettingFn.bet(prevSamples: Tracker)
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
    fun measuredClcaErrorCounts(): ClcaErrorCounts // empty when polling
    fun welford(): Welford   // running mean, variance, stddev

    /// Tracker : keeps track of the latest sample, number of samples, and the sample sum, mean and variance.
    //  doesnt update the statistics until the next sample is called. Must call done() when finished.
    override fun numberOfSamples(): Int    // total number of samples so far
    override fun sum() = welford().sum()
    override fun mean() = welford().mean
    override fun variance() = welford().variance()
}

//// For polling audits, assumes card.hasContest(contestId) for all samples
class PollingSamplerTracker(
    val contestId: Int,
    val assorter: AssorterIF,
    val cvrPairs: List<Pair<CvrIF, CvrIF>>, // Pair(mvr, card)
    val allowReset: Boolean = true,  // needed ?
): SamplerTracker {
    val permutedIndex = MutableList(cvrPairs.size) { it }

    private var idx = 0
    private var welford = Welford()

    init {
        cvrPairs.forEach { (mvr, card) ->
            require(mvr.location() == card.location())  { "mvr location ${mvr.location()} != card.location ${card.location()}"}
            require(card.hasContest(contestId))  { " card.location ${card.location()} does not have contest $contestId" }
        }
    }

    override fun sample(): Double {
        while (hasNext()) {
            val (mvr, _) = cvrPairs[permutedIndex[idx]]
            idx++
            val assortVal =  assorter.assort(mvr, usePhantoms = true)
            welford.update(assortVal)
            return assortVal
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
        welford = Welford()
    }

    override fun maxSamples() = cvrPairs.size
    override fun countCvrsUsedInAudit() = idx
    override fun nmvrs() = cvrPairs.size

    override fun hasNext() = (idx < cvrPairs.size)
    override fun next() = sample()

    // tracker reflects "previous sequence"
    override fun numberOfSamples() = welford.count
    override fun measuredClcaErrorCounts() = ClcaErrorCounts.empty(0.0, 0.0)

    override fun welford() = welford

    companion object {

        fun withMaxSample(
            contestId: Int,
            assorter: AssorterIF,
            completeSamples: List<Pair<CvrIF, CvrIF>>, // Pair(mvr, card)
            maxSampleIndex: Int?,  // index into complete sample list
        ): PollingSamplerTracker {
            val extract = mutableListOf<Pair<CvrIF, CvrIF>>()
            completeSamples.forEachIndexed { idx, pair ->
                if (pair.second.hasContest(contestId) && (maxSampleIndex == null || idx < maxSampleIndex)) {
                    extract.add(pair)
                }
            }
            return PollingSamplerTracker(contestId, assorter, extract)
        }
    }
}

//// For clca/oa audits, assumes card.hasContest(contestId) for all samples
class ClcaSamplerErrorTracker(
    val contestId: Int,
    val cassorter: ClcaAssorter,
    val samples: List<Pair<CvrIF, AuditableCard>>,
    val clcaErrorTracker: ClcaErrorTracker,
    val allowReset: Boolean = true,  // needed ?
    val name: String? = null, // debugging
): SamplerTracker, ErrorTracker {

    constructor(contestId: Int, cassorter: ClcaAssorter, samples:List<Pair<CvrIF, AuditableCard>>, name: String? = null)
            : this(contestId, cassorter, samples, ClcaErrorTracker(cassorter.noerror, cassorter.assorter.upperBound()),
                   name = name)

    init {
        require(cassorter.noerror == clcaErrorTracker.noerror)
        require(cassorter.assorter.upperBound() == clcaErrorTracker.upper)
        samples.forEach { (mvr, card) ->
            require(mvr.location() == card.location())  { "mvr location ${mvr.location()} != card.location ${card.location()}"}
            require(card.hasContest(contestId))  { " card.location ${card.location()} does not have contest $contestId" }
        }
    }

    val permutedIndex = MutableList(samples.size) { it }
    private var idx = 0

    override fun sample(): Double {
        while (idx < samples.size) {
            val (mvr, card) = samples[permutedIndex[idx]]
            idx++
            val nextVal = cassorter.bassort(mvr, card, hasStyle=card.exactContests())
            clcaErrorTracker.addSample(nextVal, card.poolId == null) // dont track errors from oa pools
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
        clcaErrorTracker.reset()
        idx = 0
    }

    override fun maxSamples() = samples.size
    override fun countCvrsUsedInAudit() = idx  // count of cvrs used in the audit
    override fun nmvrs() = samples.size

    override fun hasNext() = (idx < samples.size)
    override fun next() = sample()

    override fun numberOfSamples() = clcaErrorTracker.numberOfSamples()
    override fun welford() = clcaErrorTracker.welford

    //// ErrorTracker
    override fun measuredClcaErrorCounts(): ClcaErrorCounts = clcaErrorTracker.measuredClcaErrorCounts()
    override fun noerror(): Double = clcaErrorTracker.noerror

    // debugging
    fun dump(outputFilename: String) {
        val writer: OutputStreamWriter = FileOutputStream(outputFilename).writer()
        samples.forEachIndexed { idx, pair ->
            writer.write("mvr $idx: ${pair.first}\n")
            writer.write("cvr $idx: ${pair.second}\n")
        }
        writer.close()
    }

    companion object {
        // pull the desired samples out
        fun fromIndexList(
            contestId: Int,
            cassorter: ClcaAssorter,
            pairs: List<Pair<AuditableCard, AuditableCard>>,
            wantIndices: List<Int>,
            clcaErrorTracker: ClcaErrorTracker? = null,
        ): ClcaSamplerErrorTracker {
            val extract = mutableListOf<Pair<AuditableCard, AuditableCard>>()
            var wantIdx = 0
            pairs.forEachIndexed { idx, pair ->
                if (wantIdx < wantIndices.size && idx == wantIndices[wantIdx]) {
                    extract.add(pair)
                    wantIdx++
                }
            }
            return ClcaSamplerErrorTracker(contestId, cassorter, extract,
                clcaErrorTracker ?: ClcaErrorTracker(cassorter.noerror, cassorter.assorter.upperBound()))
        }

        // I think we do the extraction in order to use reset() ?
        fun withMaxSample(
            contestId: Int,
            cassorter: ClcaAssorter,
            cvrPairs: List<Pair<CvrIF, AuditableCard>>, // Pair(mvr, card)
            maxSampleIndex: Int? = null,  // if maxSampleIndex != null then reset = false)
            name: String?=null,
        ): ClcaSamplerErrorTracker {
            val maxIndex = maxSampleIndex ?: Int.MAX_VALUE
            val extract = mutableListOf<Pair<CvrIF, AuditableCard>>()
            cvrPairs.forEachIndexed { idx, pair ->
                if (pair.second.hasContest(contestId) && idx < maxIndex) {
                    extract.add(pair)
                }
            }
            return ClcaSamplerErrorTracker(contestId, cassorter, extract, name=name)
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

// tracks errors from passed-in assort values; use ClcaSamplerErrorTracker if you want a SamplerTracker
// noerror = 0.0 turns off the error tracking part, just does welford tracking
data class ClcaErrorTracker(val noerror: Double, val upper: Double, val welford:Welford, val errorCounts: MutableMap<Double, Int>): ErrorTracker {
    val taus = Taus(upper)

    constructor(noerror: Double, upper: Double) : this(noerror, upper, Welford(), mutableMapOf<Double, Int>())

    // when the sample is from an OA pool, the value is not a CLCA error
    fun addSample(sample : Double, isClcaError: Boolean = true) {
        welford.update(sample)

        if (isClcaError && noerror != 0.0) {
            if (sample != noerror) {
                val counter = errorCounts.getOrPut(sample) { 0 }
                errorCounts[sample] = counter + 1
            }
        }
    }

    fun reset() {
        errorCounts.clear()
        welford.reset()
    }

    fun errorRates() = errorCounts.mapValues { it.value / numberOfSamples().toDouble() }.toSortedMap()
    fun errorCounts() = errorCounts.toSortedMap()

    override fun measuredClcaErrorCounts(): ClcaErrorCounts {
        val clcaErrors = errorCounts.toList().filter { (key, _) -> taus.isClcaError(key / noerror) }.toMap().toSortedMap()
        return ClcaErrorCounts(clcaErrors, welford.count, noerror, upper)
    }

    override fun toString(): String {
        return "ClcaErrorTracker(noerror=$noerror, valueCounter=${errorCounts.toSortedMap()}, count=${welford.count})"
    }

    override fun numberOfSamples() = welford.count
    override fun sum() = welford.sum()
    override fun mean() = welford.mean
    override fun variance() = welford.variance()
    override fun noerror() = noerror
}