package org.cryptobiotic.rlauxe.corla

import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.estimate.*
import org.cryptobiotic.rlauxe.workflow.*

class CorlaSingleRoundAuditTaskGenerator(
    val Nc: Int,
    val margin: Double,
    val underVotePct: Double,
    val phantomPct: Double,
    val mvrsFuzzPct: Double,
    val parameters : Map<String, Any>,
    val auditConfig: AuditConfig? = null,
    val clcaConfigIn: ClcaConfig? = null,
    val nsimEst: Int = 100,
    val quiet: Boolean = true,
    val p2flips: Double? = null,
    val p1flips: Double? = null,
): ContestAuditTaskGenerator {
    override fun name() = "CorlaSingleRoundAuditTaskGenerator"

    override fun generateNewTask(): ClcaSingleRoundSingleContestAuditTask {
        val useConfig = auditConfig ?: AuditConfig(
            AuditType.CLCA, true, nsimEst = nsimEst,
            clcaConfig = clcaConfigIn ?: ClcaConfig()
        )

        val sim =
            ContestSimulation.make2wayTestContest(Nc = Nc, margin, undervotePct = underVotePct, phantomPct = phantomPct)
        val testCvrs = sim.makeCvrs() // includes undervotes and phantoms
        val testMvrs = if (p2flips != null || p1flips != null) makeFlippedMvrs(testCvrs, Nc, p2flips, p1flips) else
            makeFuzzedCvrsFrom(listOf(sim.contest), testCvrs, mvrsFuzzPct)

        val clcaWorkflow = WorkflowTesterClca(useConfig, listOf(sim.contest), emptyList(),
                                 MvrManagerClcaForTesting(testCvrs, testMvrs, useConfig.seed))
        return ClcaSingleRoundSingleContestAuditTask(
            name(),
            clcaWorkflow,
            testMvrs,
            parameters + mapOf("mvrsFuzzPct" to mvrsFuzzPct, "auditType" to 3.0),
            quiet,
            auditor = AuditCorlaAssertion(),
        )
    }
}

class CorlaContestAuditTaskGenerator(
    val Nc: Int,
    val margin: Double,
    val underVotePct: Double,
    val phantomPct: Double,
    val mvrsFuzzPct: Double,
    val parameters : Map<String, Any>,
    val auditConfigIn: AuditConfig? = null,
    val clcaConfigIn: ClcaConfig? = null,
    val p2flips: Double? = null,
): ContestAuditTaskGenerator {
    override fun name() = "CorlaWorkflowTaskGenerator"

    override fun generateNewTask(): ContestAuditTask {
        val auditConfig = auditConfigIn ?:
        AuditConfig(
            AuditType.CLCA, true, nsimEst = 10,
            clcaConfig = clcaConfigIn ?: ClcaConfig(ClcaStrategyType.fuzzPct, mvrsFuzzPct)
        )

        val sim = ContestSimulation.make2wayTestContest(Nc=Nc, margin, undervotePct=underVotePct, phantomPct=phantomPct)
        val testCvrs = sim.makeCvrs() // includes undervotes and phantoms

        val testMvrs =  if (p2flips != null) makeFlippedMvrs(testCvrs, Nc, p2flips, 0.0) else
            makeFuzzedCvrsFrom(listOf(sim.contest), testCvrs, mvrsFuzzPct)

        val clca = CorlaAudit(auditConfig, listOf(sim.contest), MvrManagerClcaForTesting(testCvrs, testMvrs, auditConfig.seed), quiet = true)
        return ContestAuditTask(
            "genAuditWithErrorsPlots mvrsFuzzPct = $mvrsFuzzPct",
            clca,
            parameters + mapOf("mvrsFuzzPct" to mvrsFuzzPct, "auditType" to 3.0)
        )
    }
}

class CorlaAudit(
    val auditConfig: AuditConfig,
    contestsToAudit: List<Contest>, // the contests you want to audit
    val mvrManagerForTesting: MvrManagerClcaForTesting, // mutable
    val quiet: Boolean = false,
): AuditWorkflow() {
    private val contestsUA: List<ContestUnderAudit>
    private val auditRounds = mutableListOf<AuditRound>()

    init {
        require (auditConfig.auditType == AuditType.CLCA)
        contestsUA = contestsToAudit.map { ContestUnderAudit(it, isClca=true, hasStyle=auditConfig.hasStyle).addStandardAssertions() }
    }

    override fun runAuditRound(auditRound: AuditRound, quiet: Boolean): Boolean  {
        val complete = runClcaAuditRound(auditConfig, auditRound.contestRounds, mvrManagerForTesting, auditRound.roundIdx,
            auditor = AuditCorlaAssertion()
        )
        auditRound.auditWasDone = true
        auditRound.auditIsComplete = complete
        return complete
    }

    override fun auditConfig() =  this.auditConfig
    override fun auditRounds() = auditRounds
    override fun contestsUA(): List<ContestUnderAudit> = contestsUA
    override fun mvrManager() = mvrManagerForTesting
}

/////////////////////////////////////////////////////////////////////////////////

// See ComparisonAudit.riskMeasurement() in colorado-rla us.freeandfair.corla.model
class AuditCorlaAssertion(val quiet: Boolean = true): ClcaAssertionAuditorIF {

    override fun run(
        auditConfig: AuditConfig,
        contestRound: ContestRound,
        assertionRound: AssertionRound,
        sampler: Sampler,
        roundIdx: Int,
    ): TestH0Result {
        val contestUA = contestRound.contestUA
        val cassertion = assertionRound.assertion as ClcaAssertion
        val cassorter = cassertion.cassorter
        // val sampler = ClcaWithoutReplacement(contest.id, cvrPairs, cassorter, allowReset = false)

        // Corla(val N: Int, val riskLimit: Double, val reportedMargin: Double, val noerror: Double,
        //    val p1: Double, val p2: Double, val p3: Double, val p4: Double): RiskTestingFn
        val testFn = Corla(
            N = contestUA.Nb,
            riskLimit = auditConfig.riskLimit,
            reportedMargin = cassertion.assorter.reportedMargin(),
            noerror = cassorter.noerror(),
            p1 = 0.0, p2 = 0.0, p3 = 0.0, p4 = 0.0, // TODO
        )

        val testH0Result = testFn.testH0(sampler.maxSamples(), terminateOnNullReject = true) { sampler.sample() }
        val samplesNeeded = testH0Result.sampleCount

        val measuredCounts = if (testH0Result.tracker is ClcaErrorRatesIF)
            (testH0Result.tracker as ClcaErrorRatesIF).errorCounts()
        else
            null

        assertionRound.auditResult = AuditRoundResult(
            roundIdx,
            nmvrs = sampler.nmvrs(),
            maxBallotIndexUsed = sampler.maxSampleIndexUsed(),
            pvalue = testH0Result.pvalueLast,
            samplesUsed = samplesNeeded,
            status = testH0Result.status,
            measuredMean = testH0Result.tracker.mean(),
            measuredCounts = measuredCounts,
        )

        if (!quiet) println(" ${contestUA.name} ${assertionRound.auditResult}")
        return testH0Result
    }
}