package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.core.PluralityErrorRates
import org.cryptobiotic.rlauxe.util.secureRandom

enum class AuditType { POLLING, CLCA, ONEAUDIT;
    fun isClca() = (this == CLCA)
    fun isOA() = (this == ONEAUDIT)
    fun isPolling() = (this == POLLING)
}

data class AuditConfig(
    val auditType: AuditType,
    val hasStyle: Boolean, // has Card Style Data (CSD), i.e. we know which contests each card/ballot contains
                           // I think all this means is "cvrs have undervotes" aka "cvrs are complete"
    val riskLimit: Double = 0.05,
    val seed: Long = secureRandom.nextLong(), // determines sample order. set carefully to ensure truly random.

    // simulation control
    val nsimEst: Int = 100, // number of simulation estimation trials
    val quantile: Double = 0.80, // use this percentile success for estimated sample size
    val contestSampleCutoff: Int? = 30000, // use this number of cvrs in the estimation, set to null to use all
    val simFuzzPct: Double? = null, // for simulating the estimation and testMvr fuzzing

    // audit sample size control
    val removeCutoffContests: Boolean = false, // remove contests that need more samples than contestSampleCutoff
    val minRecountMargin: Double = 0.005, // do not audit contests less than this recount margin
    val removeTooManyPhantoms: Boolean = false, // do not audit contests if phantoms > margin
    val auditSampleLimit: Int? = null, // limit audit sample size; audit all samples, ignore risk limit

    // old config, replace by error strategies
    val pollingConfig: PollingConfig = PollingConfig(),
    val clcaConfig: ClcaConfig = ClcaConfig(ClcaStrategyType.generalAdaptive),
    val oaConfig: OneAuditConfig = OneAuditConfig(OneAuditStrategyType.optimalComparison, useFirst = true),

    // default error strategies
    val pollingErrorStrategy: PollingErrorStrategy = PollingErrorStrategy(),
    val clcaBettingStrategy: ClcaBettingStrategy = ClcaBettingStrategy(),
    val oaBettingStrategy: OneAuditBettingStrategy = OneAuditBettingStrategy(),

    val skipContests: List<Int> = emptyList(),
    val version: Double = 2.0,
) {
    val isClca = auditType == AuditType.CLCA
    val isOA = auditType == AuditType.ONEAUDIT
    val isPolling = auditType == AuditType.POLLING

    fun simFuzzPct() = simFuzzPct

    override fun toString() = buildString {
        appendLine("AuditConfig(auditType=$auditType, hasStyle=$hasStyle, riskLimit=$riskLimit, seed=$seed version=$version" )
        appendLine("  nsimEst=$nsimEst, quantile=$quantile, simFuzzPct=$simFuzzPct,")
        append("  minRecountMargin=$minRecountMargin removeTooManyPhantoms=$removeTooManyPhantoms")
        if (contestSampleCutoff != null) { append(" contestSampleCutoff=$contestSampleCutoff removeCutoffContests=$removeCutoffContests") }
        if (auditSampleLimit != null) { append(" auditSampleLimit=$auditSampleLimit (risk measuring audit)") }
        appendLine()

        if (skipContests.isNotEmpty()) { appendLine("  skipContests=$skipContests") }
        when (auditType) {
            AuditType.POLLING -> appendLine("  $pollingConfig")
            AuditType.CLCA -> appendLine("  $clcaConfig")
            AuditType.ONEAUDIT -> appendLine("  $oaConfig")
        }
    }
    fun strategy() : String {
        return when (auditType) {
            AuditType.POLLING -> "polling"
            AuditType.CLCA -> clcaConfig.strategy.toString()
            AuditType.ONEAUDIT -> oaConfig.strategy.toString()
        }
    }
}

// uses AlphaMart
data class PollingConfig(
    val d: Int = 100,  // shrinkTrunc weight TODO study what this should be, eg for noerror assumption?
)

// oracle: use actual measured error rates, testing only
// fuzzPct: model errors with fuzz simulation at first, then measured
// apriori: pass in apriori errorRates at first, then measured
// phantoms: use phantom rates at first.
// previous: use phantom rates at first, then measured.
// optimalComparison:  OptimalComparisonNoP1, assume P1 = 0, closed form solution for lamda

