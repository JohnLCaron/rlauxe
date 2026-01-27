package org.cryptobiotic.rlauxe.betting

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.estimate.CardSamples
import org.cryptobiotic.rlauxe.util.Welford
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.doubleIsClose
import org.cryptobiotic.rlauxe.util.doublePrecision
import kotlin.random.Random

private val logger = KotlinLogging.logger("SamplerTracker")

//// abstraction for creating a sequence of assort values
interface SamplerTracker: SampleTracker, Iterator<Double> {
    // Sampler : abstraction for creating a sequence of assort values
    fun sample(): Double // get next in sample
    fun maxSamples(): Int  // max samples available, needed by testFn
    fun maxSampleIndexUsed(): Int // the largest cvr index used in the sampling
    fun nmvrs(): Int // total number mvrs

    override fun reset()   // start over again with different permutation (may be prohibited)

    /// Tracker : keeps track of the latest sample, number of samples, and the sample sum, mean and variance.
    //  doesnt update the statistics until the next sample is called. Must call done() when finished.
    override fun numberOfSamples(): Int    // total number of samples so far
    fun welford(): Welford   // running mean, variance, stddev
    fun done()    // end of sampling, update statistics to final form
}

interface SampleErrorTracker: SampleTracker {
    fun measuredClcaErrorCounts(): ClcaErrorCounts
    fun noerror(): Double
}

// Note that we are stuffing the sampling logic into card.hasContest(contestId)

//// For polling audits. Production runPollingAuditRound
class PollingSamplerTracker(
    val contestId: Int,
    val cvrPairs: List<Pair<CvrIF, CvrIF>>, // Pair(mvr, card)
    val assorter: AssorterIF,
    val allowReset: Boolean = true,
    maxSampleIndexIn: Int? = null,
): SamplerTracker {
    val maxSampleIndex = maxSampleIndexIn ?: cvrPairs.size
    val permutedIndex = MutableList(cvrPairs.size) { it }

    var maxSamples: Int = 0
    private var idx = 0
    private var welford = Welford()

    init {
        cvrPairs.forEach { (mvr, card) ->
            require(mvr.location() == card.location())  { "mvr location ${mvr.location()} != card.location ${card.location()}"}  }
        maxSamples = cvrPairs.take(maxSampleIndex).count { it.second.hasContest(contestId) }
    }

    override fun sample(): Double {
        while (idx < cvrPairs.size && idx < maxSampleIndex) {
            val (mvr, card) = cvrPairs[permutedIndex[idx]]
            idx++
            if (card.hasContest(contestId)) {
                if (lastVal != null) welford.update(lastVal!!)
                lastVal =  assorter.assort(mvr, usePhantoms = true)
                return lastVal!!
            }
        }
        logger.error{"PollingSampling no samples left for contest ${contestId} and Assorter ${assorter}"}
        throw RuntimeException("PollingSampling no samples left for contest ${contestId} and assorter ${assorter}")
    }

    override fun reset() {
        if (!allowReset) {
            logger.error {"PollingSampling reset not allowed; contest ${contestId} assorter ${assorter}\""}
            throw RuntimeException("PollingSampling reset not allowed")
        }
        permutedIndex.shuffle(Random)
        idx = 0
        maxSamples = cvrPairs.take(maxSampleIndex).count { it.second.hasContest(contestId) }
        welford = Welford()
    }

    override fun maxSamples() = maxSamples
    override fun maxSampleIndexUsed() = idx
    override fun nmvrs() = cvrPairs.size

    override fun hasNext() = (welford.count + 1 < maxSamples)
    override fun next() = sample()

    // tracker reflects "previous sequence"
    var lastVal: Double? = null
    override fun numberOfSamples() = welford.count
    override fun welford() = welford
    override fun done() {
        if (lastVal != null) welford.update(lastVal!!)
        lastVal = null
    }

    ///////////////////////////////// temporary
    override fun sum() = welford.sum()
    override fun mean() = welford.mean
    override fun variance() = welford.variance()

    override fun last(): Double = lastVal!!
    override fun addSample(sample: Double) {
        TODO("Not implemented")
    }
}

