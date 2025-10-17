package org.cryptobiotic.rlauxe.sf

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
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
import org.cryptobiotic.rlauxe.workflow.CreateAudit
import org.cryptobiotic.rlauxe.workflow.CreateElectionIF
import kotlin.Boolean
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach

private val logger = KotlinLogging.logger("CreateSfElectionNS")

// no styles case
class CreateSfElectionNS(
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
        // pass 1 through cvrs, make card pools and augment
        val (cardPools: List<CardPoolFromCvrs>, contestAmended: Map<Int, Int>) = createCardPoolsNS(
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
    }

    // no styles case
    fun createCardPoolsNS(
        infos: Map<Int, ContestInfo>,
        castVoteRecordZip: String,
        contestManifestFilename: String,
        cvrExportCsv: String,
    ): Pair<List<CardPoolFromCvrs>, Map<Int, Int>> { // return CardPools, contestId -> increaseNc

        val contestManifest = readContestManifestFromZip(castVoteRecordZip, contestManifestFilename)

        // create the unamended card Pools,
        var count = 0
        val cardPoolsU: MutableMap<String, CardPoolNs> = mutableMapOf()
        cvrExportCsvIterator(cvrExportCsv).use { cvrIter ->
            while (cvrIter.hasNext()) {
                count++
                val cvrExport: CvrExport = cvrIter.next()
                val pool = cardPoolsU.getOrPut(cvrExport.poolKey()) {
                    CardPoolNs(cvrExport.poolKey(), cardPoolsU.size + 1, infos)
                }
                pool.accumulateVotes(cvrExport.toCvr())
                pool.cvrs.add(cvrExport)
            }
        }
        println("unamended pools = ${cardPoolsU.size} from $count cvrs")

        // for each pool, every cvr has to have every contest in the pool
        var cvrsAmended = 0
        val increaseNc = mutableMapOf<Int, Int>()  // contestId, nadded
        cardPoolsU.filter { it.key != unpooled }.values.forEach { pool ->
            val needContests = pool.contestTabs.keys
            val cvrsM = mutableListOf<CvrExport>()

            pool.cvrs.forEach { cvr ->
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
                } else {
                    cvrsM.add(cvr)
                }
            }
            pool.cvrs = cvrsM
        }
        println("cvrsAmended = $cvrsAmended")
        println("contestAmended")
        increaseNc.toSortedMap().forEach { println("  $it") }

        // create amended card Pools; dont need to hold onto the list of cvrs
        val cardPoolsM: MutableMap<String, CardPoolFromCvrs> = mutableMapOf()
        val cvrIterM = CardPoolList(cardPoolsU.values).iterator()
        while (cvrIterM.hasNext()) {
            val cvrExport: CvrExport = cvrIterM.next()
            val pool = cardPoolsM.getOrPut(cvrExport.poolKey()) {
                CardPoolNs(cvrExport.poolKey(), cardPoolsM.size + 1, infos)
            }
            pool.accumulateVotes(cvrExport.toCvr())
        }

        return Pair(cardPoolsM.values.toList(), increaseNc)
    }


    override fun cardPools() = cardPoolsNotUnpooled
    override fun contestsUA() = contestsOA

    override fun allCvrs() = null
    override fun testMvrs() = null

    override fun cvrExport() = CloseableIterable { cvrExportCsvIterator(cvrExportCsv) }
}

// keep all the CVRS, which we can use to calculate average assort.
class CardPoolNs( poolName: String, poolId: Int, contestInfos: Map<Int, ContestInfo>) : CardPoolFromCvrs(poolName, poolId, contestInfos) {
    // TODO keeping all the CvrExport in memory here. better to write them back ??
    var cvrs = mutableListOf<CvrExport>()
}

class CardPoolList(val cardPools: Collection<CardPoolNs>): Iterable<CvrExport> {

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
        val bitter = pool.cvrs.iterator()
        override fun hasNext(): Boolean = bitter.hasNext()
        override fun next(): CvrExport = bitter.next()
        override fun close() {}
    }
}

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

    CreateAudit("sf2024", topdir, auditConfig, election)
    println("createSfElectionNoStyles took $stopwatch")
}
