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
    val sampleCount: Int,   // number of samples used in testH0
    val sampleMean: Double, // average of the assort values in the sample
    val pvalues: List<Double>,  // pvalues_i
    val bets: List<Double>,  // lamda_i
    val errorRates: ErrorRates,  // p2o,p1o,p1u,p2u count of errors (clca only)
) {
    override fun toString() = buildString {
        append("TestH0Result status=$status")
        append(" sampleCount=$sampleCount")
        append(" pvalue=${pvalues.last()}")
    }
}

interface RiskTestingFn {
    fun testH0(
        maxSamples: Int,
        terminateOnNullReject: Boolean,
        showSequences: Boolean = false,
        startingTestStatistic: Double = 1.0,
        drawSample: () -> Double,
    ): TestH0Result
}