package org.cryptobiotic.rlauxe.persist.csv

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.AuditableCardIF
import org.cryptobiotic.rlauxe.audit.CardWithBatchName
import org.cryptobiotic.rlauxe.audit.MergeBatchesIntoCardManifestIterable
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.CountyAudit
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.protobuf.ProtoCardIterator
// import org.cryptobiotic.rlauxe.persist.protobuf.ProtobufCardIterator
import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.util.CloseableIterable
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.workflow.PersistedMvrManager
import java.io.BufferedInputStream
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.Test

class TimeCardReading {

    @Test
    fun timeReadRawBytes () {
        val topdir = "${testdataDir}/cases/corla/clca"

        val stopwatch = Stopwatch()
        var totalRead = 0L

        val publisher = Publisher("$topdir/audit")
        val file = File(publisher.sortedCardsFile())
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
    fun timeReadCardsCsvIterator () {
        val topdir = "${testdataDir}/cases/corla/consistent"

        val stopwatch = Stopwatch()
        var ncards = 0

        val publisher = Publisher("$topdir/audit")
        val inputStream = File(publisher.sortedCardsFile()).inputStream()

        val bufferSize = 8096 // 100_000
        val cardIter: CloseableIterator<CardWithBatchName> = IteratorCardsCsvStream(inputStream, 8096)
        while (cardIter.hasNext()) { //  && ncards < 1000000) {
            val card = cardIter.next()
            ncards++
        }

        val msPer = stopwatch.elapsed(TimeUnit.MILLISECONDS) / ncards.toDouble()
        val secPer = msPer / 1000

        println("IteratorCardsCsvStream buffer=$bufferSize ncards = $ncards, took $stopwatch = $msPer ms/card")
        val totalCards = 4982747
        println("time to read all cards = ${dfn(totalCards * secPer, 3)} secs")
        // IteratorCardsCsvStream buffer=8096 ncards = 4982786, took 53.92 s = 0.010820653345337328 ms/card
        // IteratorCardsCsvStream buffer=100000 ncards = 4982786, took 49.18 s = 0.00986797345902473 ms/card
        // IteratorCardsCsvStream buffer=8096 ncards = 4982786, took 49.47 s = 0.009926775904082575 ms/card
        // no difference
    }

    @Test
    fun timeReadSortedManifestFromCsv () {
        val topdir = "${testdataDir}/cases/corla/consistent"
        val auditRecord = AuditRecord.read(topdir) as AuditRecord
        val mvrManager = PersistedMvrManager(auditRecord)
        val publisher = Publisher("$topdir/audit")
        val styles = mvrManager.styles()

        val csvCards: CloseableIterable<AuditableCard> =
            MergeBatchesIntoCardManifestIterable(
                CloseableIterable { readCardsCsvIterator(publisher.sortedCardsFile()) },
                styles ?: emptyList(),
            )

        val stopwatch = Stopwatch()
        var ncards = 0

        // includes time to merge the styles
        val cardIter =  csvCards.iterator()
        while (cardIter.hasNext()) { //  && ncards < 1000000) {
            val card = cardIter.next()
            ncards++
        }

        val msPer = stopwatch.elapsed(TimeUnit.MILLISECONDS) / ncards.toDouble()
        val secPer = msPer / 1000

        println("timeReadSortedManifestFromCsv ncards = $ncards, took $stopwatch = $msPer ms/card")
        val totalCards = 4982747
        println("time to read all cards = ${dfn(totalCards * secPer, 3)} secs")
        // timeReadSortedManifestFromCsv ncards = 4982786, took 51.47 s = 0.010328157781610529 ms/card
        // timeReadSortedManifestFromCsv ncards = 4982786, took 53.13 s = 0.01066130473995873 ms/card
        // timeReadSortedManifestFromCsv ncards = 4982786, took 56.67 s = 0.011371148590366914 ms/card
        // timeReadSortedManifestFromCsv ncards = 4982786, took 51.65 s = 0.010363680077771753 ms/card
        // avg 53.25
        // maybe 53 / 49.4 = 1.07 = 7%  slower than timeReadCardsCsvIterator
    }

    /*
    @Test
    fun timeReadSortedCardsFromProtobuf () {
        val topdir = "${testdataDir}/cases/corla/consistent"
        val auditRecord = AuditRecord.read(topdir) as AuditRecord
        val mvrManager = PersistedMvrManager(auditRecord)
        val publisher = Publisher("$topdir/audit")
        val styles = mvrManager.styles()

        val bufferSize = 100_000
        val protobufFilename = publisher.cardsProtobufFile()

        // also merges the styles
        val protoCards = CloseableIterable { ProtobufCardIterator(protobufFilename, bufferSize, styles) }

        val stopwatch = Stopwatch()
        var ncards = 0

        val cardIter =  protoCards.iterator()
        while (cardIter.hasNext()) { //  && ncards < 1000000) {
            val card = cardIter.next()
            ncards++
        }

        val msPer = stopwatch.elapsed(TimeUnit.MILLISECONDS) / ncards.toDouble()
        val secPer = msPer / 1000

        println("timeReadSortedCardsFromProtobuf ncards = $ncards, took $stopwatch = $msPer ms/card")
        val totalCards = 4982747
        println("time to read all cards = ${dfn(totalCards * secPer, 3)} secs")
        // timeReadSortedManifestFromProtobuf ncards = 4982786, took 20.61 s = 0.004133631265721627 ms/card
        // timeReadSortedCardsFromProtobuf ncards = 4982786, took 20.53 s = 0.004118378754375564 ms/card
        // timeReadSortedCardsFromProtobuf ncards = 4982786, took 19.21 s = 0.003853868097084643 ms/card
        // avg 20.1
        // maybe 53 / 18.5 = 2.63 faster than timeReadCardsCsvIterator
    } */

    @Test
    fun timeReadSortedCardsFromProto () {
        val topdir = "${testdataDir}/cases/corla/consistent"
        val auditRecord = AuditRecord.read(topdir) as AuditRecord
        val mvrManager = PersistedMvrManager(auditRecord)
        val publisher = Publisher("$topdir/audit")
        val styles = mvrManager.styles()

        val bufferSize = 100_000
        val protoFilename = publisher.cardsProtoFile()

        // also merges the styles
        val protoCards = CloseableIterable { ProtoCardIterator(protoFilename, bufferSize, styles) }

        val stopwatch = Stopwatch()
        var ncards = 0

        val cardIter =  protoCards.iterator()
        while (cardIter.hasNext()) { //  && ncards < 1000000) {
            val card = cardIter.next()
            ncards++
        }

        val msPer = stopwatch.elapsed(TimeUnit.MILLISECONDS) / ncards.toDouble()
        val secPer = msPer / 1000

        println("timeReadSortedCardsFromProto ncards = $ncards, took $stopwatch = $msPer ms/card")
        val totalCards = 4982747
        println("time to read all cards = ${dfn(totalCards * secPer, 3)} secs")
        // timeReadSortedCardsFromProto ncards = 4982786, took 15.35 s = 0.0030785990006394013 ms/card
        // timeReadSortedCardsFromProto ncards = 4982786, took 15.39 s = 0.0030866266381899604 ms/card
        // timeReadSortedCardsFromProto ncards = 4982786, took 14.49 s = 0.0029076103208124935 ms/card
        // avg 15.08
        // 20.1/15.08 = 1.33 = 33% faster than protobuf
        // 53/15.08 = 3.5 faster than csv
    }

    @Test
    fun timeConsistentSampling () {
        // val styles: List<CountyStyles> = Colorado2024Input.countyStyles

        val topdir = "${testdataDir}/cases/corla/consistent"
        val countyAudit = AuditRecord.read(topdir) as CountyAudit
        // val contestNameMap = countyAudit.contests.associate { it.contest.info().name to it }
        val mvrManager = PersistedMvrManager(countyAudit)
        val cards: CloseableIterable<AuditableCardIF> = mvrManager.sortedManifest().cards
        val contestsUA = mvrManager.contestsUA

        val stopwatch = Stopwatch()

        var ncards = 0
        var included = 0
        var newMvrs = 0
        var haveSampleSize = 0
        var haveNewSampleSize = 0
        val previousSamples = emptySet<Long>()

        val sortedCardIter = cards.iterator()
        while (sortedCardIter.hasNext()) {
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