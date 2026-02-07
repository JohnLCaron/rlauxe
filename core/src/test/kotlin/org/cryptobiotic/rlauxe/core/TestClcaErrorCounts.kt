package org.cryptobiotic.rlauxe.core


import org.cryptobiotic.rlauxe.betting.ClcaErrorCounts
import org.cryptobiotic.rlauxe.betting.ClcaErrorTracker
import org.cryptobiotic.rlauxe.betting.TausRateTable
import org.cryptobiotic.rlauxe.betting.computeBassortValues
import org.cryptobiotic.rlauxe.estimate.MultiContestTestData
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.doublePrecision
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random
import kotlin.test.*

class TestClcaErrorCounts {

    @Test
    fun testClcaErrorRate() {
        var upper = 1.0
        val u12 = 1.0 / (2 * upper)
        val computeTaus = listOf(0.0, u12, 1 - u12, 2 - u12, 1 + u12, 2.0)
        val totalSamples = 1000

        // what if upper is > 1 ?
        upper = 10.0
        val dilutedMargin = .02
        var noerror: Double = 1.0 / (2.0 - dilutedMargin / upper)

        val fuzz = .01
        var bassorts = computeBassortValues(noerror = noerror, upper = upper)
        var errorCounts = bassorts.associate { it to (fuzz * totalSamples).toInt() }

        val cerr2 = ClcaErrorCounts(errorCounts, 1000, noerror, upper)
        println(cerr2.sumRates())
        assertEquals(fuzz * bassorts.size, cerr2.sumRates(), doublePrecision)

        // what if upper is < 1 ?
        upper = 0.5678
        noerror = 1.0 / (2.0 - dilutedMargin / upper)

        bassorts = computeBassortValues(noerror = noerror, upper = upper)
        errorCounts = bassorts.associate { it to (fuzz * totalSamples).toInt() }

        val cerr3 = ClcaErrorCounts(errorCounts, 1000, noerror, upper)
        println(cerr3.sumRates())
        assertEquals(fuzz * bassorts.size, cerr3.sumRates(), doublePrecision)

        println(cerr3.show())
    }

    @Test
    fun testTracker() {
        val noerrors = .42
        val tracker = ClcaErrorTracker(noerrors, 1.0)
        tracker.addSample(noerrors)
        tracker.addSample(2*noerrors)
        tracker.addSample(2*noerrors)
        tracker.addSample(3*noerrors/2)
        tracker.addSample(noerrors/2)
        tracker.addSample(0.0)

        assertEquals(6, tracker.numberOfSamples())
        assertEquals(listOf(1, 1, 1, 2), tracker.errorCounts().toList().map { it.second })

        val n = tracker.numberOfSamples().toDouble()
        assertEquals(listOf(1/n, 1/n, 1/n, 2/n), tracker.errorRates().toList().map { it.second })

        assertEquals(0.49, tracker.mean())
        assertEquals(0.09799999, tracker.variance(), doublePrecision)
    }

    @Test
    fun testTrackerAddSamples() {
        var upper = 1.1
        val dilutedMargin = .02
        val fuzz = .01
        val clcaSamples = 1000

        var noerror: Double = 1.0 / (2.0 - dilutedMargin / upper)
        val tracker = ClcaErrorTracker(noerror, upper)
        var bassorts = computeBassortValues(noerror = noerror, upper = upper)

        bassorts.forEachIndexed { idx, bassort ->
            repeat((idx * fuzz * clcaSamples).toInt()) {
                tracker.addSample(bassort)
            }
        }

        tracker.valueCounter.forEach { (bassort, count) ->
            val idx = bassorts.indexOf(bassort)
            assertEquals((idx * fuzz * clcaSamples).toInt(), count)
        }

        val countedTotal = tracker.valueCounter.values.sum()
        assertEquals(countedTotal, tracker.numberOfSamples())
        assertEquals(0, tracker.noerrorCount)

        var errorCounts: ClcaErrorCounts = tracker.measuredClcaErrorCounts()
        assertEquals(upper, errorCounts.upper)
        assertEquals(noerror, errorCounts.noerror)
        assertEquals(countedTotal, errorCounts.totalSamples)

        // add some noerrors
        repeat(11) {
            tracker.addSample(noerror)
        }
        assertEquals(countedTotal+11, tracker.numberOfSamples())
        assertEquals(11, tracker.noerrorCount)
    }

