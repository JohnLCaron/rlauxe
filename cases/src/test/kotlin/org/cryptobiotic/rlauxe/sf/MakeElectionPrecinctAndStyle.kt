package org.cryptobiotic.rlauxe.sf

import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.dominion.cvrExportCsvFile
import kotlin.test.Test

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.dominion.CvrExport
import org.cryptobiotic.rlauxe.oneaudit.OneAuditPoolFromCvrs
import org.cryptobiotic.rlauxe.dominion.cvrExportCsvIterator
import org.cryptobiotic.rlauxe.audit.CardPool
import org.cryptobiotic.rlauxe.dominion.CvrExportToCardAdapter
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.util.TransformingIterator
import org.cryptobiotic.rlauxe.util.nfz
import org.cryptobiotic.rlauxe.utils.tabulateCardsAndCount
import kotlin.Boolean
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach
import kotlin.collections.toList

class MakeElectionPrecinctAndStyle {
    val sfDir = "$testdataDir/cases/sf2024"
    val castVoteRecordZip = "$sfDir/CVR_Export_20241202143051.zip"
    val cvrExportCsv = "$sfDir/$cvrExportCsvFile"
    val auditdir = "$testdataDir/cases/sf2024/oaps/audit"
    val contestManifestFilename = "ContestManifest.json"
    val candidateManifestFile = "CandidateManifest.json"

    val creation = AuditCreationConfig(AuditType.ONEAUDIT, riskLimit=.05,)
    val round = AuditRoundConfig(
        SimulationControl(nsimTrials = 22),
        ContestSampleControl(minRecountMargin = .005, minMargin=0.0, contestSampleCutoff = 2500, auditSampleCutoff = 5000),
        ClcaConfig(fuzzMvrs=.001), null)

    val mvrSource: MvrSource = MvrSource.testPrivateMvrs

    @Test
    fun makeElectionPrecinctOA() {

        val election = CreatePrecinctAndStyle(
            castVoteRecordZip,
            contestManifestFilename,
            candidateManifestFile,
            cvrExportCsv,
            auditType = creation.auditType,
            poolsHaveOneCardStyle=true,
            mvrSource = mvrSource
        )

        createElectionRecord(election, auditDir = auditdir, validate = true)

        val config = Config(election.electionInfo(), creation, round)
        createAuditRecord(config, election, auditDir = auditdir, validate = true)

        val result = startFirstRound(auditdir)
        if (result.isErr) logger.error{ result.toString() }
    }
}


private val logger = KotlinLogging.logger("CreateSfprecinctOA")

