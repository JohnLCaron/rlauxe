package org.cryptobiotic.rlauxe.betting

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.Welford
import org.cryptobiotic.rlauxe.util.doubleIsClose
import org.cryptobiotic.rlauxe.util.doublePrecision
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
    fun done()    // end of sampling, update statistics to final form
    fun welford(): Welford   // running mean, variance, stddev

    /// Tracker : keeps track of the latest sample, number of samples, and the sample sum, mean and variance.
    //  doesnt update the statistics until the next sample is called. Must call done() when finished.
    override fun numberOfSamples(): Int    // total number of samples so far
    override fun sum() = welford().sum()
    override fun mean() = welford().mean
    override fun variance() = welford().variance()
}

//// For polling audits.
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

//// For clca/oa audits, assumes card.hasContest(contestId) for all samples
class ClcaSamplerErrorTracker(
    val contestId: Int,
    val cassorter: ClcaAssorter,
    val samples: List<Pair<CvrIF, AuditableCard>>, // Pair(mvr, card)
    val allowReset: Boolean = true,  // needed ?
    val name: String? = null, // debugging
): SamplerTracker, ErrorTracker {
    val permutedIndex = MutableList(samples.size) { it }
    val clcaErrorTracker = ClcaErrorTracker(cassorter.noerror, cassorter.assorter.upperBound())

    private var idx = 0
    private var welford = Welford()

    init {
        samples.forEach { (mvr, card) ->
            require(mvr.location() == card.location())  { "mvr location ${mvr.location()} != card.location ${card.location()}"}
            require(card.hasContest(contestId))  { " card.location ${card.location()} does not have contest $contestId" }
        }
    }

    override fun sample(): Double {
        while (idx < samples.size) {
            val (mvr, card) = samples[permutedIndex[idx]]
            idx++
            val nextVal = cassorter.bassort(mvr, card, hasStyle=card.exactContests())

            clcaErrorTracker.addSample(nextVal, card.poolId == null) // dont track errors from oa pools

            //// lastVal seems unneeded; inconsistent welford and clcaErrorTracker
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
        // debug()
    }

    /* fun debug() {
        //     val contestId: Int,
        //    voteForNin: Int, //
        //    val isIrv: Boolean,
        //    val candidateIds: List<Int>
        val tab = ContestTabulation(18, 1, false, listOf(0,1))
        var idx = 0
        while (idx < this.samples.size) {
            val wantIdx = this.permutedIndex[idx]
            val (mvr, card) = this.samples[wantIdx]
            if (mvr.poolId() == 18) {
                val cands = mvr.votes(17)
                if (cands != null) {
                    tab.addVotes(cands, mvr.isPhantom())
                    println("mvr ${mvr.location()} ${cands.contentToString()}")
                }
            }
            idx++
        }
        println("reset tab for contest 17 and pool 18  = $tab")
        println("============================================")
    } */

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
        if (debugPhantoms && name != null)
            println("  ClcaSamplerErrorTracker $name done nsamples=${welford.count} p1o = ${measuredClcaErrorCounts().getNamedCount("p1o")}")
    }
    val debugPhantoms = false

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
            previousErrorCounts: ClcaErrorCounts? = null,
        ): ClcaSamplerErrorTracker {
            val extract = mutableListOf<Pair<AuditableCard, AuditableCard>>()
            var wantIdx = 0
            pairs.forEachIndexed { idx, pair ->
                if (wantIdx < wantIndices.size && idx == wantIndices[wantIdx]) {
                    extract.add(pair)
                    wantIdx++
                }
            }
            val result = ClcaSamplerErrorTracker(contestId, cassorter, extract)
            if (previousErrorCounts != null)
                result.clcaErrorTracker.setFromPreviousCounts(previousErrorCounts)
            return result
        }

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

// tracks errors from paassed-in assort values; ClcaSamplerErrorTracker is also a SamplerTracker
class ClcaErrorTracker(val noerror: Double, val upper: Double): ErrorTracker {
    val taus = Taus(upper)

    private var totalSamples = 0
    private var countTrackError = 0
    private var welford = Welford()

    private val errorCounts = mutableMapOf<Double, Int>() // assort value -> count
    private var noerrorCount = 0

