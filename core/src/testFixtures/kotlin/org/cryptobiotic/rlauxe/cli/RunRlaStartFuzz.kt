package org.cryptobiotic.rlauxe.cli

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import org.cryptobiotic.rlauxe.audit.*

import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.persist.json.*
import org.cryptobiotic.rlauxe.estimate.MultiContestTestData
import org.cryptobiotic.rlauxe.estimate.makeFuzzedCvrsFrom
import org.cryptobiotic.rlauxe.persist.csv.writeAuditableCardCsvFile
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.clearDirectory
import org.cryptobiotic.rlauxe.persist.validateOutputDirOfFile
import org.cryptobiotic.rlauxe.raire.RaireContestUnderAudit
import org.cryptobiotic.rlauxe.raire.simulateRaireTestContest
import org.cryptobiotic.rlauxe.workflow.*
import kotlin.io.path.Path
import kotlin.math.min

/**
 * Create a multicontest audit, with fuzzed test data, stored in private record.
 */
object RunRlaStartFuzz {

    @JvmStatic
    fun main(args: Array<String>) {
        val parser = ArgParser("RunRlaStartFuzz")
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
        println("RunRlaStartFuzz on $inputDir isPolling=$isPolling minMargin=$minMargin fuzzMvrs=$fuzzMvrs, pctPhantoms=$pctPhantoms, ncards=$ncards ncontests=$ncontests" +
                " addRaire=$addRaireContest addRaireCandidates=$addRaireCandidates")
        if (!isPolling) startTestElectionClca(inputDir, minMargin, fuzzMvrs, pctPhantoms, ncards, ncontests, addRaireContest, addRaireCandidates)
            else startTestElectionPolling(inputDir, minMargin, fuzzMvrs, pctPhantoms, ncards, ncontests)
    }

