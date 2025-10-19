package org.cryptobiotic.rlauxe.audit

import io.github.oshai.kotlinlogging.KotlinLogging
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
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIterator
import org.cryptobiotic.rlauxe.persist.csv.writeAuditableCardCsvFile
import org.cryptobiotic.rlauxe.persist.json.writeAuditConfigJsonFile
import org.cryptobiotic.rlauxe.persist.json.writeCardPoolsJsonFile
import org.cryptobiotic.rlauxe.persist.json.writeContestsJsonFile
import org.cryptobiotic.rlauxe.persist.validateOutputDirOfFile
import org.cryptobiotic.rlauxe.util.CloseableIterable
import org.cryptobiotic.rlauxe.util.Prng
import org.cryptobiotic.rlauxe.util.SortMerge
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.cleanCsvString
import org.cryptobiotic.rlauxe.util.createZipFile
import org.cryptobiotic.rlauxe.verify.VerifyContests
import kotlin.collections.forEach
import kotlin.io.path.Path

interface CreateElectionIF {
    fun contestsUA(): List<ContestUnderAudit>
    fun cardPools(): List<CardPoolIF>? // only if OneAudit

    fun allCvrs(): Pair<List<Cvr>, List<Cvr>>  // (cvrs, mvrs) including phantoms
    fun cvrExport(): Pair<CloseableIterable<CvrExport>, List<Cvr>>? // (cvrs, extra) dont include phantoms
}

class CreateAudit(val name: String, val topdir: String, val auditConfig: AuditConfig, election: CreateElectionIF, auditdir: String? = null, clear: Boolean = true) {
    private val logger = KotlinLogging.logger("CreateAudit-$name")

    val auditDir = auditdir ?: "$topdir/audit"
    val stopwatch = Stopwatch()

    init {
        if (clear) clearDirectory(Path(auditDir))

        val publisher = Publisher(auditDir)
        writeAuditConfigJsonFile(auditConfig, publisher.auditConfigFile())
        logger.info{"writeAuditConfigJsonFile to ${publisher.auditConfigFile()}\n  $auditConfig"}

        val cardPools = if (auditConfig.isOA) {
                val pools = election.cardPools()!!
                writeCardPoolsJsonFile(pools, publisher.cardPoolsFile())
                logger.info { "write ${pools.size} cardPools, to ${publisher.cardPoolsFile()}" }
                pools
            } else null
        val poolNameToId = if (cardPools == null) null else cardPools.associate { it.poolName to it.poolId }

        val contestsUA = election.contestsUA()
        if (auditConfig.isOA) {
            addOAClcaAssortersFromMargin(contestsUA as List<OAContestUnderAudit>, cardPools!!)
        } else {
            contestsUA.forEach { it.addClcaAssertionsFromReportedMargin() }
        }
        logger.info { "added ClcaAssertions from reported margin " }

        val (allCvrs, testMvrs) = election.allCvrs()
        if (allCvrs.isNotEmpty()) {
            val sortedCards = createSortedCardsAllCvrs(allCvrs, auditConfig.seed)
            writeAuditableCardCsvFile(sortedCards, publisher.cardsCsvFile())
            createZipFile(publisher.cardsCsvFile(), delete = false)
            logger.info{"write ${sortedCards.size} cvrs to ${publisher.cardsCsvFile()}"}

            // save the sorted testMvrs if they exist
            if (testMvrs.isNotEmpty()) {
                require (testMvrs.size == sortedCards.size)

                val mvrCards = sortedCards.map { AuditableCard.fromCvr(testMvrs[it.index], it.index, it.prn) }
                val mvrFile = publisher.testMvrsFile()
                validateOutputDirOfFile(mvrFile)
                writeAuditableCardCsvFile(mvrCards, mvrFile)
                logger.info{"write ${testMvrs.size} test mvrs to $mvrFile"}
            }

        } else {
            val (cvrIter, extra) = election.cvrExport()!!
            val phantoms = makePhantomCvrs(contestsUA.map { it.contest } )
            writeSortedCardsExternalSort(topdir=topdir, cvrIter, extra + phantoms, auditConfig.seed, poolNameToId)
        }

        // this may change the auditStatus to misformed
        val verifier = VerifyContests(auditDir)
        val resultsv = verifier.verify(contestsUA, false)
        if (resultsv.hasErrors) {
            logger.warn{ resultsv.toString() }
        }

        // sf only writes these:
        // val auditableContests: List<OAContestUnderAudit> = contestsUA.filter { it.preAuditStatus == TestH0Status.InProgress }

        // write contests
        writeContestsJsonFile(contestsUA, publisher.contestsFile())
        logger.info{"write ${contestsUA.size} contests to ${publisher.contestsFile()}"}
    }

    fun writeSortedCardsExternalSort(topdir: String, cards: CloseableIterable<CvrExport>, phantoms: List<Cvr>, seed: Long, poolNameToId: Map<String, Int>?) {
        val publisher = Publisher("$topdir/audit")
        SortMerge(scratchDirectory = "$topdir/sortChunks", publisher.cardsCsvFile(), seed, poolNameToId, showPoolVotes = auditConfig.isClca)
            .run(cards.iterator(), phantoms)
        createZipFile(publisher.cardsCsvFile(), delete = false)

        // kludge; test Mvrs are the same as the Cvrs but the votes are shown
        validateOutputDirOfFile(publisher.testMvrsFile())
        SortMerge(scratchDirectory = "$topdir/sortChunks", publisher.testMvrsFile(), seed, poolNameToId, showPoolVotes = true)
            .run(cards.iterator(), phantoms)
        createZipFile(publisher.testMvrsFile(), delete = false)
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

// The pooled cvrs dont have votes associated with them
fun createCvrsFromPools(pools: List<CardPoolIF>) : List<Cvr> {
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
    val totalRedactedBallots = pools.sumOf { it.ncards() }
    require(cvrs.size == totalRedactedBallots)
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