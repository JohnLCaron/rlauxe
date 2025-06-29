package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.estimate.makeFlippedMvrs
import org.cryptobiotic.rlauxe.estimate.makeFuzzedCvrsFrom
import org.cryptobiotic.rlauxe.oneaudit.makeContestOA
import org.cryptobiotic.rlauxe.oneaudit.makeTestMvrs

// mvrsFuzzPct=fuzzPct, nsimEst = nsimEst
class OneAuditContestAuditTaskGenerator(
    val Nc: Int, // including undervotes but not phantoms
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

        val contestOA2 = makeContestOA(margin, Nc, cvrPercent = cvrPercent, undervotePercent = underVotePct, phantomPercent=phantomPct)
        val oaCvrs = makeTestMvrs(contestOA2)
        val oaMvrs = makeFuzzedCvrsFrom(listOf(contestOA2), oaCvrs, mvrsFuzzPct)

        val oneaudit = OneAudit(auditConfig=auditConfig, listOf(contestOA2),
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
    val Nc: Int, // including undervotes but not phantoms
    val margin: Double,
    val underVotePct: Double,
    val phantomPct: Double,
    val cvrPercent: Double,
    val mvrsFuzzPct: Double,
    val parameters : Map<String, Any>,
    val auditConfigIn: AuditConfig? = null,
    val nsimEst: Int = 100,
    val quiet: Boolean = true,
    val skewPct: Double = 0.0,
    val p2flips: Double? = null,
    val p1flips: Double? = null,
): ContestAuditTaskGenerator {

    override fun name() = "ClcaSingleRoundAuditTaskGenerator"

    override fun generateNewTask(): ClcaSingleRoundAuditTask {
        val auditConfig = auditConfigIn ?: AuditConfig(
            AuditType.ONEAUDIT, true, nsimEst = nsimEst,
            oaConfig = OneAuditConfig(strategy= OneAuditStrategyType.reportedMean, simFuzzPct = mvrsFuzzPct)
        )

        val contestOA = makeContestOA(margin, Nc, cvrPercent = cvrPercent, undervotePercent = underVotePct,
            phantomPercent=phantomPct, skewPct = skewPct)
        val oaCvrs = makeTestMvrs(contestOA)
        val oaMvrs =  if (p2flips != null || p1flips != null) {
            makeFlippedMvrs(oaCvrs, Nc, p2flips, p1flips)
        } else {
            makeFuzzedCvrsFrom(listOf(contestOA), oaCvrs, mvrsFuzzPct)
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