// Error Rates: the minimum p1o is always the phantom rate. Subsequent rounds, always use measured rates.
//  apriori: pass in apriori errorRates for first round.
//  fuzzPct: ClcaErrorTable.getErrorRates(contest.ncandidates, clcaConfig.simFuzzPct) for first round.
//  oracle: use actual measured error rates for first round. (violates martingale condition)
enum class ClcaStrategyType { generalAdaptive, apriori, fuzzPct, oracle  }
data class ClcaConfig(
    val strategy: ClcaStrategyType = ClcaStrategyType.generalAdaptive,
    val fuzzPct: Double? = null, // use to generate apriori errorRates for simulation, only used when ClcaStrategyType = fuzzPct
    val pluralityErrorRates: PluralityErrorRates? = null, // use as apriori errorRates for simulation and audit. TODO use SampleErrorTracker
    val d: Int = 100,  // shrinkTrunc weight for error rates
)

// reportedMean: eta0 = reportedMean, shrinkTrunk
// bet99: eta0 = reportedMean, 99% max bet
// eta0Eps: eta0 = upper*(1 - eps), shrinkTrunk
// optimalComparison = uses bettingMart with OptimalComparisonNoP1
// note OneAudit uses ClcaConfig for error estimation
enum class OneAuditStrategyType { reportedMean, bet99, eta0Eps, optimalComparison }
data class OneAuditConfig(
    val strategy: OneAuditStrategyType = OneAuditStrategyType.optimalComparison,
    val d: Int = 100,  // shrinkTrunc weight
    val useFirst: Boolean = false, // use actual cvrs for estimation
)

////////////////////////////////////////////////////////////
// not used yet
// uses AlphaMart with TruncShrinkage
data class PollingErrorStrategy(
    val d: Int = 100,  // shrinkTrunc weight TODO study what this should be, eg for noerror assumption?
)

// Error Rates: the minimum p2o is always the phantom rate. Subsequent rounds, always use measured rates.
//  apriori: pass in apriori errorRates for first round.
//  fuzzPct: ClcaErrorTable.getErrorRates(contest.ncandidates, clcaConfig.simFuzzPct) for first round.
//  noerrors: assume noerrors on first round.
//  oracle: use actual measured error rates for first round. (violates martingale condition)
// optimalComparison:  OptimalComparisonNoP1, assume P1 = 0, closed form solution for lamda.
enum class ClcaBettingStrategyType { apriori, fuzzPct, noerrors, oracle, optimalComparison }
data class ClcaBettingStrategy(
    val strategy: ClcaBettingStrategyType = ClcaBettingStrategyType.noerrors,
    val fuzzPct: Double? = null, // use to generate apriori errorRates, (if null use simFuzzPct?)
    val errorRates: PluralityErrorRates? = null, // use as apriori errorRates for simulation and audit
    val d: Int = 100,  // shrinkTrunc weight for error rates
)

// reportedMean: eta0 = reportedMean, shrinkTrunk
// bet99: eta0 = reportedMean, 99% max bet
// eta0Eps: eta0 = upper*(1 - eps), shrinkTrunk
// optimalComparison = uses bettingMart with OptimalComparisonNoP1
enum class OneAuditBettingStrategyType { reportedMean, bet99, eta0Eps, optimalComparison }
data class OneAuditBettingStrategy(
    val strategy: OneAuditBettingStrategyType = OneAuditBettingStrategyType.optimalComparison,
    val d: Int = 100,  // shrinkTrunc weight
    val useFirst: Boolean = true, // use actual cvrs for estimation
)

//// clca
//  use selected ClcaBettingStrategy
//  estimation: use real cards, simulate cards with ClcaCardSimulatedErrorRates
//  auditing: simulate mvrs with simFuzzPct or ClcaSimulatedErrorRates? if simFuzzPct, see how it compares or ClcaSimulatedErrorRates

/// polling
//  use AlphaMart/TruncShrink
//  estimation: use real cards: simulate cards with PollingCardFuzzSampler with simFuzzPct. done
//  auditing: simulate mvrs with simFuzzPct

/// OneAudit
//  use OneAuditBettingStrategy and ClcaBettingStrategy
//  estimation: use real cards, simulate cards with ClcaBettingStrategy/ClcaCardSimulatedErrorRates, dont touch pool cards
//  auditing: simulate mvrs with simFuzzPct




