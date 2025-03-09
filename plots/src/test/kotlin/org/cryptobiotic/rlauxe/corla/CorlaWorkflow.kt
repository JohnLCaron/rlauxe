package org.cryptobiotic.rlauxe.corla

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.CvrUnderAudit
import org.cryptobiotic.rlauxe.estimate.*
import org.cryptobiotic.rlauxe.util.*
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
            AuditType.CLCA, true, nsimEst = nsimEst, samplePctCutoff = 1.0,
            clcaConfig = clcaConfigIn ?: ClcaConfig(ClcaStrategyType.noerror)
        )

        val sim =
            ContestSimulation.make2wayTestContest(Nc = Nc, margin, undervotePct = underVotePct, phantomPct = phantomPct)
        var testCvrs = sim.makeCvrs() // includes undervotes and phantoms
        val testMvrs = if (p2flips != null || p1flips != null) makeFlippedMvrs(testCvrs, Nc, p2flips, p1flips) else
            makeFuzzedCvrsFrom(listOf(sim.contest), testCvrs, mvrsFuzzPct)

        val clcaWorkflow = ClcaWorkflow(useConfig, listOf(sim.contest), emptyList(), testCvrs)
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

        val clca = CorlaWorkflow(auditConfig, listOf(sim.contest), testCvrs, quiet = true)
        return WorkflowTask(
            "genAuditWithErrorsPlots mvrsFuzzPct = $mvrsFuzzPct",
            clca,
            testMvrs,
            parameters + mapOf("mvrsFuzzPct" to mvrsFuzzPct, "auditType" to 3.0)
        )
    }
}

// cloned ClcaWorkflow
class CorlaWorkflow(
    val auditConfig: AuditConfig,
    val contestsToAudit: List<Contest>, // the contests you want to audit
    val cvrs: List<Cvr>, // includes undervotes and phantoms.
    val quiet: Boolean = false,
): RlauxWorkflowIF {
    private val contestsUA: List<ContestUnderAudit>
    val cvrsUA: List<CvrUnderAudit>
    private val auditRounds = mutableListOf<AuditRound>()

    init {
        require (auditConfig.auditType == AuditType.CLCA)

        contestsUA = contestsToAudit.map { ContestUnderAudit(it, isComparison=true, auditConfig.hasStyles) }
        contestsUA.forEach { contest ->
            contest.makeClcaAssertions(cvrs)
        }

        val prng = Prng(auditConfig.seed)
        cvrsUA = cvrs.map { CvrUnderAudit(it, prng.next()) }
    }

    /*
    override fun startNewRound(quiet: Boolean): AuditRound {
        val previousRound = if (auditRounds.isEmpty()) null else auditRounds.last()
        val roundIdx = auditRounds.size + 1

        val auditRound = if (previousRound == null) {
            val contestRounds = contestsUA.map { ContestRound(it, roundIdx) }
            AuditRound(roundIdx, contestRounds = contestRounds, sampledIndices = emptyList())
        } else {
            previousRound.createNextRound()
        }
        auditRounds.add(auditRound)

        estimateSampleSizes(
            auditConfig,
            auditRound,
            cvrs,
            show=!quiet,
        )

        auditRound.sampledIndices = sample(this, auditRound, auditRounds.previousSamples(roundIdx), quiet)
        return auditRound
    }

     */

    override fun runAudit(auditRound: AuditRound, mvrs: List<Cvr>, quiet: Boolean): Boolean  {
        return runClcaAudit(auditConfig, auditRound.contestRounds, auditRound.sampledIndices, mvrs, cvrs,
            auditRound.roundIdx, auditor = AuditCorlaAssertion())
    }

    override fun auditConfig() =  this.auditConfig
    override fun auditRounds() = auditRounds
    override fun contestUA(): List<ContestUnderAudit> = contestsUA
    override fun cvrs() = cvrs
    override fun getBallotsOrCvrs() : List<BallotOrCvr> = cvrsUA
}

/////////////////////////////////////////////////////////////////////////////////

