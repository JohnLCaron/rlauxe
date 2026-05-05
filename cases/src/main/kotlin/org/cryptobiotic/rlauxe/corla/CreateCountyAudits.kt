package org.cryptobiotic.rlauxe.corla

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.AuditCreationConfig
import org.cryptobiotic.rlauxe.audit.AuditRoundConfig
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.CardPoolIF
import org.cryptobiotic.rlauxe.audit.CardStyle
import org.cryptobiotic.rlauxe.audit.CardWithBatchName
import org.cryptobiotic.rlauxe.audit.Config
import org.cryptobiotic.rlauxe.audit.ElectionBuilder
import org.cryptobiotic.rlauxe.audit.ElectionInfo
import org.cryptobiotic.rlauxe.audit.MergeBatchesIntoCardManifestIterator
import org.cryptobiotic.rlauxe.audit.MvrsToCardsWithBatchNameIterator
import org.cryptobiotic.rlauxe.audit.createAuditRecord
import org.cryptobiotic.rlauxe.audit.createElectionRecord
import org.cryptobiotic.rlauxe.audit.startFirstRound
import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.ContestIF
import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.estimate.makeCvrsForOnePool
import org.cryptobiotic.rlauxe.oneaudit.OneAuditPoolFromBallotStyle
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.clearDirectory
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIterator
import org.cryptobiotic.rlauxe.persist.csv.writeCardCsvFile
import org.cryptobiotic.rlauxe.persist.validateOutputDirOfFile
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.Closer
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.TransformingIterator
import org.cryptobiotic.rlauxe.util.makePhantomCvrs
import org.cryptobiotic.rlauxe.util.roundUp
import org.cryptobiotic.rlauxe.utils.tabulateCardsAndCount
import kotlin.collections.associateBy
import kotlin.io.path.Path

private val logger = KotlinLogging.logger("CreateCountyAudits")

private val debugUndervotes = true