    @Test
    fun testTausErrorTable() {
        var maxMaxPct = 0.0

        val show = false
        val maxMaxDiffs = mutableMapOf<Int, Double>()  // ncandidates -> maxDiff

        repeat(111) {
            val mvrsFuzzPct = Random.nextDouble(0.01)
            val margin = Random.nextDouble(0.10)
            val undervotePct = Random.nextDouble(0.10)
            // data class MultiContestTestData(
            //    val ncontest: Int,
            //    val nballotStyles: Int,
            //    val totalBallots: Int, // including undervotes and phantoms
            //    val marginRange: ClosedFloatingPointRange<Double> = 0.01.. 0.03,
            //    val underVotePctRange: ClosedFloatingPointRange<Double> = 0.01.. 0.30, // needed to set Nc
            //    val phantomPctRange: ClosedFloatingPointRange<Double> = 0.00..  0.005, // needed to set Nc
            //    val addPoolId: Boolean = false, // add cardStyle info to cvrs and cards
            //    val ncands: Int? = null,
            //    val poolPct: Double? = null,  // if not null, make a pool with this pct with two ballotStyles
            //    val seqCands: Boolean = false // if true, use ncands = 2 .. ncontests + 1
            //)
            val testData = MultiContestTestData(9, 1, 50000, margin..margin,
                undervotePct .. undervotePct, 0.0 .. 0.0, seqCands=true)
            val testCvrs = testData.makeCvrsFromContests()

            testData.contests.forEach { contest ->
                val contestUA = ContestWithAssertions(contest).addStandardAssertions()
                val assertion = contestUA.minClcaAssertion()!!
                val cassorter = assertion.cassorter

                //     fun makeErrorRates(ncandidates: Int, fuzzPct: Double, totalSamples: Int, noerror: Double, upper: Double): ClcaErrorCounts {
                val errorCounts = TausRateTable.makeErrorCounts(
                    contestUA.ncandidates,
                    mvrsFuzzPct,
                    contestUA.Npop,
                    cassorter.noerror(),
                    cassorter.assorter.upperBound()
                )
                if (show) println("ncand=${contestUA.ncandidates} mvrsFuzzPct=$mvrsFuzzPct errors=${errorCounts.errorCounts}")

                // TODO actually fuzz the cvrs
                val fuzzPcts = TausRateTable.calcFuzzPct(contestUA.ncandidates, errorCounts)
                var maxDiff = 0.0
                fuzzPcts.forEach { fuzzPct ->
                    maxDiff = max(maxDiff, abs(fuzzPct - mvrsFuzzPct))
                }
                val maxPct = maxDiff / mvrsFuzzPct
                if (show) {
                    println(" fuzzPcts = ${fuzzPcts}")
                    println(" maxDiff=${dfn(maxDiff, 6)} maxDiffPct=${df(maxPct)}")
                    println()
                }
                val maxMaxDiff = maxMaxDiffs.getOrPut(contestUA.ncandidates) { 0.0 }
                maxMaxDiffs[contestUA.ncandidates] = max(maxMaxDiff, maxDiff)
                if (maxPct < 1.0) maxMaxPct = max(maxMaxPct, maxPct)
            }
        }
        maxMaxDiffs.forEach { (ncand, maxDiff) ->
            println(" ncand = $ncand maxMaxDiff=${dfn(maxDiff, 6)}") //  maxMaxPct=${df(maxMaxPct)}")
        }
    }
}