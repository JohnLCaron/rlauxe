package org.cryptobiotic.rlauxe.audit

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.oneaudit.CardPoolIF
import org.cryptobiotic.rlauxe.oneaudit.OAContestUnderAudit
import org.cryptobiotic.rlauxe.oneaudit.addOAClcaAssortersFromMargin
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.clearDirectory
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIterator
import org.cryptobiotic.rlauxe.persist.csv.writeAuditableCardCsvFile
import org.cryptobiotic.rlauxe.persist.existsOrZip
import org.cryptobiotic.rlauxe.persist.json.writeAuditConfigJsonFile
import org.cryptobiotic.rlauxe.persist.json.writeCardPoolsJsonFile
import org.cryptobiotic.rlauxe.persist.json.writeContestsJsonFile
import org.cryptobiotic.rlauxe.persist.validateOutputDirOfFile
import org.cryptobiotic.rlauxe.util.CloseableIterable
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.Closer
import org.cryptobiotic.rlauxe.util.Prng
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.createZipFile
import kotlin.collections.forEach
import kotlin.io.path.Path

interface CreateElection2IF {
    fun contestsUA(): List<ContestUnderAudit>
    fun cardPools(): List<CardPoolIF>? // only if OneAudit

    fun hasTestMvrs(): Boolean
    fun allCvrs(): Pair<CloseableIterable<AuditableCard>, CloseableIterable<AuditableCard>>  // (cvrs, mvrs) including phantoms
}

private val logger = KotlinLogging.logger("CreateAudit")

class CreateAudit2(val name: String, val topdir: String, val config: AuditConfig, election: CreateElection2IF, auditdir: String? = null, clear: Boolean = true) {

    val auditDir = auditdir ?: "$topdir/audit"
    val stopwatch = Stopwatch()

    init {
        if (clear) clearDirectory(Path(auditDir))

        val publisher = Publisher(auditDir)
        writeAuditConfigJsonFile(config, publisher.auditConfigFile())
        logger.info{"writeAuditConfigJsonFile to ${publisher.auditConfigFile()}\n  $config"}

        val cardPools = if (config.isOA) {
                val pools = election.cardPools()!!
                writeCardPoolsJsonFile(pools, publisher.cardPoolsFile())
                logger.info { "write ${pools.size} cardPools, to ${publisher.cardPoolsFile()}" }
                pools
            } else null

        val contestsUA = election.contestsUA()
        if (config.isOA) {
            addOAClcaAssortersFromMargin(contestsUA as List<OAContestUnderAudit>, cardPools!!)
        } else {
            contestsUA.forEach { it.addClcaAssertionsFromReportedMargin() }
        }
        logger.info { "added ClcaAssertions from reported margin " }

        val (cvrs, mvrs) = election.allCvrs()
        val countCvrs = writeAuditableCardCsvFile(cvrs.iterator(), publisher.cardManifestFile())
        createZipFile(publisher.cardManifestFile(), delete = true)
        logger.info{"write ${countCvrs} cards to ${publisher.cardManifestFile()}"}

        // TODO check if empty
        if (election.hasTestMvrs()) {
            validateOutputDirOfFile(publisher.testMvrsFile())
            val countMvrs = writeAuditableCardCsvFile(mvrs.iterator(), publisher.testMvrsFile())
            createZipFile(publisher.testMvrsFile(), delete = true)
            logger.info { "write ${countMvrs} cards to ${publisher.testMvrsFile()}" }
        }

            /* save the sorted testMvrs if they exist
            if (testMvrs.isNotEmpty()) {
                require (testMvrs.size == sortedCards.size)

                val mvrCards = sortedCards.map { AuditableCard.fromCvr(testMvrs[it.index], it.index, it.prn) }
                val mvrFile = publisher.testMvrsFile()
                validateOutputDirOfFile(mvrFile)
                writeAuditableCardCsvFile(mvrCards, mvrFile)
                logger.info{"write ${testMvrs.size} test mvrs to $mvrFile"}
            } */

       /*  } else {
            val (cvrIter, extra) = election.cvrExport()!!
            val phantoms = makePhantomCvrs(contestsUA.map { it.contest } )
            writeSortedCardsExternalSort(topdir=topdir, cvrIter, extra + phantoms, config.seed, poolNameToId)
        } */

        /* this may change the auditStatus to misformed
        val verifier = VerifyContests(auditDir)
        val resultsv = verifier.verify(contestsUA, false)
        if (resultsv.hasErrors) {
            logger.warn{ resultsv.toString() }
        } */

        // sf only writes these:
        // val auditableContests: List<OAContestUnderAudit> = contestsUA.filter { it.preAuditStatus == TestH0Status.InProgress }

        // write contests
        writeContestsJsonFile(contestsUA, publisher.contestsFile())
        logger.info{"write ${contestsUA.size} contests to ${publisher.contestsFile()}"}
    }
}

fun writeSortedCardsInternalSort(publisher: Publisher, seed: Long) {
    val cvrs = readCardsCsvIterator(publisher.cardManifestFile())
    val sortedCvrs = createSortedCards(cvrs, seed)
    val countCvrs = writeAuditableCardCsvFile(Closer(sortedCvrs.iterator()), publisher.sortedCardsFile())
    createZipFile(publisher.sortedCardsFile(), delete = true)
    logger.info{"write ${countCvrs} cards to ${publisher.sortedCardsFile()}"}

    if (existsOrZip(publisher.testMvrsFile())) {
        val mvrs = readCardsCsvIterator(publisher.testMvrsFile())
        val sortedMvrs = createSortedCards(mvrs, seed)
        val countMvrs = writeAuditableCardCsvFile(Closer(sortedMvrs.iterator()), publisher.sortedMvrsFile())
        createZipFile(publisher.sortedMvrsFile(), delete = true)
        logger.info{"write ${countMvrs} cards to ${publisher.sortedMvrsFile()}"}
    }
}

/*
fun writeSortedCardsExternalSort(topdir: String, seed: Long, poolNameToId: Map<String, Int>?) {
    val publisher = Publisher("$topdir/audit")
    SortMerge(scratchDirectory = "$topdir/sortChunks", publisher.sortedCardsFile(), seed, poolNameToId, showPoolVotes = config.isClca)
        .run(cards.iterator(), phantoms)
    createZipFile(publisher.sortedCardsFile(), delete = false)

    // kludge; test Mvrs are the same as the Cvrs but the votes are shown
    validateOutputDirOfFile(publisher.testMvrsFile())
    SortMerge(scratchDirectory = "$topdir/sortChunks", publisher.testMvrsFile(), seed, poolNameToId, showPoolVotes = true)
        .run(cards.iterator(), phantoms)
    createZipFile(publisher.testMvrsFile(), delete = false)
}

fun createSortedCardsFromPools2(cvrs: List<Cvr>, seed: Long, pools: List<CardPoolWithBallotStyle>) : List<AuditableCard> {
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
fun createCvrsFromPools2(pools: List<CardPoolIF>) : List<Cvr> {
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
} */


// for a real audit, there are no votes
fun createSortedCards(unsortedCards: CloseableIterator<AuditableCard>, seed: Long) : List<AuditableCard> {
    val prng = Prng(seed)
    val cards = mutableListOf<AuditableCard>()
    unsortedCards.use { cardIter ->
        while (cardIter.hasNext()) {
            cards.add( cardIter.next().copy(prn = prng.next()))
        }
    }
    return cards.sortedBy { it.prn }
}