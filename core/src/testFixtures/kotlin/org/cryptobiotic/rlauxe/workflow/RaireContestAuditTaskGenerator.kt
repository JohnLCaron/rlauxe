package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.estimate.makeFuzzedCvrsFrom
import org.cryptobiotic.rlauxe.raire.simulateRaireTestData

class RaireContestAuditTaskGenerator(
    val Nc: Int, // including undervotes but not phantoms
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
            clcaConfig = clcaConfigIn ?: ClcaConfig(ClcaStrategyType.noerror)
        )

        val (rcontest, testCvrs) = simulateRaireTestData(
            N = Nc,
            contestId = 111,
            ncands = 4,
            minMargin = margin,
            undervotePct = underVotePct,
            phantomPct = phantomPct,
            quiet = true
        )
        var testMvrs = makeFuzzedCvrsFrom(listOf(rcontest.contest), testCvrs, mvrsFuzzPct) // this will fail

        val clca = ClcaAudit(
            useConfig, emptyList(), listOf(rcontest),
            StartTestBallotCardsClca(testCvrs, testMvrs, useConfig.seed)
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
    val Nc: Int, // including undervotes but not phantoms
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

    override fun generateNewTask(): ClcaSingleRoundAuditTask {
        val useConfig = auditConfig ?: AuditConfig(
            AuditType.CLCA, true, nsimEst = nsimEst,
            clcaConfig = clcaConfigIn ?: ClcaConfig(ClcaStrategyType.noerror)
        )

        val (rcontest, testCvrs) = simulateRaireTestData(
            N = Nc,
            contestId = 111,
            ncands = 4,
            minMargin = margin,
            undervotePct = underVotePct,
            phantomPct = phantomPct,
            quiet = true
        )
        var testMvrs = makeFuzzedCvrsFrom(listOf(rcontest.contest), testCvrs, mvrsFuzzPct) // this will fail

        val raireAudit = ClcaAudit(
            useConfig, emptyList(), listOf(rcontest),
            StartTestBallotCardsClca(testCvrs, testMvrs, useConfig.seed)
        )

        return ClcaSingleRoundAuditTask(
            name(),
            raireAudit,
            testMvrs,
            parameters + mapOf("mvrsFuzzPct" to mvrsFuzzPct, "auditType" to 4.0),
            quiet = true,
            auditor = AuditClcaAssertion(),
        )
    }
}
