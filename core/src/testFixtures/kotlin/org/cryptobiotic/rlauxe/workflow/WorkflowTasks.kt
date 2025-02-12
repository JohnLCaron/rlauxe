package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.concur.ConcurrentTaskG
import org.cryptobiotic.rlauxe.concur.ConcurrentTaskRunnerG
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.TestH0Status
import org.cryptobiotic.rlauxe.oneaudit.makeContestOA
import org.cryptobiotic.rlauxe.raire.makeRaireContest
import org.cryptobiotic.rlauxe.sampling.ContestSimulation
import org.cryptobiotic.rlauxe.sampling.makeFlippedMvrs
import org.cryptobiotic.rlauxe.sampling.makeFuzzedCvrsFrom
import org.cryptobiotic.rlauxe.sampling.makeOtherCvrForContest
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.Welford
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt

// for running workflows with one contest, multiple times for testing

private val quiet = true

// runs test workflow with fake mvrs already generated, and the cvrs are variants of those
// return number of mvrs hand counted
fun runWorkflow(name: String, workflow: RlauxWorkflowIF, testMvrs: List<Cvr>, quiet: Boolean=false): Int {
    val stopwatch = Stopwatch()

    val previousSamples = mutableSetOf<Int>()
    val rounds = mutableListOf<Round>()
    var roundIdx = 1

    var done = false
    while (!done) {
        val indices = workflow.chooseSamples(roundIdx, show=false)
        if (indices.isEmpty()) {
            done = true

        } else {
            val currRound = Round(roundIdx, indices, previousSamples.toSet())
            rounds.add(currRound)
            previousSamples.addAll(indices)

            if (!quiet) println("estimateSampleSizes round $roundIdx took ${stopwatch.elapsed(TimeUnit.MILLISECONDS)} ms\n")
            stopwatch.start()

            val sampledMvrs = indices.map {
                testMvrs[it]
            }

            done = workflow.runAudit(indices, sampledMvrs, roundIdx)
            if (!quiet) println("runAudit $roundIdx done=$done took ${stopwatch.elapsed(TimeUnit.MILLISECONDS)} ms\n")
            roundIdx++
        }
    }

    if (!quiet && rounds.isNotEmpty()) {
        rounds.forEach { println(it) }
        workflow.showResults(rounds.last().sampledIndices.size)
    }
    return if (rounds.isEmpty()) 0 else rounds.last().sampledIndices.size
}

data class Round(val round: Int, val sampledIndices: List<Int>, val previousSamples: Set<Int>) {
    var newSamples: Int = 0
    init {
        newSamples = sampledIndices.count { it !in previousSamples }
    }
    override fun toString(): String {
        return "Round(round=$round, newSamples=$newSamples)"
    }
}

interface WorkflowTaskGenerator {
    fun name(): String
    fun generateNewTask(): ConcurrentTaskG<WorkflowResult>
}

class ClcaWorkflowTaskGenerator(
    val Nc: Int, // including undervotes but not phantoms
    val margin: Double,
    val underVotePct: Double,
    val phantomPct: Double,
    val mvrsFuzzPct: Double,
    val parameters : Map<String, Any>,
    val auditConfig: AuditConfig? = null,
    val clcaConfigIn: ClcaConfig? = null,
    val Nb: Int = Nc,
    val nsimEst: Int = 100,
    val p2flips: Double? = null,
    ): WorkflowTaskGenerator {
    override fun name() = "ClcaWorkflowTaskGenerator"

    override fun generateNewTask(): WorkflowTask {
        val useConfig = auditConfig ?:
            AuditConfig(AuditType.CARD_COMPARISON, true, nsimEst = nsimEst,
                clcaConfig = clcaConfigIn ?: ClcaConfig(ClcaStrategyType.noerror))

        val sim = ContestSimulation.make2wayTestContest(Nc=Nc, margin, undervotePct=underVotePct, phantomPct=phantomPct)
        var testCvrs = sim.makeCvrs() // includes undervotes and phantoms
        var testMvrs =  if (p2flips != null) makeFlippedMvrs(testCvrs, Nc, p2flips, 0.0) else
            makeFuzzedCvrsFrom(listOf(sim.contest), testCvrs, mvrsFuzzPct)

        if (!useConfig.hasStyles && Nb > Nc) { // TODO wtf?
            val otherContestId = 42
            val otherCvrs = List<Cvr>(Nb - Nc) { makeOtherCvrForContest(otherContestId) }
            testCvrs = testCvrs + otherCvrs
            testMvrs = testMvrs + otherCvrs
        }

        val clcaWorkflow = ClcaWorkflow(useConfig, listOf(sim.contest), emptyList(), testCvrs, quiet = quiet)
        return WorkflowTask(
            name(),
            clcaWorkflow,
            testMvrs,
            parameters + mapOf("mvrsFuzzPct" to mvrsFuzzPct, "auditType" to 3.0)
        )
    }
}