class CreateCountyAudits(
    val countyName: String,
    val auditdir: String,
    val stateElection: ColoradoCountyElection,
    val countyContestTab: CountyContestTab
): ElectionBuilder {
    val publisher = Publisher(auditdir)
    val ncards: Int
    val contestsUA: List<ContestWithAssertions>
    val countyCardPools: List<CardPoolIF>

    init {
        val builders: List<CountyContestBuilder> =
            stateElection.corlaContestBuilders.filter { it.counties.contains(countyName) }
                .map { CountyContestBuilder(it, countyContestTab.contests[it.orgContestName]!!) }

        countyCardPools = stateElection.cardPools.filter { it.poolName.lowercase().startsWith(countyName.lowercase()) }

        // set contest total cards as sum over pools
        builders.forEach { it.setTotalCardsFromPools(countyCardPools) }

        val contests = builders.map { it.makeContest() }

        // have to save the mvrs and generate the cardManifest from them
        clearDirectory(Path(auditdir))
        ncards = createAndSaveUnsortedMvrs(contests, countyCardPools, publisher)

        // read them back in as an Iterator, so we dont have to read all into memory
        val infos = contests.map { it.info() }.associateBy { it.id }
        val mvrs: CloseableIterator<CardWithBatchName> = readCardsCsvIterator(publisher.unsortedMvrsFile())
        val auditableCardIter: CloseableIterator<AuditableCard> =
            MergeBatchesIntoCardManifestIterator(mvrs, countyCardPools)

        // are we handling the batches correctly using mvrs?
        val (manifestTabs, count) = tabulateCardsAndCount(auditableCardIter, infos)
        require(ncards == count)
        val npopMapm: Map<Int, Int> = manifestTabs.mapValues { it.value.ncardsTabulated }
        val npopMap: Map<Int, Int> = builders.associate { it.info.id to (it.Npop ?: npopMapm[it.info.id] ?: 1) }

        contestsUA = ContestWithAssertions.make(contests, npopMap, isClca = true)
    }

    override fun electionInfo(): ElectionInfo {
        val useName = countyName.replace(" ", "_")
        return ElectionInfo(
            useName, AuditType.CLCA, ncards,
            contestsUA.size, pollingMode = null
        )
    }

    override fun contestsUA() = contestsUA

    override fun cards(): CloseableIterator<CardWithBatchName> {
        val publisher = Publisher(auditdir)
        val unsortedMvrs = readCardsCsvIterator(publisher.unsortedMvrsFile())
        return TransformingIterator(unsortedMvrs) { mvr ->
            when {
                mvr.phantom -> mvr
                else -> mvr.copy(poolId = null, styleName = CardStyle.fromCvr)
            }
        }
    }

    override fun ncards() = ncards
    override fun cardStyles() = countyCardPools
    override fun cardPools() = countyCardPools
    override fun createUnsortedMvrsInternal() = null
    override fun createUnsortedMvrsExternal() = null
    override fun toString(): String {
        return "CorlaElectionBuilder(countyName='$countyName', auditdir='$auditdir')"
    }

    // for one county, one contest
    class CountyContestBuilder(corlaContestBuilder: CorlaContestBuilder, contestTab: ContestTab) {
        val info = corlaContestBuilder.info
        val Nc: Int     // taken from contestRound.contestBallotCardCount
        var Npop: Int? = null
        val candidateVotes: Map<Int, Int>

        var poolTotalCards: Int = 0
        var poolTotalVotes: Int = 0

        init {
            // TODO this assume these are in teh same order as for corlaContestBuilder.info. BAD
            candidateVotes = contestTab.choices.values.toList().mapIndexed { idx, choice -> Pair(idx, choice) }.toMap()
            val totalVotes = roundUp(contestTab.contestVotes() / info.voteForN.toDouble())

            val singleCounty = corlaContestBuilder.counties.size == 1
            if (corlaContestBuilder.contestRound != null && singleCounty) {
                var useNc = corlaContestBuilder.contestRound.contestBallotCardCount
                if (useNc < totalVotes) {
                    println("*** Contest '${info.name}' has $totalVotes total votes, but CorlaContestRoundCsv.contestBallotCardCount is ${corlaContestBuilder.contestRound.contestBallotCardCount} - using totalVotes")
                    useNc = totalVotes
                }
                Nc = useNc
                Npop = corlaContestBuilder.contestRound.ballotCardCount
            } else { // we dont know the Nc or Npop by County....; could pass in the division of Nc (proportional to voteCount)? barf
                Nc = totalVotes
            }
        }

        fun setTotalCardsFromPools(cardPools: List<CardPoolIF>) {
            poolTotalCards = cardPools.filter { it.hasContest(info.id) }.sumOf { it.ncards() }
            poolTotalVotes = cardPools.filter { it.hasContest(info.id) }.sumOf { it.contestTab(info.id)!!.nvotes() }
        }

        fun makeContest(): Contest {
            val candVotes = candidateVotes.filter { info.candidateIds.contains(it.key) } // get rid of writeins?
            // val totalVotes = candVotes.map {it.value}.sum()
            // val ncards = max(builder.poolTotalCards(), totalVotes)
            // val useNc = max(ncards, builder.Nc)
            info.metadata["PoolPct"] = (100.0 * poolTotalCards / Nc).toInt().toString()
            // assume Ncast = Nc; maybe Ncase = builder.poolTotalCards() ??
            return Contest(info, candVotes, Nc, Nc)
        }
    }
}

    ////////////////////////////////////////////////////////////////////
// Create audit where pools are from the precinct total. May be CLCA or OneAudit
fun createCountyAudits(
    topdir: String,
    wantCounties: List<String>,
    creationConfig: AuditCreationConfig,
    roundConfig: AuditRoundConfig,
    startFirstRound: Boolean,
) {
    val stopwatch = Stopwatch()

    val countyElection = ColoradoCountyElection()
    val contestTabByCounty: Map<String, CountyContestTab> = convertToCountyContestTabs(Colorado2024Input.contestsByCounty).associateBy { it.countyName }

    /* createColoradoElection(
        externalSortDir = topdir,
        auditdir = "$topdir/audit", // or put it in audit ??
        pollingMode = null,
        creationConfig,
        roundConfig,
        startFirstRound = startFirstRound,
        name = "CorlaContest24",
    ) */

    wantCounties.map { countyName ->
        val election = CreateCountyAudits(countyName, "$topdir/$countyName/audit", countyElection, contestTabByCounty[countyName]!!)

        createElectionRecord(election, auditDir = election.auditdir, clear = false)
        val config = Config(election.electionInfo(), creationConfig, roundConfig)

        createAuditRecord(config, election, auditDir = election.auditdir, externalSortDir = "$topdir/${election.countyName}")

        if (startFirstRound) {
            val result = startFirstRound(election.auditdir)
            if (result.isErr) logger.error { result.toString() }
        }
    }

    logger.info { "createCountyAudits for $wantCounties took $stopwatch" }
}

