package org.cryptobiotic.rlauxe.sf

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.unwrap
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.CvrExport
import org.cryptobiotic.rlauxe.oneaudit.CardPoolFromCvrs
import org.cryptobiotic.rlauxe.oneaudit.CardPoolIF
import org.cryptobiotic.rlauxe.oneaudit.unpooled
import org.cryptobiotic.rlauxe.persist.csv.*
import org.cryptobiotic.rlauxe.raire.makeRaireContestUA
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.estimate.makePhantomCvrs
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.Closer
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.util.ErrorMessages
import kotlin.Boolean
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach

private val logger = KotlinLogging.logger("createSfElectionFromCsvExportOA")

// Compare CLCA against OneAudit with styles on the SanFrancisco 2024 General Election.
class CreateSfElection(
    castVoteRecordZip: String,
    contestManifestFilename: String,
    candidateManifestFile: String,
    val cvrExportCsv: String,
    val isClca: Boolean,
): CreateElectionIF {

    val cardPoolsNotUnpooled: List<CardPoolIF>
    val contestsOA: List<ContestUnderAudit>

    init {
        val (contestNcs, contestInfos) = makeContestInfos(
            castVoteRecordZip,
            contestManifestFilename,
            candidateManifestFile
        )
        val infos = contestInfos.associateBy { it.id }

        // pass 1 through cvrs, make card pools, including unpooled
        val (cardPoolMap: List<CardPoolFromCvrs>, contestTabSums) = createCardPools(
            infos,
            castVoteRecordZip,
            contestManifestFilename,
            cvrExportCsv,
        )

        // write ballot pools, but not unpooled
        cardPoolsNotUnpooled = cardPoolMap.filter { it.poolName != unpooled }

        // make contests based on cardPool tabulations
        val unpooledPool = cardPoolMap.find { it.poolName == unpooled }!!
        contestsOA = if (isClca) makeClcaContests(contestTabSums, contestNcs).sortedBy { it.id }
            else makeAllOneAuditContests(contestTabSums, contestNcs, unpooledPool).sortedBy { it.id }
    }

    fun createCardPools(
        // auditDir: String,
        contestInfos: Map<Int, ContestInfo>,
        castVoteRecordZip: String,
        contestManifestFilename: String,
        cvrExportCsv: String,
    ): Pair<List<CardPoolFromCvrs>, Map<Int, ContestTabulation>> {

        val contestManifest = readContestManifestFromZip(castVoteRecordZip, contestManifestFilename)
        println("IRV contests = ${contestManifest.irvContests}")

        // make the card pools
        val cardPools: MutableMap<String, CardPoolFromCvrs> = mutableMapOf()
        val contestTabs = mutableMapOf<Int, ContestTabulation>()
        cvrExportCsvIterator(cvrExportCsv).use { cvrIter ->
            while (cvrIter.hasNext()) {
                val cvrExport: CvrExport = cvrIter.next()
                val pool = cardPools.getOrPut(cvrExport.poolKey()) {
                    CardPoolFromCvrs(cvrExport.poolKey(), cardPools.size + 1, contestInfos)
                }
                pool.accumulateVotes(cvrExport.toCvr())

                cvrExport.votes.forEach { (id, cands) ->
                    val contestTab = contestTabs.getOrPut(id) { ContestTabulation(contestInfos[id]!!) }
                    contestTab.addVotes(cands, phantom=false)
                }
            }
        }

        //// because the unpooled is in a pool, the sum of pools are all the votes
        val sortedPools = cardPools.toSortedMap()
        val contestTabSums = mutableMapOf<Int, ContestTabulation>()
        sortedPools.forEach { (_, pool: CardPoolFromCvrs) ->
            pool.addTo(contestTabSums)
        }

        val staxContests = StaxReader().read("src/test/data/SF2024/summary.xml")
        println("staxContests")
        contestTabSums.toSortedMap().forEach { (id, contestTab) ->
            val contestName = contestManifest.contests[id]!!.Description
            val staxContest: StaxReader.StaxContest = staxContests.find { it.id == contestName }!!
            if (staxContest.ncards() != contestTab.ncards) {
                logger.warn { "staxContest $contestName ($id) has ncards = ${staxContest.ncards()} not equal to cvr summary = ${contestTab.ncards} " }
                // assertEquals(staxContest.blanks(), contest.blanks)
            }
            println("  $contestName ($id) has stax ncards = ${staxContest.ncards()}, cvr ncards = ${contestTab.ncards}")
        }

        return Pair(cardPools.values.toList(), contestTabSums)
    }

    override fun cardPools() = cardPoolsNotUnpooled
    override fun contestsUA() = contestsOA

    override fun allCvrs(): Pair<CloseableIterator<AuditableCard>?, CloseableIterator<AuditableCard>?> {
        val phantomCvrs = makePhantomCvrs(contestsUA().map { it.contest })
        val phantomSeq = phantomCvrs.mapIndexed { idx, cvr -> AuditableCard.fromCvr(cvr, idx, 0L) }.asSequence()

        val cvrIter = cvrExportCsvIterator(cvrExportCsv)
        val poolNameToId = cardPoolsNotUnpooled.associate { it.poolName to it.poolId }
        val cardSeq = CvrExportToCardAdapter(cvrIter, poolNameToId).asSequence()

        val allCardsIter = (cardSeq + phantomSeq).iterator()

        return Pair(Closer(allCardsIter), null)
    }
}

