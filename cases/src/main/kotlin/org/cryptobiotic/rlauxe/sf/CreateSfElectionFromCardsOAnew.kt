package org.cryptobiotic.rlauxe.sf

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.CvrExport
import org.cryptobiotic.rlauxe.oneaudit.CardPoolFromCvrs
import org.cryptobiotic.rlauxe.oneaudit.CardPoolIF
import org.cryptobiotic.rlauxe.oneaudit.unpooled
import org.cryptobiotic.rlauxe.persist.csv.*
import org.cryptobiotic.rlauxe.workflow.CreateAudit
import org.cryptobiotic.rlauxe.workflow.CreateElectionIF
import kotlin.Boolean
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach

private val logger = KotlinLogging.logger("createSfElectionFromCsvExportOA")

// use the contestManifest and candidateManifest to create the contestInfo, both regular and IRV.
// Use "CvrExport" CSV file to tally the votes and create the assertions.
class CreateSfElectionOAnew(
    castVoteRecordZip: String,
    contestManifestFilename: String,
    candidateManifestFile: String,
    val cvrExportCsv: String,
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
        val (cardPoolMap: Map<Int, CardPoolFromCvrs>, contestTabSums) = createCardPools(
            infos,
            castVoteRecordZip,
            contestManifestFilename,
            cvrExportCsv,
        )

        // write ballot pools, but not unpooled
        cardPoolsNotUnpooled = cardPoolMap.values.filter { it.poolName != unpooled }

        // make contests based on cardPool tabulations
        val unpooledPool = cardPoolMap.values.find { it.poolName == unpooled }!!
        contestsOA = makeAllOneAuditContests(contestTabSums, contestNcs, unpooledPool).sortedBy { it.id }
    }

    fun createCardPools(
        // auditDir: String,
        contestInfos: Map<Int, ContestInfo>,
        castVoteRecordZip: String,
        contestManifestFilename: String,
        cvrExportCsv: String,
    ): Pair<Map<Int, CardPoolFromCvrs>, Map<Int, ContestTabulation>> {

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
                    val contestTab = contestTabs.getOrPut(id) { ContestTabulation(contestInfos[id]!! ) }
                    contestTab.addVotes(cands)
                }
            }
        }

        //// because the unpooled is in a pool, the sum of pools are all the votes
        val sortedPools = cardPools.toSortedMap()
        val contestTabSums = mutableMapOf<Int, ContestTabulation>()
        sortedPools.forEach { (_, pool : CardPoolFromCvrs) ->
            pool.sum( contestTabSums)
        }

        val staxContests = StaxReader().read("src/test/data/SF2024/summary.xml")
        println("staxContests")
        contestTabSums.toSortedMap().forEach { (id, contestTab) ->
            val contestName = contestManifest.contests[id]!!.Description
            val staxContest: StaxReader.StaxContest = staxContests.find { it.id == contestName}!!
            if (staxContest.ncards() != contestTab.ncards) {
                logger.warn{"staxContest $contestName ($id) has ncards = ${staxContest.ncards()} not equal to cvr summary = ${contestTab.ncards} "}
                // assertEquals(staxContest.blanks(), contest.blanks)
            }
            println("  $contestName ($id) has stax ncards = ${staxContest.ncards()}, cvr ncards = ${contestTab.ncards}")
        }

        return Pair(cardPools.values.associateBy { it.poolId }, contestTabSums)
    }

    override fun makeCardPools() = cardPoolsNotUnpooled
    override fun makeContestsUA(hasStyles: Boolean) = contestsOA

    override fun allCvrs() = emptyList<Cvr>()

    override fun cvrExport() = cvrExportCsvIterator(cvrExportCsv)

    override fun hasCvrExport() = true
    override fun testMvrs() = null // TODO
}

fun createSfElectionOAnew(
    topdir: String,
    castVoteRecordZip: String,
    contestManifestFilename: String,
    candidateManifestFile: String,
    cvrExportCsv: String,
    auditConfigIn: AuditConfig? = null,
) {
    val auditConfig = auditConfigIn ?: AuditConfig(
        AuditType.ONEAUDIT, hasStyles = true, sampleLimit = 20000, riskLimit = .05, nsimEst = 50,
        oaConfig = OneAuditConfig(OneAuditStrategyType.optimalComparison, useFirst = false)
    )

    val election = CreateSfElectionOAnew(
        castVoteRecordZip,
        contestManifestFilename,
        candidateManifestFile,
        cvrExportCsv,
    )

    CreateAudit("sf2024", topdir, auditConfig, election)
}