    // this seems bogus; what about sum, variance, etc ?
    fun setFromPreviousCounts(prevCounts: ClcaErrorCounts) {
        require(prevCounts.noerror == noerror)
        require(prevCounts.upper == upper)
        totalSamples = prevCounts.totalSamples
        prevCounts.errorCounts.forEach { bassort, count ->
            errorCounts[bassort] = count
        }
    }

    // when the sample is from an OA pool, the value is not a CLCA error
    fun addSample(sample : Double, trackError: Boolean = true) {
        totalSamples++
        if (trackError) countTrackError++
        welford.update(sample)

        if (trackError && noerror != 0.0) {
            if (doubleIsClose(sample, noerror, doublePrecision))
                noerrorCount++
            else if (sample != noerror) {
                // println("  $sample taus=${sample / noerror} name=${taus.nameOf(sample / noerror)}")
                val counter = errorCounts.getOrPut(sample) { 0 }
                errorCounts[sample] = counter + 1
            }
        }
    }

    fun reset() {
        errorCounts.clear()
        totalSamples = 0
        countTrackError = 0
        noerrorCount = 0
        welford = Welford()
    }

    fun errorRates() = errorCounts.mapValues { it.value / numberOfSamples().toDouble() }.toSortedMap()
    fun errorCounts() = errorCounts.toSortedMap()
    fun noerrorCount() = noerrorCount

    override fun measuredClcaErrorCounts(): ClcaErrorCounts {
        val clcaErrors = errorCounts.toList().filter { (key, _) -> taus.isClcaError(key / noerror) }.toMap().toSortedMap()
        return ClcaErrorCounts(clcaErrors, totalSamples, noerror, upper)
    }

    override fun toString(): String {
        return "ClcaErrorTracker(noerror=$noerror, noerrorCount=$noerrorCount, valueCounter=${errorCounts.toSortedMap()}, count=$totalSamples countTrackError=$countTrackError)"
    }

    override fun numberOfSamples() = totalSamples
    override fun sum() = welford.sum()
    override fun mean() = welford.mean
    override fun variance() = welford.variance()
    override fun noerror() = noerror
}


// lightweight ErrorTracker for GeneralAdaptiveBetting.bet(), not a full blown SamplerTracker
class ClcaErrorTrackerOld(val noerror: Double, val upper: Double): ErrorTracker {
    val taus = Taus(upper)

    private var last = 0.0
    private var sum = 0.0
    private var welford = Welford()
    private var prevTotalSamples = 0

    override fun numberOfSamples() = prevTotalSamples + welford.count
    override fun sum() = sum
    override fun mean() = welford.mean
    override fun variance() = welford.variance()
    override fun noerror() = noerror

    val valueCounter = mutableMapOf<Double, Int>()
    var noerrorCount = 0
    var sequences: DebuggingSequences?=null

    fun setFromPreviousCounts(prevCounts: ClcaErrorCounts) {
        require(prevCounts.noerror == noerror)
        require(prevCounts.upper == upper)
        prevTotalSamples = prevCounts.totalSamples
        prevCounts.errorCounts.forEach { bassort, count ->
            valueCounter[bassort] = count
        }
    }

    fun setDebuggingSequences(sequences: DebuggingSequences) {
        this.sequences = sequences
    }

    fun addSample(sample : Double) {
        last = sample
        sum += sample
        welford.update(sample)

        if (noerror != 0.0) {
            if (doubleIsClose(sample, noerror))
                noerrorCount++
            else if (taus.isClcaError(sample / noerror)) {
                val counter = valueCounter.getOrPut(sample) { 0 }
                valueCounter[sample] = counter + 1
            }
        }
    }

    fun reset() {
        last = 0.0
        sum = 0.0
        welford = Welford()
        valueCounter.clear()
        noerrorCount = 0
    }

    override fun measuredClcaErrorCounts(): ClcaErrorCounts {
        val clcaErrors = valueCounter.toList().filter { (key, _) -> taus.isClcaError(key / noerror) }.toMap().toSortedMap()
        return ClcaErrorCounts(clcaErrors, numberOfSamples(), noerror, upper)
    }

    fun errorRates() = valueCounter.mapValues { it.value / numberOfSamples().toDouble() }.toSortedMap()
    fun errorCounts() = valueCounter.toSortedMap()

    override fun toString(): String {
        return "ClcaErrorTracker(noerror=$noerror, noerrorCount=$noerrorCount, valueCounter=${valueCounter.toSortedMap()}, N=${numberOfSamples()})"
    }
}


