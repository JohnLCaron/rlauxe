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
import kotlin.String
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach

private val logger = KotlinLogging.logger("CreateSfElectionNS")
private val show = false

// the contests in a pool constitute the ballot "pool style"
// TODO dont modify the contest.Nc, but generate the cardManifest with possibleContests
class CreateSfElectionPoolStyle(
    castVoteRecordZip: String,
    contestManifestFilename: String,
    candidateManifestFile: String,
    val cvrExportCsv: String,
    val isPolling: Boolean,
    val hasStyle:Boolean,
    ): CreateElectionIF {

    val contestsUA: List<ContestUnderAudit>
    var cardPoolMapByName: Map<String, CardPoolIF>
    val cardPools: List<CardPoolIF>

    init {
        val (contestNcs, contestInfos) = makeContestInfos(
            castVoteRecordZip,
            contestManifestFilename,
            candidateManifestFile
        )
        val infos = contestInfos.associateBy { it.id }

        // pass 1 through cvrs, make card pools
        val (cvrTabs: Map<Int, ContestTabulation>, allCardPools: Map<String, CardPoolFromCvrs>) = createCardPoolsWithStyle(infos, cvrExportCsv)
        cardPoolMapByName = allCardPools.filter { it.value.poolName != unpooled } // exclude the unpooled
        cardPools = cardPoolMapByName.values.toList() // exclude the unpooled

        if (!isPolling) { // OneAudit
            val unpooledPool = allCardPools[unpooled]!!
            println(unpooledPool)

            // make contests based on cardPool tabulations (included the unpooled)
            //val contestTabSums = mutableMapOf<Int, ContestTabulation>()
            //allCardPools.forEach { pool : CardPoolFromCvrs -> pool.addTo( contestTabSums) }

            //val contestNcsAmended = mutableMapOf<Int, Int>()
            //unamendedContestNcs.forEach { (contestId, Nc) -> contestNcsAmended[contestId] = Nc + (increaseNc[contestId] ?: 0) }
            contestsUA = makeAllOneAuditContests(cvrTabs, contestNcs, unpooledPool, emptyMap<Int, Int>(), hasStyle).sortedBy { it.id }

        }  else { // Polling
            // TODO wrong: use unamended tabs and contest.Nc.

            // calculate Nb by totalling the cvrs that have that contest
            val contestTabs = tabulateAuditableCards(cardManifestOld(), infos)
            contestsUA = makePollingContests(contestTabs, contestNcs).sortedBy { it.id }
        }
    }

    fun makePollingContests(contestTabSums: Map<Int, ContestTabulation>, contestNcs: Map<Int, Int>): List<ContestUnderAudit> {
        val contestsUAs = mutableListOf<ContestUnderAudit>()
        contestTabSums.map { (contestId, contestSumTab)  ->
            val useNc = contestNcs[contestId] ?: contestSumTab.ncards
            if (useNc > 0) {
                if (!contestSumTab.isIrv) { // cant do IRV
                    val contest = Contest(contestSumTab.info, contestSumTab.votes, useNc, contestSumTab.ncards) // TODO Ncast = Nb wrong
                    val Nb = contestSumTab.ncards // tabs.ncards + contest.Np TODO
                    val contestUA = ContestUnderAudit(contest, isClca = false, Nbin=Nb).addStandardAssertions()
                    contestUA.contest.info().metadata["PoolPct"] = 0
                    contestsUAs.add(contestUA)
                }
            }
        }
        return contestsUAs
    }

    fun createCardPoolsWithStyle(
        infos: Map<Int, ContestInfo>,
        cvrExportCsv: String,
    ): Pair< Map<Int, ContestTabulation>, Map<String, CardPoolFromCvrs>> { // return CardPools, contestId -> increaseNc

        val cvrTabs = mutableMapOf<Int, ContestTabulation>()

        // create the card Pools from the CvrExport
        var count = 0
        val cardPools: MutableMap<String, CardPoolFromCvrs> = mutableMapOf()
        cvrExportCsvIterator(cvrExportCsv).use { cvrIter ->
            while (cvrIter.hasNext()) {
                count++
                val cvrExport: CvrExport = cvrIter.next()
                val pool = cardPools.getOrPut(cvrExport.poolKey()) {
                    CardPoolNs(cvrExport.poolKey(), cardPools.size + 1, infos)
                }
                val cvr = cvrExport.toCvr()
                pool.accumulateVotes(cvr)

                tabulateCvr(cvr, infos, cvrTabs)
            }
        }
        println("pools = ${cardPools.size} from $count cvrs")

        return Pair(cvrTabs, cardPools)
    }

    override fun cardPools() = if (isPolling) null else cardPools
    override fun contestsUA() = contestsUA

    fun cardManifestOld(): CloseableIterator<AuditableCard> {
        val phantomCvrs = makePhantomCvrs(contestsUA().map { it.contest })
        val phantomSeq = phantomCvrs.mapIndexed { idx, cvr -> AuditableCard.fromCvrHasStyle(cvr, idx, isClca=true) }.asSequence()

        // bogus
        val cvrIter: CloseableIterable<CvrExport>  = CardPoolModifiedCvrIterable(cardPoolMapByName as Map<String, CardPoolNs> , CloseableIterable { cvrExportCsvIterator(cvrExportCsv) })
        val poolNameToId = cardPools.associate { it.poolName to it.poolId }
        val cardSeq = CvrExportToCardAdapter(cvrIter.iterator(), poolNameToId).asSequence()

        val allCardsIter = (cardSeq + phantomSeq).iterator()
        return Closer(allCardsIter)
    }

    override fun allCvrs(): Pair<CloseableIterator<AuditableCard>?, CloseableIterator<AuditableCard>?> {
        return Pair(Closer(cardManifestOld()), null)
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

fun createSfElectionPoolStyle(
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

        isPolling -> AuditConfig( // I think hasStyle = false since we are using diluted margins
            AuditType.POLLING, hasStyle = false, riskLimit = .05, contestSampleCutoff = 10000, nsimEst = 100,
            pollingConfig = PollingConfig())

        else -> AuditConfig( // I think hasStyle = false since we are using diluted margins
            AuditType.ONEAUDIT, hasStyle = false, riskLimit = .05, contestSampleCutoff = 30000, nsimEst = 100,
            oaConfig = OneAuditConfig(OneAuditStrategyType.optimalComparison, useFirst = true)
        )
    }

    val election = CreateSfElectionPoolStyle(
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
