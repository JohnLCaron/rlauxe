package org.cryptobiotic.timing

import org.cryptobiotic.rlauxe.corlaCounty.CountyCardsFromManifest
import org.cryptobiotic.rlauxe.corlaInput.Statewide2020General
import org.cryptobiotic.rlauxe.corlacvr.readCountyCvrs
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.CountyAuditRecord
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.protobuf.ProtoCardIterator
import org.cryptobiotic.rlauxe.util.Locations.cases
import org.cryptobiotic.rlauxe.util.Stopwatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals

class TimeCountyCards {

    @Test
    fun timeReadSchema () {
        val county = "Denver"
        val stopwatch = Stopwatch()

        val stateInput = Statewide2020General()
        val countyInput = stateInput.corlaCountyInput(county)!!

        println("readCorlaCvrs from ${countyInput.manifestSource} and ${countyInput.cvrsSource}")
        val countyCvrs = readCountyCvrs(countyInput.cvrsSource, readRows = false)
        println("CountyCardsFromManifest (${countyCvrs.nrows()}) took ${stopwatch.elapsed(TimeUnit.MILLISECONDS)} msec")

        println(countyCvrs.schema.show())
    }

    @Test
    fun timeReadCards () {
        val county = "Denver"
        val stopwatch = Stopwatch()

        val stateInput = Statewide2020General()
        val countyInput = stateInput.corlaCountyInput(county)!!
        println("readCorlaCvrs from ${countyInput.manifestSource} and ${countyInput.cvrsSource}")

        val countyCards = CountyCardsFromManifest(countyInput)
        var count = 0
        countyCards.iterator().forEach {
            count++
        }
        println("CountyCardsFromManifest ($count) took ${stopwatch.elapsed(TimeUnit.MILLISECONDS)} msec")
        val time1 = stopwatch.elapsed(TimeUnit.MILLISECONDS)

        stopwatch.start()
        val topdir = "$cases/corla/2020"
        val auditRecord = AuditRecord.read(topdir) as CountyAuditRecord
        val countyPublisher = Publisher(auditRecord.publisher.countyAuditRecordTopdir(county))
        val countyCvrFile = countyPublisher.cardManifestFile()
        println("read county cards from ${countyCvrFile}")

        val readCountyCvrs = auditRecord.readCountyCards(county, null)
        val time2 = stopwatch.elapsed(TimeUnit.MILLISECONDS)
        println("readCountyCards (${readCountyCvrs.size}) took ${stopwatch.elapsed(TimeUnit.MILLISECONDS)} msec ratio=${time1 / time2.toDouble()}")
        assertEquals(count, readCountyCvrs.size)

        stopwatch.start()
        val protoIter = ProtoCardIterator(countyPublisher.unsortedCardsProtoFile(), styles=auditRecord.readCardStyles())
        var countp = 0
        protoIter.iterator().forEach {
            countp++
        }
        val time3 = stopwatch.elapsed(TimeUnit.MILLISECONDS)
        println("readCountyProtoCards (${countp}) took ${stopwatch.elapsed(TimeUnit.MILLISECONDS)} msec ratio=${time2 / time3.toDouble()}")
        assertEquals(count, countp)
    }
}