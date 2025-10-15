package org.cryptobiotic.rlauxe.cli

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import org.cryptobiotic.rlauxe.audit.*

import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.CvrExport
import org.cryptobiotic.rlauxe.persist.json.*
import org.cryptobiotic.rlauxe.estimate.MultiContestTestData
import org.cryptobiotic.rlauxe.estimate.makeFuzzedCvrsFrom
import org.cryptobiotic.rlauxe.oneaudit.CardPoolIF
import org.cryptobiotic.rlauxe.oneaudit.OAContestUnderAudit
import org.cryptobiotic.rlauxe.oneaudit.makeOneContestUA
import org.cryptobiotic.rlauxe.oneaudit.toCardPools
import org.cryptobiotic.rlauxe.persist.csv.writeAuditableCardCsvFile
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.clearDirectory
import org.cryptobiotic.rlauxe.persist.validateOutputDirOfFile
import org.cryptobiotic.rlauxe.raire.RaireContestUnderAudit
import org.cryptobiotic.rlauxe.raire.simulateRaireTestContest
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.Closer
import org.cryptobiotic.rlauxe.workflow.*
import kotlin.io.path.Path
import kotlin.math.min

object RunRlaStartOneAudit {

    @JvmStatic
    fun main(args: Array<String>) {
        val parser = ArgParser("RunRlaStartOneAudit")
        val inputDir by parser.option(
            ArgType.String,
            shortName = "in",
            description = "Directory containing test election record (topdir)"
        ).required()
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
        println(
            "RunRlaStartOneAudit on $inputDir minMargin=$minMargin fuzzMvrs=$fuzzMvrs, pctPhantoms=$pctPhantoms, ncards=$ncards ncontests=$ncontests" +
                    " addRaire=$addRaireContest addRaireCandidates=$addRaireCandidates"
        )
        startTestElectionOneAudit(
            inputDir,
            minMargin,
            fuzzMvrs,
            pctPhantoms,
            ncards,
            ncontests,
            addRaireContest,
            addRaireCandidates
        )
    }

    fun startTestElectionOneAudit(
        topdir: String,
        minMargin: Double,
        fuzzMvrs: Double,
        pctPhantoms: Double?,
        ncards: Int,
        ncontests: Int,
        addRaire: Boolean,
        addRaireCandidates: Int,
    ) {
        println("Start TestElectionOneAudit")
        val auditDir = "$topdir/audit"
        clearDirectory(Path(auditDir))

        val auditConfig = AuditConfig(
            AuditType.ONEAUDIT, hasStyles = true, sampleLimit = 20000, nsimEst = 10,
            oaConfig = OneAuditConfig(OneAuditStrategyType.optimalComparison, useFirst = true)
        )

        clearDirectory(Path(auditDir))
        val election = TestOneAuditElection(
            auditDir,
            auditConfig,
            minMargin,
            fuzzMvrs,
            pctPhantoms,
            ncards,
            ncontests,
            addRaire,
            addRaireCandidates)

        CreateAudit("RunRlaStartOneAudit", topdir = topdir, auditConfig, election, clear = false)
    }

    class TestOneAuditElection(
        auditDir: String,
        auditConfig: AuditConfig,
        minMargin: Double,
        fuzzMvrs: Double,
        pctPhantoms: Double?,
        ncards: Int,
        ncontests: Int,
        addRaire: Boolean,
        addRaireCandidates: Int,
    ): ElectionIF {
        val workflow: OneAudit
        val contestsUA = mutableListOf<OAContestUnderAudit>()
        val allCardPools = mutableListOf<CardPoolIF>()
        var allCvrs: List<Cvr>

        init {
            // fun makeOneContestUA(
            //    margin: Double,
            //    Nc: Int,
            //    cvrFraction: Double,
            //    undervoteFraction: Double,
            //    phantomFraction: Double,
            //): Triple<OAContestUnderAudit, List<BallotPool>, List<Cvr>> {
            val (contestOA, ballotPools, testCvrs) = makeOneContestUA(
                margin = minMargin,
                Nc = ncards,
                cvrFraction = .95,
                undervoteFraction = .01,
                phantomFraction = pctPhantoms ?: 0.0
            )
            // Synthetic cvrs for testing, reflecting the exact contest votes, plus undervotes and phantoms.
            println("ncvrs = ${testCvrs.size}")
            allCvrs = testCvrs

            contestsUA.add(contestOA)
            val infos = mapOf(contestOA.contest.info().id to contestOA.contest.info())
            val cardPools = ballotPools.toCardPools(infos)
            allCardPools.addAll(cardPools)

            /*
            if (addRaire) {
                val (rcontest: RaireContestUnderAudit, rcvrs: List<Cvr>) = simulateRaireTestContest(
                    N = ncards / 2,
                    contestId = 111,
                    addRaireCandidates,
                    minMargin = .04,
                    quiet = true
                )
                contestsUA.add(rcontest)
                allCvrs = testCvrs + rcvrs
            } */

            val allContests = contestsUA.map { it.contest }
            allContests.forEach { println("  $it") }
            println()

            // TODO are these randomized?
            val testMvrs = if (fuzzMvrs == 0.0) testCvrs
                // fuzzPct of the Mvrs have their votes randomly changed ("fuzzed")
                else makeFuzzedCvrsFrom(allContests, testCvrs, fuzzMvrs)
            println("nmvrs = ${testMvrs.size} fuzzed at ${fuzzMvrs}")

            // TODO use MvrManagerTestFromRecord to do sorting and save sortedCards, mvrsUA
            // save the sorted cards

            val mvrManager = MvrManagerOneAuditForTesting(testCvrs, testMvrs, auditConfig.seed)
            // writeAuditableCardCsvFile(mvrManager.sortedCards, publisher.cardsCsvFile())
            // println("   write ${mvrManager.sortedCards.size} sortedCards to ${publisher.cardsCsvFile()}")

            // save the sorted testMvrs
            val mvrFile = "$auditDir/private/testMvrs.csv"
            validateOutputDirOfFile(mvrFile)
            writeAuditableCardCsvFile(mvrManager.mvrsUA, mvrFile)
            println("   write ${mvrManager.sortedCards.size} testMvrs to ${mvrFile}")

            workflow = OneAudit(auditConfig, contestsUA, mvrManager=mvrManager)
            //writeContestsJsonFile(workflow.contestsUA(), publisher.contestsFile())
            //println("   writeContestsJsonFile ${publisher.contestsFile()}")

            // get the first round of samples wanted, write them to round1 subdir
            val publisher = Publisher(auditDir)
            val auditRound = runChooseSamples(workflow, publisher)
            writeAuditRoundJsonFile(auditRound, publisher.auditRoundFile(1))
            println("   writeAuditStateJsonFile ${publisher.auditRoundFile(1)}")
        }

        override fun makeCardPools() = allCardPools

        override fun makeContestsUA(hasStyles: Boolean) = contestsUA

        override fun makeCvrs() = allCvrs

        override fun cvrExport() = Closer(emptyList<CvrExport>().iterator())

        override fun hasCvrExport() = false
    }
}
