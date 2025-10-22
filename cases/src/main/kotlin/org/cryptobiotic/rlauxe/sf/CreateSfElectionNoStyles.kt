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
import org.cryptobiotic.rlauxe.util.CloseableIterable
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.estimate.makePhantomCvrs
import org.cryptobiotic.rlauxe.util.ContestTabulation
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach

private val logger = KotlinLogging.logger("CreateSfElectionNS")
private val show = false

// no styles case
class CreateSfElectionNS(
    castVoteRecordZip: String,
    contestManifestFilename: String,
    candidateManifestFile: String,
    val cvrExportCsv: String,
): CreateElection2IF {
    val cardPoolsNotUnpooled: List<CardPoolIF>
    val contestsOA: List<ContestUnderAudit>
    val extra = mutableListOf<Cvr>()
    val cardPoolsNs: Map<String, CardPoolNs>

    init {
        val (contestNcs, contestInfos) = makeContestInfos(
            castVoteRecordZip,
            contestManifestFilename,
            candidateManifestFile
        )
        val infos = contestInfos.associateBy { it.id }

        // pass 1 through cvrs, make card pools, including unpooled
        // pass 1 through cvrs, make card pools and augment
        val (cardPools: List<CardPoolNs>, contestAmended: Map<Int, Int>) = createCardPoolsNS(
            infos,
            castVoteRecordZip,
            contestManifestFilename,
            cvrExportCsv,
        )
        val contestNcsAmended = mutableMapOf<Int, Int>()
        contestNcs.forEach { (contestId, Nc) -> contestNcsAmended[contestId] = Nc + (contestAmended[contestId] ?: 0) }

        val contestTabSums = mutableMapOf<Int, ContestTabulation>()
        cardPools.forEach { pool : CardPoolFromCvrs ->
            pool.addTo( contestTabSums)
        }

        val unpooledPool = cardPools.find { it.poolName == unpooled }!!
        println(unpooledPool)
        cardPoolsNotUnpooled = cardPools.filter { it.poolName != unpooled }

        // make contests based on cardPool tabulations
        contestsOA = makeAllOneAuditContests(contestTabSums, contestNcsAmended, unpooledPool).sortedBy { it.id }
        cardPoolsNs = cardPools.associateBy { it.poolName }
    }

    // no styles case
    fun createCardPoolsNS(
        infos: Map<Int, ContestInfo>,
        castVoteRecordZip: String,
        contestManifestFilename: String,
        cvrExportCsv: String,
    ): Pair<List<CardPoolNs>, Map<Int, Int>> { // return CardPools, contestId -> increaseNc

        val contestManifest = readContestManifestFromZip(castVoteRecordZip, contestManifestFilename)

        // create the card Pools,
        var count = 0
        val cardPools: MutableMap<String, CardPoolNs> = mutableMapOf()
        cvrExportCsvIterator(cvrExportCsv).use { cvrIter ->
            while (cvrIter.hasNext()) {
                count++
                val cvrExport: CvrExport = cvrIter.next()
                val pool = cardPools.getOrPut(cvrExport.poolKey()) {
                    CardPoolNs(cvrExport.poolKey(), cardPools.size + 1, infos)
                }
                pool.accumulateVotes(cvrExport.toCvr())
                pool.add(cvrExport)
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

        /* not needed, but leave it in for now for sanity check
        // create amended card Pools; TODO how to amend the cvrs persistently; add extra votes ??
        val cardPoolsM: MutableMap<String, CardPoolFromCvrs> = mutableMapOf()
        val cvrIterM = CardPoolCvrIterator(cardPools.values).iterator()
        while (cvrIterM.hasNext()) {
            val cvrExport: CvrExport = cvrIterM.next()
            val pool = cardPoolsM.getOrPut(cvrExport.poolKey()) {
                CardPoolNs(cvrExport.poolKey(), cardPoolsM.size + 1, infos)
            }
            pool.accumulateVotes(cvrExport.toCvr())
        }

        cardPools.forEach { (poolId, poolu) ->
            poolu.contestTabs.keys.forEach { id ->
                val poolu1 = poolu.contestTabs[id]
                val poolm = cardPoolsM[poolId]!!
                val poolm1 = poolm.contestTabs[id]
                if (poolm1 != poolu1)
                    println("why")
            }
        } */

        return Pair(cardPools.values.toList(), increaseNc)
    }

    override fun cardPools() = cardPoolsNotUnpooled
    override fun contestsUA() = contestsOA

    override fun hasTestMvrs() = false
    override fun allCvrs(): Pair<CloseableIterable<AuditableCard>, CloseableIterable<AuditableCard>> {
        val phantomCvrs = makePhantomCvrs(contestsUA().map { it.contest })
        val phantomSeq = phantomCvrs.mapIndexed { idx, cvr -> AuditableCard.fromCvr(cvr, idx, 0L) }.asSequence()

        val cvrIter: CloseableIterable<CvrExport>  = CardPoolModifiedCvrIterable(cardPoolsNs, CloseableIterable { cvrExportCsvIterator(cvrExportCsv) })
        val poolNameToId = cardPoolsNotUnpooled.associate { it.poolName to it.poolId }
        val cardSeq = CvrExportToCardAdapter(cvrIter.iterator(), poolNameToId).asSequence()

        val allCardsIter = (cardSeq + phantomSeq).iterator()
        val allCardsIterable = CloseableIterable { allCardsIter.iterator() }
        val emptyIterable = CloseableIterable { emptyList<AuditableCard>().iterator() }
        return Pair(allCardsIterable, emptyIterable)
    }

}

// keep all the CVRS, which we can use to calculate average assort.
class CardPoolNs( poolName: String, poolId: Int, contestInfos: Map<Int, ContestInfo>) : CardPoolFromCvrs(poolName, poolId, contestInfos) {
    var cvrMap = mutableMapOf<String, CvrExport>()
    fun add(cvr: CvrExport) {
        cvrMap[cvr.id] = cvr
    }
}

// tricky bit of business, an iterator where we substitute the mofified
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

/* TODO remove
class CardPoolCvrIterator(val cardPools: Collection<CardPoolNs>): Iterable<CvrExport> {

    override fun iterator(): CloseableIterator<CvrExport> {
        return PoolIterator()
    }

    inner class PoolIterator() : CloseableIterator<CvrExport> {
        var pools = cardPools.iterator()
        var base = getNextBaseIterator()

        override fun hasNext(): Boolean {
            if (base == null) base = getNextBaseIterator()
            if (base == null) return false

            // its possible that the base iterator is empty
            while (!base!!.hasNext()) {
                base = getNextBaseIterator()
                if (base == null) return false
            }
            return true
        }

        override fun next(): CvrExport {
            return base!!.next()
        }

        fun getNextBaseIterator(): BaseIterator? {
            if (!pools.hasNext()) return null
            return BaseIterator(pools.next())
        }

        override fun close() {}
    }

    class BaseIterator(val pool: CardPoolNs) : CloseableIterator<CvrExport> {
        val bitter = pool.cvrMap.values.iterator()
        override fun hasNext(): Boolean = bitter.hasNext()
        override fun next(): CvrExport = bitter.next()
        override fun close() {}
    }
} */

//////////////////////////////////////////////////////////////////////////////////////

fun createSfElectionNoStyles(
    topdir: String,
    castVoteRecordZip: String,
    contestManifestFilename: String,
    candidateManifestFile: String,
    cvrExportCsv: String,
    auditConfigIn: AuditConfig? = null,
) {
    val stopwatch = Stopwatch()
    val auditConfig = when {
        (auditConfigIn != null) -> auditConfigIn
        else -> AuditConfig(
            AuditType.ONEAUDIT, hasStyles = true, riskLimit = .05, sampleLimit = 20000, nsimEst = 1,
            oaConfig = OneAuditConfig(OneAuditStrategyType.optimalComparison, useFirst = true)
        )
    }

    val election = CreateSfElectionNS(
        castVoteRecordZip,
        contestManifestFilename,
        candidateManifestFile,
        cvrExportCsv,
    )

    CreateAudit2("sf2024", topdir, auditConfig, election)
    println("createSfElectionNoStyles took $stopwatch")
}