class ClcaSamplerTracker(
    val contestId: Int,
    val cvrPairs: List<Pair<CvrIF, AuditableCard>>, // Pair(mvr, card)
    val cassorter: ClcaAssorter,
    val allowReset: Boolean,
    maxSampleIndexIn: Int? = null,
): SamplerTracker {
    val maxSampleIndex = maxSampleIndexIn ?: cvrPairs.size
    val permutedIndex = MutableList(cvrPairs.size) { it }

    private var maxSamples = 0
    private var idx = 0
    private var welford = Welford()

    init {
        cvrPairs.forEach { (mvr, card) ->
            require(mvr.location() == card.location())  { "mvr location ${mvr.location()} != card.location ${card.location()}"}  }
        maxSamples = cvrPairs.take(maxSampleIndex).count { it.second.hasContest(contestId) }
    }

    override fun sample(): Double {
        while (idx < cvrPairs.size && idx < maxSampleIndex) {
            val (mvr, card) = cvrPairs[permutedIndex[idx]]
            idx++
            if (card.hasContest(contestId)) {
                if (lastVal != null) welford.update(lastVal!!)
                lastVal = cassorter.bassort(mvr, card, hasStyle=card.exactContests())
                return lastVal!!
            }
        }
        logger.error{"ClcaSampling no samples left for ${contestId} and ComparisonAssorter ${cassorter}"}
        throw RuntimeException("ClcaSampling no samples left for ${contestId} and ComparisonAssorter ${cassorter}")
    }

    override fun reset() {
        if (!allowReset) {
            logger.error{"ClcaSampling reset not allowed ; contest ${contestId} cassorter ${cassorter}"}
            throw RuntimeException("ClcaSampling reset not allowed")
        }
        permutedIndex.shuffle(Random)
        idx = 0
        maxSamples = cvrPairs.take(maxSampleIndex).count { it.second.hasContest(contestId) }
        welford = Welford()
    }

    override fun maxSamples() = maxSamples
    override fun maxSampleIndexUsed() = idx
    override fun nmvrs() = cvrPairs.size

    override fun hasNext() = (welford.count + 1 < maxSamples)
    override fun next() = sample()

    // tracker reflects "previous sequence"
    var lastVal: Double? = null
    override fun numberOfSamples() = welford.count
    override fun welford() = welford

    override fun done() {
        if (lastVal != null) welford.update(lastVal!!)
        lastVal = null
    }

    ///////////////////////////////// temporary
    override fun sum() = welford.sum()
    override fun mean() = welford.mean
    override fun variance() = welford.variance()

    override fun last(): Double = lastVal!!
    override fun addSample(sample: Double) {
        TODO("Not implemented")
    }
}

