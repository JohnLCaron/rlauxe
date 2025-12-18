package org.cryptobiotic.rlauxe.sf

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.unwrap
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.dominion.CvrExport
import org.cryptobiotic.rlauxe.oneaudit.CardPoolFromCvrs
import org.cryptobiotic.rlauxe.oneaudit.CardPoolIF
import org.cryptobiotic.rlauxe.oneaudit.unpooled
import org.cryptobiotic.rlauxe.raire.makeRaireContestUA
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.audit.makePhantomCvrs
import org.cryptobiotic.rlauxe.dominion.CvrExportToCvrAdapter
import org.cryptobiotic.rlauxe.dominion.cvrExportCsvIterator
import org.cryptobiotic.rlauxe.oneaudit.CvrsWithStylesToCardManifest
import org.cryptobiotic.rlauxe.oneaudit.makeOneAuditContests
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.util.ErrorMessages
import org.cryptobiotic.rlauxe.util.tabulateAuditableCards
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
    val hasStyle:Boolean,
): CreateElectionIF {
    val cardPoolMapByName: Map<String, CardPoolIF>
    val cardPools: List<CardPoolIF>
    val phantomCount: Map<Int, Int>
    val contestsUA: List<ContestUnderAudit>
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
        val (allCardPools: Map<String, CardPoolFromCvrs>, allCvrTabs: Map<Int, ContestTabulation>, ncards) = createCardPools(
            infos,
            castVoteRecordZip,
            contestManifestFilename,
            cvrExportCsv,
        )

        cardPoolMapByName = allCardPools.filter { it.value.poolName != unpooled } // exclude the unpooled
        cardPools = cardPoolMapByName.values.toList() // exclude the unpooled
        val unpooledPool = allCardPools[unpooled]!! // this does not have the diluted count
        this.cardCount = ncards

        // we need Nc to make the phantom cvrs in createCardManifest()
        phantomCount = countPhantoms(allCvrTabs, contestNcs)

        // we need to know the diluted Nb before we can create the UAs: another pass through the cvrExports
        val manifestTabs = tabulateAuditableCards(createCardManifest(config.isOA, phantomCount), infos)
        val contestNbs = manifestTabs.mapValues { it.value.ncards }
        println("contestNbs= ${contestNbs}")

        // make contests based on cvr tabulations
        contestsUA = if (config.isClca) makeClcaContests(allCvrTabs, contestNcs, contestNbs).sortedBy { it.id }
            else if (config.isOA) {
                val contests = makeContests(allCvrTabs, unpooledPool, contestNcs) // TODO leave out IRV
                makeOneAuditContests(contests, contestNbs, cardPools).sortedBy { it.id }
            }
            else makePollingContests(allCvrTabs, contestNcs, contestNbs).sortedBy { it.id }
    }

    fun createCardPools(
        // auditDir: String,
        contestInfos: Map<Int, ContestInfo>,
        castVoteRecordZip: String,
        contestManifestFilename: String,
        cvrExportCsv: String,
    ): Triple<Map<String, CardPoolFromCvrs>, Map<Int, ContestTabulation>, Int> {

        val allCardPools: MutableMap<String, CardPoolFromCvrs> = mutableMapOf()
        val allCvrTabs = mutableMapOf<Int, ContestTabulation>()
        var cardCount = 0
        cvrExportCsvIterator(cvrExportCsv).use { cvrIter ->
            while (cvrIter.hasNext()) {
                cardCount++
                val cvrExport: CvrExport = cvrIter.next()
                val wtf =  cvrExport.poolKey()
                val pool = allCardPools.getOrPut(cvrExport.poolKey()) {
                    CardPoolFromCvrs(cvrExport.poolKey(), allCardPools.size + 1, contestInfos)
                }
                pool.accumulateVotes(cvrExport.toCvr())

                cvrExport.votes.forEach { (id, cands) ->
                    val contestTab = allCvrTabs.getOrPut(id) { ContestTabulation(contestInfos[id]!!) }
                    contestTab.addVotes(cands, phantom=false)
                }
            }
        }

        //// because the unpooled is in a pool, the sum of pools are all the votes
        val sortedPools = allCardPools.toSortedMap()
        val poolTabs = mutableMapOf<Int, ContestTabulation>()
        sortedPools.forEach { (_, pool: CardPoolFromCvrs) ->
            pool.addTo(poolTabs)
        }

        // check they are the same
        require(allCvrTabs.size == poolTabs.size)
        allCvrTabs.forEach { (id, cvrTab) ->
            val poolTab = poolTabs[id]
            require(poolTab != null)
            require(poolTab == cvrTab)
        }

        // check against staxContests
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
        }

        return Triple(allCardPools, allCvrTabs, cardCount)
    }

    fun countPhantoms(contestTabSums: Map<Int, ContestTabulation>, contestNcs: Map<Int, Int>): Map<Int, Int> {
        val result = mutableMapOf<Int, Int>()
        contestTabSums.forEach { (_, contestSumTab) ->
            val info = contestSumTab.info
            val useNc = contestNcs[info.id] ?: contestSumTab.ncards
            val Ncast = contestSumTab.ncards
            result[info.id] = useNc - Ncast
        }
        return result
    }

    override fun cardPools() = if (config.isOA) cardPools else null
    override fun contestsUA() = contestsUA
    override fun cardManifest() = createCardManifest(config.isOA, phantomCount)

    fun createCardManifest(isOA: Boolean, phantomCount: Map<Int,Int>): CloseableIterator<AuditableCard> {
        val cvrExportIter = cvrExportCsvIterator(cvrExportCsv)
        val cvrIter = CvrExportToCvrAdapter(cvrExportIter, cardPools.associate { it.name() to it.poolId() })

        return CvrsWithStylesToCardManifest(
            config.auditType, hasStyle,
            cvrIter,
            makePhantomCvrs(phantomCount),
            if (isOA) cardPools else null,
        )
    }
}

