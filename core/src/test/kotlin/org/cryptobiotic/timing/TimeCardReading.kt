package org.cryptobiotic.timing

import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.CountyAuditRecord
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.bin.FastSamplingCardIterator
import org.cryptobiotic.rlauxe.persist.csv.AddStyles
import org.cryptobiotic.rlauxe.persist.csv.CardCsvReader
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIterator
import org.cryptobiotic.rlauxe.persist.json.readCardStylesJsonFile
import org.cryptobiotic.rlauxe.persist.protobuf.ProtoCardIterable
import org.cryptobiotic.rlauxe.persist.protobuf.ProtoCardIterator
import org.cryptobiotic.rlauxe.util.Locations.testdataDir
import org.cryptobiotic.rlauxe.util.CloseableIterable
import org.cryptobiotic.rlauxe.util.CloseableIterableInline
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.Locations.cases
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.Welford
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.workflow.PersistedMvrManager
import java.io.BufferedInputStream
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.Test

class TimeCardReading {
    val topdir = "${cases}/corla/2020/"

//             timeReadCsv: accum=1674172692 took 282.3 s = 14115. ms/trial count=20, mean=3.4212 stddev=0.0459 us/card   3.5x slower
//           timeReadProto: accum=1674172692 took 80.96 s = 4047.5 ms/trial count=20, mean=0.9810 stddev=0.0377 us/card
//      CloseableIterable2: accum=1674172692 took 73.18 s = 3658.8 ms/trial count=20, mean=0.8868 stddev=0.0318 us/card *
// timeReadProtoNonGeneric: accum=1674172692 took 77.19 s = 3859.0 ms/trial count=20, mean=0.9353 stddev=0.0452 us/card
//     // timeFastSampling: accum=1334069640 took 2.931 s = 146.25 ms/trial count=20, mean=0.0354 stddev=0.0036 us/card   25x faster

//// results ntrials = 20
//          ProtoCardIteratorM: accum=1143043148 took 267.1 s = 13354.45 ms/trial count=20, mean=2.6801 stddev=0.0738 us/card  = 1.0
//           CloseableIterable: accum=1143043148 took 263.1 s = 13151.8  ms/trial count=20, mean=2.6394 stddev=0.1009 us/card  =  .98
//     CloseableIterableInline: accum=1143043148 took 383.4 s = 19169.4  ms/trial count=20, mean=3.8471 stddev=0.0841 us/card  = 1.43 = 43% slower (!)
// CloseableIterableNonGeneric: accum=1143043148 took 261.5 s = 13071.6  ms/trial count=20, mean=2.6233 stddev=0.0713 us/card  =  .98
//                 timeReadCsv: accum=1143043148 took 1068 s =  53387.0  ms/trial count=20, mean=10.7143 stddev=0.2442 us/card = 4.0 = 4x slower
//            timeFastSampling: accum=1626482836 took 8.611 s =   429.0  ms/trial count=20, mean=0.0861 stddev=0.0118 us/card  = 31 times faster
//      timeFastSamplingCached:                  took 1.120 s =    54.9  ms/trial count=20, mean=0.0110 stddev=0.0053 us/card  = 243 times faster

