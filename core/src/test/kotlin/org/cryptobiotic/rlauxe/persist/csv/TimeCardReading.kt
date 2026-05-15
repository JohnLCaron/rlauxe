package org.cryptobiotic.rlauxe.persist.csv

import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.CardWithBatchName
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.util.CloseableIterable
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.workflow.PersistedMvrManager
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import kotlin.test.Test

class TimeCardReading {

    @Test
    fun timeReadCardsCsvIterator () {
        val topdir = "${testdataDir}/cases/corla/consistent"

        val stopwatch = Stopwatch()
        var ncards = 0

        val publisher = Publisher("$topdir/audit")
        val cardIter: CloseableIterator<CardWithBatchName> = readCardsCsvIterator(publisher.cardManifestFile())
        while (cardIter.hasNext()) { //  && ncards < 1000000) {
            val card = cardIter.next()
            ncards++
        }

        val msPer = stopwatch.elapsed(TimeUnit.MILLISECONDS) / ncards.toDouble()
        val secPer = msPer / 1000

        println("timeCardReading ncards = $ncards, took $stopwatch = $msPer ms/card")
        val totalCards = 4982747
        println("time to read all cards = ${dfn(totalCards * secPer, 3)} secs")
    }

    @Test
    fun timeWithLargeBuffer () {
        val topdir = "${testdataDir}/cases/corla/consistent"

        val stopwatch = Stopwatch()
        var ncards = 0

        val publisher = Publisher("$topdir/audit")

        val bufferSize = 100_000
        val inputStream = File(publisher.cardManifestFile()).inputStream()
        val cardIter = IteratorCardsCsvStream(inputStream, bufferSize)

        while (cardIter.hasNext()) {
            val card = cardIter.next()
            ncards++
        }

        val msPer = stopwatch.elapsed(TimeUnit.MILLISECONDS) / ncards.toDouble()
        val secPer = msPer / 1000

        println("timeWithLargeBuffer ($bufferSize):  ncards = $ncards, took $stopwatch = $msPer ms/card")
        val totalCards = 4982747
        println("time to read all cards = ${dfn(totalCards * secPer, 3)} secs")
    }

    @Test
    fun timeReadRawBytes () {
        val topdir = "${testdataDir}/cases/corla/clca"

        val stopwatch = Stopwatch()
        var totalRead = 0L

        val publisher = Publisher("$topdir/audit")
        val file = File(publisher.cardManifestFile())
        val inputStream = file.inputStream()
        val fileLength = file.length()

        val bufferSize = 100_000
        val byteBuffer = ByteArray(bufferSize)
        val bufferedStream = BufferedInputStream(inputStream, bufferSize)
        while (true) {
            val bytesRead = bufferedStream.read(byteBuffer)
            totalRead += bytesRead
            if (bytesRead <= 0) break
        }

        println("timeReadRawBytes with buffer= ($bufferSize), fileLength=$fileLength  fillRead = ${totalRead.equals(fileLength)}, took ${stopwatch.elapsed(TimeUnit.MILLISECONDS)} msec")
    }

    @Test
    fun timeConsistentSampling () {
        // val styles: List<CountyStyles> = Colorado2024Input.countyStyles

        val topdir = "${testdataDir}/cases/corla/consistent"
        val countyAudit = AuditRecord.readWithResult("$topdir/audit").unwrap()
        // val contestNameMap = countyAudit.contests.associate { it.contest.info().name to it }
        val mvrManager = PersistedMvrManager(countyAudit)
        val cards: CloseableIterable<AuditableCard> = mvrManager.sortedManifest().cards
        val contestsUA = mvrManager.contestsUA

        val stopwatch = Stopwatch()

        var ncards = 0
        var included = 0
        var newMvrs = 0
        var haveSampleSize = 0
        var haveNewSampleSize = 0
        val previousSamples = emptySet<Long>()

        val sortedCardIter = cards.iterator()
        while ( sortedCardIter.hasNext() && ncards < 100000) {
            // get the next card in sorted order
            val card = sortedCardIter.next()
            ncards++

            var include = false
            contestsUA.forEach { contest ->
                // does this contest want this card ?
                if (card.hasContest(contest.id)) {
                    include = true
                    included++
                }
            }

            /* if (include) {
                if (!previousSamples.contains(card.prn))
                    newMvrs++
            } */

            contestsUA.forEach { contest ->
                if (card.hasContest(contest.id)) {
                    if (include) {
                        haveSampleSize++
                        // TODO do all at once at the end for speed ??
                        //if (!previousSamples.contains(card.prn)) {
                        //    haveNewSampleSize++
                        //}
                    }
                }
            }
        }

        println("ncards = $ncards, included = $included that took $stopwatch= ${stopwatch.elapsed(TimeUnit.MILLISECONDS)/ncards.toDouble()} ms/card")
    }
}