package org.cryptobiotic.rlauxe.core


import org.cryptobiotic.rlauxe.util.doublePrecision
import kotlin.test.*

class TestClcaErrorRates {

    @Test
    fun testBasics() {
        val er = ClcaErrorRates.fromList(listOf(0.1, 0.2, 0.3, 0.4))
        val erl = er.toList()
        val er2 = ClcaErrorRates.fromList(erl)
        val er3 = ClcaErrorRates(0.1, 0.2, 0.3, 0.4)

        assertEquals(er, er2)
        assertEquals(er, er3)
        assertEquals(er2, er3)
        assertFalse(er.areZero())
        assertTrue(ClcaErrorRates(0.0, 0.0, 0.0, 0.0).areZero())

        val mess = assertFailsWith<RuntimeException> {
            ClcaErrorRates.fromList(listOf(0.1, 0.2, 0.3, 0.4, 0.5))
        }.message
        assertEquals("ErrorRates list must have 4 elements", mess)

        val mess2 = assertFailsWith<RuntimeException> {
            ClcaErrorRates.fromList(listOf(0.1, 0.2, 0.3, 1.1))
        }.message
        assertEquals("p2u out of range 1.1", mess2)
    }


    @Test
    fun testTracker() {
        val noerrors = .42
        val tracker = PrevSamplesWithRates(noerrors)
        tracker.addSample(noerrors)
        tracker.addSample(2*noerrors)
        tracker.addSample(2*noerrors)
        tracker.addSample(3*noerrors/2)
        tracker.addSample(noerrors/2)
        tracker.addSample(0.0)

        assertEquals(6, tracker.numberOfSamples())
        assertEquals(listOf(1, 1, 1, 1, 2), tracker.clcaErrorCounts())

        val n = tracker.numberOfSamples().toDouble()
        assertEquals(listOf(1/n, 1/n, 1/n, 2/n), tracker.clcaErrorRates().toList())
        assertEquals(listOf(1/n, 1/n, 1/n, 2/n), tracker.errorRatesList())

        assertEquals(0.49, tracker.mean())
        assertEquals(0.09799999, tracker.variance(), doublePrecision)
    }
}