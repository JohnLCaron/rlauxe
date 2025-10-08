package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.estimate.makeFlippedMvrs
import org.cryptobiotic.rlauxe.estimate.makeFuzzedCvrsFrom

// mvrsFuzzPct=fuzzPct, nsimEst = nsimEst
class OneAuditContestAuditTaskGenerator(
    val Nc: Int,
    val margin: Double,
    val underVotePct: Double,
    val phantomPct: Double,
    val cvrPercent: Double,
    val mvrsFuzzPct: Double,
    val parameters : Map<String, Any>,
    val auditConfigIn: AuditConfig? = null,
    val nsimEst: Int = 100,
) : ContestAuditTaskGenerator {
    override fun name() = "OneAuditWorkflowTaskGenerator"

    override fun generateNewTask(): ContestAuditTask {
        val auditConfig = auditConfigIn ?: AuditConfig(
            AuditType.ONEAUDIT, true, nsimEst = nsimEst,
            oaConfig = OneAuditConfig(strategy= OneAuditStrategyType.reportedMean, simFuzzPct = mvrsFuzzPct)
        )

        val (contestOA, _, oaCvrs) = makeOneContestUA(margin, Nc, cvrPercent = cvrPercent, undervotePercent = underVotePct, phantomPercent=phantomPct)
        val oaMvrs = makeFuzzedCvrsFrom(listOf(contestOA.contest), oaCvrs, mvrsFuzzPct)

        val oneaudit = OneAudit(auditConfig=auditConfig, listOf(contestOA),
            MvrManagerClcaForTesting(oaCvrs, oaMvrs, auditConfig.seed))
        return ContestAuditTask(
            name(),
            oneaudit,
            parameters + mapOf("cvrPercent" to cvrPercent, "fuzzPct" to mvrsFuzzPct, "auditType" to 1.0)
        )
    }
}

// Do the audit in a single round, dont use estimateSampleSizes
class OneAuditSingleRoundAuditTaskGenerator(
    val Nc: Int,
    val margin: Double,
    val underVotePct: Double,
    val phantomPct: Double,
    val cvrPercent: Double,
    val mvrsFuzzPct: Double,
    val parameters : Map<String, Any>,
    val auditConfigIn: AuditConfig? = null,
    val nsimEst: Int = 100,
    val quiet: Boolean = true,
    val p2flips: Double? = null,
    val p1flips: Double? = null,
): ContestAuditTaskGenerator {

    override fun name() = "ClcaSingleRoundAuditTaskGenerator"

    override fun generateNewTask(): ClcaSingleRoundAuditTask {
        val auditConfig = auditConfigIn ?: AuditConfig(
            AuditType.ONEAUDIT, true, nsimEst = nsimEst,
            oaConfig = OneAuditConfig(strategy= OneAuditStrategyType.reportedMean, simFuzzPct = mvrsFuzzPct)
        )

        val (contestOA, _, oaCvrs) =
            makeOneContestUA(margin, Nc, cvrPercent = cvrPercent, undervotePercent = underVotePct, phantomPercent=phantomPct)
        val oaMvrs =  if (p2flips != null || p1flips != null) {
            makeFlippedMvrs(oaCvrs, Nc, p2flips, p1flips)
        } else {
            makeFuzzedCvrsFrom(listOf(contestOA.contest), oaCvrs, mvrsFuzzPct)
        }

        val oneaudit = OneAudit(auditConfig=auditConfig, listOf(contestOA), MvrManagerClcaForTesting(oaCvrs, oaMvrs, auditConfig.seed))
        return ClcaSingleRoundAuditTask(
            name(),
            oneaudit,
            oaMvrs,
            parameters + mapOf("mvrsFuzzPct" to mvrsFuzzPct, "auditType" to 1.0),
            quiet,
            auditor = OneAuditAssertionAuditor(),
        )
    }
}


