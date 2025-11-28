package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.estimate.makeFlippedMvrs
import org.cryptobiotic.rlauxe.oneaudit.makeOneAuditTest
import kotlin.random.Random

// Generate OA contest, do full audit
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

        val (contestOA, oaCvrs) = makeOneAuditTest(
            margin,
            Nc,
            cvrFraction = cvrPercent,
            undervoteFraction = underVotePct,
            phantomFraction = phantomPct
        )
        val oaMvrs = makeFuzzedCvrsFrom(listOf(contestOA.contest), oaCvrs, mvrsFuzzPct)

        val oneaudit = WorkflowTesterOneAudit(
            config=config,
            listOf(contestOA),
            MvrManagerForTesting(oaCvrs, oaMvrs, config.seed))

        return ContestAuditTask(
            name(),
            oneaudit,
            parameters + mapOf("cvrPercent" to cvrPercent, "fuzzPct" to mvrsFuzzPct, "auditType" to 1.0)
        )
    }
}

// Generate OA contest, do audit in a single round
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

    override fun generateNewTask(): ClcaSingleRoundWorkflowTask {
        val config = auditConfigIn ?: AuditConfig(
            AuditType.ONEAUDIT, true,
            simFuzzPct = mvrsFuzzPct,
            oaConfig = OneAuditConfig(strategy= OneAuditStrategyType.reportedMean, )
        )

        val (contestOA, oaCvrs) =
            makeOneAuditTest(
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

        val oneaudit = WorkflowTesterOneAudit(config=config, listOf(contestOA), MvrManagerForTesting(oaCvrs, oaMvrs, config.seed))
        return ClcaSingleRoundWorkflowTask(
            name(),
            oneaudit,
            auditor = OneAuditAssertionAuditor(),
            oaMvrs,
            parameters + mapOf("mvrsFuzzPct" to mvrsFuzzPct, "auditType" to 1.0),
            quiet,
        )
    }
}

// Generate OA contest where the pools have 2 card styles so Nb > Nc, then do audit in a single round
class OneAuditSingleRoundWithDilutedMargin(
    val Nc: Int,
    val margin: Double,
    val underVotePct: Double,
    val phantomPct: Double,
    val cvrPercent: Double,
    val extraInPool: Int,
    val mvrsFuzzPct: Double,
    val parameters : Map<String, Any>,
    val auditConfigIn: AuditConfig? = null,
    val quiet: Boolean = true,
): ContestAuditTaskGenerator {

    override fun name() = "ClcaSingleRoundAuditTaskGenerator"

    override fun generateNewTask(): ClcaSingleRoundWorkflowTask {
        val config = auditConfigIn ?: AuditConfig(
            AuditType.ONEAUDIT, true,
            simFuzzPct = mvrsFuzzPct,
            oaConfig = OneAuditConfig(strategy= OneAuditStrategyType.reportedMean, )
        )

        val (contestOA, mvrs, cards) =
            makeOneAuditTest(
                margin,
                Nc,
                cvrFraction = cvrPercent,
                undervoteFraction = underVotePct,
                phantomFraction = phantomPct,
                hasStyle = config.hasStyle,
                extraInPool = extraInPool
            )

        // different seed each time
        val manager =  MvrManagerFromManifest(cards, mvrs, listOf(contestOA.contest.info()), simFuzzPct= mvrsFuzzPct, Random.nextLong())
        val oneaudit = WorkflowTesterOneAudit(config=config, listOf(contestOA), manager)
        return ClcaSingleRoundWorkflowTask(
            name(),
            oneaudit,
            auditor = OneAuditAssertionAuditor(),
            mvrs,
            parameters + mapOf("mvrsFuzzPct" to mvrsFuzzPct, "auditType" to 1.0),
            quiet,
        )
    }
}