class PollingWorkflowTaskGenerator(
    val Nc: Int, // including undervotes but not phantoms
    val margin: Double,
    val underVotePct: Double,
    val phantomPct: Double,
    val mvrsFuzzPct: Double,
    val parameters : Map<String, Any>,
    val auditConfig: AuditConfig? = null,
    val Nb: Int = Nc,
    val nsimEst: Int = 100,

    ) : WorkflowTaskGenerator {
    override fun name() = "PollingWorkflowTaskGenerator"

    override fun generateNewTask(): ConcurrentTaskG<WorkflowResult> {
        val useConfig = auditConfig ?: AuditConfig(
            AuditType.POLLING, true, nsimEst = nsimEst, pollingConfig = PollingConfig(simFuzzPct = mvrsFuzzPct)
        )

        val sim = ContestSimulation.make2wayTestContest(Nc=Nc, margin, undervotePct=underVotePct, phantomPct=phantomPct)
        val testCvrs = sim.makeCvrs() // includes undervotes and phantoms
        var testMvrs = makeFuzzedCvrsFrom(listOf(sim.contest), testCvrs, mvrsFuzzPct)
        var ballotManifest = sim.makeBallotManifest(useConfig.hasStyles)

        if (!useConfig.hasStyles && Nb > Nc) {
            val otherContestId = 42
            val otherCvrs = List<Cvr>(Nb - Nc) { makeOtherCvrForContest(otherContestId) }
            testMvrs = testMvrs + otherCvrs

            val otherBallots = List<Ballot>(Nb - Nc) { Ballot("other${Nc+it}", false, null) }
            ballotManifest = BallotManifest(ballotManifest.ballots + otherBallots, emptyList())
        }

        val pollingWorkflow = PollingWorkflow(useConfig, listOf(sim.contest), ballotManifest, Nb, quiet = quiet)
        return WorkflowTask(
            name(),
            pollingWorkflow,
            testMvrs,
            parameters + mapOf("fuzzPct" to mvrsFuzzPct, "auditType" to 2.0)
        )
    }
}

class OneAuditWorkflowTaskGenerator(
    val Nc: Int, // including undervotes but not phantoms
    val margin: Double,
    val underVotePct: Double,
    val phantomPct: Double,
    val cvrPercent: Double,
    val fuzzPct: Double,
    val parameters : Map<String, Any>,
    val auditConfigIn: AuditConfig? = null,
    ) : WorkflowTaskGenerator {
    override fun name() = "OneAuditWorkflowTaskGenerator"

    override fun generateNewTask(): WorkflowTask {
        val auditConfig = auditConfigIn ?: AuditConfig(
            AuditType.ONEAUDIT, true, nsimEst = 10, oaConfig = OneAuditConfig(strategy=OneAuditStrategyType.default, simFuzzPct = fuzzPct)
        )

        val contestOA2 = makeContestOA(margin, Nc, cvrPercent = cvrPercent, phantomPct, undervotePercent = underVotePct, phantomPercent=phantomPct)
        val oaCvrs = contestOA2.makeTestCvrs()
        val oaMvrs = makeFuzzedCvrsFrom(listOf(contestOA2.makeContest()), oaCvrs, fuzzPct)

        val oneaudit = OneAuditWorkflow(auditConfig=auditConfig, listOf(contestOA2), oaCvrs, quiet = quiet)
        return WorkflowTask(
            name(),
            oneaudit,
            oaMvrs,
            parameters + mapOf("cvrPercent" to cvrPercent, "fuzzPct" to fuzzPct, "auditType" to 1.0)
        )
    }
}

