package org.cryptobiotic.rlauxe.core

// NOTE: leave them in this order, as contest status is min rank
enum class TestH0Status(val rank: Int, val complete: Boolean, val success: Boolean) {
    InProgress(0,false, false),

    // contest status
    ContestMisformed(1,true, false), // Contest incorrectly formed
    MinMargin(2,true, false), // margin too small for RLA to efficiently work
    TooManyPhantoms(3,true, false), // too many phantoms, makes margin < 0
    FailMaxSamplesAllowed(4,true, false),  // estimated samples greater than maximum samples allowed
    AuditorRemoved(5,true, false),  // auditor decide to remove it
    NoLosers(6,true, true),  // no losers, ie ncandidates = nwinners

    // possible returns from RiskTestingFn
    LimitReached(10,false, false),  // cant tell from the number of samples available
    StatRejectNull(11,true, true), // statistical rejection of H0
    //// only when sampling without replacement all the way close to Nc
    SampleSumRejectNull(12,true, true), // SampleSum > Nc / 2, so we know H0 is false
    AcceptNull(13,true, false), // SampleSum + (all remaining ballots == 1) < Nc / 2, so we know that H0 is true.
}

data class TestH0Result(
    val status: TestH0Status,  // how did the test conclude?
    val sampleCount: Int,      // number of samples used in testH0
    val sampleFirstUnderLimit: Int, // first sample index with pvalue with risk < limit, one based TODO not needed
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