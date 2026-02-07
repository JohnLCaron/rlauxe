package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.estimate.makeFuzzedCvrsForClca
import org.cryptobiotic.rlauxe.raire.simulateRaireTestContest

class RaireContestAuditTaskGenerator(
    val Nc: Int,
    val margin: Double,
    val underVotePct: Double,
    val phantomPct: Double,
    val mvrsFuzzPct: Double,
    val parameters : Map<String, Any>,
    val auditConfig: AuditConfig? = null,
    val clcaConfigIn: ClcaConfig? = null,
    val nsimEst: Int = 100,
    ): ContestAuditTaskGenerator {

    override fun name() = "RaireWorkflowTaskGenerator"

    override fun generateNewTask(): SingleContestAuditTask {
        val useConfig = auditConfig ?: AuditConfig(
            AuditType.CLCA, true, nsimEst = nsimEst,
            clcaConfig = clcaConfigIn ?: ClcaConfig()
        )

        val (rcontest, testCvrs) = simulateRaireTestContest(
            N = Nc,
            contestId = 111,
            ncands = 4,
            minMargin = margin,
            undervotePct = underVotePct,
            phantomPct = phantomPct,
            quiet = true,
        )
        val testMvrs = makeFuzzedCvrsForClca(listOf(rcontest.contest.info()), testCvrs, mvrsFuzzPct) // this will fail

        val clca = WorkflowTesterClca(
            useConfig, emptyList(), listOf(rcontest),
            MvrManagerForTesting(cvrs=testCvrs, mvrs=testMvrs, useConfig.seed),
        )
        return SingleContestAuditTask(
            name(),
            clca,
            // testMvrs,
            parameters + mapOf("mvrsFuzzPct" to mvrsFuzzPct, "auditType" to 4.0)
        )
    }
}

// Do the audit in a single round, dont use estimateSampleSizes
class RaireSingleRoundAuditTaskGenerator(
    val Nc: Int,
    val margin: Double,
    val underVotePct: Double,
    val phantomPct: Double,
    val mvrsFuzzPct: Double,
    val parameters : Map<String, Any>,
    val auditConfig: AuditConfig? = null,
    val clcaConfigIn: ClcaConfig? = null,
    val nsimEst: Int = 100,
): ContestAuditTaskGenerator {

    override fun name() = "ClcaSingleRoundAuditTaskGenerator"

    override fun generateNewTask(): ClcaSingleRoundWorkflowTask {
        val useConfig = auditConfig ?: AuditConfig(
            AuditType.CLCA, true, nsimEst = nsimEst,
            clcaConfig = clcaConfigIn ?: ClcaConfig()
        )

        val (rcontest, testCvrs) = simulateRaireTestContest(
            N = Nc,
            contestId = 111,
            ncands = 4,
            minMargin = margin,
            undervotePct = underVotePct,
            phantomPct = phantomPct,
            quiet = true,
        )
        val testMvrs = makeFuzzedCvrsForClca(listOf(rcontest.contest.info()), testCvrs, mvrsFuzzPct) // this will fail

        val raireAudit = WorkflowTesterClca(
            useConfig, emptyList(), listOf(rcontest),
            MvrManagerForTesting(testCvrs, testMvrs, useConfig.seed),
        )

        return ClcaSingleRoundWorkflowTask(
            name(),
            raireAudit,
            auditor = ClcaAssertionAuditor(),
            testMvrs,
            parameters + mapOf("mvrsFuzzPct" to mvrsFuzzPct, "auditType" to 4.0),
            quiet = true,
        )
    }
}
