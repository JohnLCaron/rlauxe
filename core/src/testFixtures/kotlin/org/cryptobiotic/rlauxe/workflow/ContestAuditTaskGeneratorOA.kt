package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.estimate.makeFlippedMvrs
import org.cryptobiotic.rlauxe.estimate.makeFuzzedCvrsForPolling
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
        )

        // data class ContestMvrCardAndPools(
        //    val contestUA: ContestUnderAudit,
        //    val mvrs: List<Cvr>,
        //    val cards: List<AuditableCard>,
        //    val pools: List<CardPoolIF>,
        //)
        val (contestUA, mvrs, cards, pools) = makeOneAuditTest(
            margin,
            Nc,
            cvrFraction = cvrPercent,
            undervoteFraction = underVotePct,
            phantomFraction = phantomPct
        )
        // TODO should be OneAuditPairFuzzer ??
        val oaMvrs = makeFuzzedCvrsForPolling(listOf(contestUA.contest), mvrs, mvrsFuzzPct)

        val oneaudit = WorkflowTesterOneAudit(
            config=config,
            listOf(contestUA),
            MvrManagerForTesting(mvrs, oaMvrs, seed=config.seed, pools=pools)
        )

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

    override fun name() = "OneAuditSingleRoundAuditTaskGenerator"

    override fun generateNewTask(): ClcaSingleRoundWorkflowTask {
        val config = auditConfigIn ?: AuditConfig(
            AuditType.ONEAUDIT, true,
            simFuzzPct = mvrsFuzzPct,
            // use default strategy
        )

        val (contestUA, mvrs, cards, pools) = makeOneAuditTest(
                margin,
                Nc,
                cvrFraction = cvrPercent,
                undervoteFraction = underVotePct,
                phantomFraction = phantomPct
            )

        // TODO should be OneAuditPairFuzzer ??
        val oaMvrs =  if (p2flips != null || p1flips != null) {
            makeFlippedMvrs(mvrs, Nc, p2flips, p1flips)
        } else {
            makeFuzzedCvrsForPolling(listOf(contestUA.contest), mvrs, mvrsFuzzPct)
        }

        val oneaudit = WorkflowTesterOneAudit(config=config, listOf(contestUA),
            MvrManagerForTesting(mvrs, oaMvrs, seed=config.seed, pools=pools))

        return ClcaSingleRoundWorkflowTask(
            name(),
            oneaudit,
            auditor = OneAuditAssertionAuditor(pools),
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

    override fun name() = "OneAuditSingleRoundWithDilutedMargin"

    override fun generateNewTask(): ClcaSingleRoundWorkflowTask {
        val config = auditConfigIn ?: AuditConfig(AuditType.ONEAUDIT, true, simFuzzPct = mvrsFuzzPct)

        val (contestUA, mvrs, cards, pools) = makeOneAuditTest(
                margin,
                Nc,
                cvrFraction = cvrPercent,
                undervoteFraction = underVotePct,
                phantomFraction = phantomPct,
                extraInPool = extraInPool
            )

        // TODO should be OneAuditPairFuzzer ??
        // different seed each time
        val manager =  MvrManagerFromManifest(cards, mvrs, listOf(contestUA.contest.info()), seed=Random.nextLong(), simFuzzPct=mvrsFuzzPct, pools=pools)
        val oneaudit = WorkflowTesterOneAudit(config=config, listOf(contestUA), manager)
        return ClcaSingleRoundWorkflowTask(
            name(),
            oneaudit,
            auditor = OneAuditAssertionAuditor(pools),
            mvrs,
            parameters + mapOf("mvrsFuzzPct" to mvrsFuzzPct, "auditType" to 1.0),
            quiet,
        )
    }
}