package org.cryptobiotic.rlauxe.cli

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.audit.OneAuditStrategyType
import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.oneaudit.OneAuditPoolFromCvrs
import org.cryptobiotic.rlauxe.oneaudit.OneAuditVunderFuzzer

import org.cryptobiotic.rlauxe.oneaudit.makeOneAuditTest
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.clearDirectory
import org.cryptobiotic.rlauxe.persist.json.readContestsJsonFileUnwrapped
import org.cryptobiotic.rlauxe.util.Closer
import org.cryptobiotic.rlauxe.workflow.PersistedWorkflowMode
import org.cryptobiotic.rlauxe.workflow.readCardManifest
import org.cryptobiotic.rlauxe.workflow.readCardPools
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
        val cvrFraction by parser.option(
            ArgType.Double,
            shortName = "cvrFraction",
            description = "CVR fraction (0.0 to 1.0)"
        ).default(0.95)
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
        val hasStyle by parser.option(
            type = ArgType.Boolean,
            shortName = "hasStyle",
            description = "hasStyle"
        ).default(true)
        val extra by parser.option(
            type = ArgType.Double,
            shortName = "extraPct",
            description = "add extra percent to simulate diluted margin"
        ).default(.01)
        val calc by parser.option(
            type = ArgType.Boolean,
            shortName = "calc",
            description = "calculate mvrs needed for first round"
        ).default(false)

        parser.parse(args)
        println(
            "RunRlaCreateOneAudit on $inputDir minMargin=$minMargin fuzzMvrs=$fuzzMvrs, cvrFraction=$cvrFraction, ncards=$ncards hasStyle=$hasStyle" +
                    " extra=$extra cals =$calc"
        )
        startTestElectionOneAudit(
            inputDir,
            minMargin,
            fuzzMvrs,
            cvrFraction=cvrFraction,
            ncards,
            extra,
            calc,
        )
    }

    fun startTestElectionOneAudit(
        topdir: String,
        minMargin: Double,
        fuzzPct: Double,
        cvrFraction: Double,
        ncards: Int,
        extraPct: Double,
        calc: Boolean,
    ) {
        val auditDir = "$topdir/audit"
        clearDirectory(Path(auditDir))

        val config = AuditConfig(
            AuditType.ONEAUDIT, contestSampleCutoff = 20000, nsimEst = 10, simFuzzPct = fuzzPct,
            persistedWorkflowMode = PersistedWorkflowMode.testPrivateMvrs,
            oaConfig = if (calc) OneAuditConfig(strategy = OneAuditStrategyType.calcMvrsNeeded) else OneAuditConfig()
        )

        clearDirectory(Path(auditDir))
        val election = TestOneAuditElection(
            auditDir,
            config,
            minMargin,
            cvrFraction = cvrFraction,
            ncards,
            extraPct,
        )

        CreateAudit("RunRlaStartOneAudit", config, election, auditDir = "$topdir/audit", clear = false)

        // write the sorted cards: why isnt this part of CreateAudit? Because seed must be generated after committment to cardManifest
        val publisher = Publisher(auditDir)
        writeSortedCardsInternalSort(publisher, config.seed)

        // simulate the mvrs, write to private dir
        val contests = readContestsJsonFileUnwrapped(publisher.contestsFile())
        val infos = contests.map{ it.contest.info() }.associateBy { it.id }
        val cardManifest = readCardManifest(publisher)
        val cardPools = readCardPools(publisher, infos)
        val scardIter = cardManifest.cards.iterator()
        val sortedCards = mutableListOf<AuditableCard>()
        scardIter.forEach { sortedCards.add(it) }

        // OneAuditVunderFuzzer creates fuzzed mvrs (non-pooled) and simulated mvrs (pooled)
        // TODO use cardPools
        val vunderFuzz =
            OneAuditVunderFuzzer(cardPools!!, infos, fuzzPct, sortedCards)
        val oaFuzzedPairs: List<Pair<AuditableCard, AuditableCard>> = vunderFuzz.mvrCvrPairs
        val sortedMvrs = oaFuzzedPairs.map { it.first }

        // have to write this here, where we know the mvrs
        writePrivateMvrs(publisher, sortedMvrs)
    }

    class TestOneAuditElection(
        auditDir: String,
        val config: AuditConfig,
        minMargin: Double,
        cvrFraction: Double,
        ncards: Int,
        extraPct: Double,
    ): CreateElectionIF {
        val contestsUA = mutableListOf<ContestWithAssertions>()
        val cardPools: List<OneAuditPoolFromCvrs>
        val cardManifest: List<AuditableCard>

        init {
            // one contest
            val (contestOA, mvrs, cardManifest, pools) =
                    makeOneAuditTest(
                        margin = minMargin,
                        Nc = ncards,
                        cvrFraction = cvrFraction,
                        undervoteFraction = .01,
                        phantomFraction = 0.0,
                        extraInPool= (extraPct * ncards).toInt(),
                    )
            contestsUA.add(contestOA)
            this.cardManifest = cardManifest
            this.cardPools = pools
        }

        override fun populations() = cardPools
        override fun cardPools() = cardPools
        override fun contestsUA() = contestsUA
        override fun cardManifest() = Closer (cardManifest.iterator() )
    }
}