/*
fun runCorlaAudit(auditConfig: AuditConfig,
                 contests: List<ContestRound>,
                 sampleIndices: List<Int>,
                 mvrs: List<Cvr>,
                 cvrs: List<Cvr>,
                 roundIdx: Int,
                 quiet: Boolean): Boolean {


    val contestsNotDone = contests.filter{ !it.done }
    val sampledCvrs = sampleIndices.map { cvrs[it] }

    // prove that sampledCvrs correspond to mvrs
    require(sampledCvrs.size == mvrs.size)
    val cvrPairs: List<Pair<Cvr, Cvr>> = mvrs.zip(sampledCvrs)
    cvrPairs.forEach { (mvr, cvr) -> require(mvr.id == cvr.id) }

    if (!quiet) println("runAudit round $roundIdx")
    var allDone = true
    contestsNotDone.forEach { contest ->
        if (contest.contestUA.contest.choiceFunction == SocialChoiceFunction.IRV) {
            println("here")
        }
        val contestAssertionStatus = mutableListOf<TestH0Status>()
        contest.assertionRounds.forEach { assertionRound ->
            if (!assertionRound.status.complete) {
                val testH0Result = runCorlaAudit(auditConfig, contest.contestUA.contest, assertionRound, cvrPairs, roundIdx, quiet=quiet)
                assertionRound.status = testH0Result.status
                if (testH0Result.status.complete) assertionRound.round = roundIdx
            }
            contestAssertionStatus.add(assertionRound.status)
        }
        contest.done = contestAssertionStatus.all { it.complete }
        contest.status = contestAssertionStatus.minBy { it.rank } // use lowest rank status.
        allDone = allDone && contest.done
    }
    return allDone
}
 */
/*

    //   The auditors retrieve the indicated cards, manually read the votes from those cards, and input the MVRs
    // fun runAudit(auditRound: AuditRound, mvrs: List<Cvr>, quiet:Boolean): Boolean {

        val contestsNotDone = auditRound.contestRounds.filter{ !it.done }
        val sampledCvrs = auditRound.sampledIndices.map { cvrs[it] }
        val roundIdx = auditRound.roundIdx

        // prove that sampledCvrs correspond to mvrs
        require(sampledCvrs.size == mvrs.size)
        val cvrPairs: List<Pair<Cvr, Cvr>> = mvrs.zip(sampledCvrs)
        cvrPairs.forEach { (mvr, cvr) -> require(mvr.id == cvr.id) }

        // TODO could parallelize across assertions
        if (!quiet) println("runAudit round $roundIdx")
        var allDone = true
        contestsNotDone.forEach { contest ->
            val contestUA = contest.contestUA
            var allAssertionsDone = true
            contest.assertionRounds.forEach { assertion ->
                if (!assertion.status.complete) {
                    val testH0Result = runCorlaAudit(auditConfig, contestUA.contest, assertion, cvrPairs, roundIdx, quiet=quiet)
                    assertion.status = testH0Result.status
                    assertion.round = roundIdx
                    allAssertionsDone = allAssertionsDone && assertion.status.complete
                }
            }
            if (allAssertionsDone) {
                contest.done = true
                contest.status = TestH0Status.StatRejectNull // TODO ???
            }
            allDone = allDone && contest.done

        }
        return allDone
    }

 */

/////////////////////////////////////////////////////////////////////////////////

class AuditCorlaAssertion(val quiet: Boolean = true): ClcaAssertionAuditor {

    override fun run(
        auditConfig: AuditConfig,
        contest: ContestIF,
        assertionRound: AssertionRound,
        cvrPairs: List<Pair<Cvr, Cvr>>, // (mvr, cvr)
        roundIdx: Int,
    ): TestH0Result {
        val cassertion = assertionRound.assertion as ClcaAssertion
        val cassorter = cassertion.cassorter
        val sampler = ClcaWithoutReplacement(contest, cvrPairs, cassorter, allowReset = false)

        // Corla(val N: Int, val riskLimit: Double, val reportedMargin: Double, val noerror: Double,
        //    val p1: Double, val p2: Double, val p3: Double, val p4: Double): RiskTestingFn
        val testFn = Corla(
            N = contest.Nc,
            riskLimit = auditConfig.riskLimit,
            reportedMargin = cassertion.assorter.reportedMargin(),
            noerror = cassorter.noerror(),
            p1 = 0.0, p2 = 0.0, p3 = 0.0, p4 = 0.0, // todo
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