package org.cryptobiotic.rlauxe.core

enum class TestH0Status(val complete: Boolean, val success: Boolean) {
    InProgress(false, false),

    // possible returns from RiskTestingFn
    StatRejectNull(true, true), // statistical rejection of H0
    LimitReached(false, false),  // cant tell from the number of samples available
    //// only when sampling without replacement all the way close to Nc
    SampleSumRejectNull(true, true), // SampleSum > Nc / 2, so we know H0 is false
    AcceptNull(true, false), // SampleSum + (all remaining ballots == 1) < Nc / 2, so we know that H0 is true.

    // contest status
    ContestMisformed(true, false), // Contest incorrectly formed
    MinMargin(true, false), // margin too small for RLA to efficiently work
    TooManyPhantoms(true, false), // too many phantoms, makes margin < 0
    FailMaxSamplesAllowed(true, false),  // estimated samples greater than maximum samples allowed
}

data class TestH0Result(
    val status: TestH0Status,  // how did the test conclude?
    val sampleCount: Int,      // number of samples used in testH0
    val sampleFirstUnderLimit: Int, // first sample index with pvalue with risk < limit, one based
    val pvalueMin: Double,    // smallest pvalue in the sequence
    val pvalueLast: Double,    // last pvalue
    val tracker: SampleTracker,
) {
    override fun toString() = buildString {
        append("TestH0Result status=$status")
        append(" sampleCount=$sampleCount")
        append(" sampleFirstUnderLimit=${sampleFirstUnderLimit}")
        append(" pvalueMin=${pvalueMin}")
        append(" pvalueLast=${pvalueLast}")
    }
}

interface RiskTestingFn {
    fun testH0(
        maxSamples: Int,
        terminateOnNullReject: Boolean,
        startingTestStatistic: Double = 1.0,
        drawSample: () -> Double,
    ): TestH0Result
}