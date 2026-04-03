package org.cryptobiotic.rlauxe.sf

import com.github.michaelbull.result.Result
import com.github.michaelbull.result.unwrap
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.dominion.CvrExport
import org.cryptobiotic.rlauxe.oneaudit.OneAuditPoolFromCvrs
import org.cryptobiotic.rlauxe.audit.unpooled
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.dominion.CvrExportToCvrAdapter
import org.cryptobiotic.rlauxe.dominion.cvrExportCsvIterator
import org.cryptobiotic.rlauxe.audit.CardPool
import org.cryptobiotic.rlauxe.oneaudit.makeOneAuditContests
import org.cryptobiotic.rlauxe.irv.makeRaireOneAuditContest
import org.cryptobiotic.rlauxe.irv.makeRaireContest
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.util.ErrorMessages
import org.cryptobiotic.rlauxe.utils.countPhantoms
import org.cryptobiotic.rlauxe.utils.tabulateCardsAndCount
import kotlin.Boolean
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach

private val logger = KotlinLogging.logger("CreateSfElection")

// SanFrancisco 2024 General Election.
class CreateSfElection(
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
    // val phantomCount: Map<Int, Int>  // id -> nphantoms
    val contestsUA: List<ContestWithAssertions>
    val ncards: Int

    init {
        val (contestNcs, contestInfos) = makeContestInfos(
            castVoteRecordZip,
            contestManifestFilename,
            candidateManifestFile
        )
        val infos = contestInfos.associateBy { it.id }
        // println("contestNcs ${contestNcs}")

        // pass 1 through cvrs, make card pools, including unpooled
        val (allCardPools: Map<String, OneAuditPoolFromCvrs>, allCvrTabs: Map<Int, ContestTabulation>, ncards) = createCardPools(
            infos,
            cvrExportCsv,
            poolsHaveOneCardStyle,
        )

        cardPoolMapByName = allCardPools.filter { it.value.poolName != unpooled } // exclude the unpooled
        cardPoolBuilders = cardPoolMapByName.values.toList() // exclude the unpooled
        val unpooledPool = allCardPools[unpooled]!! // this does not have the diluted count

        // we need Nc to make the phantom cvrs in createCardManifest()
        // phantomCount = countPhantoms(allCvrTabs, contestNcs)

        // we need to know the diluted Nb before we can create the assertions: another pass through the cvrExports
        val cards = createCards(auditType)
        val auditableCardIter: CloseableIterator<AuditableCard> = MergeBatchesIntoCardManifestIterator(cards, cardPoolBuilders)

        val (manifestTabs, count) = tabulateCardsAndCount( auditableCardIter, infos)
        val contestNbs = manifestTabs.mapValues { it.value.ncardsTabulated }
        // println("contestNbs= ${contestNbs}")
        this.ncards = count

        // make contests based on cvr tabulations
        cardPools = cardPoolBuilders.map { it.toOneAuditPool() } // TODO not sure why need to convert
        contestsUA = if (auditType.isClca()) {
            makeClcaContestsSF(infos, allCvrTabs, contestNcs, contestNbs).sortedBy { it.id }
        } else if (auditType.isOA()) {
            makeOneAuditContestsSF(infos, allCvrTabs, contestNcs, contestNbs, unpooledPool, cardPools).sortedBy { it.id } // TODO
        } else {
            makePollingContestsSF(infos, allCvrTabs, contestNcs, contestNbs).sortedBy { it.id }
        }
    }

    fun createCardPools(
        contestInfos: Map<Int, ContestInfo>,
        cvrExportCsv: String,
        poolsHaveOneCardStyle: Boolean,
    ): Triple<Map<String, OneAuditPoolFromCvrs>, Map<Int, ContestTabulation>, Int> {

        val allCardPools: MutableMap<String, OneAuditPoolFromCvrs> = mutableMapOf()
        val allCvrTabs = mutableMapOf<Int, ContestTabulation>()
        var cardCount = 0
        cvrExportCsvIterator(cvrExportCsv).use { cvrIter ->
            while (cvrIter.hasNext()) {
                cardCount++
                val cvrExport: CvrExport = cvrIter.next()
                val pool = allCardPools.getOrPut(cvrExport.poolKey()) {
                    OneAuditPoolFromCvrs(cvrExport.poolKey(), allCardPools.size + 1, poolsHaveOneCardStyle, contestInfos)
                }
                pool.accumulateVotes(cvrExport.toCvr())

                cvrExport.votes.forEach { (id, cands) ->
                    val contestTab = allCvrTabs.getOrPut(id) { ContestTabulation(contestInfos[id]!!) }
                    contestTab.addVotes(cands, phantom=false)
                }
            }
        }

        //// because the unpooled is in a pool, the sum of pools equals all votes
        val sortedPools = allCardPools.toSortedMap()
        val poolTabs = mutableMapOf<Int, ContestTabulation>()
        sortedPools.forEach { (_, pool: OneAuditPoolFromCvrs) ->
            pool.addTo(poolTabs)
        }

        // check they are the same
        require(allCvrTabs.size == poolTabs.size)
        allCvrTabs.forEach { (id, cvrTab) ->
            val poolTab = poolTabs[id]
            require(poolTab != null)
            require(poolTab == cvrTab)
        }

        /* check against staxContests TODO put in seperate check / vertify
        val contestManifest = readContestManifestFromZip(castVoteRecordZip, contestManifestFilename)
        println("IRV contests = ${contestManifest.irvContests}")

        val staxContests = StaxReader().read("src/test/data/SF2024/summary.xml")
        println("staxContests")
        poolTabs.toSortedMap().forEach { (id, contestTab) ->
            val contestName = contestManifest.contests[id]!!.Description
            val staxContest: StaxReader.StaxContest = staxContests.find { it.id == contestName }!!
            if (staxContest.ncards() != contestTab.ncards) {
                logger.warn { "staxContest $contestName ($id) has ncards = ${staxContest.ncards()} not equal to cvr summary = ${contestTab.ncards} " }
                // assertEquals(staxContest.blanks(), contest.blanks)
            }
            println("  $contestName ($id) has stax ncards = ${staxContest.ncards()}, cvr ncards = ${contestTab.ncards}")
        } */

        return Triple(allCardPools, allCvrTabs, cardCount)
    }

    override fun electionInfo() = ElectionInfo(
        "SF24$auditType", auditType, ncards(), contestsUA.size, cvrsContainUndervotes = true,
        mvrSource = mvrSource
    )

    override fun batches() = null // TODO !cvrsHaveUndervotes need batches
    override fun cardPools() = if (auditType.isOA()) cardPoolBuilders else null
    override fun contestsUA() = contestsUA
    override fun cards() = createCards(auditType)
    override fun ncards() = ncards

    // same cvrs for CLCA and OneAudit
    fun createCards(auditType: AuditType): CloseableIterator<CardWithBatchName> {
        val cvrExportIter = cvrExportCsvIterator(cvrExportCsv)
        val cvrIter = CvrExportToCvrAdapter(cvrExportIter, cardPoolBuilders.associate { it.name() to it.id() })

        return CvrsToCardsWithBatchNameIterator(
                auditType,
                cvrIter,
                null, // there are no phantoms
                // OA: use batch.possibleContests() to dilute the margin; CLCA: dont use batch.possibleContests() if clcaHasUndervotes
                if (auditType.isClca()) null else cardPoolBuilders
        )
    }

    // TODO add optional fuzz or some other error method
    // convert the cvrExports to the private mvrs; must be in same order as createCards
    override fun createUnsortedMvrsExternal() = null
    override fun createUnsortedMvrsInternal(): List<Cvr> {
        val cvrExportIter = cvrExportCsvIterator(cvrExportCsv)
        val cvrIter = CvrExportToCvrAdapter(cvrExportIter, cardPoolBuilders.associate { it.name() to it.id() })

        val unsortedMvrs = mutableListOf<Cvr>()
        cvrIter.use { iter ->
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

fun createSfElection(
    auditdir: String,
    castVoteRecordZip: String,
    contestManifestFilename: String,
    candidateManifestFile: String,
    cvrExportCsv: String,
    creation: AuditCreationConfig,
    round: AuditRoundConfig,
    mvrSource: MvrSource = MvrSource.testPrivateMvrs,
 ): Result<AuditRoundIF, ErrorMessages> {
    val stopwatch = Stopwatch()

    val election = CreateSfElection(
        castVoteRecordZip,
        contestManifestFilename,
        candidateManifestFile,
        cvrExportCsv,
        auditType = creation.auditType,
        poolsHaveOneCardStyle=false,
        mvrSource = mvrSource
    )
    createElectionRecord(election, auditDir = auditdir)
    logger.info{"createSfElection took $stopwatch"}
    stopwatch.start()

    val config = Config(election.electionInfo(), creation, round)
    createAuditRecord(config, election, auditDir = auditdir)

    val result = startFirstRound(auditdir)
    if (result.isErr) logger.error{ result.toString() }
    logger.info{"startFirstSfRound took $stopwatch"}

    return result
}