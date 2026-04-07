package org.cryptobiotic.rlauxe.sf

import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.dominion.cvrExportCsvFile
import kotlin.test.Test

import com.github.michaelbull.result.Result
import com.github.michaelbull.result.unwrap
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.dominion.CvrExport
import org.cryptobiotic.rlauxe.oneaudit.OneAuditPoolFromCvrs
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.dominion.cvrExportCsvIterator
import org.cryptobiotic.rlauxe.audit.CardPool
import org.cryptobiotic.rlauxe.dominion.CvrExportToCardAdapter
import org.cryptobiotic.rlauxe.oneaudit.makeOneAuditContests
import org.cryptobiotic.rlauxe.irv.makeRaireOneAuditContest
import org.cryptobiotic.rlauxe.irv.makeRaireContest
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.util.ErrorMessages
import org.cryptobiotic.rlauxe.util.TransformingIterator
import org.cryptobiotic.rlauxe.utils.tabulateCardsAndCount
import kotlin.Boolean
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach
import kotlin.collections.toList

class MakeElectionPrecinctOA {
    val sfDir = "$testdataDir/cases/sf2024"
    val castVoteRecordZip = "$sfDir/CVR_Export_20241202143051.zip"
    val cvrExportCsv = "$sfDir/$cvrExportCsvFile"
    val auditdir = "$testdataDir/cases/sf2024/oap/audit"
    val contestManifestFilename = "ContestManifest.json"
    val candidateManifestFile = "CandidateManifest.json"

    val creation = AuditCreationConfig(AuditType.ONEAUDIT, riskLimit=.05,)
    val round = AuditRoundConfig(
        SimulationControl(nsimTrials = 2),
        ContestSampleControl(minRecountMargin = .005, minMargin=0.0, contestSampleCutoff = 2500, auditSampleCutoff = 5000),
        ClcaConfig(fuzzMvrs=.001), null)

    val mvrSource: MvrSource = MvrSource.testPrivateMvrs

