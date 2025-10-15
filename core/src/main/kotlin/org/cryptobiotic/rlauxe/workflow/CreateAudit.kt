package org.cryptobiotic.rlauxe.workflow

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.AuditConfig
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.CvrIteratorAdapter
import org.cryptobiotic.rlauxe.audit.checkContestsCorrectlyFormed
import org.cryptobiotic.rlauxe.audit.checkContestsWithCvrs
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.CvrExport
import org.cryptobiotic.rlauxe.estimate.makePhantomCvrs
import org.cryptobiotic.rlauxe.oneaudit.BallotPool
import org.cryptobiotic.rlauxe.oneaudit.CardPoolIF
import org.cryptobiotic.rlauxe.oneaudit.CardPoolWithBallotStyle
import org.cryptobiotic.rlauxe.oneaudit.OAContestUnderAudit
import org.cryptobiotic.rlauxe.oneaudit.addOAClcaAssortersFromMargin
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.clearDirectory
import org.cryptobiotic.rlauxe.persist.csv.poolNameToId
import org.cryptobiotic.rlauxe.persist.csv.writeAuditableCardCsvFile
import org.cryptobiotic.rlauxe.persist.csv.writeBallotPoolCsvFile
import org.cryptobiotic.rlauxe.persist.json.writeAuditConfigJsonFile
import org.cryptobiotic.rlauxe.persist.json.writeContestsJsonFile
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.Prng
import org.cryptobiotic.rlauxe.util.SortMerge
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.cleanCsvString
import org.cryptobiotic.rlauxe.util.createZipFile
import kotlin.collections.forEach
import kotlin.collections.plus
import kotlin.io.path.Path

interface ElectionIF {
    fun makeCardPools(): List<CardPoolIF>
    fun makeContestsUA(hasStyles: Boolean): List<ContestUnderAudit>
    fun makeCvrs(): List<Cvr>
    fun cvrExport(): CloseableIterator<CvrExport>
    fun hasCvrExport() : Boolean
}

class CreateAudit(val name: String, val topdir: String, auditConfig: AuditConfig, clear: Boolean = true, election: ElectionIF) {
    private val logger = KotlinLogging.logger("CreateAudit-$name")

    val auditDir = "$topdir/audit"
    val stopwatch = Stopwatch()
    val isOA : Boolean

    init {
        if (clear) clearDirectory(Path(auditDir))

        val publisher = Publisher(auditDir)
        writeAuditConfigJsonFile(auditConfig, publisher.auditConfigFile())
        logger.info{"writeAuditConfigJsonFile to ${publisher.auditConfigFile()}\n  $auditConfig"}
        isOA = auditConfig.auditType == AuditType.ONEAUDIT

        val cardPools = if (isOA) election.makeCardPools() else emptyList()
        val ballotPools =  if (isOA) {
            val pools = cardPools.map { it.toBallotPools() }.flatten()
            writeBallotPoolCsvFile(pools, publisher.ballotPoolsFile())
            logger.info { "write ${cardPools.size} cardPools, ${pools.size} ballotPools to ${publisher.ballotPoolsFile()}" }
            pools
        } else null

        val contestsUA = election.makeContestsUA(auditConfig.hasStyles)
        if (isOA) {
            addOAClcaAssortersFromMargin(contestsUA as List<OAContestUnderAudit>, cardPools.associate { it.poolId to it })
        } else {
            contestsUA.forEach { it.addClcaAssertionsFromReportedMargin() }
        }

        val phantoms = makePhantomCvrs(contestsUA.map { it.contest } )
        if (!election.hasCvrExport()) {
            val allCvrs =  election.makeCvrs() + phantoms
            val cards = createSortedCardsFromPools(allCvrs, auditConfig.seed, cardPools as List<CardPoolWithBallotStyle>)
            writeAuditableCardCsvFile(cards, publisher.cardsCsvFile())
            createZipFile(publisher.cardsCsvFile(), delete = false)
            logger.info{"write ${cards.size} cvrs to ${publisher.cardsCsvFile()}"}

            val state = checkContestsWithCvrs(contestsUA, CvrIteratorAdapter(cards.iterator()), ballotPools=ballotPools, show = false)
            logger.info { state }

        } else {
            // fun writeSortedCardsExternalSort(scratchDirectory: String, auditDir: String, cardIter: CloseableIterator<CvrExport>, seed: Long, ballotPools: List<BallotPool>?) {
            writeSortedCardsExternalSort(topdir=topdir, election.cvrExport(), phantoms, auditConfig.seed, ballotPools)
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

// for a real audit, there are no votes
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

fun writeSortedCardsExternalSort(topdir: String, cardIter: CloseableIterator<CvrExport>, phantoms: List<Cvr>, seed: Long, ballotPools: List<BallotPool>?) {
    val publisher = Publisher("$topdir/audit")
    val pools = ballotPools?.poolNameToId() // all we need is to know what the id is for each pool, so we can assign

    SortMerge(scratchDirectory = "$topdir/sortChunks", publisher.cardsCsvFile(), seed = seed, pools = pools).run2(cardIter, phantoms)
    createZipFile(publisher.cardsCsvFile(), delete = false)
}