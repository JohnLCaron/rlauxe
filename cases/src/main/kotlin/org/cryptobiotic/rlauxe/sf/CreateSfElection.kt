package org.cryptobiotic.rlauxe.sf

import com.github.michaelbull.result.Ok
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
import org.cryptobiotic.rlauxe.oneaudit.unpooled
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.makePhantomCvrs
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.dominion.CvrExportToCvrAdapter
import org.cryptobiotic.rlauxe.dominion.cvrExportCsvIterator
import org.cryptobiotic.rlauxe.oneaudit.makeOneAuditContests
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.csv.readCardPoolCsvFile
import org.cryptobiotic.rlauxe.persist.csv.writeCardPoolCsvFile
import org.cryptobiotic.rlauxe.raire.makeRaireOneAuditContest
import org.cryptobiotic.rlauxe.raire.makeRaireContest
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.util.ErrorMessages
import org.cryptobiotic.rlauxe.util.tabulateAuditableCards
import org.cryptobiotic.rlauxe.workflow.PersistedWorkflowMode
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
        val config: AuditConfig,
        poolsHaveOneCardStyle: Boolean,
    ): CreateElectionIF {
    val cardPoolMapByName: Map<String, OneAuditPoolFromCvrs>
    val cardPools: List<OneAuditPoolFromCvrs>
    val phantomCount: Map<Int, Int>  // id -> nphantoms
    val contestsUA: List<ContestWithAssertions>
    val cardCount: Int

    init {
        val (contestNcs, contestInfos) = makeContestInfos(
            castVoteRecordZip,
            contestManifestFilename,
            candidateManifestFile
        )
        val infos = contestInfos.associateBy { it.id }
        println("contestNcs ${contestNcs}")

        // pass 1 through cvrs, make card pools, including unpooled
        val (allCardPools: Map<String, OneAuditPoolFromCvrs>, allCvrTabs: Map<Int, ContestTabulation>, ncards) = createCardPools(
            infos,
            castVoteRecordZip,
            contestManifestFilename,
            cvrExportCsv,
            poolsHaveOneCardStyle,
        )

        cardPoolMapByName = allCardPools.filter { it.value.poolName != unpooled } // exclude the unpooled
        cardPools = cardPoolMapByName.values.toList() // exclude the unpooled
        val unpooledPool = allCardPools[unpooled]!! // this does not have the diluted count
        this.cardCount = ncards

        // we need Nc to make the phantom cvrs in createCardManifest()
        phantomCount = countPhantoms(allCvrTabs, contestNcs)

        // we need to know the diluted Nb before we can create the assertions: another pass through the cvrExports
        val manifestTabs = tabulateAuditableCards( createCardManifest(config.auditType), infos)
        val contestNbs = manifestTabs.mapValues { it.value.ncards }
        println("contestNbs= ${contestNbs}")

        // make contests based on cvr tabulations
        contestsUA = if (config.isClca) {
            makeClcaContestsSF(infos, allCvrTabs, contestNcs, contestNbs).sortedBy { it.id }
        } else if (config.isOA) {
            makeOneAuditContestsSF(infos, allCvrTabs, contestNcs, contestNbs, unpooledPool, cardPools).sortedBy { it.id }
        } else {
            makePollingContestsSF(infos, allCvrTabs, contestNcs, contestNbs).sortedBy { it.id }
        }

        // debug
        val csvFile = "/home/stormy/rla/tests/scratch/cardPoolCsvYes.csv"
        writeCardPoolCsvFile(cardPools, csvFile)

        val roundtrips = readCardPoolCsvFile(csvFile,  infos).associateBy { it.poolId }
        cardPools.forEach {
            val roundtrip = roundtrips[it.poolId]
            if (roundtrip != it) {
                println("failed $it")
            }
        }
    }

    fun createCardPools(
        contestInfos: Map<Int, ContestInfo>,
        castVoteRecordZip: String,
        contestManifestFilename: String,
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

    fun countPhantoms(contestTabSums: Map<Int, ContestTabulation>, contestNcs: Map<Int, Int>): Map<Int, Int> {
        val result = mutableMapOf<Int, Int>()
        contestTabSums.forEach { (_, contestSumTab) ->
            val useNc = contestNcs[contestSumTab.contestId] ?: contestSumTab.ncards
            val Ncast = contestSumTab.ncards
            result[contestSumTab.contestId] = useNc - Ncast
        }
        return result
    }

    override fun populations() = if (config.isClca) emptyList() else cardPools
    override fun contestsUA() = contestsUA
    override fun cardManifest() = createCardManifest(config.auditType)

    // these are the same cvrs for CLCA and OneAudit
    fun createCardManifest(auditType: AuditType): CloseableIterator<AuditableCard> {
        val cvrExportIter = cvrExportCsvIterator(cvrExportCsv)
        val cvrIter = CvrExportToCvrAdapter(cvrExportIter, cardPools.associate { it.name() to it.id() })

        return if (auditType == AuditType.ONEAUDIT) CvrsWithPopulationsToCardManifest(
            auditType,
            cvrIter,
            makePhantomCvrs(phantomCount),
            cardPools)
        else
            CvrsWithPopulationsToCardManifest(
                auditType,
                cvrIter,
                makePhantomCvrs(phantomCount),
                null)
    }

    fun createUnsortedMvrs(): List<Cvr> {
        val cvrExportIter = cvrExportCsvIterator(cvrExportCsv)
        val cvrIter = CvrExportToCvrAdapter(cvrExportIter, cardPools.associate { it.name() to it.id() })

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
        val useNc = contestNcs[contestId] ?: cvrTab.ncards
        val info = infos[contestId]!!

        val contestUA: ContestWithAssertions = if (!cvrTab.isIrv) {
            val contest = Contest(info, cvrTab.votes, useNc, cvrTab.ncards)
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
                           unpooledPool: OneAuditPoolFromCvrs, oneAuditPools: List<OneAuditPoolFromCvrs>): List<ContestWithAssertions> {
    val contestsUAs = mutableListOf<ContestWithAssertions>()

    // make non IRV contests
    val regularContests = makeRegularContests(infos, allCvrTabs, unpooledPool, contestNcs)
    val regularOAcontests = makeOneAuditContests(regularContests, contestNbs, oneAuditPools).sortedBy { it.id }
    contestsUAs.addAll(regularOAcontests)

    // now make the IRV contests
    allCvrTabs.filter{ it.value.isIrv }. map { (contestId, cvrTab)  ->
        val info = infos[contestId]!!
        val useNc = contestNcs[contestId] ?: cvrTab.ncards
        val contestUA = makeRaireOneAuditContest(info, cvrTab, useNc, Nbin=contestNbs[contestId]!!, oneAuditPools)

        val unpooledTab = unpooledPool.contestTabs[contestId]!!
        val unpooledPct = 100.0 * unpooledTab.ncards / cvrTab.ncards   // denominator is cards that have the contest, not Npop
        val poolPct = (100 - unpooledPct).toInt()
        contestUA.contest.info().metadata["PoolPct"] = poolPct
        contestsUAs.add(contestUA)
    }
    return contestsUAs
}

fun makePollingContestsSF(infos: Map<Int, ContestInfo>, allCvrTabs: Map<Int, ContestTabulation>, contestNcs: Map<Int, Int>, contestNbs: Map<Int, Int>): List<ContestWithAssertions> {
    val contestsUAs = mutableListOf<ContestWithAssertions>()
    allCvrTabs.map { (contestId, contestSumTab)  ->
        val useNc = contestNcs[contestId] ?: contestSumTab.ncards
        if (useNc > 0) {
            if (!contestSumTab.isIrv) { // cant do IRV
                val info = infos[contestId]!!
                val contest = Contest(info, contestSumTab.votes, useNc, contestSumTab.ncards)
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
        val useNc = contestNcs[info.id] ?: contestSumTab.ncards
        if (useNc > 0 && !info.isIrv) {
            val contest = Contest(info, contestSumTab.votes, useNc, contestSumTab.ncards)
            val unpooledTab = unpooledPool.contestTabs[info.id]!!
            val unpooledPct = 100.0 * unpooledTab.ncards / contestSumTab.ncards
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
    val candidateManifest = if (resultCandidateM is Ok) resultCandidateM.unwrap()
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
    topdir: String,
    castVoteRecordZip: String,
    contestManifestFilename: String,
    candidateManifestFile: String,
    cvrExportCsv: String,
    auditConfigIn: AuditConfig? = null,
    poolsHaveOneCardStyle: Boolean,
    auditType : AuditType,
) {
    val stopwatch = Stopwatch()
    val config = when {
        (auditConfigIn != null) -> auditConfigIn

        (auditType ==  AuditType.CLCA) -> AuditConfig(AuditType.CLCA, riskLimit = .05, nsimEst=20)

        (auditType ==  AuditType.ONEAUDIT) -> AuditConfig(
            AuditType.ONEAUDIT, riskLimit = .05, nsimEst = 20,
            persistedWorkflowMode = PersistedWorkflowMode.testPrivateMvrs,  // write mvrs to private
            oaConfig = OneAuditConfig(OneAuditStrategyType.generalAdaptive, useFirst = false)
        )

        else -> AuditConfig(AuditType.POLLING, riskLimit = .05, contestSampleCutoff = 10000, nsimEst = 20)
    }

    val election = CreateSfElection(
        castVoteRecordZip,
        contestManifestFilename,
        candidateManifestFile,
        cvrExportCsv,
        config = config,
        poolsHaveOneCardStyle=poolsHaveOneCardStyle,
    )
    CreateAudit("sf2024", config, election, auditDir = "$topdir/audit", )

    // convert the cvrExports to the private mvrs
    val unsortedMvrs = election.createUnsortedMvrs()
    writeUnsortedPrivateMvrs(Publisher("$topdir/audit"), unsortedMvrs, seed = config.seed)

    println("createSfElection took $stopwatch")

}