//// For clca audits. Production RunClcaContestTask
class ClcaSamplerErrorTracker(
    val contestId: Int,
    val cvrPairs: List<Pair<CvrIF, AuditableCard>>, // Pair(mvr, card)
    val cassorter: ClcaAssorter,
    val allowReset: Boolean,
    maxSampleIndexIn: Int? = null,  // if maxSampleIndexIn != null then reset = false
): SamplerTracker, SampleErrorTracker {
    val maxSampleIndex = maxSampleIndexIn ?: cvrPairs.size
    val permutedIndex = MutableList(cvrPairs.size) { it }
    val clcaErrorTracker = ClcaErrorTracker2(cassorter.noerror, cassorter.assorter.upperBound())
    // val firstDebug: Pair<Int, Int>

    private var maxSamples = 0
    private var idx = 0
    private var welford = Welford()

    init {
        cvrPairs.forEach { (mvr, card) ->
            require(mvr.location() == card.location())  { "mvr location ${mvr.location()} != card.location ${card.location()}"}  }
        maxSamples = cvrPairs.take(maxSampleIndex).count { it.second.hasContest(contestId) }
        // firstDebug = debug()
    }

    override fun sample(): Double {
        while (idx < cvrPairs.size && idx < maxSampleIndex) {
            val (mvr, card) = cvrPairs[permutedIndex[idx]]
            idx++
            if (card.hasContest(contestId)) {
                val nextVal = cassorter.bassort(mvr, card, hasStyle=card.exactContests())
                clcaErrorTracker.addSample(nextVal, card.poolId == null) // dont track errors from oa pools
                if (lastVal != null) welford.update(lastVal!!)
                lastVal = nextVal
                return nextVal
            }
        }
        logger.error{"ClcaSampling no samples left for ${contestId} and ComparisonAssorter ${cassorter}"}
        throw RuntimeException("ClcaSampling no samples left for ${contestId} and ComparisonAssorter ${cassorter}")
    }

    override fun reset() {
        if (!allowReset) {
            logger.error{"ClcaSampling reset not allowed ; contest ${contestId} cassorter ${cassorter}"}
            throw RuntimeException("ClcaSampling reset not allowed")
        }
        permutedIndex.shuffle(Random)
        maxSamples = cvrPairs.take(maxSampleIndex).count { it.second.hasContest(contestId) }
        welford = Welford()
        clcaErrorTracker.reset()
        idx = 0
        lastVal = null
    }

    fun debug(): Pair<Int, Int> {
        var debugIdx = 0
        var countHasContest = 0
        var countNotPooled = 0
        while (debugIdx < cvrPairs.size && debugIdx < maxSampleIndex) {
            val (mvr, card) = cvrPairs[permutedIndex[debugIdx]]
            debugIdx++
            if (card.hasContest(contestId)) {
                countHasContest++
                if (card.poolId == null) countNotPooled++
            }
        }
        println("$contestId ${cassorter.shortName()}: countHasContest=$countHasContest countNotPooled=$countNotPooled " +
                "pct=${df(countNotPooled / countHasContest.toDouble())}")
        return Pair(countHasContest, countNotPooled)
    }

    override fun maxSamples() = maxSamples
    override fun maxSampleIndexUsed() = idx
    override fun nmvrs() = cvrPairs.size

    override fun hasNext() = (welford.count + 1 < maxSamples)
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

    ///////////////////////////////// temporary TODO remove
    override fun sum() = welford.sum()
    override fun mean() = welford.mean
    override fun variance() = welford.variance()

    override fun last(): Double = lastVal!!
    override fun addSample(sample: Double) {
        TODO("Remove this")
    }
}


//// For clca audits. Production RunClcaContestTask
class ClcaSamplerErrorTracker2(
    val contestId: Int,
    cvrPairs: List<Pair<CvrIF, AuditableCard>>, // Pair(mvr, card)
    cardSamples: CardSamples,
    val cassorter: ClcaAssorter,
    val allowReset: Boolean,
): SamplerTracker, SampleErrorTracker {
    val samples = cardSamples.extractSubsetByIndex(contestId, cvrPairs)
    val permutedIndex = MutableList(samples.size) { it }
    val clcaErrorTracker = ClcaErrorTracker2(cassorter.noerror, cassorter.assorter.upperBound())
    val firstDebug: Pair<Int, Int>

    private var idx = 0
    private var welford = Welford()

    init {
        samples.forEach { (mvr, card) ->
            require(mvr.location() == card.location())  { "mvr location ${mvr.location()} != card.location ${card.location()}"}  }
        firstDebug = debug()
    }

    override fun sample(): Double {
        while (idx < samples.size) {
            val (mvr, card) = samples[permutedIndex[idx]]
            idx++
            if (card.hasContest(contestId)) {
                val nextVal = cassorter.bassort(mvr, card, hasStyle=card.exactContests())
                clcaErrorTracker.addSample(nextVal, card.poolId == null) // dont track errors from oa pools
                if (lastVal != null) welford.update(lastVal!!)
                lastVal = nextVal
                return nextVal
            }  else {
                logger.error{"cardSamples for contest ${contestId} list card not contining the contest at index ${permutedIndex[idx-1]}"}
            }
        }
        logger.error{"ClcaSampling no samples left for ${contestId} and ComparisonAssorter ${cassorter}"}
        throw RuntimeException("ClcaSampling no samples left for ${contestId} and ComparisonAssorter ${cassorter}")
    }

    override fun reset() {
        if (!allowReset) {
            logger.error{"ClcaSampling reset not allowed ; contest ${contestId} cassorter ${cassorter}"}
            throw RuntimeException("ClcaSampling reset not allowed")
        }
        permutedIndex.shuffle(Random)
        welford = Welford()
        clcaErrorTracker.reset()
        idx = 0
        lastVal = null
    }

    fun debug(): Pair<Int, Int> {
        var debugIdx = 0
        var countHasContest = 0
        var countNotPooled = 0
        while (debugIdx < samples.size) {
            val (mvr, card) = samples[permutedIndex[debugIdx]]
            debugIdx++
            if (card.hasContest(contestId)) {
                countHasContest++
                if (card.poolId == null) countNotPooled++
            }
        }
        println("$contestId ${cassorter.shortName()}: countHasContest=$countHasContest countNotPooled=$countNotPooled " +
                "pct=${df(countNotPooled / countHasContest.toDouble())}")
        return Pair(countHasContest, countNotPooled)
    }

    override fun maxSamples() = samples.size
    override fun maxSampleIndexUsed() = idx
    override fun nmvrs() = samples.size

    override fun hasNext() = (welford.count + 1 < samples.size)
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

    ///////////////////////////////// temporary TODO remove
    override fun sum() = welford.sum()
    override fun mean() = welford.mean
    override fun variance() = welford.variance()

    override fun last(): Double = lastVal!!
    override fun addSample(sample: Double) {
        TODO("Remove this")
    }
}

