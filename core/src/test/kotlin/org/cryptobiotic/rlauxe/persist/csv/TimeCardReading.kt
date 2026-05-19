package org.cryptobiotic.rlauxe.persist.csv

import org.cryptobiotic.rlauxe.audit.AuditableCardIF
import org.cryptobiotic.rlauxe.audit.AuditableCardM
import org.cryptobiotic.rlauxe.audit.CardWithStyleName
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.CountyAudit
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.protobuf.ProtoCardIterator
import org.cryptobiotic.rlauxe.persist.protobuf.ProtoCardIteratorM
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
        val cardIter: CloseableIterator<CardWithStyleName> = IteratorCardsCsvStream(inputStream, 8096)
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

        val csvCards = CloseableIterable { readCardsCsvIteratorM(publisher.sortedCardsFile(), styles) }

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

    @Test
    fun timeReadCsvAndMergeCardM () {
        val topdir = "${testdataDir}/cases/corla/consistent"
        val auditRecord = AuditRecord.read(topdir) as AuditRecord
        val mvrManager = PersistedMvrManager(auditRecord)
        val publisher = Publisher("$topdir/audit")
        val styles = mvrManager.styles()

        val stopwatch = Stopwatch()
        var ncards = 0

        // includes time to merge the styles
        val cardIter: CloseableIterator<AuditableCardM> = readCardsCsvIteratorM(publisher.sortedCardsFile(), styles)
        while (cardIter.hasNext()) { //  && ncards < 1000000) {
            val card = cardIter.next()
            ncards++
        }

        val msPer = stopwatch.elapsed(TimeUnit.MILLISECONDS) / ncards.toDouble()
        val secPer = msPer / 1000

        println("timeReadAndMergeCardM ncards = $ncards, took $stopwatch = $msPer ms/card")
        val totalCards = 4982747
        println("time to read all cards = ${dfn(totalCards * secPer, 3)} secs")
        // timeReadAndMergeCardM ncards = 4982795, took 60.18 s = 0.012076154046072535 ms/card
        // timeReadAndMergeCardM ncards = 4982795, took 54.69 s = 0.010974362782334011 ms/card
        // timeReadAndMergeCardM ncards = 4982795, took 55.34 s = 0.011104811656911432 ms/card
        // timeReadAndMergeCardM ncards = 4982795, took 56.13 s = 0.01126355790274334 ms/card
        // avg 56.6
    }

    @Test
    fun timeReadSortedCardsFromProto () {
        val topdir = "${testdataDir}/cases/corla/consistent"
        val auditRecord = AuditRecord.read(topdir) as AuditRecord
        val mvrManager = PersistedMvrManager(auditRecord)
        val publisher = Publisher("$topdir/audit")
        val styles = mvrManager.styles()

        val bufferSize = 100_000
        val protoFilename = publisher.sortedCardsProtoFile()

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
    fun timeReadSortedCardsMFromProto () {
        val topdir = "${testdataDir}/cases/corla/consistent"
        val auditRecord = AuditRecord.read(topdir) as AuditRecord
        val mvrManager = PersistedMvrManager(auditRecord)
        val publisher = Publisher("$topdir/audit")
        val styles = mvrManager.styles()

        val bufferSize = 100_000
        val protoFilename = publisher.sortedCardsProtoFile()

        // also merges the styles
        val protoCards = CloseableIterable { ProtoCardIteratorM(protoFilename, bufferSize, styles) }

        val stopwatch = Stopwatch()
        var ncards = 0

        val cardIter =  protoCards.iterator()
        while (cardIter.hasNext()) { //  && ncards < 1000000) {
            val card = cardIter.next()
            ncards++
        }

        val msPer = stopwatch.elapsed(TimeUnit.MILLISECONDS) / ncards.toDouble()
        val secPer = msPer / 1000

        println("timeReadSortedCardsMFromProto ncards = $ncards, took $stopwatch = $msPer ms/card")
        // timeReadSortedCardsMFromProto ncards = 4982795, took 19.92 s = 0.003995347992441993 ms/card
        // timeReadSortedCardsMFromProto ncards = 4982795, took 14.91 s = 0.002990289586467033 ms/card
        // timeReadSortedCardsMFromProto ncards = 4982795, took 21.64 s = 0.004341137855360295 ms/card
        // timeReadSortedCardsMFromProto ncards = 4982795, took 14.40 s = 0.0028887401548729178 ms/card
        // timeReadSortedCardsMFromProto ncards = 4982795, took 16.01 s = 0.0032104471486384648 ms/card
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