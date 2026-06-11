package org.cryptobiotic.rlauxe.corla

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.AuditCreationConfig
import org.cryptobiotic.rlauxe.audit.AuditRoundConfig
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.CardPoolIF
import org.cryptobiotic.rlauxe.audit.CardStyle
import org.cryptobiotic.rlauxe.audit.AuditableCardM
import org.cryptobiotic.rlauxe.audit.Config
import org.cryptobiotic.rlauxe.audit.ElectionBuilder
import org.cryptobiotic.rlauxe.audit.ElectionInfo
import org.cryptobiotic.rlauxe.audit.Sampling
import org.cryptobiotic.rlauxe.audit.createAuditRecord
import org.cryptobiotic.rlauxe.audit.createElectionRecord
import org.cryptobiotic.rlauxe.audit.startFirstRound
import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.clearDirectory
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIteratorM
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.TransformingIterator
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.util.roundUp
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import kotlin.collections.associateBy
import kotlin.io.path.Path

private val logger = KotlinLogging.logger("CreateCountyAudits")

private val debugUndervotes = true

// TODO is this obsolete ??

class CreateCountyAudits(
    val countyName: String,
    val auditdir: String,
    val stateElection: CountyContestBuilder,
    val countyContestTab: CountyContestTabs,
    val hasStyle: Boolean,
): ElectionBuilder {
    val publisher = Publisher(auditdir)
    val ncards: Int
    val contestsUA: List<ContestWithAssertions>
    val countyCardPools: List<CardPoolIF>

    init {
        val builders: List<CountyContestBuilderOld> =
            stateElection.corlaContestBuilders.filter { it.counties.contains(countyName) }
                .map { CountyContestBuilderOld(it, countyContestTab.contests[it.info.name]!!) }

        countyCardPools = emptyList() // stateElection.cardPools.filter { it.poolName.lowercase().startsWith(countyName.lowercase()) }

        // set contest pool totals
        builders.forEach { it.setTotalCardsFromPools(countyCardPools) }

        val contests = builders.map { it.makeContest() }

        // have to save the mvrs and generate the cardManifest from them
        clearDirectory(Path(auditdir))
        ncards = createAndSaveUnsortedMvrs(contests, countyCardPools, publisher)

        // read them back in as an Iterator, so we dont have to read all into memory
        // val infos = contests.map { it.info() }.associateBy { it.id }
        // val auditableCardIter: CloseableIterator<AuditableCardM> = readCardsCsvIteratorM(publisher.unsortedMvrsFile(), styles = countyCardPools)

        /* are we handling the batches correctly using mvrs?
        val (manifestTabs, count) = tabulateCardsAndCount(auditableCardIter, infos)
        require(ncards == count)
        val npopMapm: Map<Int, Int> = manifestTabs.mapValues { it.value.ncardsTabulated }
        val npopMap: Map<Int, Int> = builders.associate { it.info.id to (it.Npop ?: npopMapm[it.info.id] ?: 1) } */

        val npopMap: Map<Int, Int> = builders.associate { it.info.id to it.Npop!! }.toMap()

        contestsUA = ContestWithAssertions.make(contests, npopMap, isClca = true, hasStyle = hasStyle)
    }

    override fun electionInfo(): ElectionInfo {
        // val useName = countyName.replace(" ", "_")
        return ElectionInfo(
            countyName, AuditType.CLCA, ncards,
            contestsUA.size, pollingMode = null
        )
    }

    override fun contestsUA() = contestsUA

    override fun cards(): CloseableIterator<AuditableCardM> {
        val publisher = Publisher(auditdir)
        val unsortedMvrs: CloseableIterator<AuditableCardM> = readCardsCsvIteratorM(publisher.unsortedMvrsFile(), styles = null)
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
    class CountyContestBuilderOld(corlaContestBuilder: CorlaContestBuilder, contestTab: CountyContestTab) {
        val info = corlaContestBuilder.info
        val Nc: Int     // taken from contestRound.contestBallotCardCount
        var Npop: Int = 0
        val candidateVotes: Map<Int, Int>

        var poolTotalCards: Int = 0
        var poolTotalVotes: Int = 0

        init {
            // TODO this assume these are in the same order as for corlaContestBuilder.info. BAD
            candidateVotes = contestTab.choices.values.toList().mapIndexed { idx, choice -> Pair(idx, choice) }.toMap()
            val totalVotes = roundUp(contestTab.contestVotes() / info.voteForN.toDouble())

            val singleCounty = corlaContestBuilder.counties.size == 1
            // TODO single county appropriate for the individual counties ??
            if (corlaContestBuilder.contest != null) { //  && singleCounty) {
                var useNc = corlaContestBuilder.contest.nc
                if (useNc < totalVotes) {
                    println("*** Contest '${info.name}' has $totalVotes total votes, but CorlaContestRoundCsv.contestBallotCardCount is ${corlaContestBuilder.contest.nc} - using totalVotes")
                    useNc = totalVotes
                }
                Nc = useNc
                Npop = corlaContestBuilder.contest.npop
            } else { // we dont know the Nc or Npop by County....; could pass in the division of Nc (proportional to voteCount)? barf
                Nc = totalVotes
                Npop = totalVotes
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
    coloradoInput: ColoradoInput,
    creationConfig: AuditCreationConfig,
    roundConfig: AuditRoundConfig,
    startFirstRound: Boolean,
) {
    val stopwatch = Stopwatch()

    // misc data by county
    writeCountyAuditData(topdir, coloradoInput)

    val countyElection = CountyContestBuilder(coloradoInput)
    val contestTabByCounty: Map<String, CountyContestTabs> = convertToCountyContestTabs(coloradoInput.contestTabsByCounty.values.toList())
        .associateBy { it.countyName }

    /* createColoradoElection(
        externalSortDir = topdir,
        auditdir = "$topdir/audit", // or put it in audit ??
        pollingMode = null,
        creationConfig,
        roundConfig,
        startFirstRound = startFirstRound,
        name = "Corla24County",
    ) */
    val whichCounties = if (wantCounties.isNotEmpty()) wantCounties else contestTabByCounty.keys.toList()

    whichCounties.map { countyName ->
        val election = CreateCountyAudits(countyName, "$topdir/$countyName/audit", countyElection,
            contestTabByCounty[countyName]!!,
            hasStyle = roundConfig.sampling.sampling == Sampling.consistent)

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

fun writeCountyAuditData(topdir: String, coloradoInput: ColoradoInput) {
    // misc data by county
    val countyMvrs = coloradoInput.countyMvrs

    val outputFilename = "$topdir/countyData.csv"
    val writer: OutputStreamWriter = FileOutputStream(outputFilename).writer()
    writer.write("county,   nmvrs, npop\n")
    countyMvrs.sortedBy { it.countyName }.forEach {
        writer.write("${it.countyName}, ${nfn(it.countMvr, 5)}\n")
    }
    writer.close()
    println("wrote countyData to $outputFilename")
}

