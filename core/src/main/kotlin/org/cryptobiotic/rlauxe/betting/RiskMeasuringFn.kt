package org.cryptobiotic.rlauxe.betting

/**
 * Keeps track of the latest sample, number of samples, and the sample sum, mean and variance.
 *
 * Its up to the method using this to not add the
 * current sample to it until the end of the iteration.
 * This ensures that the called function doesnt have access to the current sample,
 * as required by "predictable function of the data X1 , . . . , Xi−1" requirement.
 */
interface SampleTracker {
    fun last(): Double  // latest sample
    fun numberOfSamples(): Int    // total number of samples so far
    fun sum(): Double   // sum of samples so far
    fun mean(): Double   // average of samples so far
    fun variance(): Double   // variance of samples so far
    fun addSample(sample : Double)
    fun reset()
}

/** All risk measureing functions implement this */
interface RiskMeasuringFn {
    fun testH0(
        maxSamples: Int,
        terminateOnNullReject: Boolean,
        startingTestStatistic: Double = 1.0, // T, must grow to 1/riskLimit
        drawSample: () -> Double,
    ): TestH0Result
}

// NOTE: contest status is min rank of assertions
enum class TestH0Status(val rank: Int, val complete: Boolean, val success: Boolean) {
    // starting state
    InProgress(0,false, false),

    // "pre-audit" contest status
    NoLosers(1,true, true),  // no losers, ie ncandidates <= nwinners
    NoWinners(2,true, true),  // no winners, eg all candidates have < minFraction
    ContestMisformed(3,true, false), // Contest incorrectly formed
    MinMargin(4,true, false), // margin too small for RLA to efficiently work
    TooManyPhantoms(5,true, false), // too many phantoms, makes margin < 0

    FailMaxSamplesAllowed(6,true, false),  // estimated samples greater than maximum samples allowed
    AuditorRemoved(7,true, false),  // auditor decide to remove it

    // possible returns from RiskTestingFn
    LimitReached(10,false, false),  // cant tell from the number of samples available
    StatRejectNull(11,true, true), // statistical rejection of H0
    //// only happens when sampling without replacement all the way close to Nc
    SampleSumRejectNull(12,true, true), // SampleSum > Nc / 2, so we know H0 is false
    AcceptNull(13,true, false), // SampleSum + (all remaining ballots == 1) < Nc / 2, so we know that H0 is true.
}

// LOOK pvalueLast = pvalueMin when you terminate on p < risk.
//   but not when you hit maxSamples
//   but not "risk measuring" audits.
//   probably should show pmin instead of plast in viewer.
data class TestH0Result(
    val status: TestH0Status,  // how did the test conclude?
    val sampleCount: Int,      // number of samples used in testH0
    val pvalueMin: Double,     // smallest pvalue in the sequence.
    val pvalueLast: Double,    // last pvalue.
    val tracker: SampleTracker,
) {
    override fun toString() = buildString {
        append("TestH0Result status=$status")
        append(" sampleCount=$sampleCount")
        append(" pvalueMin=${pvalueMin}")
        append(" pvalueLast=${pvalueLast}")
    }
}