    @Test
    fun makeElectionPrecinctOA() {

        val election = CreateSfprecinctOA(
            castVoteRecordZip,
            contestManifestFilename,
            candidateManifestFile,
            cvrExportCsv,
            auditType = creation.auditType,
            poolsHaveOneCardStyle=false,
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
class CreateSfprecinctOA(
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
    val cardStyleMap: Map<Set<Int>, CardStyleIF>
    val cardStyles: List<CardStyleIF>
    val contestsUA: List<ContestWithAssertions>
    val ncards: Int

    val ballotTypesContestManifest: BallotTypesContestManifest =
        readBallotTypeContestManifestUnwrapped("src/test/data/SF2024/manifests/BallotTypeContestManifest.json")!!

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
        cardStyles = styles.values.toList()

        // the full and complete CarswiTHStyleNAme
        val cards = createCards(auditType)
        // merged with the cardStyles
        val auditableCardIter: CloseableIterator<AuditableCard> = MergeBatchesIntoCardManifestIterator(cards, cardStyles)

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

        // val cardStylesMap = mutableMapOf<Int, CardStyleIF>() // contests.hashCode -> contests
        val cardStyles = mutableMapOf<Set<Int>, CardStyleIF>() // contests.hashCode -> contests
        val precinctPools: MutableMap<String, OneAuditPoolFromCvrs> = mutableMapOf()
        val unpool = OneAuditPoolFromCvrs("unpool", 0, true, contestInfos)

        val allCvrTabs = mutableMapOf<Int, ContestTabulation>()
        var cardCount = 0
        cvrExportCsvIterator(cvrExportCsv).use { cvrIter ->
            while (cvrIter.hasNext()) {
                cardCount++
                val cvrExport: CvrExport = cvrIter.next()
                if (cvrExport.group == 1) { // only want the precinct voting
                    cardStyles.getOrPut(cvrExport.votes.keys) { CardStyle.from(cardStyles.size + 1, cvrExport.votes.keys) }

                    val precinctName = "precinct${cvrExport.precinctPortionId}"
                    val pool = precinctPools.getOrPut(precinctName) {
                        OneAuditPoolFromCvrs(precinctName, precinctPools.size + 1, poolsHaveOneCardStyle, contestInfos)
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

    data class PrecinctData(val pools: Map<String, OneAuditPoolFromCvrs>, val tabs: Map<Int, ContestTabulation>, val styles: Map<Set<Int>, CardStyleIF>,
                            val unpool: OneAuditPoolFromCvrs)

    override fun electionInfo() = ElectionInfo(
        "SF24-PrecinctOA", auditType, ncards(), contestsUA.size, cvrsContainUndervotes = true,
        mvrSource = mvrSource
    )

    override fun cardStyles() = cardStyles // TODO !cvrsHaveUndervotes need batches
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
            val precinctName = "precinct${cvrExport.precinctPortionId}"
            // val poolName = cvrExport.poolKey()  // TODO HEY poolKey()
            val pool = if (cvrExport.group != 1) null else poolMap[ precinctName ]
            val poolId = pool?.poolId

            val style = if (cvrExport.group != 1) null else cardStyleMap[cvrExport.votes.keys]!!

            val hasCvr = auditType.isClca() || (auditType.isOA() && poolId == null)
            val votes = if (hasCvr) cvrExport.votes else null  // removes votes for pooled data

            // Add location as poolName + index
            val location = if (!convertPoolIds || pool == null) null else {
                val poolCount =  poolCounts.getOrPut(precinctName) { 0 }
                poolCounts[precinctName] = poolCount + 1
                "pool ${pool.name()} position${poolCount+1}"
            }

            CardWithBatchName(
                cvrExport.id,
                location,
                countIndex++,
                0,
                phantom = false,
                votes =  votes,
                poolId = poolId,
                styleName = style?.name() ?: CardStyle.fromCvr, // style different than pool !
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

fun makeClcaContestsSF(infos: Map<Int, ContestInfo>, allCvrTabs: Map<Int, ContestTabulation>, contestNcs : Map<Int, Int>, contestNbs: Map<Int, Int>): List<ContestWithAssertions> {
    val contestsUAs = mutableListOf<ContestWithAssertions>()
    allCvrTabs.map { (contestId, cvrTab)  ->
        val useNc = contestNcs[contestId] ?: cvrTab.ncardsTabulated
        val info = infos[contestId]!!

        val contestUA: ContestWithAssertions = if (!cvrTab.isIrv) {
            val contest = Contest(info, cvrTab.votes, useNc, cvrTab.ncardsTabulated)
            ContestWithAssertions(contest, NpopIn=contestNbs[contestId]).addStandardAssertions()
        } else {
            makeRaireContest(info, cvrTab, useNc, Nbin=contestNbs[contestId]!!) // HERE
        }
        contestUA.contest.info().metadata["PoolPct"] = 0
        contestsUAs.add(contestUA)
    }
    return contestsUAs
}

fun makeOneAuditContestsSF(infos: Map<Int, ContestInfo>, allCvrTabs: Map<Int, ContestTabulation>, contestNcs : Map<Int, Int>, contestNbs: Map<Int, Int>,
                           unpooledPool: OneAuditPoolFromCvrs, oneAuditPools: List<CardPool>): List<ContestWithAssertions> {
    val contestsUAs = mutableListOf<ContestWithAssertions>()

    // make non IRV contests
    val regularContests = makeRegularContests(infos, allCvrTabs, unpooledPool, contestNcs)
    val regularOAcontests = makeOneAuditContests(regularContests, contestNbs, oneAuditPools).sortedBy { it.id }
    contestsUAs.addAll(regularOAcontests)

    // now make the IRV contests
    allCvrTabs.filter{ it.value.isIrv }. map { (contestId, cvrTab)  ->
        val info = infos[contestId]!!
        val useNc = contestNcs[contestId] ?: cvrTab.ncardsTabulated
        val contestUA = makeRaireOneAuditContest(info, cvrTab, useNc, Nbin=contestNbs[contestId]!!, oneAuditPools)

        val unpooledTab = unpooledPool.contestTabs[contestId]!!
        val unpooledPct = 100.0 * unpooledTab.ncardsTabulated / cvrTab.ncardsTabulated   // denominator is cards that have the contest, not Npop
        val poolPct = (100 - unpooledPct).toInt()
        contestUA.contest.info().metadata["PoolPct"] = poolPct
        contestsUAs.add(contestUA)
    }
    return contestsUAs
}

fun makePollingContestsSF(infos: Map<Int, ContestInfo>, allCvrTabs: Map<Int, ContestTabulation>, contestNcs: Map<Int, Int>, contestNbs: Map<Int, Int>): List<ContestWithAssertions> {
    val contestsUAs = mutableListOf<ContestWithAssertions>()
    allCvrTabs.map { (contestId, contestSumTab)  ->
        val useNc = contestNcs[contestId] ?: contestSumTab.ncardsTabulated
        if (useNc > 0) {
            if (!contestSumTab.isIrv) { // cant do IRV
                val info = infos[contestId]!!
                val contest = Contest(info, contestSumTab.votes, useNc, contestSumTab.ncardsTabulated)
                val contestUA = ContestWithAssertions(contest, isClca = false, NpopIn=contestNbs[contestId]).addStandardAssertions()
                contestUA.contest.info().metadata["PoolPct"] = 0
                contestsUAs.add(contestUA)
            }
        }
    }
    return contestsUAs
}

fun makeRegularContests(infos: Map<Int, ContestInfo>, allCvrTabs: Map<Int, ContestTabulation>, unpooledPool: OneAuditPoolFromCvrs, contestNcs: Map<Int, Int>): List<Contest> {
    val contests = mutableListOf<Contest>()
    allCvrTabs.map { (contestId, contestSumTab)  ->
        val info = infos[contestId]!!
        val useNc = contestNcs[info.id] ?: contestSumTab.ncardsTabulated
        if (useNc > 0 && !info.isIrv) {
            val contest = Contest(info, contestSumTab.votes, useNc, contestSumTab.ncardsTabulated)
            val unpooledTab = unpooledPool.contestTabs[info.id]!!
            val unpooledPct = 100.0 * unpooledTab.ncardsTabulated / contestSumTab.ncardsTabulated
            val poolPct = (100 - unpooledPct).toInt()
            contest.info().metadata["PoolPct"] = poolPct
            contests.add(contest)
        }
    }
    return contests
}

fun makeContestInfos(
    castVoteRecordZip: String,
    contestManifestFilename: String,
    candidateManifestFile: String,
    show: Boolean = false
): Pair<Map<Int, Int>, List<ContestInfo>> {

    val contestManifest = readContestManifestFromZip(castVoteRecordZip, contestManifestFilename)
    if (show) println("contestManifest = $contestManifest")

    val resultCandidateM: Result<CandidateManifestJson, ErrorMessages> = readCandidateManifestJsonFromZip(castVoteRecordZip, candidateManifestFile)
    val candidateManifest = if (resultCandidateM .isOk) resultCandidateM.unwrap()
    else throw RuntimeException("Cannot read CandidateManifestJson from ${candidateManifestFile} err = $resultCandidateM")

    val contestInfos = makeContestInfos(contestManifest, candidateManifest).sortedBy { it.id }
    if (show) contestInfos.forEach { println("   ${it} nwinners = ${it.nwinners} choiceFunction = ${it.choiceFunction}") }

    // The contest Ncs come from the contestManifest
    val contestNcs: Map<Int, Int> = makeContestNcs(contestManifest, contestInfos) // contestId -> Nc
    return Pair(contestNcs, contestInfos)
}

// TODO eliminate write-ins
fun makeContestInfos(contestManifest: ContestManifest, candidateManifest: CandidateManifestJson): List<ContestInfo> {
    return contestManifest.contests.values.map { contestM: ContestMJson ->
        val candidateNames =
            candidateManifest.List.filter { it.ContestId == contestM.Id }.associate { candidateM: CandidateM ->
                Pair(candidateM.Description, candidateM.Id)
            }
        val isIrv = (contestM.NumOfRanks > 0)
        ContestInfo(
            contestM.Description,
            contestM.Id,
            candidateNames,
            if (!isIrv) SocialChoiceFunction.PLURALITY else SocialChoiceFunction.IRV,
            if (!isIrv) contestM.VoteFor else 1,
            if (!isIrv) contestM.VoteFor else candidateNames.size,
        )
    }
}

fun makeContestNcs(contestManifest: ContestManifest, contestInfos: List<ContestInfo>): Map<Int, Int> { // contestId -> Nc
    val staxContests: List<StaxReader.StaxContest> = StaxReader().read("src/test/data/SF2024/summary.xml") // sketchy
    val contestNcs= mutableMapOf<Int, Int>()
    contestInfos.forEach { info ->
        val contestM = contestManifest.contests.values.find { it.Description == info.name }
        if (contestM != null) {
            val staxContest = staxContests.find { it.id == info.name }
            if (staxContest != null) contestNcs[info.id] = staxContest.ncards()!!
            else println("*** cant find contest '${info.name}' in summary.xml")

        } else println("*** cant find contest '${info.name}' in ContestManifest")
    }
    return contestNcs
}

/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////