    @Test
    fun timeReadRawBytes () {

        val stopwatch = Stopwatch()
        var totalRead = 0L

        val publisher = Publisher("$topdir")
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

        println("timeReadRawBytes with buffer= ($bufferSize), fileLength=$fileLength  fillRead = ${totalRead.equals(fileLength)}, took ${stopwatch.elapsed(
            TimeUnit.MILLISECONDS)} msec")
    }
    // timeReadRawBytes with buffer= (100000), fileLength=988329974  fillRead = false, took 124 msec

    @Test
    fun timeReadCardsCsvNoStyles () {
        val stopwatch = Stopwatch()
        var ncards = 0

        val publisher = Publisher("$topdir")

        var accum = 0
        val bufferSize = 8096 // 100_000
        val cardIter = CardCsvReader(publisher.sortedCardsFile(), emptyList()).iterator()
        while (cardIter.hasNext()) { //  && ncards < 1000000) {
            val card = cardIter.next()
            accum += card.index() // prevent optimization
            ncards++
        }

        val msPer = stopwatch.elapsed(TimeUnit.MILLISECONDS) / ncards.toDouble()
        val secPer = msPer / 1000

        println("timeReadCardsCsvNoStyles buffer=$bufferSize ncards = $ncards, took $stopwatch = $msPer ms/card")
        val totalCards = 4125823
        println("time to read all cards = ${dfn(totalCards * secPer, 3)} secs")
    }
    // IteratorCardsCsvStream buffer=8096 ncards = 4125823, took 13.60 s = 0.003295827281005511 ms/card
    // time to read all cards = 16.422 secs
    // IteratorCardsCsvStream buffer=8096 ncards = 4125823, took 14.28 s = 0.003460885258529026 ms/card
    //time to read all cards = 14.279 secs

    @Test
    fun timeReadCsv () {
        val auditRecord = AuditRecord.read(topdir) as AuditRecord
        val mvrManager = PersistedMvrManager(auditRecord)
        val publisher = Publisher("$topdir")
        val styles = mvrManager.styles()

        var accum = 0
        val welford = Welford()
        val stopwatch = Stopwatch()

        val ntrials = 20
        repeat(ntrials) {
            val trial = Stopwatch()
            var ncards = 0

            val csvIter = readCardsCsvIterator(publisher.sortedCardsFile(), AddStyles(styles))
            while (csvIter.hasNext()) { //  && ncards < 1000_000) {
                val card = csvIter.next()
                accum += card.index() // prevent optimization
                ncards++
            }

            welford.update(trial.elapsed(TimeUnit.MICROSECONDS) / ncards.toDouble())
        }

        val msPer = stopwatch.elapsed(TimeUnit.MILLISECONDS) / ntrials.toDouble()
        println("timeReadCsv: accum=$accum took $stopwatch = $msPer ms/trial ${welford.show()} us/card")
    }
    // timeReadCsv: accum=1674172692 took 282.3 s = 14115.3 ms/trial count=20, mean=3.4212 stddev=0.0459 us/card
    // timeReadCsv: accum=1674172692 took 287.3 s = 14365.6 ms/trial count=20, mean=3.4819 stddev=0.0461 us/card
    // timeReadCsv: accum=-1419529919 took 14.36 s = 14361.0 ms/trial count=1, mean=3.4809 stddev=0.0000 us/card

        // println("time to read all cards = ${dfn(totalCards * secPer, 3)} secs")
        // timeReadSortedManifestFromCsv ncards = 4982786, took 51.47 s = 0.010328157781610529 ms/card
        // timeReadSortedManifestFromCsv ncards = 4982786, took 53.13 s = 0.01066130473995873 ms/card
        // timeReadSortedManifestFromCsv ncards = 4982786, took 56.67 s = 0.011371148590366914 ms/card
        // timeReadSortedManifestFromCsv ncards = 4982786, took 51.65 s = 0.010363680077771753 ms/card
        // avg 53.25
        // maybe 53 / 49.4 = 1.07 = 7%  slower than timeReadCardsCsvIterator

    @Test
    fun timeReadCsvAndMergeCardM () {
        val auditRecord = AuditRecord.read(topdir) as AuditRecord
        val mvrManager = PersistedMvrManager(auditRecord)
        val publisher = Publisher("$topdir")
        val styles = mvrManager.styles()

        val stopwatch = Stopwatch()
        var ncards = 0

        // includes time to merge the styles
        var accum = 0
        val cardIter: CloseableIterator<AuditableCard> =
            readCardsCsvIterator(publisher.sortedCardsFile(), AddStyles(styles))
        while (cardIter.hasNext()) { //  && ncards < 1000000) {
            val card = cardIter.next()
            accum += card.index() // prevent optimization
            ncards++
        }

        val msPer = stopwatch.elapsed(TimeUnit.MILLISECONDS) / ncards.toDouble()
        val secPer = msPer / 1000

        println("timeReadAndMergeCardM ncards = $ncards, took $stopwatch = $msPer ms/card")
        val totalCards = 4982747
        println("time to read all cards = ${dfn(totalCards * secPer, 3)} secs")
    }
    // timeReadAndMergeCardM ncards = 4125823, took 14.81 s = 0.003589102101568584 ms/card
    //time to read all cards = 17.884 secs
        // timeReadAndMergeCardM ncards = 4982795, took 60.18 s = 0.012076154046072535 ms/card
        // timeReadAndMergeCardM ncards = 4982795, took 54.69 s = 0.010974362782334011 ms/card
        // timeReadAndMergeCardM ncards = 4982795, took 55.34 s = 0.011104811656911432 ms/card
        // timeReadAndMergeCardM ncards = 4982795, took 56.13 s = 0.01126355790274334 ms/card
        // avg 56.6

    @Test
    fun timeReadProtoCardInline () {
        val auditRecord = AuditRecord.read(topdir) as AuditRecord
        val mvrManager = PersistedMvrManager(auditRecord)
        val publisher = Publisher("$topdir")
        val styles = mvrManager.styles()

        val bufferSize = 100_000
        val protoFilename = publisher.sortedCardsProtoFile()
        val protoIterable = CloseableIterableInline { ProtoCardIterator(protoFilename, bufferSize, styles) }

        val stopwatch = Stopwatch()
        var accum = 0
        val welford = Welford()

        val ntrials = 1
        repeat(ntrials) {
            val trial = Stopwatch()
            var ncards = 0

            val protoIter = protoIterable.iterator()
            while (protoIter.hasNext()) { //  && ncards < 1000_000) {
                val card = protoIter.next()
                accum += card.index() // prevent optimization
                ncards++
            }

            welford.update(trial.elapsed(TimeUnit.MICROSECONDS) / ncards.toDouble())
        }

        val msPer = stopwatch.elapsed(TimeUnit.MILLISECONDS) / ntrials.toDouble()
        println("timeReadProtoCardInline: accum=$accum took $stopwatch = $msPer ms/trial ${welford.show()} us/card")
    }
        // timeReadProtoCardInline: accum=-1419529919 took 4.661 s = 4657.0 ms/trial count=1, mean=1.1289 stddev=0.0000 us/card
        // timeReadProtoCardInline: accum=1143043148 took 383.4 s = 19169.4 ms/trial count=20, mean=3.8471 stddev=0.0841 us/card


        // TODO why is this slower than timeReadProto ??
        // timeReadSortedCardsMFromProto ncards = 4982795, took 19.92 s = 0.003995347992441993 ms/card
        // timeReadSortedCardsMFromProto ncards = 4982795, took 14.91 s = 0.002990289586467033 ms/card
        // timeReadSortedCardsMFromProto ncards = 4982795, took 21.64 s = 0.004341137855360295 ms/card
        // timeReadSortedCardsMFromProto ncards = 4982795, took 14.40 s = 0.0028887401548729178 ms/card
        // timeReadSortedCardsMFromProto ncards = 4982795, took 16.01 s = 0.0032104471486384648 ms/card

        // timeReadSortedCardsMFromProto ncards = 4982774, took 15.11 s = 0.0030302397820972816 ms/card
        // timeReadSortedCardsMFromProto ncards = 4982774, took 18.98 s = 0.0038075176598416868 ms/card
        // timeReadSortedCardsMFromProto ncards = 4982774, took 20.09 s = 0.004030887212624935 ms/card

    @Test
    fun timeReadProto () {
        val publisher = Publisher("$topdir")
        val protoFilename = publisher.sortedCardsProtoFile()
        val styles = readCardStylesJsonFile(publisher.cardStylesFile()).unwrap()

        val bufferSize = 100_000
        val stopwatch = Stopwatch()

        var accum = 0
        val welford = Welford()

        val ntrials = 20
        repeat(ntrials) {
            val trial = Stopwatch()
            var ncards = 0

            val protoIter: CloseableIterator<AuditableCard> = ProtoCardIterator(protoFilename, bufferSize, styles)
            while (protoIter.hasNext()) { //  && ncards < 1000_000) {
                val card = protoIter.next()
                accum += card.index() // prevent optimization
                ncards++
            }

            welford.update(trial.elapsed(TimeUnit.MICROSECONDS) / ncards.toDouble())
        }

        val msPer = stopwatch.elapsed(TimeUnit.MILLISECONDS) / ntrials.toDouble()
        println("timeReadProto: accum=$accum took $stopwatch = $msPer ms/trial ${welford.show()} us/card")
    }
    // timeReadProto: accum=1674172692 took 80.96 s = 4047.5 ms/trial count=20, mean=0.9810 stddev=0.0377 us/card
    // timeReadProto: accum=1143043148 took 267.1 s = 13354.45 ms/trial count=20, mean=2.6801 stddev=0.0738 us/card (old)

        // timeReadProto (100000):  ncards = 4982774, accum=1560390711 took 13.64 s = 0.002735825465895102 ms/card
        // timeReadProto (100000):  ncards = 4982774, accum=1560390711 took 14.37 s = 0.0028825308954409734 ms/card
        // timeReadProto (100000):  ncards = 4982774, accum=1560390711 took 14.57 s = 0.002921465031325924 ms/card
        // timeReadProto (100000):  ncards = 4982774, accum=1560390711 took 13.66 s = 0.002739237220070587 ms/card
        // avg 14.06

    @Test
    fun timeReadProtoIterable () {
        val publisher = Publisher("$topdir")
        val protoFilename = publisher.sortedCardsProtoFile()
        val styles = readCardStylesJsonFile(publisher.cardStylesFile()).unwrap()

        val bufferSize = 100_000
        val stopwatch = Stopwatch()

        var accum = 0

        val protoIterable = CloseableIterable { ProtoCardIterator(protoFilename, bufferSize, styles) }
        val welford = Welford()

        val ntrials = 20
        repeat(ntrials) {
            val trial = Stopwatch()
            var ncards = 0

            val protoIter = protoIterable.iterator()
            while (protoIter.hasNext()) { //  && ncards < 1000_000) {
                val card = protoIter.next()
                accum += card.index() // prevent optimization
                ncards++
            }

            welford.update(trial.elapsed(TimeUnit.MICROSECONDS) / ncards.toDouble())
        }

        val msPer = stopwatch.elapsed(TimeUnit.MILLISECONDS) / ntrials.toDouble()
        println("CloseableIterable2: accum=$accum took $stopwatch = $msPer ms/trial ${welford.show()} us/card")
    }
    // CloseableIterable2: accum=1674172692 took 73.18 s = 3658.8 ms/trial count=20, mean=0.8868 stddev=0.0318 us/card

        // CloseableIterable2  accum=1143043148 took 275.2 s = 13757.3 ms/trial
        // CloseableIterable2: accum=1143043148 took 263.1 s = 13151.8 ms/trial count=20, mean=2.6394 stddev=0.1009 us/card
        //      timeReadProto: accum=1143043148 took 267.1 s = 13354.45 ms/trial count=20, mean=2.6801 stddev=0.0738 us/card

        // CloseableIterable2 (100000):  ncards = 4982774, accum=1560390711 took 15.45 s = 0.003098274174184902 ms/card
        // CloseableIterable2 (100000):  ncards = 4982774, accum=1560390711 took 14.44 s = 0.0028951744550324778 ms/card
        // CloseableIterable2 (100000):  ncards = 4982774, accum=1560390711 took 14.17 s = 0.0028427939938676728 ms/card
        // CloseableIterable2 (100000):  ncards = 4982774, accum=1560390711 took 15.37 s = 0.0030836237003725236 ms/card
        // CloseableIterable2 (100000):  ncards = 4982774, accum=1560390711 took 14.90 s = 0.002988897349147282 ms/card
        // CloseableIterable2 (100000):  ncards = 4982774, accum=1560390711 took 14.12 s = 0.002832558731341217 ms/card
        // avg = 14.6866

    @Test
    fun timeReadProtoNonGeneric () {
        val publisher = Publisher("$topdir")
        val protoFilename = publisher.sortedCardsProtoFile()
        val styles = readCardStylesJsonFile(publisher.cardStylesFile()).unwrap()

        val bufferSize = 100_000
        val stopwatch = Stopwatch()

        var accum = 0

        val protoIterable = ProtoCardIterable(protoFilename, bufferSize, styles) // non generic
        val welford = Welford()

        val ntrials = 20
        repeat(ntrials) {
            val trial = Stopwatch()
            var ncards = 0

            val protoIter = protoIterable.iterator()
            while (protoIter.hasNext()) { //  && ncards < 1000_000) {
                val card = protoIter.next()
                accum += card.index() // prevent optimization
                ncards++
            }

            welford.update(trial.elapsed(TimeUnit.MICROSECONDS) / ncards.toDouble())
        }

        val msPer = stopwatch.elapsed(TimeUnit.MILLISECONDS) / ntrials.toDouble()
        println("timeReadProtoNonGeneric: accum=$accum took $stopwatch = $msPer ms/trial ${welford.show()} us/card")
    }
