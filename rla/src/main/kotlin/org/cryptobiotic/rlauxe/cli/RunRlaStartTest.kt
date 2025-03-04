package org.cryptobiotic.rlauxe.cli

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required

import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.CvrUnderAudit
import org.cryptobiotic.rlauxe.persist.json.*
import org.cryptobiotic.rlauxe.estimate.MultiContestTestData
import org.cryptobiotic.rlauxe.estimate.makeFuzzedCvrsFrom
import org.cryptobiotic.rlauxe.persist.json.Publisher
import org.cryptobiotic.rlauxe.raire.RaireContestUnderAudit
import org.cryptobiotic.rlauxe.raire.makeRaireContest
import org.cryptobiotic.rlauxe.workflow.*
import kotlin.math.min

/** Create the starting election state. */
object RunRlaStartTest {

    @JvmStatic
    fun main(args: Array<String>) {
        val parser = ArgParser("RunRlaStartTest")
        val inputDir by parser.option(
            ArgType.String,
            shortName = "in",
            description = "Directory containing test election record"
        ).required()
        val isPolling by parser.option(
            ArgType.Boolean,
            shortName = "isPolling",
            description = "Polling election"
        ).default(false)
        val minMargin by parser.option(
            ArgType.Double,
            shortName = "minMargin",
            description = "contest minimum margin"
        ).default(0.04)
        val fuzzMvrs by parser.option(
            ArgType.Double,
            shortName = "fuzzMvrs",
            description = "Fuzz Mvrs by this factor (0.0 to 1.0)"
        ).default(0.0)
        val pctPhantoms by parser.option(
            ArgType.Double,
            shortName = "pctPhantoms",
            description = "Pct phantoms (0.0 to 1.0)"
        )
        val ncards by parser.option(
            ArgType.Int,
            shortName = "ncards",
            description = "Total number of ballot/cards"
        ).default(10000)
        val ncontests by parser.option(
            ArgType.Int,
            shortName = "ncontests",
            description = "Number of contests"
        ).default(11)
        val mvrFile by parser.option(
            ArgType.String,
            shortName = "mvrs",
            description = "File containing sampled Mvrs"
        ).required()
        val addRaireContest by parser.option(
            ArgType.Boolean,
            shortName = "addRaire",
            description = "Add a Raire Contest"
        ).default(false)
        val addRaireCandidates by parser.option(
            ArgType.Int,
            shortName = "rcands",
            description = "Number of candidates for raire contest"
        ).default(5)

        parser.parse(args)
        println("RunRlaStartTest on $inputDir isPolling=$isPolling minMargin=$minMargin fuzzMvrs=$fuzzMvrs, pctPhantoms=$pctPhantoms, ncards=$ncards ncontests=$ncontests" +
                "addRaire=$addRaireContest addRaireCandidates=$addRaireCandidates\n  mvrFile=$mvrFile")
        val retval = if (!isPolling) startTestElectionClca(inputDir, minMargin, fuzzMvrs, pctPhantoms, ncards, ncontests, addRaireContest, addRaireCandidates, mvrFile)
        else startTestElectionPolling(inputDir, minMargin, fuzzMvrs, pctPhantoms, ncards, mvrFile)
    }

    fun startTestElectionClca(
        topdir: String,
        minMargin: Double,
        fuzzMvrs: Double,
        pctPhantoms: Double?,
        ncards: Int,
        ncontests: Int,
        addRaire: Boolean,
        addRaireCandidates: Int,
        mvrFile: String,
    ): Int {
        println("Start startTestElectionClca")
        val publisher = Publisher(topdir)
        val auditConfig = AuditConfig(AuditType.CLCA, hasStyles = true, nsimEst = 100, samplePctCutoff=1.0, minMargin=.0,
            removeTooManyPhantoms=false, clcaConfig = ClcaConfig(strategy = ClcaStrategyType.previous)
        )
        writeAuditConfigJsonFile(auditConfig, publisher.auditConfigFile())

        val maxMargin = .10
        val useMin = min(minMargin, maxMargin)
        val phantomPctRange: ClosedFloatingPointRange<Double> =
            if (pctPhantoms == null) 0.00..0.005 else pctPhantoms..pctPhantoms

        val testData =
            MultiContestTestData(ncontests, 4, ncards, marginRange = useMin..maxMargin, phantomPctRange = phantomPctRange)

        val contests: List<Contest> = testData.contests
        println("$testData")
        contests.forEach { println("  $it") }
        println()

        // Synthetic cvrs for testing, reflecting the exact contest votes, plus undervotes and phantoms.
        // TODO add raire cvrs here
        var testCvrs = testData.makeCvrsFromContests()

        val raireContests = mutableListOf<RaireContestUnderAudit>()
        if (addRaire) {
            val (rcontest: RaireContestUnderAudit, rcvrs: List<Cvr>) = makeRaireContest(N=ncards/2, addRaireCandidates, minMargin=.04, quiet = true)
            raireContests.add(rcontest)
            // TODO merge(testCvrs + rcvrs)
            testCvrs = testCvrs + rcvrs
        }

        // TODO are these randomized?
        val allContests = contests + raireContests.map { it.contest }
        val testMvrs = if (fuzzMvrs == 0.0) testCvrs
                    // fuzzPct of the Mvrs have their votes randomly changed ("fuzzed")
                    else makeFuzzedCvrsFrom(allContests, testCvrs, fuzzMvrs)

        // ClcaWorkflow assigns the sample numbers, and creates the assertions
        var clcaWorkflow = ClcaWorkflow(auditConfig, contests, raireContests, testCvrs)
        writeCvrsJsonFile(clcaWorkflow.cvrsUA, publisher.cvrsFile())
        println("   writeCvrsJsonFile ${publisher.cvrsFile()}")

        // save the testMvrs. kludgey
        val mvrus = testMvrs.mapIndexed { idx, mvr ->
            val cvr = clcaWorkflow.cvrsUA[idx]
            CvrUnderAudit(mvr, cvr.sampleNumber())
        }
        publisher.validateOutputDirOfFile(mvrFile)
        writeCvrsJsonFile(mvrus, mvrFile)
        println("   writeCvrsJsonFile ${mvrFile}")

        // get the first round of samples wanted, write them to round1 subdir
        val auditRound = runChooseSamples(clcaWorkflow, publisher)

        // write the partial audit state to round1
        writeAuditRoundJsonFile(auditRound, publisher.auditRoundFile(1))
        println("   writeAuditStateJsonFile ${publisher.auditRoundFile(1)}")

        return if (auditRound.sampledIndices.isNotEmpty()) 0 else 1
    }

