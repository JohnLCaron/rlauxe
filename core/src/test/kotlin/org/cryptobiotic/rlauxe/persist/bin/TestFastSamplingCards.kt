package org.cryptobiotic.rlauxe.persist.bin

import org.cryptobiotic.rlauxe.audit.makeFastCards
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.CountyAuditRecord
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.workflow.PersistedMvrManager
import java.util.concurrent.TimeUnit
import kotlin.test.Test

// dont run in tests
class TestFastSamplingCards {
    val testFastSamplFile = "$testdataDir/temp/fastSampling.bin"

    // @Test
    fun writeSamplingCards() {
        val topdir = "${testdataDir}/cases/corla/consistent"
        val publisher = Publisher("$topdir")

        val countyAudit = AuditRecord.read(topdir) as CountyAuditRecord
        val mvrManager = PersistedMvrManager(countyAudit)
        val styles = mvrManager.styles()!! // TODO maybe not optional ?
        val cardManifest = countyAudit.readSortedManifest(styles)

        val cardIter = cardManifest.cards.iterator()

        val stopwatch = Stopwatch()
        val ncards = writeFastSamplingCards(cardIter, testFastSamplFile)
        cardIter.close()

        println("writeSamplingCards ncards = $ncards, took $stopwatch")
    }

    @Test
    fun readSamplingCards() {
        val topdir = "${testdataDir}/cases/corla/consistent"
        val publisher = Publisher("$topdir")

        val countyAudit = AuditRecord.read(topdir) as CountyAuditRecord
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

    // @Test
    fun testMakeFastCards() {
        val topdir = "${testdataDir}/cases/sf2024/oa"
        val auditRecord = AuditRecord.read(topdir) as AuditRecord
        val mvrManager = PersistedMvrManager(auditRecord)
        val styles = mvrManager.styles()!!

        val stopwatch = Stopwatch()
        val publisher = Publisher(topdir)
        val ncards = makeFastCards(publisher, styles)
        println("ncards = $ncards that took $stopwatch= ${stopwatch.elapsed(TimeUnit.MILLISECONDS)/ncards.toDouble()} ms/card")

    }
}