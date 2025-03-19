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
import org.cryptobiotic.rlauxe.persist.csv.writeCvrsCsvFile
import org.cryptobiotic.rlauxe.persist.json.Publisher
import org.cryptobiotic.rlauxe.raire.RaireContestUnderAudit
import org.cryptobiotic.rlauxe.raire.simulateRaireTestData
import org.cryptobiotic.rlauxe.workflow.*
import kotlin.math.min

/** Create the starting election state, with fuzzed test data. */
object RunRlaStartFuzz {

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
        // require(topdir.startsWith("/home/stormy/temp"))
        // clearDirectory(Path.of(topdir))

        val publisher = Publisher(topdir)
        val auditConfig = AuditConfig(AuditType.CLCA, hasStyles = true, nsimEst = 100,
            clcaConfig = ClcaConfig(strategy = ClcaStrategyType.previous)
        )
        writeAuditConfigJsonFile(auditConfig, publisher.auditConfigFile())

        // TODO something better about generating range of margins, phantoms, fuzz, etc.
        //   maybe just make it all configurable...
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
            val (rcontest: RaireContestUnderAudit, rcvrs: List<Cvr>) = simulateRaireTestData(N=ncards/2, contestId=111, addRaireCandidates, minMargin=.04, quiet = true)
            raireContests.add(rcontest)
            // TODO merge(testCvrs + rcvrs)
            testCvrs = testCvrs + rcvrs
        }

        // TODO are these randomized?
        val allContests = contests + raireContests.map { it.contest }
        val testMvrs = if (fuzzMvrs == 0.0) testCvrs
                    // fuzzPct of the Mvrs have their votes randomly changed ("fuzzed")
                    else makeFuzzedCvrsFrom(allContests, testCvrs, fuzzMvrs)
        val ballotCards = BallotCardsClcaStart(testCvrs, testMvrs, auditConfig.seed)

        //// could be inside of BallotCardsClca
        writeCvrsCsvFile(ballotCards.cvrsUA, publisher.cvrsCsvFile()) // TODO wrap in Result ??
        println("   writeCvrsCvsFile ${publisher.cvrsCsvFile()}")

        // save the sorted testMvrs
        publisher.validateOutputDirOfFile(mvrFile)
        writeCvrsCsvFile(ballotCards.mvrsUA, mvrFile)
        println("   writeMvrsJsonFile ${mvrFile}")

        val clcaWorkflow = ClcaWorkflow(auditConfig, contests, raireContests, ballotCards)
        writeContestsJsonFile(clcaWorkflow.contestsUA(), publisher.contestsFile())
        println("   writeContestsJsonFile ${publisher.contestsFile()}")

        // get the first round of samples wanted, write them to round1 subdir
        val auditRound = runChooseSamples(clcaWorkflow, publisher)

        // write the partial audit state to round1
        writeAuditRoundJsonFile(auditRound, publisher.auditRoundFile(1))
        println("   writeAuditStateJsonFile ${publisher.auditRoundFile(1)}")

        return if (auditRound.sampleNumbers.isNotEmpty()) 0 else 1
    }

    fun startTestElectionPolling(
        topdir: String,
        minMargin: Double,
        fuzzMvrsPct: Double,
        pctPhantoms: Double?,
        ncards: Int,
        mvrFile: String,
    ): Int {
        val publisher = Publisher(topdir)
        val auditConfig = AuditConfig(AuditType.POLLING, hasStyles = true, nsimEst = 100)
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
        val testMvrs = makeFuzzedCvrsFrom(contests, testCvrs, fuzzMvrsPct)
        val pairs = testMvrs.zip(testCvrs)
        pairs.forEach { (mvr, cvr) ->
            require(mvr.id == cvr.id)
        }

        val ballotCards = BallotCardsPollingStart(ballotManifest.ballots, testMvrs, auditConfig.seed)
        val ballotManifestUA = BallotManifestUnderAudit(ballotCards.ballotsUA, ballotManifest.ballotStyles)
        writeBallotManifestJsonFile(ballotManifestUA, publisher.ballotManifestFile())
        println("   writeBallotManifestJsonFile ${publisher.ballotManifestFile()}")

        // save the sorted testMvrs
        var lastRN = 0L
        val mvruas = ballotCards.ballotsUA.mapIndexed { idx, ballotUA ->
            require(ballotUA.sampleNumber() > lastRN)
            lastRN = ballotUA.sampleNumber()
            val mvr = testMvrs[idx]
            CvrUnderAudit(mvr, ballotUA.index(), ballotUA.sampleNumber())
        }
        publisher.validateOutputDirOfFile(mvrFile)
        writeCvrsCsvFile(mvruas, mvrFile)
        println("   writeMvrsJsonFile ${mvrFile}")

        // PollingWorkflow creates the assertions
        val pollingWorkflow = PollingWorkflow(auditConfig, contests, ballotCards)

        writeContestsJsonFile(pollingWorkflow.contestsUA(), publisher.contestsFile())
        println("   writeContestsJsonFile ${publisher.contestsFile()}")

        // get the first round of samples wanted, write them to round1 subdir
        val auditRound = runChooseSamples(pollingWorkflow, publisher)

        // write the partial audit state to round1
        writeAuditRoundJsonFile(auditRound, publisher.auditRoundFile(1))
        println("   writeAuditStateJsonFile ${publisher.auditRoundFile(1)}")

        return if (auditRound.sampleNumbers.isNotEmpty()) 0 else 1
    }
}

fun runChooseSamples(workflow: RlauxWorkflowIF, publish: Publisher): AuditRound {
    val round = workflow.startNewRound(quiet = false)
    if (round.sampleNumbers.isNotEmpty()) {
        writeSampleNumbersJsonFile(round.sampleNumbers, publish.sampleNumbersFile(round.roundIdx))
        println("   writeSampleIndicesJsonFile ${publish.sampleNumbersFile(round.roundIdx)}")
    } else {
        println("*** FAILED TO GET ANY SAMPLES ***")
    }
    return round
}
