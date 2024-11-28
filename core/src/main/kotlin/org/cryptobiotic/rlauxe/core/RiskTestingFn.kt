package org.cryptobiotic.rlauxe.core

enum class TestH0Status(val fail: Boolean) {
    StatRejectNull(false), // statistical rejection of H0
    LimitReached(true), // cant tell from the number of samples available

    //// only when sampling without replacement all the way to N, in practice, this never happens I think
    SampleSumRejectNull(false), // SampleSum > N * t, so we know H0 is false
    AcceptNull(true), // SampleSum + (all remaining ballots == 1) < N * t, so we know that H0 is true.
}

data class TestH0Result(
    val status: TestH0Status,  // how did the test conclude?
    val sampleCount: Int,   // number of samples used
    val sampleMean: Double, // average of the assort values in the sample
    val pvalues: List<Double>,  // set of pvalues
    val bets: List<Double>,  // ni
    val samplingErrors: List<Int> = emptyList(),  // p0,p1,p2,p3,p4 count
) {
    override fun toString() = buildString {
        append("TestH0Result status=$status")
        append("  sampleCount=$sampleCount")
        append("  sampleMean=$sampleMean")
    }
}

interface RiskTestingFn {
    fun testH0(
        maxSample: Int,
        terminateOnNullReject: Boolean,
        showDetails: Boolean = false,
        drawSample: () -> Double,
    ): TestH0Result
}