    // TODO use CreateAudit
    fun startTestElectionClca(
        auditDir: String,
        minMargin: Double,
        fuzzMvrs: Double,
        pctPhantoms: Double?,
        ncards: Int,
        ncontests: Int,
        addRaire: Boolean,
        addRaireCandidates: Int,
    ): Int {
        println("Start startTestElectionClca")
        clearDirectory(Path(auditDir))

        val publisher = Publisher(auditDir)
        val auditConfig = AuditConfig(
            AuditType.CLCA, hasStyles = true, nsimEst = 100,
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
        println("$testData")

        // Synthetic cvrs for testing, reflecting the exact contest votes, plus undervotes and phantoms.
        // TODO add raire cvrs here
        var testCvrs = testData.makeCvrsFromContests()
        println("ncvrs = ${testCvrs.size}")

        val raireContests = mutableListOf<RaireContestUnderAudit>()
        if (addRaire) {
            val (rcontest: RaireContestUnderAudit, rcvrs: List<Cvr>) = simulateRaireTestContest(N=ncards/2, contestId=111, addRaireCandidates, minMargin=.04, quiet = true)
            raireContests.add(rcontest)
            testCvrs = testCvrs + rcvrs
        }

        val contests: List<Contest> = testData.contests
        val allContests = contests + raireContests.map { it.contest }

        allContests.forEach { println("  $it") }
        println()

        // TODO are these randomized?
        val testMvrs = if (fuzzMvrs == 0.0) testCvrs
                    // fuzzPct of the Mvrs have their votes randomly changed ("fuzzed")
                    else makeFuzzedCvrsFrom(allContests, testCvrs, fuzzMvrs)
        println("nmvrs = ${testMvrs.size}")

        // TODO use MvrManagerTestFromRecord to do sorting and save sortedCards, mvrsUA
        // save the sorted cards
        val mvrManager = MvrManagerClcaForTesting(testCvrs, testMvrs, auditConfig.seed)
        writeAuditableCardCsvFile(mvrManager.sortedCards, publisher.cardsCsvFile()) // TODO wrap in Result ??
        println("   write ${mvrManager.sortedCards.size} sortedCards to ${publisher.cardsCsvFile()}")

        // save the sorted testMvrs
        val mvrFile = "$auditDir/private/testMvrs.csv"
        validateOutputDirOfFile(mvrFile)
        writeAuditableCardCsvFile(mvrManager.mvrsUA, mvrFile)
        println("   write ${mvrManager.sortedCards.size} testMvrs to ${mvrFile}")

        val clcaWorkflow = ClcaAuditTester(auditConfig, contests, raireContests, mvrManager)
        writeContestsJsonFile(clcaWorkflow.contestsUA(), publisher.contestsFile())
        println("   writeContestsJsonFile ${publisher.contestsFile()}")

        // get the first round of samples wanted, write them to round1 subdir
        val auditRound = runChooseSamples(clcaWorkflow, publisher)

        // write the partial audit state to round1
        writeAuditRoundJsonFile(auditRound, publisher.auditRoundFile(1))
        println("   writeAuditStateJsonFile ${publisher.auditRoundFile(1)}")

        return if (auditRound.samplePrns.isNotEmpty()) 0 else 1
    }

    fun startTestElectionPolling(
        auditDir: String,
        minMargin: Double,
        fuzzMvrsPct: Double,
        pctPhantoms: Double?,
        ncards: Int,
        ncontests: Int = 11,
    ): Int {
        println("Start startTestElectionPolling")
        clearDirectory(Path(auditDir))

        val publisher = Publisher(auditDir)
        val auditConfig = AuditConfig(AuditType.POLLING, hasStyles = true, nsimEst = 100)
        writeAuditConfigJsonFile(auditConfig, publisher.auditConfigFile())
        println("   writeAuditConfigJsonFile ${publisher.auditConfigFile()}")

        val maxMargin = .08
        val useMin = min(minMargin, maxMargin)
        val phantomPctRange: ClosedFloatingPointRange<Double> =
            if (pctPhantoms == null) 0.00..0.005 else pctPhantoms..pctPhantoms
        val testData = MultiContestTestData(ncontests, 4, ncards, marginRange = useMin..maxMargin, phantomPctRange = phantomPctRange)

        val contests: List<Contest> = testData.contests
        println("Start testPersistentWorkflowPolling $testData")
        contests.forEach { println("  $it") }
        println()

        val (testCvrs, ballots) = testData.makeCvrsAndBallots(auditConfig.hasStyles)
        val testMvrs = makeFuzzedCvrsFrom(contests, testCvrs, fuzzMvrsPct)
        val pairs = testMvrs.zip(testCvrs)
        pairs.forEach { (mvr, cvr) ->
            require(mvr.id == cvr.id)
        }

        // TODO use MvrManagerTestFromRecord to do sorting and save sortedCards, mvrsUA
        // save the sorted cards
        val mvrManager = MvrManagerPollingForTesting(ballots, testMvrs, auditConfig.seed)
        writeAuditableCardCsvFile(mvrManager.sortedCards, publisher.cardsCsvFile())
        println("   writeCvrsCvsFile ${publisher.cardsCsvFile()}")

        // save the sorted testMvrs
        val mvrFile = "$auditDir/private/testMvrs.csv"
        validateOutputDirOfFile(mvrFile)
        writeAuditableCardCsvFile(mvrManager.mvrsUA, mvrFile)
        println("   writeMvrsJsonFile ${mvrFile}")

        // PollingWorkflow creates the assertions
        val pollingWorkflow = PollingAuditTester(auditConfig, contests, mvrManager)

        writeContestsJsonFile(pollingWorkflow.contestsUA(), publisher.contestsFile())
        println("   writeContestsJsonFile ${publisher.contestsFile()}")

        // get the first round of samples wanted, write them to round1 subdir
        val auditRound = runChooseSamples(pollingWorkflow, publisher)

        // write the partial audit state to round1
        writeAuditRoundJsonFile(auditRound, publisher.auditRoundFile(1))
        println("   writeAuditStateJsonFile ${publisher.auditRoundFile(1)}")

        return if (auditRound.samplePrns.isNotEmpty()) 0 else 1
    }
}

fun runChooseSamples(workflow: RlauxAuditIF, publish: Publisher): AuditRound {
    val round = workflow.startNewRound(quiet = false)
    if (round.samplePrns.isNotEmpty()) {
        writeSamplePrnsJsonFile(round.samplePrns, publish.samplePrnsFile(round.roundIdx))
        println("   writeSampleIndicesJsonFile ${publish.samplePrnsFile(round.roundIdx)}")
    } else {
        println("*** FAILED TO GET ANY SAMPLES ***")
    }
    return round
}

