package org.cryptobiotic.rlauxe.sf

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.unwrap
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.ContestIF
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.CvrExport
import org.cryptobiotic.rlauxe.oneaudit.CardPoolFromCvrs
import org.cryptobiotic.rlauxe.oneaudit.CardPoolIF
import org.cryptobiotic.rlauxe.oneaudit.unpooled
import org.cryptobiotic.rlauxe.persist.csv.*
import org.cryptobiotic.rlauxe.raire.makeRaireContestUA
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.estimate.makePhantomCards
import org.cryptobiotic.rlauxe.estimate.makePhantomCvrs
import org.cryptobiotic.rlauxe.util.CloseableIterable
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.Closer
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.util.CvrToAuditableCardClca
import org.cryptobiotic.rlauxe.util.ErrorMessages
import org.cryptobiotic.rlauxe.util.tabulateAuditableCards
import kotlin.Boolean
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach
import kotlin.collections.plus
import kotlin.sequences.plus

private val logger = KotlinLogging.logger("createSfElectionFromCsvExportOA")

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
    val contests: List<ContestIF>
    val contestsUA: List<ContestUnderAudit>
    val cardCount: Int
    val manifest: CardLocationManifest

    init {
        val (contestNcs, contestInfos) = makeContestInfos(
            castVoteRecordZip,
            contestManifestFilename,
            candidateManifestFile
        )
        val infos = contestInfos.associateBy { it.id }
        println("contestNcs ${contestNcs}")

        // pass 1 through cvrs, make card pools, including unpooled
        val (allCardPools: Map<String, CardPoolFromCvrs>, cvrTabs: Map<Int, ContestTabulation>, ncards) = createCardPools(
            infos,
            castVoteRecordZip,
            contestManifestFilename,
            cvrExportCsv,
        )
        cardPoolMapByName = allCardPools.filter { it.value.poolName != unpooled } // exclude the unpooled
        cardPools = cardPoolMapByName.values.toList() // exclude the unpooled
        val unpooledPool = allCardPools[unpooled]!!
        this.cardCount = ncards

        contests = makeContests(cvrTabs, contestNcs)

        // we need to know the diluted Nb before we can create the UAs
        manifest = createCardManifest()
        val manifestTabs = tabulateAuditableCards(manifest.cardLocations.iterator(), infos)
        val contestNbs = manifestTabs.mapValues { it.value.ncards }
        println("contestNbs ${contestNbs}")

        // make contests based on cvr tabulations
        contestsUA = if (config.isClca) makeClcaContests(contests, cvrTabs, contestNcs, contestNbs, hasStyle).sortedBy { it.id }
            else if (config.isOA) makeAllOneAuditContests(cvrTabs, contestNcs, unpooledPool, contestNbs, hasStyle).sortedBy { it.id }
            else makePollingContests(cvrTabs, contestNcs, contestNbs, hasStyle).sortedBy { it.id }
    }

    fun createCardPools(
        // auditDir: String,
        contestInfos: Map<Int, ContestInfo>,
        castVoteRecordZip: String,
        contestManifestFilename: String,
        cvrExportCsv: String,
    ): Triple<Map<String, CardPoolFromCvrs>, Map<Int, ContestTabulation>, Int> {

        val cardPools: MutableMap<String, CardPoolFromCvrs> = mutableMapOf()
        val cvrTabs = mutableMapOf<Int, ContestTabulation>()
        var cardCount = 0
        cvrExportCsvIterator(cvrExportCsv).use { cvrIter ->
            while (cvrIter.hasNext()) {
                cardCount++
                val cvrExport: CvrExport = cvrIter.next()
                val pool = cardPools.getOrPut(cvrExport.poolKey()) {
                    CardPoolFromCvrs(cvrExport.poolKey(), cardPools.size + 1, contestInfos)
                }
                pool.accumulateVotes(cvrExport.toCvr())

                cvrExport.votes.forEach { (id, cands) ->
                    val contestTab = cvrTabs.getOrPut(id) { ContestTabulation(contestInfos[id]!!) }
                    contestTab.addVotes(cands, phantom=false)
                }
            }
        }

        //// because the unpooled is in a pool, the sum of pools are all the votes
        val sortedPools = cardPools.toSortedMap()
        val poolTabs = mutableMapOf<Int, ContestTabulation>()
        sortedPools.forEach { (_, pool: CardPoolFromCvrs) ->
            pool.addTo(poolTabs)
        }

        // check they are the same
        require(cvrTabs.size == poolTabs.size)
        cvrTabs.forEach { (id, cvrTab) ->
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

        return Triple(cardPools, poolTabs, cardCount)
    }

    fun makeContests(contestTabSums: Map<Int, ContestTabulation>, contestNcs: Map<Int, Int>): List<ContestIF> {
        val contests = mutableListOf<ContestIF>()
        contestTabSums.forEach { (contestId, contestSumTab) ->
            val info = contestSumTab.info
            val useNc = contestNcs[info.id] ?: contestSumTab.ncards
            if (useNc > 0) contests.add( Contest(contestSumTab.info, contestSumTab.votes, useNc, contestSumTab.ncards)) // The IRV are fake
        }
        return contests
    }

    override fun cardPools() = if (config.isOA) cardPools else null
    override fun contestsUA() = contestsUA
    override fun cardManifest() = manifest

    fun createCardManifest(): CardLocationManifest {
        val cardPoolIterable: CloseableIterable<AuditableCard> = CardPoolCvrIterable(
            cardPoolMapByName,
            CloseableIterable { cvrExportCsvIterator(cvrExportCsv) },
            config)
        val phantomCards = makePhantomCards(contests, this.cardCount)

        return CardLocationManifest(CardIterable(phantomCards, cardPoolIterable), emptyList())
    }

    data class CardIterable(val phantomCards : List<AuditableCard>, val cardPoolIterable: CloseableIterable<AuditableCard>): CloseableIterable<AuditableCard> {
        override fun iterator(): CloseableIterator<AuditableCard> {
            val phantoms = phantomCards.asSequence()
            val cardSeq = cardPoolIterable.iterator().asSequence()
            val allSeq =  cardSeq + phantoms
            return Closer( allSeq.iterator())
        }
    }

    /* deprecate i think
    override fun allCvrs(): Pair<CloseableIterator<AuditableCard>?, CloseableIterator<AuditableCard>?> {
        return Pair(cardManifest().cardLocations.iterator(), null)
    } */
}

