package org.cryptobiotic.rlauxe.core


import org.cryptobiotic.rlauxe.util.doublePrecision
import kotlin.test.*

class TestClcaErrorCounts {

    @Test
    fun testClcaErrorRate() {
        var upper = 1.0
        val u12 = 1.0 / (2 * upper)
        val computeTaus = listOf(0.0, u12, 1 - u12, 2 - u12, 1 + u12, 2.0)
        val totalSamples = 1000

        val perr = PluralityErrorRates.fromList(listOf(0.1, 0.2, 0.1, 0.1))
        val cerr = ClcaErrorCounts.fromPluralityErrorRates(perr, 1.1, totalSamples, 1.0)
        println(cerr.clcaErrorRate())
        assertEquals(perr.toList().sum(), cerr.clcaErrorRate())

        // what if upper is > 1 ?
        upper = 10.0
        val dilutedMargin = .02
        var noerror: Double = 1.0 / (2.0 - dilutedMargin / upper)

        val fuzz = .01
        var bassorts = computeBassortValues(noerror=noerror, upper=upper)
        var errorCounts = bassorts.associate { it to (fuzz * totalSamples).toInt() }

        val cerr2 = ClcaErrorCounts(errorCounts, 1000, noerror, upper)
        println(cerr2.clcaErrorRate())
        assertEquals(fuzz * bassorts.size, cerr2.clcaErrorRate())

        // what if upper is < 1> ?
        upper = 0.5678
        noerror = 1.0 / (2.0 - dilutedMargin / upper)

        bassorts = computeBassortValues(noerror=noerror, upper=upper)
        errorCounts = bassorts.associate { it to (fuzz * totalSamples).toInt() }

        val cerr3 = ClcaErrorCounts(errorCounts, 1000, noerror, upper)
        println(cerr3.clcaErrorRate())
        assertEquals(fuzz * bassorts.size, cerr3.clcaErrorRate())

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

        var errorCounts: ClcaErrorCounts = tracker.measuredCounts()
        assertEquals(upper, errorCounts.upper)
        assertEquals(noerror, errorCounts.noerror)
        assertEquals(countedTotal, errorCounts.totalSamples)

        // add some noerrors
        repeat(11) {
            tracker.addSample(noerror)
        }
        assertEquals(countedTotal+11, tracker.numberOfSamples())
        assertEquals(11, tracker.noerrorCount)

        errorCounts = tracker.measuredCounts()
        assertEquals(11, errorCounts.errorCounts[noerror])
        assertEquals(countedTotal+11, errorCounts.totalSamples)
    }

    @Test
    fun testTrackerWithPools() {
        var upper = 1.1
        val dilutedMargin = .02
        val fuzz = .01
        val poolAvg = 0.505
        val clcaSamples = 1000
        val poolSamples = 1000

        val noerror: Double = 1.0 / (2.0 - dilutedMargin / upper)
        val tracker = ClcaErrorTracker(noerror, upper)
        val bassorts = computeBassortValues(noerror = noerror, upper = upper)

        bassorts.forEachIndexed { idx, bassort ->
            repeat((idx * fuzz * clcaSamples).toInt()) {
                tracker.addSample(bassort)
            }
        }

        val tauOA = TausOA(upper, poolAvg)

        tauOA.tausOA.forEach { (bassort, desc) ->
            repeat((fuzz * poolSamples).toInt()) {
                tracker.addSample(bassort * noerror)
            }
        }

        var errorCounts: ClcaErrorCounts = tracker.measuredCounts()
        println(errorCounts.show(poolAvg))
        assertContains(errorCounts.show(poolAvg), "loser=10, other=10, winner=10", )
        assertContains(errorCounts.show(poolAvg), "win-oth=10, oth-los=20, noerror=0, oth-win=30, los-oth=40, los-win=50")

        val countedTotal = tracker.valueCounter.values.sum()
        assertEquals(countedTotal, tracker.numberOfSamples())
        assertEquals(0, tracker.noerrorCount)

        assertEquals(upper, errorCounts.upper)
        assertEquals(noerror, errorCounts.noerror)
        assertEquals(countedTotal, errorCounts.totalSamples)
    }
}