//// For clca audits with styles and no errors
class ClcaNoErrorSamplerTracker(
    val contestId: Int,
    val contestNc: Int,
    val cassorter: ClcaAssorter,
    val cvrIterator: Iterator<CvrIF>,
): SamplerTracker {
    private var idx = 0
    private var done = false
    private val welford = Welford()

    override fun sample(): Double {
        while (cvrIterator.hasNext()) {
            val cvr = cvrIterator.next()
            idx++
            if (cvr.hasContest(contestId)) {
                val result = cassorter.bassort(cvr, cvr, hasStyle=true)
                welford.update(result)
                return result
            }
        }
        done = true
        if (!warned) {
            logger.warn { "ClcaNoErrorIterator no samples left for ${contestId} and ComparisonAssorter ${cassorter}" }
            warned = true
        }
        return 0.0
    }

    override fun reset() {
        logger.error{"ClcaNoErrorIterator reset not allowed"}
        throw RuntimeException("ClcaNoErrorIterator reset not allowed")
    }

    override fun numberOfSamples() = welford.count
    override fun welford() = welford

    override fun done() {}

    override fun maxSamples() = contestNc
    override fun maxSampleIndexUsed() = idx
    override fun nmvrs() = contestNc

    override fun hasNext() = !done && cvrIterator.hasNext()
    override fun next() = sample()

    companion object {
        var warned = false
    }

    ///////////////////////////////// temporary
    override fun last(): Double {
        TODO("Not yet implemented")
    }

    override fun sum(): Double {
        TODO("Not yet implemented")
    }

    override fun mean(): Double {
        TODO("Not yet implemented")
    }

    override fun variance(): Double {
        TODO("Not yet implemented")
    }

    override fun addSample(sample: Double) {
        TODO("Not yet implemented")
    }
}

class ClcaErrorTracker2(val noerror: Double, val upper: Double) {
    val taus = Taus(upper)
    private var count = 0
    private var countTrackError = 0

    val valueCounter = mutableMapOf<Double, Int>()
    var noerrorCount = 0

    // when the sample is from an OA pool, the values is not a CLCA error
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
        val clcaErrors = valueCounter.toList().filter { (key, value) -> taus.isClcaError(key / noerror) }.toMap().toSortedMap()
        return ClcaErrorCounts(clcaErrors, count, noerror, upper)
    }

    override fun toString(): String {
        return "ClcaErrorTracker(noerror=$noerror, noerrorCount=$noerrorCount, valueCounter=${valueCounter.toSortedMap()}, count=$count countTrackError=$countTrackError)"
    }
}
