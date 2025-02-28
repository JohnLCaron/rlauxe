package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.core.*

// per round
data class AuditState(
    val name: String,
    val roundIdx: Int,
    val nmvrs: Int,
    val auditWasDone: Boolean,
    val auditIsComplete: Boolean,
    val contests: List<ContestUnderAudit>,
) {
    fun show() =
        "AuditState($name, $roundIdx, nmvrs=$nmvrs, auditWasDone=$auditWasDone, auditIsComplete=$auditIsComplete)" +
                " ncontests=${contests.size} ncontestsDone=${contests.filter { it.done }.count()}"
}

data class EstimationRoundResult(
    val roundIdx: Int,
    val strategy: String,
    val fuzzPct: Double,
    val startingTestStatistic: Double,
    val startingRates: ClcaErrorRates? = null, // aprioti error rates (clca only)
    val estimatedDistribution: List<Int>,   // distribution of estimated sample size; currently deciles
) {
    override fun toString() = "round=$roundIdx estimatedDistribution=$estimatedDistribution fuzzPct=$fuzzPct " +
            " startingRates=$startingRates"
}

data class AuditRoundResult(
    val roundIdx: Int,
    val estSampleSize: Int,   // estimated sample size
    val maxBallotIndexUsed: Int,  // maximum ballot index (for multicontest audits)
    val pvalue: Double,       // last pvalue when testH0 terminates
    val samplesNeeded: Int,   // first sample when pvalue < riskLimit
    val samplesUsed: Int,     // sample count when testH0 terminates
    val status: TestH0Status, // testH0 status
    val measuredMean: Double, // measured population mean
    val startingRates: ClcaErrorRates? = null, // aprioti error rates (clca only)
    val measuredRates: ClcaErrorRates? = null, // measured error rates (clca only)
) {
    override fun toString() = "round=$roundIdx estSampleSize=$estSampleSize maxBallotIndexUsed=$maxBallotIndexUsed " +
            " pvalue=$pvalue samplesNeeded=$samplesNeeded samplesUsed=$samplesUsed status=$status"
}