fun makeClcaContests(contests: List<ContestIF>, cvrTabs: Map<Int, ContestTabulation>, contestNcs : Map<Int, Int>, contestNbs: Map<Int, Int>, hasStyle:Boolean): List<ContestUnderAudit> {
    val contestsUAs = mutableListOf<ContestUnderAudit>()
    contests.map { contest  ->
        val info = contest.info()
        val cvrTab = cvrTabs[contest.id]!!

        val useNc = contestNcs[info.id] ?: cvrTab.ncards
        val contestUA: ContestUnderAudit = if (!cvrTab.isIrv) {
            ContestUnderAudit(contest, hasStyle=hasStyle, Nbin=contestNbs[info.id]).addStandardAssertions()
        } else {
            makeRaireContestUA(info, cvrTab, useNc, hasStyle=hasStyle, Nbin=contestNbs[info.id])
        }
        contestUA.contest.info().metadata["PoolPct"] = 0
        contestsUAs.add(contestUA)
    }
    return contestsUAs
}

fun makeAllOneAuditContests(contestTabSums: Map<Int, ContestTabulation>, contestNcs: Map<Int, Int>, unpooled: CardPoolFromCvrs, contestNbs: Map<Int, Int>, hasStyle:Boolean): List<ContestUnderAudit> {
    val contestsUAs = mutableListOf<ContestUnderAudit>()
    contestTabSums.map { (contestId, contestSumTab)  ->
        val info = contestSumTab.info
        val unpooledTab: ContestTabulation = unpooled.contestTabs[contestId]!!

        val useNc = contestNcs[info.id] ?: contestSumTab.ncards
        if (useNc > 0) {
            val contestOA: ContestUnderAudit = if (!contestSumTab.isIrv) {
                val contest = Contest(contestSumTab.info, contestSumTab.votes, useNc, contestSumTab.ncards)
                ContestUnderAudit(contest, isClca = true, Nbin=contestNbs[info.id]).addStandardAssertions()
            } else {
                makeRaireContestUA(contestSumTab.info, contestSumTab, useNc, hasStyle=hasStyle, Nbin=contestNbs[info.id])
            }
            // annotate with the pool %
            val unpooledPct = 100.0 * unpooledTab.ncards / contestSumTab.ncards
            val poolPct = (100 - unpooledPct).toInt()
            contestOA.contest.info().metadata["PoolPct"] = poolPct
            contestsUAs.add(contestOA)
        }
    }
    return contestsUAs
}