// timeReadProtoNonGeneric: accum=1674172692 took 77.19 s = 3859.05 ms/trial count=20, mean=0.9353 stddev=0.0452 us/card
// timeReadProtoNonGeneric: accum=1143043148 took 261.5 s = 13071.6 ms/trial count=20, mean=2.6233 stddev=0.0713 us/card

    @Test
    fun timeFastSampling() {
        val publisher = Publisher("$topdir")

        val countyAudit = AuditRecord.read(topdir) as CountyAuditRecord
        val mvrManager = PersistedMvrManager(countyAudit)
        val styles = mvrManager.styles()

        val stopwatch = Stopwatch()
        val welford = Welford()
        var accum = 0

        val ntrials = 20
        repeat(ntrials) {
            val trial = Stopwatch()
            var ncards = 0

            val cardIter = FastSamplingCardIterator(publisher.fastSamplingFile(), styles, 100_000)
            while (cardIter.hasNext()) { //  && ncards < 1000_000) {
                val card = cardIter.next()
                accum += card.style.id() // prevent optimization
                ncards++
            }

            welford.update(trial.elapsed(TimeUnit.MICROSECONDS) / ncards.toDouble())
        }

        val msPer = stopwatch.elapsed(TimeUnit.MILLISECONDS) / ntrials.toDouble()
        println("timeFastSampling: accum=$accum took $stopwatch = $msPer ms/trial ${welford.show()} us/card")
    }
    // timeFastSampling: accum=1334069640 took 2.931 s = 146.25 ms/trial count=20, mean=0.0354 stddev=0.0036 us/card
    // timeFastSampling: accum=1334069640 took 2.922 s = 145.9 ms/trial count=20, mean=0.0354 stddev=0.0069 us/card

    @Test
    fun timeFastSamplingCached() {

        val countyAudit = AuditRecord.read(topdir) as AuditRecord
        val mvrManager = PersistedMvrManager(countyAudit)
        val cachedFastSampling = mvrManager.samplingCards()

        val stopwatch = Stopwatch()
        val welford = Welford()
        var accum = 0L

        val ntrials = 20
        repeat(ntrials) {
            val trial = Stopwatch()
            var ncards = 0

            val cardIter = cachedFastSampling.iterator()
            while (cardIter.hasNext()) { //  && ncards < 1000_000) {
                val card = cardIter.next()
                accum += card.prn() // prevent optimization
                ncards++
            }

            welford.update(trial.elapsed(TimeUnit.MICROSECONDS) / ncards.toDouble())
        }

        val msPer = stopwatch.elapsed(TimeUnit.MILLISECONDS) / ntrials.toDouble()
        println("timeFastSamplingCached: accum=$accum took $stopwatch = $msPer ms/trial ${welford.show()} us/card")
    }
    // timeFastSamplingCached: accum=3878404933236835290 took 24.68 ms = 21.0 ms/trial count=1, mean=0.0051 stddev=0.0000 us/card
    // timeFastSamplingCached: accum=3781122369898499336 took 237.9 ms = 11.7 ms/trial count=20, mean=0.0028 stddev=0.0016 us/card

    @Test
    fun timeConsistentSampling () {
        val countyAudit = AuditRecord.read(topdir) as CountyAuditRecord
        // val contestNameMap = countyAudit.contests.associate { it.contest.info().name to it }
        val mvrManager = PersistedMvrManager(countyAudit)
        val cards: CloseableIterable<AuditableCard> = mvrManager.sortedManifest().cardIterable
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
    // ncards = 4125823, included = 106250121 that took 12.20 s= 0.002956985794107018 ms/card
}