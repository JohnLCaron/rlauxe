package org.cryptobiotic.rlauxe.cli

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.estimate.OneAuditVunderBarFuzzer

import org.cryptobiotic.rlauxe.oneaudit.CardPoolIF
import org.cryptobiotic.rlauxe.oneaudit.makeOneAuditTest
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.clearDirectory
import org.cryptobiotic.rlauxe.persist.csv.readAuditableCardCsvFile
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIterator
import org.cryptobiotic.rlauxe.persist.json.readCardPoolsJsonFileUnwrapped
import org.cryptobiotic.rlauxe.persist.json.readContestsJsonFile
import org.cryptobiotic.rlauxe.persist.json.readContestsJsonFileUnwrapped
import org.cryptobiotic.rlauxe.util.Closer
import org.cryptobiotic.rlauxe.util.VunderBar
import kotlin.io.path.Path
import kotlin.test.assertEquals

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

        CreateAudit("RunRlaStartOneAudit", config, election, auditDir = "$topdir/audit", clear = false)

        // write the cardManifest TODO why isnt this part of CreateAudit? Because seed must be generated after committment to cardManifest
        val publisher = Publisher(auditDir)
        writeSortedCardsInternalSort(publisher, config.seed)

        // simulate the mvrs, write to private dir
        val contests = readContestsJsonFileUnwrapped(publisher.contestsFile())
        val infos = contests.map{ it.contest.info() }.associateBy { it.id }
        val cardPools = readCardPoolsJsonFileUnwrapped(publisher.cardPoolsFile(), infos)
        val cardIter = readCardsCsvIterator(publisher.cardManifestFile())
        val cards = mutableListOf<AuditableCard>()
        cardIter.forEach { cards.add(it) }

        // TODO test OneAuditVunderBarFuzzer
        val vunderFuzz = OneAuditVunderBarFuzzer(VunderBar(cardPools), infos, fuzzMvrs)
        val oaFuzzedPairs: List<Pair<Cvr, AuditableCard>> = vunderFuzz.makePairsFromCards(cards)
        val fuzzedMvrs = oaFuzzedPairs.map { it.first }
        // have to write this here, where we know the mvrs
        writeSortedMvrs(publisher, fuzzedMvrs, config.seed) // permutes
    }

    class TestOneAuditElection(
        auditDir: String,
        val config: AuditConfig,
        minMargin: Double,
        pctPhantoms: Double?,
        ncards: Int,
        hasStyle: Boolean,
        extraPct: Double,
    ): CreateElectionIF {
        val contestsUA = mutableListOf<ContestUnderAudit>()
        val cardPools: List<CardPoolIF>
        val cardManifest: List<AuditableCard>

        init {
            // one contest
            val (contestOA, mvrs, cardManifest, pools) = makeOneAuditTest(
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

        override fun cardPools() = cardPools
        override fun contestsUA() = contestsUA
        override fun cardManifest() = Closer (cardManifest.iterator() )
    }
}
