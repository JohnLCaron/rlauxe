package org.cryptobiotic.rlauxe.cobra

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.CvrUnderAudit
import org.cryptobiotic.rlauxe.estimate.*
import org.cryptobiotic.rlauxe.util.*
import org.cryptobiotic.rlauxe.workflow.*

class CobraSingleRoundAuditTaskGenerator(
    val Nc: Int, // including undervotes but not phantoms
    val reportedMean: Double,
    val p2oracle: Double,
    val p2prior: Double,
    val parameters: Map<String, Any>,
    val auditConfig: AuditConfig,
    val quiet: Boolean = true,
) : WorkflowTaskGenerator {
    override fun name() = "CobraSingleRoundAuditTaskGenerator"

    override fun generateNewTask(): ClcaSingleRoundAuditTask {
        // the cvrs get generated with the reportedMeans.
        val cvrs = makeCvrsByExactMean(Nc, reportedMean)

        val info = ContestInfo(
            name = "AvB",
            id = 0,
            choiceFunction = SocialChoiceFunction.PLURALITY,
            candidateNames = listToMap("A", "B"),
        )
        val contest = makeContestFromCvrs(info, cvrs)
        val cobraWorkflow = CobraWorkflow(auditConfig, listOf(contest), cvrs, p2prior)
        val contestUA: ContestUnderAudit = cobraWorkflow.contestsUA().first()
        val cassorter = contestUA.clcaAssertions.first().cassorter

        // then the mvrs are generated with over/understatement errors, which means the cvrs overstate the winner's margin.
        val sampler = ClcaAttackSampler(cvrs, cassorter, p2 = p2oracle, withoutReplacement = true)
        val mvrs = sampler.mvrs

        return ClcaSingleRoundAuditTask(
            name(),
            cobraWorkflow,
            mvrs,
            parameters + mapOf("p2oracle" to p2oracle, "p2prior" to p2prior),
            quiet,
            auditor = AuditCobraAssertion(p2prior),
        )
    }
}

class CobraWorkflow(
    val auditConfig: AuditConfig,
    val contestsToAudit: List<Contest>, // the contests you want to audit
    val cvrs: List<Cvr>, // includes undervotes and phantoms.
    val p2prior: Double,
) : RlauxWorkflowIF {
    private val contestsUA: List<ContestUnderAudit>
    val cvrsUA: List<CvrUnderAudit>
    private val auditRounds = mutableListOf<AuditRound>()

    init {
        require(auditConfig.auditType == AuditType.CLCA)

        contestsUA = contestsToAudit.map { ContestUnderAudit(it, isComparison = true, auditConfig.hasStyles) }
        contestsUA.forEach { contest ->
            contest.makeClcaAssertions(cvrs)
        }

        val prng = Prng(auditConfig.seed)
        cvrsUA = cvrs.mapIndexed { idx, it -> CvrUnderAudit(it, idx, prng.next()) }.sortedBy { it.sampleNumber() }
    }

    override fun runAudit(auditRound: AuditRound, mvrs: List<Cvr>, quiet: Boolean): Boolean {
        return runClcaAudit(
            auditConfig, auditRound.contestRounds, auditRound.sampledIndices, mvrs, cvrs,
            auditRound.roundIdx, auditor = AuditCobraAssertion(p2prior)
        )
    }

    override fun auditConfig() = this.auditConfig
    override fun auditRounds() = auditRounds
    override fun contestsUA(): List<ContestUnderAudit> = contestsUA
    override fun cvrs() = cvrs
    override fun sortedBallotsOrCvrs(): List<BallotOrCvr> = cvrsUA
}

/////////////////////////////////////////////////////////////////////////////////

class AuditCobraAssertion(
    val p2prior: Double, // apriori rate of 2-vote overstatements
) : ClcaAssertionAuditor {

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

        val adaptive = AdaptiveComparison(
            Nc = contest.Nc,
            withoutReplacement = true,
            a = cassorter.noerror(),
            d = auditConfig.clcaConfig.d,
            ClcaErrorRates(p2prior, 0.0, 0.0, 0.0)
        )

        val testFn = BettingMart(
            bettingFn = adaptive,
            Nc = contest.Nc,
            noerror = cassorter.noerror(),
            upperBound = cassorter.upperBound(),
            withoutReplacement = true
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

        // println(" ${contest.info.name} ${assertionRound.auditResult}")
        return testH0Result
    }
}