package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.estimate.makeFuzzedCvrsFrom
import org.cryptobiotic.rlauxe.oneaudit.makeContestOA

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
            oaConfig = OneAuditConfig(strategy= OneAuditStrategyType.default, simFuzzPct = mvrsFuzzPct)
        )

        val contestOA2 = makeContestOA(margin, Nc, cvrPercent = cvrPercent, skewVotesPercent= 0.0, undervotePercent = underVotePct, phantomPercent=phantomPct)
        val oaCvrs = contestOA2.makeTestCvrs()
        val oaMvrs = makeFuzzedCvrsFrom(listOf(contestOA2.makeContest()), oaCvrs, mvrsFuzzPct)

        val oneaudit = OneAudit(auditConfig=auditConfig, listOf(contestOA2), StartTestBallotCardsClca(oaCvrs, oaMvrs, auditConfig.seed))
        return ContestAuditTask(
            name(),
            oneaudit,
            // oaMvrs,
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
    val p2flips: Double? = null,
    val p1flips: Double? = null,
): ContestAuditTaskGenerator {

    override fun name() = "ClcaSingleRoundAuditTaskGenerator"

    override fun generateNewTask(): ClcaSingleRoundAuditTask {
        val auditConfig = auditConfigIn ?: AuditConfig(
            AuditType.ONEAUDIT, true, nsimEst = nsimEst,
            oaConfig = OneAuditConfig(strategy= OneAuditStrategyType.default, simFuzzPct = mvrsFuzzPct)
        )

        val contestOA2 = makeContestOA(margin, Nc, cvrPercent = cvrPercent, skewVotesPercent= 0.0, undervotePercent = underVotePct, phantomPercent=phantomPct)
        val oaCvrs = contestOA2.makeTestCvrs()
        val oaMvrs = makeFuzzedCvrsFrom(listOf(contestOA2.makeContest()), oaCvrs, mvrsFuzzPct)

        val oneaudit = OneAudit(auditConfig=auditConfig, listOf(contestOA2), StartTestBallotCardsClca(oaCvrs, oaMvrs, auditConfig.seed))
        return ClcaSingleRoundAuditTask(
            name(),
            oneaudit,
            oaMvrs,
            parameters + mapOf("mvrsFuzzPct" to mvrsFuzzPct, "auditType" to 1.0),
            quiet,
            auditor = OneAuditClcaAssertion(),
        )
    }
}

private val debug = false


