package org.cryptobiotic.rlauxe.sf

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
import org.cryptobiotic.rlauxe.util.CloseableIterable
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.estimate.makePhantomCvrs
import org.cryptobiotic.rlauxe.util.Closer
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.util.tabulateAuditableCards
import org.cryptobiotic.rlauxe.util.tabulateCvr
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach

private val logger = KotlinLogging.logger("CreateSfElectionNS")
private val show = false

// the contests in a pool constitute the ballot "pool style"
class CreateSfElectionPoolStyles(
    castVoteRecordZip: String,
    contestManifestFilename: String,
    candidateManifestFile: String,
    val cvrExportCsv: String,
    val isPolling: Boolean,
    val hasStyle:Boolean,
    ): CreateElectionIF {

    val contestsUA: List<ContestUnderAudit>
    val cardPools: List<CardPoolIF>
    var cardPoolMapByName: Map<String, CardPoolNs>

    init {
        val (unamendedContestNcs, contestInfos) = makeContestInfos(
            castVoteRecordZip,
            contestManifestFilename,
            candidateManifestFile
        )
        val infos = contestInfos.associateBy { it.id }

        // pass 1 through cvrs, make card pools
        val (unamendedContestTabs: Map<Int, ContestTabulation>, allCardPools: List<CardPoolNs>, increaseNc: Map<Int, Int>) = createCardPoolsNoStyle(infos, cvrExportCsv)
        cardPools = allCardPools.filter { it.poolName != unpooled } // exclude the unpooled
        cardPoolMapByName = allCardPools.associateBy { it.poolName }

        if (!isPolling) { // OneAudit
            val unpooledPool = allCardPools.find { it.poolName == unpooled }!!
            println(unpooledPool)

            // make contests based on cardPool tabulations (included the unpooled)
            val contestTabSums = mutableMapOf<Int, ContestTabulation>()
            allCardPools.forEach { pool : CardPoolFromCvrs -> pool.addTo( contestTabSums) }

            val contestNcsAmended = mutableMapOf<Int, Int>()
            unamendedContestNcs.forEach { (contestId, Nc) -> contestNcsAmended[contestId] = Nc + (increaseNc[contestId] ?: 0) }
            contestsUA = makeAllOneAuditContests(contestTabSums, contestNcsAmended, unpooledPool, hasStyle).sortedBy { it.id }

        }  else { // Polling
            // use unamended tabs and contest.Nc.
            contestsUA = makePollingContests(unamendedContestTabs, unamendedContestNcs).sortedBy { it.id }

            // calculate Nb by totalling the cvrs that have that contest
            val contestMap = contestsUA.associateBy { it.id }
            val contestTabs = tabulateAuditableCards(cvrs(), infos)
            contestTabs.forEach { contestId, tab ->
                val contest = contestMap[contestId]
                if (contest != null) {
                    contest.setNb(tab.ncards + contest.Np)
                    println("contest $contestId Nb = ${tab.ncards} Nb/Nc = ${tab.ncards / contest.Nc.toDouble()}")
                }
            }
        }
    }

    fun makePollingContests(contestTabSums: Map<Int, ContestTabulation>, contestNcs: Map<Int, Int>): List<ContestUnderAudit> {
        val contestsUAs = mutableListOf<ContestUnderAudit>()
        contestTabSums.map { (contestId, contestSumTab)  ->
            val useNc = contestNcs[contestId] ?: contestSumTab.ncards
            if (useNc > 0) {
                if (!contestSumTab.isIrv) { // cant do IRV
                    val contest = Contest(contestSumTab.info, contestSumTab.votes, useNc, contestSumTab.ncards)
                    val contestUA = ContestUnderAudit(contest, isClca = false).addStandardAssertions()
                    contestUA.contest.info().metadata["PoolPct"] = 0
                    contestsUAs.add(contestUA)
                }
            }
        }
        return contestsUAs
    }

    fun createCardPoolsNoStyle(
        infos: Map<Int, ContestInfo>,
        cvrExportCsv: String,
    ): Triple<Map<Int, ContestTabulation>, List<CardPoolNs>, Map<Int, Int>> { // return CardPools, contestId -> increaseNc

        val unamendedContestTabs = mutableMapOf<Int, ContestTabulation>()

        // create the card Pools from the CvrExport
        var count = 0
        val cardPools: MutableMap<String, CardPoolNs> = mutableMapOf()
        cvrExportCsvIterator(cvrExportCsv).use { cvrIter ->
            while (cvrIter.hasNext()) {
                count++
                val cvrExport: CvrExport = cvrIter.next()
                val pool = cardPools.getOrPut(cvrExport.poolKey()) {
                    CardPoolNs(cvrExport.poolKey(), cardPools.size + 1, infos)
                }
                val cvr = cvrExport.toCvr()
                pool.accumulateVotes(cvr)
                pool.add(cvrExport)

                tabulateCvr(cvr, infos, unamendedContestTabs)
            }
        }
        println("pools = ${cardPools.size} from $count cvrs")

        // for each pool, every cvr has to have every contest in the pool
        var cvrsAmended = 0
        val increaseNc = mutableMapOf<Int, Int>()  // contestId, nadded
        cardPools.filter { it.key != unpooled }.values.forEach { pool ->
            val needContests = pool.contestTabs.keys
            val cvrsM = mutableListOf<CvrExport>()

            pool.cvrMap.values.forEach { cvr ->
                var wasAmended = false
                val votesM = cvr.votes.toMutableMap()
                needContests.forEach { contestId ->
                    if (!votesM.containsKey(contestId)) {
                        votesM[contestId] = IntArray(0)
                        wasAmended = true
                        pool.addUndervote(contestId)
                        val ca = increaseNc.getOrPut(contestId) { 0 }
                        increaseNc[contestId] = ca + 1
                    }
                }
                if (wasAmended) {
                    cvrsM.add(cvr.copy(votes = votesM))
                    cvrsAmended++
                }
            }
            // replace the modified cvrs
            cvrsM.forEach { pool.cvrMap[it.id] = it }
        }

        if (show) {
            println("cvrsAmended = $cvrsAmended")
            println("contestAmended")
            increaseNc.toSortedMap().forEach { println("  $it") }
        }

        return Triple(unamendedContestTabs, cardPools.values.toList(), increaseNc)
    }

    override fun cardPools() = if (isPolling) null else cardPools
    override fun contestsUA() = contestsUA

    fun cvrs(): CloseableIterator<AuditableCard> {
        val phantomCvrs = makePhantomCvrs(contestsUA().map { it.contest })
        val phantomSeq = phantomCvrs.mapIndexed { idx, cvr -> AuditableCard.fromCvr(cvr, idx, 0L) }.asSequence()

        val cvrIter: CloseableIterable<CvrExport>  = CardPoolModifiedCvrIterable(cardPoolMapByName, CloseableIterable { cvrExportCsvIterator(cvrExportCsv) })
        val poolNameToId = cardPools.associate { it.poolName to it.poolId }
        val cardSeq = CvrExportToCardAdapter(cvrIter.iterator(), poolNameToId).asSequence()

        val allCardsIter = (cardSeq + phantomSeq).iterator()
        return Closer(allCardsIter)
    }

    override fun allCvrs(): Pair<CloseableIterator<AuditableCard>?, CloseableIterator<AuditableCard>?> {
        return Pair(Closer(cvrs()), null)
    }

}

