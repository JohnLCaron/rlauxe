package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.*
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

    override fun generateNewTask(): ContestAuditTask {
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
            hasStyle=useConfig.hasStyle
        )
        val testMvrs = makeFuzzedCvrsFrom(listOf(rcontest.contest), testCvrs, mvrsFuzzPct) // this will fail

        val clca = WorkflowTesterClca(
            useConfig, emptyList(), listOf(rcontest),
            MvrManagerForTesting(testCvrs, testMvrs, useConfig.seed),
        )
        return ContestAuditTask(
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

    override fun generateNewTask(): ClcaSingleRoundSingleContestAuditTask {
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
            hasStyle=useConfig.hasStyle
        )
        val testMvrs = makeFuzzedCvrsFrom(listOf(rcontest.contest), testCvrs, mvrsFuzzPct) // this will fail

        val raireAudit = WorkflowTesterClca(
            useConfig, emptyList(), listOf(rcontest),
            MvrManagerForTesting(testCvrs, testMvrs, useConfig.seed),
        )

        return ClcaSingleRoundSingleContestAuditTask(
            name(),
            raireAudit,
            testMvrs,
            parameters + mapOf("mvrsFuzzPct" to mvrsFuzzPct, "auditType" to 4.0),
            quiet = true,
            auditor = ClcaAssertionAuditor(),
        )
    }
}
