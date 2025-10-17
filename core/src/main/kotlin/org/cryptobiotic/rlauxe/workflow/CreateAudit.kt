package org.cryptobiotic.rlauxe.workflow

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.AuditConfig
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.checkContestsCorrectlyFormed
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.CvrExport
import org.cryptobiotic.rlauxe.estimate.makePhantomCvrs
import org.cryptobiotic.rlauxe.oneaudit.CardPoolIF
import org.cryptobiotic.rlauxe.oneaudit.CardPoolWithBallotStyle
import org.cryptobiotic.rlauxe.oneaudit.OAContestUnderAudit
import org.cryptobiotic.rlauxe.oneaudit.addOAClcaAssortersFromMargin
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.clearDirectory
import org.cryptobiotic.rlauxe.persist.csv.writeAuditableCardCsvFile
import org.cryptobiotic.rlauxe.persist.json.writeAuditConfigJsonFile
import org.cryptobiotic.rlauxe.persist.json.writeCardPoolsJsonFile
import org.cryptobiotic.rlauxe.persist.json.writeContestsJsonFile
import org.cryptobiotic.rlauxe.persist.validateOutputDirOfFile
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.Prng
import org.cryptobiotic.rlauxe.util.SortMerge
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.cleanCsvString
import org.cryptobiotic.rlauxe.util.createZipFile
import kotlin.collections.forEach
import kotlin.io.path.Path

interface CreateElectionIF {
    fun makeCardPools(): List<CardPoolIF>
    fun makeContestsUA(hasStyles: Boolean): List<ContestUnderAudit>
    fun allCvrs(): List<Cvr>
    fun cvrExport(): CloseableIterator<CvrExport>
    fun hasCvrExport() : Boolean
    fun testMvrs(): List<Cvr>?
}

class CreateAudit(val name: String, val topdir: String, auditConfig: AuditConfig, election: CreateElectionIF, auditdir: String? = null, clear: Boolean = true) {
    private val logger = KotlinLogging.logger("CreateAudit-$name")

    val auditDir = auditdir ?: "$topdir/audit"
    val stopwatch = Stopwatch()
    val isOA : Boolean

    init {
        if (clear) clearDirectory(Path(auditDir))

        val publisher = Publisher(auditDir)
        writeAuditConfigJsonFile(auditConfig, publisher.auditConfigFile())
        logger.info{"writeAuditConfigJsonFile to ${publisher.auditConfigFile()}\n  $auditConfig"}
        isOA = auditConfig.auditType == AuditType.ONEAUDIT

        val cardPools = if (isOA) {
            val pools = election.makeCardPools()
            writeCardPoolsJsonFile(pools, publisher.cardPoolsFile())
            logger.info { "write ${pools.size} cardPools, to ${publisher.cardPoolsFile()}" }
            pools
        } else null
        val poolNameToId = if (cardPools == null) null else cardPools.associate { it.poolName to it.poolId }

        val contestsUA = election.makeContestsUA(auditConfig.hasStyles)
        if (isOA) {
            addOAClcaAssortersFromMargin(contestsUA as List<OAContestUnderAudit>, cardPools!!)
        } else {
            contestsUA.forEach { it.addClcaAssertionsFromReportedMargin() }
        }

        if (!election.hasCvrExport()) {
            val sortedCards = createSortedCardsAllCvrs(election.allCvrs(), auditConfig.seed)
            writeAuditableCardCsvFile(sortedCards, publisher.cardsCsvFile())
            createZipFile(publisher.cardsCsvFile(), delete = false)
            logger.info{"write ${sortedCards.size} cvrs to ${publisher.cardsCsvFile()}"}

            // save the sorted testMvrs if they exist
            val testMvrs = election.testMvrs()
            if (testMvrs != null) {
                val mvrCards = sortedCards.map { AuditableCard.fromCvr(testMvrs[it.index], it.index, it.prn) }
                val mvrFile = publisher.testMvrsFile()
                validateOutputDirOfFile(mvrFile)
                writeAuditableCardCsvFile(mvrCards, mvrFile)
                logger.info{"write ${testMvrs.size} test mvrs to $mvrFile"}
            }

        } else {
            val phantoms = makePhantomCvrs(contestsUA.map { it.contest } )
            writeSortedCardsExternalSort(topdir=topdir, election.cvrExport(), phantoms, auditConfig.seed, poolNameToId)
        }

        // corla
        //   val cards =  createSortedCards(allCvrs, auditConfig.seed)
        // boulder
        //   val cards = createSortedCardsFromPools(allCvrs, election.cardPools, auditConfig.seed)
        // sf
        //  SortMerge(scratchDirectory = working, "$auditDir/$sortedCardsFile", seed = seed, pools = pools).run(cvrExportCsv)

        checkContestsCorrectlyFormed(auditConfig, contestsUA)

        // election.checkContests(contestsUA)

        // sf only writes these:
        // val auditableContests: List<OAContestUnderAudit> = contestsUA.filter { it.preAuditStatus == TestH0Status.InProgress }

        // write contests
        writeContestsJsonFile(contestsUA, publisher.contestsFile())
        logger.info{"write ${contestsUA.size} contests to ${publisher.contestsFile()}"}
        logger.info{"took = $stopwatch\n"}
    }
}