// keep all the CVRS, which we can use to calculate average assort.
class CardPoolNs( poolName: String, poolId: Int, contestInfos: Map<Int, ContestInfo>) : CardPoolFromCvrs(poolName, poolId, contestInfos) {
    var cvrMap = mutableMapOf<String, CvrExport>()
    fun add(cvr: CvrExport) {
        cvrMap[cvr.id] = cvr
    }
}

// tricky bit of business, an iterator where we substitute the modified cards
class CardPoolModifiedCvrIterable(val poolMap: Map<String, CardPoolNs>, val org: CloseableIterable<CvrExport>): CloseableIterable<CvrExport> {

    override fun iterator(): CloseableIterator<CvrExport> = CardPoolModifiedCvrIterator(org.iterator())

    inner class CardPoolModifiedCvrIterator(val orgIter: CloseableIterator<CvrExport>): CloseableIterator<CvrExport> {

        override fun hasNext() = orgIter.hasNext()

        override fun next(): CvrExport {
            val orgCvr = orgIter.next()
            val poolId = orgCvr.poolKey()
            val pool = poolMap[poolId] ?: return orgCvr
            return pool.cvrMap[orgCvr.id] ?: orgCvr
        }

        override fun close() = orgIter.close()
    }
}

//////////////////////////////////////////////////////////////////////////////////////

fun createSfElectionPoolStyles(
    topdir: String,
    castVoteRecordZip: String,
    contestManifestFilename: String,
    candidateManifestFile: String,
    cvrExportCsv: String,
    auditConfigIn: AuditConfig? = null,
    isPolling: Boolean,
    ) {
    val stopwatch = Stopwatch()
    val config = when {
        (auditConfigIn != null) -> auditConfigIn
        isPolling -> AuditConfig( // use the pool style to calculate Nb
            AuditType.POLLING, hasStyle = false, riskLimit = .05, contestSampleCutoff = null, nsimEst = 10,
            pollingConfig = PollingConfig())
        else -> AuditConfig( // Note hasStyle = true
            AuditType.ONEAUDIT, hasStyle = true, riskLimit = .05, contestSampleCutoff = 20000, nsimEst = 1,
            oaConfig = OneAuditConfig(OneAuditStrategyType.optimalComparison, useFirst = true)
        )
    }

    val election = CreateSfElectionPoolStyles(
        castVoteRecordZip,
        contestManifestFilename,
        candidateManifestFile,
        cvrExportCsv,
        isPolling,
        config.hasStyle,
    )

    CreateAudit("sf2024", topdir, config, election)
    println("createSfElectionNoStyles took $stopwatch")
}