    fun startTestElectionPolling(
        topdir: String,
        minMargin: Double,
        fuzzMvrs: Double,
        pctPhantoms: Double?,
        ncards: Int,
        mvrFile: String,
    ): Int {
        val publisher = Publisher(topdir)
        val auditConfig = AuditConfig(AuditType.POLLING, hasStyles = true, nsimEst = 100, samplePctCutoff=1.0, minMargin=.00, removeTooManyPhantoms=false, )
        writeAuditConfigJsonFile(auditConfig, publisher.auditConfigFile())
        println("   writeAuditConfigJsonFile ${publisher.auditConfigFile()}")

        val maxMargin = .08
        val useMin = min(minMargin, maxMargin)
        val phantomPctRange: ClosedFloatingPointRange<Double> =
            if (pctPhantoms == null) 0.00..0.005 else pctPhantoms..pctPhantoms
        val testData = MultiContestTestData(11, 4, ncards, marginRange = useMin..maxMargin, phantomPctRange = phantomPctRange)

        val contests: List<Contest> = testData.contests
        println("Start testPersistentWorkflowPolling $testData")
        contests.forEach { println("  $it") }
        println()

        val (testCvrs, ballotManifest) = testData.makeCvrsAndBallotManifest(auditConfig.hasStyles)
        val testMvrs = makeFuzzedCvrsFrom(contests, testCvrs, fuzzMvrs)

        // PollingWorkflow assigns the sample numbers, and creates the assertions
        val pollingWorkflow = PollingWorkflow(auditConfig, contests, ballotManifest, testCvrs.size)
        val ballotManifestUA = BallotManifestUnderAudit(pollingWorkflow.ballotsUA, ballotManifest.ballotStyles)
        writeBallotManifestJsonFile(ballotManifestUA, publisher.ballotManifestFile())
        println("   writeBallotManifestJsonFile ${publisher.ballotManifestFile()}")

        // save the testMvrs. kludgey
        val mvrus = testMvrs.mapIndexed { idx, mvr ->
            val ballot = pollingWorkflow.ballotsUA[idx]
            CvrUnderAudit(mvr, ballot.sampleNumber())
        }
        publisher.validateOutputDirOfFile(mvrFile)
        writeCvrsJsonFile(mvrus, mvrFile)
        println("   writeCvrsJsonFile ${mvrFile}")

        // get the first round of samples wanted, write them to round1 subdir
        val auditRound = runChooseSamples(pollingWorkflow, publisher)

        // write the partial audit state to round1
        writeAuditRoundJsonFile(auditRound, publisher.auditRoundFile(1))
        println("   writeAuditStateJsonFile ${publisher.auditRoundFile(1)}")

        return if (auditRound.sampledIndices.isNotEmpty()) 0 else 1
    }
}

fun runChooseSamples(workflow: RlauxWorkflowIF, publish: Publisher): AuditRound {
    val round = workflow.startNewRound(quiet = false)
    if (round.sampledIndices.isNotEmpty()) {
        writeSampleIndicesJsonFile(round.sampledIndices, publish.sampleIndicesFile(round.roundIdx))
        println("   writeSampleIndicesJsonFile ${publish.sampleIndicesFile(round.roundIdx)}")
    } else {
        println("*** FAILED TO GET ANY SAMPLES ***")
    }
    return round
}
