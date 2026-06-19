package org.cryptobiotic.rlauxe.workflow

import com.github.michaelbull.result.unwrap
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.betting.TestH0Status
import org.cryptobiotic.rlauxe.core.CvrIF
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.csv.readCardsAndMergeToList
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIterator
import org.cryptobiotic.rlauxe.persist.csv.writeCardCsvFile
import org.cryptobiotic.rlauxe.persist.json.readSamplePrnsJsonFile
import org.cryptobiotic.rlauxe.util.CloseableIterable
import org.cryptobiotic.rlauxe.util.Closer
import org.cryptobiotic.rlauxe.util.ErrorMessages
import org.cryptobiotic.rlauxe.verify.verifyMvrCardPairs

// assumes that the mvrs have been set externally into the election record, eg by EnterMvrsCli.
// skip writing when doing runRoundAgain
open class PersistedMvrManager(val auditRecord: AuditRecord, val mvrWrite: Boolean = true): MvrManager {
    val config = auditRecord.config
    val contestsUA = auditRecord.contests.filter { it.preAuditStatus == TestH0Status.InProgress } // note: only InProgress
    val publisher = Publisher(auditRecord.location)

    val styles by lazy { auditRecord.readCardStyles() ?: auditRecord.readCardPools() } // styles are preferred
    val sortedManifest by lazy { auditRecord.readSortedManifest(styles) }
    val auditableCards: CloseableIterator<AuditableCard> by lazy {  sortedManifest.cards.iterator() }

    //// problem is that you lose the cache when you close and open the AuditRecord between samplings
    val cachedCards : List<SamplingCardIF>? by lazy {
        val scards = auditRecord.readSamplingCards(styles)
        if (scards == null) null else {
            val cards = mutableListOf<SamplingCardIF>()
            val siter = scards.iterator()
            while (siter.hasNext()) {
                cards.add(siter.next())
            }
            cards.toList()
        }
    }

    override fun samplingCards(): CloseableIterable<SamplingCardIF> {
        return if (cachedCards != null) CloseableIterable { cachedCards!!.iterator() } else sortedManifest().cards
    }

    override fun auditdir() = auditRecord.location

    override fun sortedManifest() = sortedManifest
    override fun styles() = styles

    override fun pools() = auditRecord.readCardPools() // could test if batches are cardPools
    override fun countyPools() = if (styles == null) null else auditRecord.readCountyCardPools(styles!!)
    override fun countyCvrPools() = if (styles == null) null else auditRecord.readCountyCvrPools(styles!!)

    override fun makeMvrCardPairsForRound(round: Int): List<Pair<CvrIF, AuditableCard>>  {
        val mvrsForRound = readMvrsForRound(round)
        val sampleNumbers = mvrsForRound.map { it.prn }

        val sampledCards = findSamples(sampleNumbers, auditableCards)
        require(sampledCards.size == mvrsForRound.size)
        val mvrCardPairs = mvrsForRound.zip(sampledCards)

        if (checkValidity) {
            val errs = ErrorMessages("PersistedMvrManager")
            verifyMvrCardPairs(mvrCardPairs, errs)
            if (errs.hasErrors()) {
                logger.error{ " ${auditRecord.showName()} verifyMvrCardPairs $errs" }
            }
        }

        if (mvrWrite) {
            val countCards = writeCardCsvFile(Closer(sampledCards.iterator()), publisher.sampleCardsFile(round)) // sampleCards
            logger.info { "write ${countCards} cards to ${publisher.sampleCardsFile(round)}" }
        }

        return mvrCardPairs
    }

    // TODO hide this: dont use this directly, use workflow.mvrManager().writeMvrsForRound(roundIdx)
    // uses private/sortedMvrsFile.cvs
    override fun writeMvrsForRound(round: Int): Int {
        val resultSamples = readSamplePrnsJsonFile(publisher.samplePrnsFile(round))
        if (resultSamples.isErr) logger.error{"$resultSamples"}
        require(resultSamples.isOk)
        val sampleNumbers = resultSamples.unwrap()

        val mergedMvrIter = readCardsCsvIterator(publisher.sortedMvrsFile(), styles)
        val sampledMvrs = findSamples(sampleNumbers, mergedMvrIter)
        require(sampledMvrs.size == sampleNumbers.size)

        // validate
        sampledMvrs.forEachIndexed { index, mvr ->
            require(mvr.prn() == sampleNumbers[index])
        }

        return writeCardCsvFile(Closer(sampledMvrs.iterator()), publisher.sampleMvrsFile(round))
    }

    // the sampleMvrsFile is added externally for real audits, and by MvrManagerTestFromRecord for test audits
    // it must be in the same order as the sorted cards
    // it is placed into publisher.sampleMvrsFile(round), and this method just reads from that file.
    // return complete list of mvrs used for this round
    private fun readMvrsForRound(round: Int): List<AuditableCard> {
        return readCardsAndMergeList(publisher.sampleMvrsFile(round))
    }

    // TODO findSamples wantst he mvrs in sorted order
    fun enterMvrsForRound(round: Int, mvrs: CloseableIterable<AuditableCard>, errs: ErrorMessages): Boolean {
        val sampledPrnsResult = readSamplePrnsJsonFile(publisher.samplePrnsFile(round))
        if (sampledPrnsResult.isErr) {
            logger.error{ "$sampledPrnsResult" } // needed?
            errs.addNested(sampledPrnsResult.component2()!!)
            return false
        }

        require(sampledPrnsResult.isOk)
        val sampledPrns = sampledPrnsResult.unwrap()

        val sampledMvrs = findSamples(sampledPrns, mvrs.iterator()) // what does this do
        require(sampledMvrs.size == sampledPrns.size)

        // validate
        sampledMvrs.forEachIndexed { index, mvr ->
            require(mvr.prn() == sampledPrns[index])
        }

        // TODO NEXTASK is this all prns or just new? humans want just new
        writeCardCsvFile(sampledMvrs , publisher.sampleMvrsFile(round))
        logger.info{"enterMvrs write sampledMvrs to '${publisher.sampleMvrsFile(round)}' for round $round"}

        return true
    }

    fun readCardsAndMerge(filename: String): CloseableIterator<AuditableCard> {
        return readCardsCsvIterator(filename, styles)
    }

    fun readCardsAndMergeList(filename: String): List<AuditableCard> {
        return readCardsAndMergeToList(filename, styles)
    }

    companion object {
        private val logger = KotlinLogging.logger("PersistedMvrManager")
        private val checkValidity = true
    }
}

/* for viewer
fun readMvrsForRound(publisher: Publisher, roundIdx: Int): List<AuditableCard> {
    return readCardCsvFile(publisher.sampleMvrsFile(roundIdx))
} */


