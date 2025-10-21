package org.cryptobiotic.rlauxe.cobra

import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.estimate.*
import org.cryptobiotic.rlauxe.util.*
import org.cryptobiotic.rlauxe.workflow.*

class CobraSingleRoundAuditTaskGenerator(
    val Nc: Int,
    val reportedMean: Double,
    val p2oracle: Double,
    val p2prior: Double,
    val parameters: Map<String, Any>,
    val auditConfig: AuditConfig,
    val quiet: Boolean = true,
) : ContestAuditTaskGenerator {
    override fun name() = "CobraSingleRoundAuditTaskGenerator"

    override fun generateNewTask(): ClcaSingleRoundAuditTask {
        // the cvrs get generated with the reportedMeans.
        val testCvrs = makeCvrsByExactMean(Nc, reportedMean)

        val info = ContestInfo(
            name = "AvB",
            id = 0,
            choiceFunction = SocialChoiceFunction.PLURALITY,
            candidateNames = listToMap("A", "B"),
        )
        val contest = makeContestFromCvrs(info, testCvrs)

        // TODO: chicken or the egg
        val cobraWorkflow1 = CobraAudit(auditConfig, listOf(contest), MvrManagerClcaForTesting(testCvrs, testCvrs, auditConfig.seed), p2prior)
        val contestUA: ContestUnderAudit = cobraWorkflow1.contestsUA().first()
        val cassorter = contestUA.clcaAssertions.first().cassorter

        // then the mvrs are generated with over/understatement errors, which means the cvrs overstate the winner's margin.
        val sampler = ClcaAttackSampler(testCvrs, cassorter, p2 = p2oracle, withoutReplacement = true)
        val mvrs = sampler.mvrs

        // maybe bogus
        val cobraWorkflow2 = CobraAudit(auditConfig, listOf(contest),
            MvrManagerClcaForTesting(testCvrs, sampler.mvrs, auditConfig.seed),
            p2prior)

        return ClcaSingleRoundAuditTask(
            name(),
            cobraWorkflow2,
            mvrs,
            parameters + mapOf("p2oracle" to p2oracle, "p2prior" to p2prior),
            quiet,
            auditor = AuditCobraAssertion(p2prior),
        )
    }
}

class CobraAudit(
    val auditConfig: AuditConfig,
    contestsToAudit: List<Contest>, // the contests you want to audit
    val mvrManagerForTesting: MvrManagerClcaForTesting, // mutable
    val p2prior: Double,
) : AuditWorkflowIF {
    private val contestsUA: List<ContestUnderAudit>
    private val auditRounds = mutableListOf<AuditRound>()

    init {
        require(auditConfig.auditType == AuditType.CLCA)

        contestsUA = contestsToAudit.map { ContestUnderAudit(it, isComparison = true, auditConfig.hasStyles) }
        contestsUA.forEach { contest ->
            contest.addClcaAssertionsFromReportedMargin()
        }
    }

    override fun runAuditRound(auditRound: AuditRound, quiet: Boolean): Boolean  {
        val complete = runClcaAuditRound(auditConfig, auditRound.contestRounds, mvrManagerForTesting, auditRound.roundIdx,
            auditor = AuditCobraAssertion(p2prior)
        )
        auditRound.auditWasDone = true
        auditRound.auditIsComplete = complete
        return complete
    }

    override fun auditConfig() = this.auditConfig
    override fun auditRounds() = auditRounds
    override fun contestsUA(): List<ContestUnderAudit> = contestsUA
    override fun mvrManager() = mvrManagerForTesting
}

/////////////////////////////////////////////////////////////////////////////////

class AuditCobraAssertion(
    val p2prior: Double, // apriori rate of 2-vote overstatements
) : ClcaAssertionAuditorIF {

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

        val adaptive = AdaptiveBetting(
            Nc = contest.Nc(),
            withoutReplacement = true,
            a = cassorter.noerror(),
            d = auditConfig.clcaConfig.d,
            ClcaErrorRates(p2prior, 0.0, 0.0, 0.0)
        )

        val testFn = BettingMart(
            bettingFn = adaptive,
            Nc = contest.Nc(),
            noerror = cassorter.noerror(),
            upperBound = cassorter.upperBound(),
            withoutReplacement = true
        )

        val testH0Result = testFn.testH0(sampler.maxSamples(), terminateOnNullReject = true) { sampler.sample() }
        val samplesNeeded = testH0Result.sampleCount

        assertionRound.auditResult = AuditRoundResult(
            roundIdx,
            nmvrs = sampler.nmvrs(),
            maxBallotIndexUsed = sampler.maxSampleIndexUsed(),
            pvalue = testH0Result.pvalueLast,
            samplesUsed = samplesNeeded,
            status = testH0Result.status,
            measuredMean = testH0Result.tracker.mean(),
            measuredRates = testH0Result.tracker.errorRates(),
        )

        // println(" ${contest.info.name} ${assertionRound.auditResult}")
        return testH0Result
    }
}