fun makeClcaContests(allCvrTabs: Map<Int, ContestTabulation>, contestNcs : Map<Int, Int>, contestNbs: Map<Int, Int>): List<ContestUnderAudit> {
    val contestsUAs = mutableListOf<ContestUnderAudit>()
    allCvrTabs.map { (contestId, cvrTab)  ->
        val info = cvrTab.info
        val useNc = contestNcs[info.id] ?: cvrTab.ncards

        val contestUA: ContestUnderAudit = if (!cvrTab.isIrv) {
            val contest = Contest(info, cvrTab.votes, useNc, cvrTab.ncards)
            ContestUnderAudit(contest, NpopIn=contestNbs[info.id]).addStandardAssertions()
        } else {
            makeRaireContestUA(info, cvrTab, useNc, Nbin=contestNbs[info.id])
        }
        contestUA.contest.info().metadata["PoolPct"] = 0
        contestsUAs.add(contestUA)
    }
    return contestsUAs
}

fun makeContests(allCvrTabs: Map<Int, ContestTabulation>, unpooledPool: CardPoolFromCvrs, contestNcs: Map<Int, Int>): List<Contest> {
    val contests = mutableListOf<Contest>()
    allCvrTabs.map { (contestId, contestSumTab)  ->
        val info = contestSumTab.info
        val useNc = contestNcs[info.id] ?: contestSumTab.ncards
        if (useNc > 0 && !info.isIrv) {
            val unpooledTab = unpooledPool.contestTabs[info.id]!!
            val contest = Contest(contestSumTab.info, contestSumTab.votes, useNc, contestSumTab.ncards)
            val unpooledPct = 100.0 * unpooledTab.ncards / contestSumTab.ncards
            val poolPct = (100 - unpooledPct).toInt()
            contest.info().metadata["PoolPct"] = poolPct
            contests.add(contest)
        }
    }
    return contests
}

fun makePollingContests(allCvrTabs: Map<Int, ContestTabulation>, contestNcs: Map<Int, Int>, contestNbs: Map<Int, Int>): List<ContestUnderAudit> {
    val contestsUAs = mutableListOf<ContestUnderAudit>()
    allCvrTabs.map { (contestId, contestSumTab)  ->
        val useNc = contestNcs[contestId] ?: contestSumTab.ncards
        if (useNc > 0) {
            if (!contestSumTab.isIrv) { // cant do IRV
                val contest = Contest(contestSumTab.info, contestSumTab.votes, useNc, contestSumTab.ncards)
                val contestUA = ContestUnderAudit(contest, isClca = false, NpopIn=contestNbs[contestId]).addStandardAssertions()
                contestUA.contest.info().metadata["PoolPct"] = 0
                contestsUAs.add(contestUA)
            }
        }
    }
    return contestsUAs
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
    hasStyle: Boolean,
    auditType : AuditType,
) {
    val stopwatch = Stopwatch()
    val config = when {
        (auditConfigIn != null) -> auditConfigIn

        (auditType ==  AuditType.CLCA) -> AuditConfig(AuditType.CLCA, hasStyle = hasStyle, contestSampleCutoff = 20000, riskLimit = .05, nsimEst=10)

        (auditType ==  AuditType.ONEAUDIT) -> AuditConfig(
            AuditType.ONEAUDIT, hasStyle = hasStyle, riskLimit = .05, contestSampleCutoff = 20000, nsimEst = 1,
            oaConfig = OneAuditConfig(OneAuditStrategyType.optimalComparison, useFirst = true)
        )

        else -> AuditConfig(AuditType.POLLING, hasStyle = hasStyle, riskLimit = .05, contestSampleCutoff = 10000, nsimEst = 100)
    }

    val election = CreateSfElection(
        castVoteRecordZip,
        contestManifestFilename,
        candidateManifestFile,
        cvrExportCsv,
        config = config,
        hasStyle = hasStyle,
    )

    CreateAudit("sf2024", config, election, auditDir = "$topdir/audit", )
    println("createSfElection took $stopwatch")
}