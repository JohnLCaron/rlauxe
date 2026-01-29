package org.cryptobiotic.rlauxe.cobra

import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.betting.BettingMart
import org.cryptobiotic.rlauxe.betting.ClcaErrorCounts
import org.cryptobiotic.rlauxe.betting.GeneralAdaptiveBetting
import org.cryptobiotic.rlauxe.betting.SamplerTracker
import org.cryptobiotic.rlauxe.betting.TestH0Result
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.core.PluralityErrorTracker
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

    override fun generateNewTask(): ClcaSingleRoundWorkflowTask {
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
        val cobraWorkflow1 = CobraAudit(auditConfig, listOf(contest), MvrManagerForTesting(testCvrs, testCvrs, auditConfig.seed), p2prior)
        val contestUA: ContestWithAssertions = cobraWorkflow1.contestsUA().first()
        val cassorter = contestUA.clcaAssertions.first().cassorter

        // then the mvrs are generated with over/understatement errors, which means the cvrs overstate the winner's margin.
        val sampler = ClcaAttackSampler(testCvrs, cassorter, p2 = p2oracle, withoutReplacement = true)
        val mvrs = sampler.mvrs

        // maybe bogus
        val cobraWorkflow2 = CobraAudit(auditConfig, listOf(contest),
            MvrManagerForTesting(testCvrs, sampler.mvrs, auditConfig.seed),
            p2prior)

        return ClcaSingleRoundWorkflowTask(
            name(),
            cobraWorkflow2,
            auditor = AuditCobraAssertion(p2prior),
            mvrs,
            parameters + mapOf("p2oracle" to p2oracle, "p2prior" to p2prior),
            quiet,
        )
    }
}

class CobraAudit(
    val auditConfig: AuditConfig,
    contestsToAudit: List<Contest>, // the contests you want to audit
    val mvrManagerForTesting: MvrManagerForTesting, // mutable
    val p2prior: Double,
) : AuditWorkflow() {
    private val contestsUA: List<ContestWithAssertions>
    private val auditRounds = mutableListOf<AuditRoundIF>()

    init {
        require(auditConfig.auditType == AuditType.CLCA)
        contestsUA = contestsToAudit.map { ContestWithAssertions(it, isClca = true,).addStandardAssertions() }
    }

    override fun runAuditRound(auditRound: AuditRound, quiet: Boolean): Boolean  {
        val complete = runClcaAuditRound(auditConfig, auditRound, mvrManagerForTesting, auditRound.roundIdx,
            auditor = AuditCobraAssertion(p2prior)
        )
        auditRound.auditWasDone = true
        auditRound.auditIsComplete = complete
        return complete
    }

    override fun auditConfig() = this.auditConfig
    override fun auditRounds() = auditRounds
    override fun contestsUA(): List<ContestWithAssertions> = contestsUA
    override fun mvrManager() = mvrManagerForTesting
}

/////////////////////////////////////////////////////////////////////////////////

class AuditCobraAssertion(
    val p2prior: Double, // apriori rate of 2-vote overstatements
) : ClcaAssertionAuditorIF {

    override fun run(
        auditConfig: AuditConfig,
        contestRound: ContestRound,
        assertionRound: AssertionRound,
        sampleTracker: SamplerTracker,
        roundIdx: Int,
    ): TestH0Result {
        val contestUA = contestRound.contestUA
        val cassertion = assertionRound.assertion as ClcaAssertion
        val cassorter = cassertion.cassorter

        // val sampler = ClcaWithoutReplacement(contest.id, cvrPairs, cassorter, allowReset = false)

        //     val Npop: Int, // population size for this contest
        //    val startingErrors: ClcaErrorCounts,  // zero for auditing
        //    val nphantoms: Int, // number of phantoms in the population
        //    val oaAssortRates: OneAuditAssortValueRates?, // only for OneAudit
        //    val d: Int = 100,  // trunc weight
        //    val maxRisk: Double, // this bounds how close lam gets to 2.0
        //    val withoutReplacement: Boolean = true,
        //    val debug: Boolean = false,
        val adaptive = GeneralAdaptiveBetting(
            Npop = contestUA.Npop,
            startingErrors = ClcaErrorCounts.empty(cassorter.noerror(), upper = cassorter.assorter.upperBound()),
            nphantoms = 0,
            oaAssortRates = null,
            d = auditConfig.clcaConfig.d,
            maxLoss = auditConfig.clcaConfig.maxLoss,
        )
        val tracker = PluralityErrorTracker(cassorter.noerror())

        val testFn = BettingMart(
            bettingFn = adaptive,
            N = contestUA.Npop,
            sampleUpperBound = cassorter.upperBound(),
            withoutReplacement = true,
            tracker = sampleTracker,
        )

        val testH0Result = testFn.testH0(sampleTracker.maxSamples(), terminateOnNullReject = true) { sampleTracker.sample() }
        val samplesNeeded = testH0Result.sampleCount

        assertionRound.auditResult = AuditRoundResult(
            roundIdx,
            nmvrs = sampleTracker.nmvrs(),
            // countCvrsUsedInAudit = sampleTracker.countCvrsUsedInAudit(),
            plast = testH0Result.pvalueLast,
            pmin = testH0Result.pvalueMin,
            samplesUsed = samplesNeeded,
            status = testH0Result.status,
        )

        // println(" ${contest.info.name} ${assertionRound.auditResult}")
        return testH0Result
    }
}