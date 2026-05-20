package org.cryptobiotic.rlauxe.persist.bin

import org.cryptobiotic.rlauxe.audit.SamplingCardIF
import org.cryptobiotic.rlauxe.audit.makeFastCards
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.CountyAudit
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.Closer
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.workflow.PersistedMvrManager
import java.util.concurrent.TimeUnit
import kotlin.test.Test

class TestFastSamplingCards {
    val testFastSamplFile = "$testdataDir/temp/fastSampling.bin"

    @Test
    fun writeSamplingCards() {
        val topdir = "${testdataDir}/cases/corla/consistent"
        val publisher = Publisher("$topdir/audit")

        val countyAudit = AuditRecord.read(topdir) as CountyAudit
        val mvrManager = PersistedMvrManager(countyAudit)
        val styles = mvrManager.styles()!! // TODO maybe not optional ?
        val cardManifest = countyAudit.readSortedManifest(styles)

        val cardIter = cardManifest.cards.iterator()

        val stopwatch = Stopwatch()
        val ncards = writeFastSamplingCards(cardIter, testFastSamplFile, styles)
        cardIter.close()

        println("writeSamplingCards ncards = $ncards, took $stopwatch")
    }

    @Test
    fun readSamplingCards() {
        val topdir = "${testdataDir}/cases/corla/consistent"
        val publisher = Publisher("$topdir/audit")

        val countyAudit = AuditRecord.read(topdir) as CountyAudit
        val mvrManager = PersistedMvrManager(countyAudit)
        val styles = mvrManager.styles()!! // TODO maybe not optional ?

        val stopwatch = Stopwatch()

        var ncards = 0
        val cardIter = FastSamplingCardIterator(publisher.fastSamplingFile(), styles, 100_000)
        while (cardIter.hasNext()) { //  && ncards < 1000_000) {
            val card = cardIter.next()
            ncards++
        }
        cardIter.close()

        println("readSamplingCards ncards = $ncards, took $stopwatch")
    }

    @Test
    fun timeConsistentSampling() {
        val topdir = "${testdataDir}/cases/corla/consistent"
        val publisher = Publisher("$topdir/audit")

        val countyAudit = AuditRecord.read(topdir) as CountyAudit
        val mvrManager = PersistedMvrManager(countyAudit)
        val styles = mvrManager.styles()!!
        val cardIter = FastSamplingCardIterator(publisher.fastSamplingFile(), styles, 100_000)

        runConsistentSampling(cardIter)
        // ncards = 4982786, included = 142470869 that took 37.95 s= 0.007615819744215385 ms/card
        // ncards = 4982786, included = 142470869 that took 39.62 s= 0.007950973611951226 ms/card
    }

    @Test
    fun timeConsistentSamplingCached() {
        val topdir = "${testdataDir}/cases/corla/consistent"
        val publisher = Publisher("$topdir/audit")

        val countyAudit = AuditRecord.read(topdir) as CountyAudit
        val mvrManager = PersistedMvrManager(countyAudit)

        val stopwatch = Stopwatch()
        val styles = mvrManager.styles()!!

        val cardIter = FastSamplingCardIterator(publisher.fastSamplingFile(), styles, 100_000)
        val samplingCards = mutableListOf<FastSamplingCard>()
        while (cardIter.hasNext()) {
            samplingCards.add(cardIter.next())
        }
        val ncards = samplingCards.size
        println("caching took $stopwatch ncards = $ncards = ${stopwatch.elapsed(TimeUnit.MILLISECONDS)/ncards.toDouble()} ms/card")
        // caching took 10.37 s ncards = 4982786 = 0.0020805629621661456 ms/card

        runConsistentSampling(Closer(samplingCards.iterator()))
        // ncards = 4982786, included = 142470869 that took 23.37 s= 0.004688541711404022 ms/card
        // ncards = 4982786, included = 142470869 that took 25.84 s= 0.005185452475783628 ms/card
    }

    @Test
    fun timeSamplingFromMvrManager() {
        val topdir = "${testdataDir}/cases/corla/consistent"
        val countyAudit = AuditRecord.read(topdir) as CountyAudit
        val mvrManager = PersistedMvrManager(countyAudit)

        runConsistentSampling(Closer(mvrManager.samplingCards().iterator()))
        // ncards = 4982786, included = 142470869 that took 30.67 s= 0.006154789710013635 ms/card
        // ncards = 4982786, included = 142470869 that took 27.15 s= 0.005448357605564437 ms/card
        // ncards = 4982786, included = 142470869 that took 28.03 s= 0.0056261697773093205 ms/card
        //  thats what we got
    }

    fun runConsistentSampling(cardIter: CloseableIterator<SamplingCardIF>) {
        val topdir = "${testdataDir}/cases/corla/consistent"
        val countyAudit = AuditRecord.read(topdir) as CountyAudit
        val mvrManager = PersistedMvrManager(countyAudit)
        val contestsUA = mvrManager.contestsUA

        val stopwatch = Stopwatch()

        var ncards = 0
        var included = 0
        var haveSampleSize = 0

        while (cardIter.hasNext()) {
            val card = cardIter.next()
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

    @Test
    fun testMakeFastCards() {
        val auditdir = "${testdataDir}/cases/sf2024/oa/audit"
        val auditRecord = AuditRecord.read(auditdir) as AuditRecord
        val mvrManager = PersistedMvrManager(auditRecord)
        val styles = mvrManager.styles()!!

        val stopwatch = Stopwatch()
        val publisher = Publisher(auditdir)
        val ncards = makeFastCards(publisher, styles)
        println("ncards = $ncards that took $stopwatch= ${stopwatch.elapsed(TimeUnit.MILLISECONDS)/ncards.toDouble()} ms/card")

    }
}