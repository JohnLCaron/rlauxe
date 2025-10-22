package org.cryptobiotic.rlauxe.util

import java.util.concurrent.TimeUnit
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.text.endsWith

class TestStopWatch {

    @Test
    fun basics() {
        val stopwatchNotRunning =  Stopwatch(false)
        assertEquals(0, stopwatchNotRunning.stop())
        stopwatchNotRunning.start()
        assertTrue(stopwatchNotRunning.stop() > 0)

        val stopwatch =  Stopwatch()
        assertTrue(stopwatch.stop() > 0)
    }

    @Test
    fun took() {
        val stopwatch =  Stopwatch()
        Thread.sleep(1000)
        val tookStr = stopwatch.took()
        // show that its stopped
        Thread.sleep(10)
        assertEquals(tookStr, stopwatch.took())

        // tookPer
        assertContains(stopwatch.tookPer(10), "for 10 nrows, 10")
        assertContains(stopwatch.tookPer(10), "ms per nrows")
        assertContains(stopwatch.tookPer(100, "glorbs"), "for 100 glorbs, 10.")
        assertContains(stopwatch.tookPer(100, "glorbs"), "ms per glorbs")
    }

    @Test
    fun statics() {
        assertEquals("took 111 ms", Stopwatch.took(111111111)) // nanosecs
        assertEquals("took 111.111111 ms for 11 thing, 10.10 ms per thing", Stopwatch.perRow(111111111, 11, "thing"))
        assertEquals("111 / 2222 ms =  .05000", Stopwatch.ratio(111111111, 2222222222))
        assertEquals("15.87 / 317.5 ms per row", Stopwatch.perRow(111111111, 2222222222, 7))
        assertEquals("111 / 2222 ms =  .05000;  15.87 / 317.5 ms per row", Stopwatch.ratioAndPer(111111111, 2222222222, 7))
    }

    @Test
    fun elapsed() {
        val stopwatch =  Stopwatch()
        val elapsed1 = stopwatch.elapsed(TimeUnit.NANOSECONDS)
        val elapsed2 = stopwatch.elapsed(TimeUnit.NANOSECONDS)
        assertTrue(elapsed2 > elapsed1)

        val elapsed3 = stopwatch.elapsed()
        val elapsed4 = stopwatch.elapsed()
        assertTrue(elapsed4 >= elapsed3)

        val elapsed6 = stopwatch.elapsed(TimeUnit.SECONDS)
        Thread.sleep(1001)
        val elapsed7 = stopwatch.elapsed(TimeUnit.SECONDS)
        assertTrue(elapsed7 > elapsed6)
    }

    @Test
    fun convert() {
        val stopwatch =  Stopwatch(false)
        println(stopwatch)
        assertTrue(stopwatch.toString().endsWith("ns"))

        stopwatch.start()
        sqrt(3.0)
        stopwatch.stop()
        println(stopwatch)
        assertTrue(stopwatch.toString().endsWith("μs"))

        stopwatch.start()
        Thread.sleep(1)
        stopwatch.stop()
        println(stopwatch)
        assertTrue(stopwatch.toString().endsWith("ms"))

        stopwatch.start()
        Thread.sleep(1000)
        stopwatch.stop()
        println(stopwatch)
        assertTrue(stopwatch.toString().endsWith("s"))
    }

}