fun makePollingContests(contestTabSums: Map<Int, ContestTabulation>, contestNcs: Map<Int, Int>, contestNbs: Map<Int, Int>, hasStyle:Boolean): List<ContestUnderAudit> {
    val contestsUAs = mutableListOf<ContestUnderAudit>()
    contestTabSums.map { (contestId, contestSumTab)  ->
        val useNc = contestNcs[contestId] ?: contestSumTab.ncards
        if (useNc > 0) {
            if (!contestSumTab.isIrv) { // cant do IRV
                val contest = Contest(contestSumTab.info, contestSumTab.votes, useNc, contestSumTab.ncards) // TODO Ncast = Nb wrong
                val contestUA = ContestUnderAudit(contest, isClca = false, hasStyle=hasStyle, Nbin=contestNbs[contestId]).addStandardAssertions()
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

class CardPoolCvrIterable(val poolMap: Map<String, CardPoolIF>, val org: CloseableIterable<CvrExport>, val config: AuditConfig): CloseableIterable<AuditableCard> {

    override fun iterator(): CloseableIterator<AuditableCard> = CardPoolCvrIterator(org.iterator())

    inner class CardPoolCvrIterator(val orgIter: CloseableIterator<CvrExport>): CloseableIterator<AuditableCard> {
        var cardIndex = 1
        override fun hasNext() = orgIter.hasNext()

        override fun next(): AuditableCard {
            val orgCvrExport = orgIter.next()
            val pool = poolMap[orgCvrExport.poolKey()]
            val hasCvr = config.isClca || (pool == null)
            val contests = if (hasCvr) intArrayOf() else pool.contests()
            val votes = if (hasCvr) orgCvrExport.votes else null
            return AuditableCard(orgCvrExport.id, cardIndex++, 0, phantom=false, contests, votes, pool?.poolId)
        }

        override fun close() = orgIter.close()
    }
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
        (auditType ==  AuditType.CLCA) -> AuditConfig(
            AuditType.CLCA, hasStyle = hasStyle, contestSampleCutoff = 20000, riskLimit = .05, nsimEst=10,
            clcaConfig = ClcaConfig(strategy = ClcaStrategyType.previous)
        )
        (auditType ==  AuditType.ONEAUDIT) -> AuditConfig(
            AuditType.ONEAUDIT, hasStyle = hasStyle, riskLimit = .05, contestSampleCutoff = 20000, nsimEst = 1,
            oaConfig = OneAuditConfig(OneAuditStrategyType.optimalComparison, useFirst = true)
        )
        else -> AuditConfig(
            AuditType.POLLING, hasStyle = hasStyle, riskLimit = .05, contestSampleCutoff = 10000, nsimEst = 100,
            pollingConfig = PollingConfig())
    }

    val election = CreateSfElection(
        castVoteRecordZip,
        contestManifestFilename,
        candidateManifestFile,
        cvrExportCsv,
        config = config,
        hasStyle = hasStyle,
    )

    CreateAudit("sf2024", topdir, config, election)
    println("createSfElection took $stopwatch")
}