fun makeAllOneAuditContests(contestTabSums: Map<Int, ContestTabulation>, contestNcs: Map<Int, Int>, unpooled: CardPoolFromCvrs): List<ContestUnderAudit> {
    val contestsUAs = mutableListOf<ContestUnderAudit>()
    contestTabSums.map { (contestId, contestSumTab)  ->
        val info = contestSumTab.info
        val unpooledTab: ContestTabulation = unpooled.contestTabs[contestId]!!

        val useNc = contestNcs[info.id] ?: contestSumTab.ncards
        if (useNc > 0) {
            val contestOA: ContestUnderAudit = if (!contestSumTab.isIrv) {
                val contest = Contest(contestSumTab.info, contestSumTab.votes, useNc, contestSumTab.ncards)
                ContestUnderAudit(contest, isClca = true).addStandardAssertions()
            } else {
                makeRaireContestUA(contestSumTab.info, contestSumTab, useNc)
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

fun makeClcaContests(contestTabSums: Map<Int, ContestTabulation>, contestNcs: Map<Int, Int>): List<ContestUnderAudit> {
    val contestsUAs = mutableListOf<ContestUnderAudit>()
    contestTabSums.map { (contestId, contestSumTab)  ->
        val info = contestSumTab.info

        val useNc = contestNcs[info.id] ?: contestSumTab.ncards
        if (useNc > 0) {
            val contestUA: ContestUnderAudit = if (!contestSumTab.isIrv) {
                val contest = Contest(contestSumTab.info, contestSumTab.votes, useNc, contestSumTab.ncards)
                ContestUnderAudit(contest).addStandardAssertions()
            } else {
                makeRaireContestUA(contestSumTab.info, contestSumTab, useNc)
            }
            contestUA.contest.info().metadata["PoolPct"] = 0
            contestsUAs.add(contestUA)
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
    isClca : Boolean,
) {
    val stopwatch = Stopwatch()
    val config = when {
        (auditConfigIn != null) -> auditConfigIn
        isClca -> AuditConfig(
            AuditType.CLCA, hasStyle = true, contestSampleCutoff = 20000, riskLimit = .05, nsimEst=10,
            clcaConfig = ClcaConfig(strategy = ClcaStrategyType.previous)
        )
        else -> AuditConfig(
            AuditType.ONEAUDIT, hasStyle = true, riskLimit = .05, contestSampleCutoff = 20000, nsimEst = 1,
            oaConfig = OneAuditConfig(OneAuditStrategyType.optimalComparison, useFirst = true)
        )
    }

    val election = CreateSfElection(
        castVoteRecordZip,
        contestManifestFilename,
        candidateManifestFile,
        cvrExportCsv,
        isClca = isClca,
    )

    CreateAudit("sf2024", topdir, config, election)
    println("createSfElection took $stopwatch")
}
