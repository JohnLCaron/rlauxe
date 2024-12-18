package org.cryptobiotic.rlauxe.util

import java.util.concurrent.TimeUnit

// adapted from Guava's Stopwatch
class Stopwatch(running: Boolean = true, val timeUnit: TimeUnit = TimeUnit.MILLISECONDS) {
    private var isRunning = false
    private var elapsedNanos: Long = 0
    private var startTick: Long = 0

    init {
        if (running) start()
    }

    fun start(): Stopwatch {
        elapsedNanos = 0
        isRunning = true
        startTick = System.nanoTime()
        return this
    }

    // return elapsed nanoseconds
    fun stop(): Long {
        if (isRunning) {
            val tick: Long = System.nanoTime()
            isRunning = false
            elapsedNanos += tick - startTick
        }
        return elapsedNanos
    }

    private fun elapsedNanos(): Long {
        return if (isRunning) System.nanoTime() - startTick + elapsedNanos else elapsedNanos
    }

    // TimeUnit.SECONDS, MILLISECONDS, MICROSECONDS, NANOSECONDS
    fun elapsed(desiredUnit: TimeUnit = TimeUnit.MILLISECONDS): Long {
        return desiredUnit.convert(elapsedNanos(), TimeUnit.NANOSECONDS)
    }

    override fun toString(): String {
        val nanos = elapsedNanos()
        val unit = chooseUnit(nanos)
        val value = nanos.toDouble() / TimeUnit.NANOSECONDS.convert(1, unit)
        // Too bad this functionality is not exposed as a regular method call
        return String.format("%.4g %s", value, abbreviate(unit))
    }

    fun took(): String {
        val took = this.stop()
        return took(took)
    }

    fun tookPer(count: Int, what: String="nrows"): String {
        val took = this.stop()
        return perRow(took, count, what)
    }

    companion object {
        fun took(tookNanos: Long): String {
            val tookMs = tookNanos / 1_000_000
            return "took ${tookMs} ms"
        }

        fun perRow(tookNanos: Long, nrows: Int, what: String="nrows"): String {
            val tookMs = tookNanos.toDouble() / 1_000_000
            val perRow = if (nrows == 0) 0.0 else tookMs / nrows
            return "took ${tookMs} ms for $nrows $what, ${perRow.sigfig()} ms per $what"
        }

        // TODO units option
        fun ratio(num: Long, den: Long): String {
            val ratio = num.toDouble() / den
            val numValue = num / 1_000_000
            val denValue = den / 1_000_000
            return "$numValue / $denValue ms =  ${ratio.sigfig()}"
        }

        fun perRow(num: Long, den: Long, nrows: Int): String {
            val numValue = num.toDouble() / nrows / 1_000_000
            val denValue = den.toDouble() / nrows / 1_000_000
            return "${numValue.sigfig()} / ${denValue.sigfig()} ms per row"
        }

        fun ratioAndPer(num: Long, den: Long, nrows: Int): String {
            return "${ratio(num, den)};  ${perRow(num, den, nrows)}"
        }

        private fun chooseUnit(nanos: Long): TimeUnit {
            if (TimeUnit.SECONDS.convert(nanos, TimeUnit.NANOSECONDS) > 0) {
                return TimeUnit.SECONDS
            }
            if (TimeUnit.MILLISECONDS.convert(nanos, TimeUnit.NANOSECONDS) > 0) {
                return TimeUnit.MILLISECONDS
            }
            if (TimeUnit.MICROSECONDS.convert(nanos, TimeUnit.NANOSECONDS) > 0) {
                return TimeUnit.MICROSECONDS
            }
            return TimeUnit.NANOSECONDS
        }

        private fun abbreviate(unit: TimeUnit): String {
            return when (unit) {
                TimeUnit.NANOSECONDS -> "ns"
                TimeUnit.MICROSECONDS -> "\u03bcs" // μs
                TimeUnit.MILLISECONDS -> "ms"
                TimeUnit.SECONDS -> "s"
                else -> throw AssertionError()
            }
        }
    }
}