// use precincts as the pools
class CreatePrecinctAndStyle(
    castVoteRecordZip: String,
    contestManifestFilename: String,
    candidateManifestFile: String,
    val cvrExportCsv: String,
    val auditType: AuditType,
    val poolsHaveOneCardStyle: Boolean,
    val mvrSource: MvrSource = MvrSource.testPrivateMvrs,
): ElectionBuilder {

    val cardPoolMapByName: Map<String, OneAuditPoolFromCvrs>
    val cardPoolBuilders: List<OneAuditPoolFromCvrs>
    val cardPools: List<CardPool>
    val cardStyleMap: Map<Set<Int>, CardStyle>
    //  val cardStyles: List<CardStyleIF>
    val contestsUA: List<ContestWithAssertions>
    val ncards: Int

    val ballotStyles: Map<Int, IntArray> = // ballot style id -> contest Ids
        readBallotTypeContestManifestUnwrapped("src/test/data/SF2024/manifests/BallotTypeContestManifest.json")!!
            .ballotStyles

    init {
        val (contestNcs, contestInfos) = makeContestInfos(
            castVoteRecordZip,
            contestManifestFilename,
            candidateManifestFile
        )
        val infos = contestInfos.associateBy { it.id }

        val (precinctPools, allCvrTabs, styles, unpool) = createPrecinctPools(
            infos,
            cvrExportCsv,
            poolsHaveOneCardStyle,
        )

        cardPoolMapByName = precinctPools
        cardPoolBuilders = cardPoolMapByName.values.toList()
        cardStyleMap = styles
        //  cardStyles = styles.values.toList()

        // the full and complete CardsWithStyleName, merged with the pools
        val auditableCardIter: CloseableIterator<AuditableCard> = MergeBatchesIntoCardManifestIterator(createCards(auditType), cardPoolBuilders)

        val (manifestTabs, count) = tabulateCardsAndCount( auditableCardIter, infos)
        val contestNbs = manifestTabs.mapValues { it.value.ncardsTabulated }
        // println("contestNbs= ${contestNbs}")
        this.ncards = count

        // make contests based on cvr tabulations
        cardPools = cardPoolBuilders.map { it.toOneAuditPool() } // TODO not sure why need to convert
        contestsUA = if (auditType.isClca()) {
            makeClcaContestsSF(infos, allCvrTabs, contestNcs, contestNbs).sortedBy { it.id }
        } else if (auditType.isOA()) {
            makeOneAuditContestsSF(infos, allCvrTabs, contestNcs, contestNbs, unpool, cardPools).sortedBy { it.id } // TODO
        } else {
            makePollingContestsSF(infos, allCvrTabs, contestNcs, contestNbs).sortedBy { it.id }
        }
    }

    fun createPrecinctPools(
        contestInfos: Map<Int, ContestInfo>,
        cvrExportCsv: String,
        poolsHaveOneCardStyle: Boolean,
    ): PrecinctData {

        val cardStyles = mutableMapOf<Set<Int>, CardStyle>() // contests.hashCode -> contests
        val precinctPools: MutableMap<String, OneAuditPoolFromCvrs> = mutableMapOf()
        val unpool = OneAuditPoolFromCvrs("unpool", 0, true, contestInfos)

        val allCvrTabs = mutableMapOf<Int, ContestTabulation>()
        var cardCount = 0
        cvrExportCsvIterator(cvrExportCsv).use { cvrIter ->
            while (cvrIter.hasNext()) {
                cardCount++
                val cvrExport: CvrExport = cvrIter.next()
                if (cvrExport.group == 1) {
                    val cardStyle = cardStyles.getOrPut(cvrExport.votes.keys) { CardStyle.from(cardStyles.size + 1, cvrExport.votes.keys) }
                    val poolName = poolName(cvrExport.precinctPortionId, cardStyle)
                    val pool = precinctPools.getOrPut(poolName) {
                        OneAuditPoolFromCvrs(poolName, precinctPools.size + 1, poolsHaveOneCardStyle, contestInfos)
                    }
                    pool.accumulateVotes(cvrExport.toCvr(null, cvrExport.id))
                } else {
                    unpool.accumulateVotes(cvrExport.toCvr(null, cvrExport.id))
                }
                cvrExport.votes.forEach { (id, cands) ->
                    val contestTab = allCvrTabs.getOrPut(id) { ContestTabulation(contestInfos[id]!!) }
                    contestTab.addVotes(cands, phantom=false)
                }
            }
        }

        return PrecinctData(precinctPools, allCvrTabs, cardStyles, unpool)
    }

    fun poolName(precinctPortionId: Int, cardStyle: CardStyle) = "precinct${nfz(precinctPortionId, 3)}-style${nfz(cardStyle.id(),2)}"

    data class PrecinctAndStyle(val precinctId: Int, val cardStyle: Set<Int>)

    data class PrecinctData(val pools: Map<String, OneAuditPoolFromCvrs>, val tabs: Map<Int, ContestTabulation>, val styles: Map<Set<Int>, CardStyle>,
                            val unpool: OneAuditPoolFromCvrs)

    override fun electionInfo() = ElectionInfo(
        "SF24-PrecinctOA", auditType, ncards(), contestsUA.size, cvrsContainUndervotes = true,
        mvrSource = mvrSource
    )

    override fun cardStyles() = null // cardStyles
    override fun cardPools() = if (auditType.isOA()) cardPoolBuilders else null
    override fun contestsUA() = contestsUA
    override fun cards() = createCards(auditType)
    override fun ncards() = ncards

    fun createCards(auditType: AuditType): CloseableIterator<CardWithBatchName> {
        val cvrExportIter = cvrExportCsvIterator(cvrExportCsv)
        val poolMap = cardPools()?.associateBy { it.name() } ?: emptyMap()
        val poolCounts = mutableMapOf<String, Int>() // to assign index within the poool
        val convertPoolIds = auditType.isOA()
        var countIndex = 0

        // TODO compare to CreateSfElection.createCards(); replacing CvrExportToCardAdapter and merge
        // converts CvrExport to CardWithBatchName, adds poolId, styleName, location
        // val cardIter = CvrExportToCardAdapter(cvrExportIter, cardPools(), auditType.isOA())

        val transformer = TransformingIterator<CvrExport, CardWithBatchName>(cvrExportIter) { cvrExport ->
            val pool = if (cvrExport.group != 1) null else {
                val cardStyle = cardStyleMap[cvrExport.votes.keys]!!
                val poolName = poolName(cvrExport.precinctPortionId, cardStyle)
                cardPoolMapByName[poolName]!!
            }
            val poolId = pool?.poolId

            val hasCvr = auditType.isClca() || (auditType.isOA() && poolId == null)
            val votes = if (hasCvr) cvrExport.votes else null  // removes votes for pooled data

            // Add location as poolName + index
            val location = if (!convertPoolIds || pool == null) null else {
                val poolCount =  poolCounts.getOrPut(pool.name()) { 0 }
                poolCounts[pool.name()] = poolCount + 1
                "${pool.name()} position${poolCount+1}"
            }

            CardWithBatchName(
                cvrExport.id,
                location,
                countIndex++,
                0,
                phantom = false,
                votes =  votes,
                poolId = poolId,
                styleName = pool?.name() ?: CardStyle.fromCvr,
            )
        }

        return transformer
    }

    // TODO add optional fuzz or some other error method?
    // the cvrExports are the private mvrs; must be in same order as createCards
    override fun createUnsortedMvrsExternal() = null
    override fun createUnsortedMvrsInternal(): List<CardWithBatchName> {
        val cvrExportIter = cvrExportCsvIterator(cvrExportCsv)
        val cardIter = CvrExportToCardAdapter(cvrExportIter, cardPools(), auditType.isOA())

        val unsortedMvrs = mutableListOf<CardWithBatchName>()
        cardIter.use { iter ->
            while( iter.hasNext()) { unsortedMvrs.add (iter.next()) }
        }
        return unsortedMvrs
    }
}