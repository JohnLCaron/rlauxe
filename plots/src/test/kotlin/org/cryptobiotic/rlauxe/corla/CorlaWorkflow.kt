package org.cryptobiotic.rlauxe.corla

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.estimate.*
import org.cryptobiotic.rlauxe.workflow.*

class CorlaSingleRoundAuditTaskGenerator(
    val Nc: Int, // including undervotes but not phantoms
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
): WorkflowTaskGenerator {
    override fun name() = "CorlaSingleRoundAuditTaskGenerator"

    override fun generateNewTask(): ClcaSingleRoundAuditTask {
        val useConfig = auditConfig ?: AuditConfig(
            AuditType.CLCA, true, nsimEst = nsimEst,
            clcaConfig = clcaConfigIn ?: ClcaConfig(ClcaStrategyType.noerror)
        )

        val sim =
            ContestSimulation.make2wayTestContest(Nc = Nc, margin, undervotePct = underVotePct, phantomPct = phantomPct)
        val testCvrs = sim.makeCvrs() // includes undervotes and phantoms
        val testMvrs = if (p2flips != null || p1flips != null) makeFlippedMvrs(testCvrs, Nc, p2flips, p1flips) else
            makeFuzzedCvrsFrom(listOf(sim.contest), testCvrs, mvrsFuzzPct)

        val clcaWorkflow = ClcaWorkflow(useConfig, listOf(sim.contest), emptyList(),
            BallotCardsClcaStart(testCvrs, testMvrs, useConfig.seed))
        return ClcaSingleRoundAuditTask(
            name(),
            clcaWorkflow,
            testMvrs,
            parameters + mapOf("mvrsFuzzPct" to mvrsFuzzPct, "auditType" to 3.0),
            quiet,
            auditor = AuditCorlaAssertion(),
        )
    }
}

class CorlaWorkflowTaskGenerator(
    val Nc: Int, // including undervotes but not phantoms
    val margin: Double,
    val underVotePct: Double,
    val phantomPct: Double,
    val mvrsFuzzPct: Double,
    val parameters : Map<String, Any>,
    val auditConfigIn: AuditConfig? = null,
    val clcaConfigIn: ClcaConfig? = null,
    val p2flips: Double? = null,
): WorkflowTaskGenerator {
    override fun name() = "CorlaWorkflowTaskGenerator"

    override fun generateNewTask(): WorkflowTask {
        val auditConfig = auditConfigIn ?:
        AuditConfig(
            AuditType.CLCA, true, nsimEst = 10,
            clcaConfig = clcaConfigIn ?: ClcaConfig(ClcaStrategyType.fuzzPct, mvrsFuzzPct)
        )

        val sim = ContestSimulation.make2wayTestContest(Nc=Nc, margin, undervotePct=underVotePct, phantomPct=phantomPct)
        val testCvrs = sim.makeCvrs() // includes undervotes and phantoms

        val testMvrs =  if (p2flips != null) makeFlippedMvrs(testCvrs, Nc, p2flips, 0.0) else
            makeFuzzedCvrsFrom(listOf(sim.contest), testCvrs, mvrsFuzzPct)

        val clca = CorlaWorkflow(auditConfig, listOf(sim.contest), BallotCardsClcaStart(testCvrs, testMvrs, auditConfig.seed), quiet = true)
        return WorkflowTask(
            "genAuditWithErrorsPlots mvrsFuzzPct = $mvrsFuzzPct",
            clca,
            parameters + mapOf("mvrsFuzzPct" to mvrsFuzzPct, "auditType" to 3.0)
        )
    }
}

class CorlaWorkflow(
    val auditConfig: AuditConfig,
    contestsToAudit: List<Contest>, // the contests you want to audit
    val ballotCards: BallotCardsClcaStart, // mutable
    val quiet: Boolean = false,
): RlauxWorkflowIF {
    private val contestsUA: List<ContestUnderAudit>
    // val cvrsUA: List<CvrUnderAudit>
    private val auditRounds = mutableListOf<AuditRound>()

    init {
        require (auditConfig.auditType == AuditType.CLCA)

        contestsUA = contestsToAudit.map { ContestUnderAudit(it, isComparison=true, auditConfig.hasStyles) }
        contestsUA.forEach { contest ->
            contest.makeClcaAssertions(ballotCards.cvrs)
        }
    }

    override fun runAudit(auditRound: AuditRound, quiet: Boolean): Boolean  {
        val complete = runClcaAudit(auditConfig, auditRound.contestRounds, ballotCards, auditRound.roundIdx,
            auditor = AuditCorlaAssertion())
        auditRound.auditWasDone = true
        auditRound.auditIsComplete = complete
        return complete
    }

    override fun auditConfig() =  this.auditConfig
    override fun auditRounds() = auditRounds
    override fun contestsUA(): List<ContestUnderAudit> = contestsUA
    override fun setMvrsBySampleNumber(sampleNumbers: List<Long>) {
        ballotCards.setMvrsBySampleNumber(sampleNumbers)
    }

    override fun ballotCards() = ballotCards
}

/////////////////////////////////////////////////////////////////////////////////

// See ComparisonAudit.riskMeasurement() in colorado-rla us.freeandfair.corla.model
class AuditCorlaAssertion(val quiet: Boolean = true): ClcaAssertionAuditor {

    override fun run(
        auditConfig: AuditConfig,
        contest: ContestIF,
        assertionRound: AssertionRound,
        // cvrPairs: List<Pair<Cvr, Cvr>>, // (mvr, cvr)
        sampler: Sampler,
        roundIdx: Int,
    ): TestH0Result {
        val cassertion = assertionRound.assertion as ClcaAssertion
        val cassorter = cassertion.cassorter
        // val sampler = ClcaWithoutReplacement(contest.id, cvrPairs, cassorter, allowReset = false)

        // Corla(val N: Int, val riskLimit: Double, val reportedMargin: Double, val noerror: Double,
        //    val p1: Double, val p2: Double, val p3: Double, val p4: Double): RiskTestingFn
        val testFn = Corla(
            N = contest.Nc,
            riskLimit = auditConfig.riskLimit,
            reportedMargin = cassertion.assorter.reportedMargin(),
            noerror = cassorter.noerror(),
            p1 = 0.0, p2 = 0.0, p3 = 0.0, p4 = 0.0, // TODO
        )

        val testH0Result = testFn.testH0(sampler.maxSamples(), terminateOnNullReject = true) { sampler.sample() }

        assertionRound.auditResult = AuditRoundResult(
            roundIdx,
            nmvrs = sampler.maxSamples(),
            maxBallotIndexUsed = sampler.maxSampleIndexUsed(),
            pvalue = testH0Result.pvalueLast,
            samplesNeeded = testH0Result.sampleFirstUnderLimit, // one based
            samplesUsed = testH0Result.sampleCount,
            status = testH0Result.status,
            measuredMean = testH0Result.tracker.mean(),
            measuredRates = testH0Result.tracker.errorRates(),
        )

        if (!quiet) println(" ${contest.info.name} ${assertionRound.auditResult}")
        return testH0Result
    }
}