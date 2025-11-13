package org.cryptobiotic.rlauxe.cli

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.ContestUnderAudit

import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.estimate.makeFuzzedCvrsFrom
import org.cryptobiotic.rlauxe.oneaudit.CardPoolIF
import org.cryptobiotic.rlauxe.oneaudit.makeOneContestUA
import org.cryptobiotic.rlauxe.persist.clearDirectory
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.Closer
import org.cryptobiotic.rlauxe.util.tabulateCvrs
import kotlin.io.path.Path

object RunRlaCreateOneAudit {

    @JvmStatic
    fun main(args: Array<String>) {
        val parser = ArgParser("RunRlaCreateOneAudit")
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
            "RunRlaCreateOneAudit on $inputDir minMargin=$minMargin fuzzMvrs=$fuzzMvrs, pctPhantoms=$pctPhantoms, ncards=$ncards ncontests=$ncontests" +
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
        val auditDir = "$topdir/audit"
        clearDirectory(Path(auditDir))

        val config = AuditConfig(
            AuditType.ONEAUDIT, hasStyle = true, contestSampleCutoff = 20000, nsimEst = 10,
            oaConfig = OneAuditConfig(OneAuditStrategyType.optimalComparison, useFirst = true)
        )

        clearDirectory(Path(auditDir))
        val election = TestOneAuditElection(
            auditDir,
            config,
            minMargin,
            fuzzMvrs,
            pctPhantoms,
            ncards,
            ncontests,
            addRaire,
            addRaireCandidates)

        CreateAudit("RunRlaStartOneAudit", topdir = topdir, config, election, clear = false)
    }

    class TestOneAuditElection(
        auditDir: String,
        val config: AuditConfig,
        minMargin: Double,
        fuzzMvrs: Double,
        pctPhantoms: Double?,
        ncards: Int,
        ncontests: Int,
        addRaire: Boolean,
        addRaireCandidates: Int,
    ): CreateElectionIF {
        val contestsUA = mutableListOf<ContestUnderAudit>()
        val allCardPools: List<CardPoolIF>
        val allCvrs: List<Cvr>
        val testMvrs: List<Cvr>

        init {
            // Synthetic cvrs for testing, reflecting the exact contest votes, plus undervotes and phantoms.
            // includes the pools votes
            val (contestOA, cardPools, testCvrs) = makeOneContestUA(
                margin = minMargin,
                Nc = ncards,
                cvrFraction = .95,
                undervoteFraction = .01,
                phantomFraction = pctPhantoms ?: 0.0
            )
            allCvrs = testCvrs
            allCardPools = cardPools

            contestsUA.add(contestOA)
            val infos = mapOf(contestOA.contest.info().id to contestOA.contest.info())

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

            val cvrTabs = tabulateCvrs(allCvrs.iterator(), infos)
            println("allCvrs = ${cvrTabs}")

            val allContests = contestsUA.map { it.contest }
            println("contests")
            allContests.forEach { println("  $it") }
            println()

            // TODO reimplement
            testMvrs = if (fuzzMvrs == 0.0) allCvrs
                // fuzzPct of the Mvrs have their votes randomly changed ("fuzzed")
                else makeFuzzedCvrsFrom(allContests, allCvrs, fuzzMvrs)
            println("nmvrs = ${testMvrs.size} fuzzed at ${fuzzMvrs}")

            val mvrTabs = tabulateCvrs(testMvrs.iterator(), infos)
            println("testMvrs = ${mvrTabs}")
            println()
        }

        override fun cardPools() = allCardPools
        override fun contestsUA() = contestsUA
        override fun cardManifest() = createCardIterator()

        fun createCardIterator(): CloseableIterator<AuditableCard> {
            return CvrsWithStylesToCards(
                config.auditType, config.hasStyle,
                Closer(allCvrs.iterator()),
                null,
                styles = allCardPools,
            )
        }
    }
}
