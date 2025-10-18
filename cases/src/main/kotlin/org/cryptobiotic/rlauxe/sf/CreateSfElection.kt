package org.cryptobiotic.rlauxe.sf

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.CvrExport
import org.cryptobiotic.rlauxe.oneaudit.CardPoolFromCvrs
import org.cryptobiotic.rlauxe.oneaudit.CardPoolIF
import org.cryptobiotic.rlauxe.oneaudit.OAContestUnderAudit
import org.cryptobiotic.rlauxe.oneaudit.OAIrvContestUA
import org.cryptobiotic.rlauxe.oneaudit.unpooled
import org.cryptobiotic.rlauxe.persist.csv.*
import org.cryptobiotic.rlauxe.raire.RaireContest
import org.cryptobiotic.rlauxe.raire.RaireContestUnderAudit
import org.cryptobiotic.rlauxe.raire.makeRaireContestUA
import org.cryptobiotic.rlauxe.util.CloseableIterable
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.workflow.CreateAudit
import org.cryptobiotic.rlauxe.workflow.CreateElectionIF
import kotlin.Boolean
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach

private val logger = KotlinLogging.logger("createSfElectionFromCsvExportOA")

// Compare CLCA, OneAudit with styles, and OneAudit without styles on the SanFrancisco 2024 General Election.
class CreateSfElection(
    castVoteRecordZip: String,
    contestManifestFilename: String,
    candidateManifestFile: String,
    val cvrExportCsv: String,
    val isClca: Boolean,
): CreateElectionIF {
    val cardPoolsNotUnpooled: List<CardPoolIF>
    val contestsOA: List<ContestUnderAudit>
    val extra = mutableListOf<Cvr>()

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
        contestsOA = makeAllOneAuditContests(contestTabSums, contestNcs, unpooledPool).sortedBy { it.id }
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
                    contestTab.addVotes(cands)
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

    override fun allCvrs() = Pair(emptyList<Cvr>(), emptyList<Cvr>())

    override fun cvrExport() = Pair(CloseableIterable { cvrExportCsvIterator(cvrExportCsv) }, extra)
}


fun makeAllOneAuditContests(contestTabSums: Map<Int, ContestTabulation>, contestNcs: Map<Int, Int>, unpooled: CardPoolFromCvrs): List<OAContestUnderAudit> {
    val contestsUAs = mutableListOf<OAContestUnderAudit>()
    contestTabSums.map { (contestId, contestSumTab)  ->
        val info = contestSumTab.info
        val unpooledTab: ContestTabulation = unpooled.contestTabs[contestId]!!

        val useNc = contestNcs[info.id] ?: contestSumTab.ncards
        if (useNc > 0) {
            val contestOA: OAContestUnderAudit = if (!contestSumTab.isIrv) {
                val contest = Contest(contestSumTab.info, contestSumTab.votes, useNc, contestSumTab.ncards)
                OAContestUnderAudit(contest)
            } else {
                val rau : RaireContestUnderAudit = makeRaireContestUA(contestSumTab.info, contestSumTab, useNc)
                OAIrvContestUA(rau.contest as RaireContest,  true, rau.rassertions)
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
    val auditConfig = when {
        (auditConfigIn != null) -> auditConfigIn
        isClca -> AuditConfig(
            AuditType.CLCA, hasStyles = true, sampleLimit = 20000, riskLimit = .05, nsimEst=10,
            clcaConfig = ClcaConfig(strategy = ClcaStrategyType.previous)
        )
        else -> AuditConfig(
            AuditType.ONEAUDIT, hasStyles = true, riskLimit = .05, sampleLimit = 20000, nsimEst = 1,
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

    CreateAudit("sf2024", topdir, auditConfig, election)
    println("createSfElection took $stopwatch")

}
