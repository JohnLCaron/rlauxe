package org.cryptobiotic.rlauxe.cli

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.estimate.OneAuditVunderBarFuzzer
import org.cryptobiotic.rlauxe.oneaudit.OneAuditPoolIF

import org.cryptobiotic.rlauxe.oneaudit.makeOneAuditTestP
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.clearDirectory
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIterator
import org.cryptobiotic.rlauxe.persist.json.readCardPoolsJsonFileUnwrapped
import org.cryptobiotic.rlauxe.persist.json.readContestsJsonFileUnwrapped
import org.cryptobiotic.rlauxe.util.Closer
import org.cryptobiotic.rlauxe.util.VunderBar
import org.cryptobiotic.rlauxe.workflow.readCardManifest
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

        parser.parse(args)
        println(
            "RunRlaCreateOneAudit on $inputDir minMargin=$minMargin fuzzMvrs=$fuzzMvrs, pctPhantoms=$pctPhantoms, ncards=$ncards hasStyle=$hasStyle" +
                    " extra=$extra"
        )
        startTestElectionOneAudit(
            inputDir,
            minMargin,
            fuzzMvrs,
            pctPhantoms,
            ncards,
            hasStyle,
            extra,
        )
    }

    fun startTestElectionOneAudit(
        topdir: String,
        minMargin: Double,
        fuzzMvrs: Double,
        pctPhantoms: Double?,
        ncards: Int,
        hasStyle: Boolean,
        extraPct: Double,
    ) {
        val auditDir = "$topdir/audit"
        clearDirectory(Path(auditDir))

        val config = AuditConfig(
            AuditType.ONEAUDIT, hasStyle = true, contestSampleCutoff = 20000, nsimEst = 10, simFuzzPct = fuzzMvrs,
            oaConfig = OneAuditConfig(OneAuditStrategyType.clca, useFirst = true)
        )

        clearDirectory(Path(auditDir))
        val election = TestOneAuditElection(
            auditDir,
            config,
            minMargin,
            pctPhantoms,
            ncards,
            hasStyle,
            extraPct,
        )

        CreateAuditP("RunRlaStartOneAudit", config, election, auditDir = "$topdir/audit", clear = false)

        // write the sorted cards: why isnt this part of CreateAudit? Because seed must be generated after committment to cardManifest
        val publisher = Publisher(auditDir)
        writeSortedCardsInternalSort(publisher, config.seed)

        // simulate the mvrs, write to private dir
        val contests = readContestsJsonFileUnwrapped(publisher.contestsFile())
        val infos = contests.map{ it.contest.info() }.associateBy { it.id }
        val cardManifest = readCardManifest(publisher, infos)
        val scardIter = cardManifest.cards.iterator()
        val sortedCards = mutableListOf<AuditableCard>()
        scardIter.forEach { sortedCards.add(it) }

        // TODO test OneAuditVunderBarFuzzer
        val vunderFuzz = OneAuditVunderBarFuzzer(VunderBar(cardManifest.populations as List<OneAuditPoolIF>, infos), infos, fuzzMvrs)
        val oaFuzzedPairs: List<Pair<AuditableCard, AuditableCard>> = vunderFuzz.makePairsFromCards(sortedCards)
        val sortedMvrs = oaFuzzedPairs.map { it.first }
        // have to write this here, where we know the mvrs
        writeSortedMvrs(publisher, sortedMvrs)
    }

    class TestOneAuditElection(
        auditDir: String,
        val config: AuditConfig,
        minMargin: Double,
        pctPhantoms: Double?,
        ncards: Int,
        hasStyle: Boolean,
        extraPct: Double,
    ): CreateElectionPIF {
        val contestsUA = mutableListOf<ContestUnderAudit>()
        val cardPools: List<PopulationIF>
        val cardManifest: List<AuditableCard>

        init {
            // one contest
            val (contestOA, mvrs, cardManifest, pools) = makeOneAuditTestP(
                margin = minMargin,
                Nc = ncards,
                cvrFraction = .95,
                undervoteFraction = .01,
                phantomFraction = pctPhantoms ?: 0.0,
                hasStyle=hasStyle,
                extraInPool= (extraPct * ncards).toInt(),
            )
            contestsUA.add(contestOA)
            this.cardManifest = cardManifest
            this.cardPools = pools
        }

        override fun populations() = cardPools
        override fun contestsUA() = contestsUA
        override fun cardManifest() = Closer (cardManifest.iterator() )
    }
}
