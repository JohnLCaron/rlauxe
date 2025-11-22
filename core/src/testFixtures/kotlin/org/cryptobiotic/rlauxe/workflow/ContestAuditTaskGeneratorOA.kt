package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.estimate.makeFlippedMvrs
import org.cryptobiotic.rlauxe.estimate.makeFuzzedCvrsFrom
import org.cryptobiotic.rlauxe.oneaudit.makeOneContestUA

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
) : ContestAuditTaskGenerator {
    override fun name() = "OneAuditWorkflowTaskGenerator"

    override fun generateNewTask(): ContestAuditTask {
        val config = auditConfigIn ?: AuditConfig(
            AuditType.ONEAUDIT, true,
            simFuzzPct = mvrsFuzzPct,
            oaConfig = OneAuditConfig(strategy= OneAuditStrategyType.reportedMean)
        )

        val (contestOA, _, oaCvrs) = makeOneContestUA(
            margin,
            Nc,
            cvrFraction = cvrPercent,
            undervoteFraction = underVotePct,
            phantomFraction = phantomPct
        )
        val oaMvrs = makeFuzzedCvrsFrom(listOf(contestOA.contest), oaCvrs, mvrsFuzzPct)

        val oneaudit = WorkflowTesterOneAudit(auditConfig=config, listOf(contestOA),
            MvrManagerClcaForTesting(oaCvrs, oaMvrs, config.seed))
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
    val quiet: Boolean = true,
    val p2flips: Double? = null,
    val p1flips: Double? = null,
): ContestAuditTaskGenerator {

    override fun name() = "ClcaSingleRoundAuditTaskGenerator"

    override fun generateNewTask(): ClcaSingleRoundAuditTask {
        val config = auditConfigIn ?: AuditConfig(
            AuditType.ONEAUDIT, true,
            simFuzzPct = mvrsFuzzPct,
            oaConfig = OneAuditConfig(strategy= OneAuditStrategyType.reportedMean, )
        )

        val (contestOA, _, oaCvrs) =
            makeOneContestUA(
                margin,
                Nc,
                cvrFraction = cvrPercent,
                undervoteFraction = underVotePct,
                phantomFraction = phantomPct
            )

        val oaMvrs =  if (p2flips != null || p1flips != null) {
            makeFlippedMvrs(oaCvrs, Nc, p2flips, p1flips)
        } else {
            makeFuzzedCvrsFrom(listOf(contestOA.contest), oaCvrs, mvrsFuzzPct)
        }

        val oneaudit = WorkflowTesterOneAudit(auditConfig=config, listOf(contestOA), MvrManagerClcaForTesting(oaCvrs, oaMvrs, config.seed))
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