class RaireWorkflowTaskGenerator(
    val Nc: Int, // including undervotes but not phantoms
    val margin: Double,
    val underVotePct: Double,
    val phantomPct: Double,
    val mvrsFuzzPct: Double,
    val parameters : Map<String, Any>,
    val auditConfigIn: AuditConfig? = null,
    val clcaConfigIn: ClcaConfig? = null,
): WorkflowTaskGenerator {
    override fun name() = "RaireWorkflowTaskGenerator"

    override fun generateNewTask(): WorkflowTask {
        val auditConfig = auditConfigIn ?:
        AuditConfig(AuditType.CARD_COMPARISON, true, nsimEst = 10,
            clcaConfig = clcaConfigIn ?: ClcaConfig(ClcaStrategyType.noerror))

        val (rcontest, testCvrs) = makeRaireContest(N=20000, minMargin=margin, quiet = true)
        // var testMvrs = makeFuzzedCvrsFrom(listOf(rcontest.contest), testCvrs, mvrsFuzzPct) // this will fail

        val clca = ClcaWorkflow(auditConfig, emptyList(), listOf(rcontest), testCvrs, quiet = quiet)
        return WorkflowTask(
            name(),
            clca,
            testCvrs, // no errors
            parameters + mapOf("mvrsFuzzPct" to mvrsFuzzPct, "auditType" to 4.0)
        )
    }
}

class WorkflowTask(
    val name: String,
    val workflow: RlauxWorkflowIF,
    val testCvrs: List<Cvr>,
    val otherParameters: Map<String, Any>,
) : ConcurrentTaskG<WorkflowResult> {
    override fun name() = name
    override fun run(): WorkflowResult {
        val nmvrs = runWorkflow(name, workflow, testCvrs, quiet = quiet)

        val contestUA = workflow.getContests().first() // theres only one
        val minAssertion = contestUA.minAssertion()!!

        return if (minAssertion.roundResults.isEmpty()) {
            WorkflowResult(
                contestUA.Nc,
                minAssertion.assorter.reportedMargin(),
                TestH0Status.ContestMisformed, // TODO why empty?
                0.0, 0.0, 0.0, 0.0,
                otherParameters,
                100.0,
            )
        } else {
            val lastRound = minAssertion.roundResults.last()
            WorkflowResult(
                contestUA.Nc,
                minAssertion.assorter.reportedMargin(),
                lastRound.status,
                minAssertion.round.toDouble(),
                lastRound.samplesUsed.toDouble(),
                lastRound.samplesNeeded.toDouble(),
                nmvrs.toDouble(),
                otherParameters,
                if (lastRound.status != TestH0Status.StatRejectNull) 100.0 else 0.0
            )
        }
    }
}

fun runRepeatedWorkflowsAndAverage(tasks: List<ConcurrentTaskG<List<WorkflowResult>>>): List<WorkflowResult> {
    val rresults: List<List<WorkflowResult>> = ConcurrentTaskRunnerG<List<WorkflowResult>>().run(tasks, nthreads=40)
    val results: List<WorkflowResult> = rresults.map { avgWorkflowResult(it) }
    return results
}

data class WorkflowResult(
        val Nc: Int,
        val margin: Double,
        val status: TestH0Status,
        val nrounds: Double,
        val samplesUsed: Double,  // weighted
        val samplesNeeded: Double, // weighted
        val nmvrs: Double, // weighted
        val parameters: Map<String, Any>,

        // from avgWorkflowResult()
        val failPct: Double = 100.0,
        val neededStddev: Double = 0.0, // success only
    ) {
    fun Dparam(key: String) = (parameters[key]!! as String).toDouble()
}

fun avgWorkflowResult(runs: List<WorkflowResult>): WorkflowResult {
    val successRuns = runs.filter { it.status.success }

    val result =  if (runs.isEmpty()) { // TODO why all empty?
        WorkflowResult(
            0,
            0.0,
            TestH0Status.ContestMisformed,
            0.0, 0.0, 0.0,0.0,
            emptyMap(),
            )
    } else if (successRuns.isEmpty()) { // TODO why all empty?
        val first = runs.first()
        WorkflowResult(
            first.Nc,
            first.margin,
            TestH0Status.ContestMisformed,
            0.0, first.Nc.toDouble(), first.Nc.toDouble(), first.Nc.toDouble(),
            first.parameters,
            )
    } else {
        val first = successRuns.first()
        val failures = runs.size - successRuns.count()
        val successPct = successRuns.count() / runs.size.toDouble()
        val failPct = failures / runs.size.toDouble()
        val Nc = first.Nc
        val welford = Welford()
        successRuns.forEach { welford.update(it.samplesNeeded) }

        WorkflowResult(
            Nc,
            first.margin,
            first.status, // hmm kinda bogus
            runs.filter{ it.nrounds > 0 } .map { it.nrounds }.average(),
            successPct * successRuns.map { it.samplesUsed }.average() + failPct * Nc,
            successPct * welford.mean + failPct * Nc,
            successPct * successRuns.map { it.nmvrs }.average() + failPct * Nc,
            first.parameters,

            100.0 * failPct,
            sqrt(welford.variance()), // success only
        )
    }

    return result
}