fun createSortedCardsFromPools(cvrs: List<Cvr>, seed: Long, pools: List<CardPoolWithBallotStyle>) : List<AuditableCard> {
    val prng = Prng(seed)
    val cards = mutableListOf<AuditableCard>()
    var idx = 0
    cvrs.forEach { cards.add(AuditableCard.fromCvr(it, idx++, prng.next())) }

    // add the pool votes
    pools.forEach { pool ->
        val cleanName = cleanCsvString(pool.poolName)
        repeat(pool.ncards()) { poolIndex ->
            cards.add(
                AuditableCard(
                    location = "pool${cleanName} card ${poolIndex + 1}",
                    index = idx++,
                    prn = prng.next(),
                    phantom = false,
                    contests = pool.contests(),
                    votes = null,
                    poolId = pool.poolId
                )
            )
        }
    }

    // or use external memory sort
    return cards.sortedBy { it.prn }
}

// The cvrs dont have votes associated with them
fun createCvrsFromPools(pools: List<CardPoolWithBallotStyle>) : List<Cvr> {
    val cvrs = mutableListOf<Cvr>()

    pools.forEach { pool ->
        val cleanName = cleanCsvString(pool.poolName)
        repeat(pool.ncards()) { poolIndex ->
            cvrs.add(
                Cvr(
                    id = "pool${cleanName} card ${poolIndex + 1}",
                    votes = pool.contests().associate{ it to IntArray(0) },
                    phantom = false,
                    poolId = pool.poolId
                )
            )
        }
    }

    return cvrs
}


// for a real audit, there are no votes
fun createSortedCardsAllCvrs(allCvrs: List<Cvr>, seed: Long) : List<AuditableCard> {
    val prng = Prng(seed)
    val cards = mutableListOf<AuditableCard>()
    var idx = 0
    allCvrs.forEach { cards.add(AuditableCard.fromCvr(it, idx++, prng.next())) }

    // or use external memory sort
    return cards.sortedBy { it.prn }
}

fun writeSortedCardsExternalSort(topdir: String, cardIter: CloseableIterator<CvrExport>, phantoms: List<Cvr>, seed: Long, poolNameToId: Map<String, Int>?) {
    val publisher = Publisher("$topdir/audit")
    SortMerge(scratchDirectory = "$topdir/sortChunks", publisher.cardsCsvFile(), seed = seed, poolNameToId = poolNameToId).run2(cardIter, phantoms)
    createZipFile(publisher.cardsCsvFile(), delete = false)
}