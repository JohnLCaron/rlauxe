package org.cryptobiotic.rlauxe.persist

import org.cryptobiotic.rlauxe.cases
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.util.sfn
import org.cryptobiotic.rlauxe.workflow.PersistedWorkflow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestCountyAuditRecord {
    val corlaData = "$cases/corla/corla2020/clca"

    @Test
    fun testReadFrom() {
        val countyAuditRecord = CountyAuditRecord.readFrom(corlaData)!!

        val workflow = PersistedWorkflow(countyAuditRecord, mvrWrite = false)
        val manager = workflow.mvrManager()
        val manifest = manager.sortedManifest()

        assertEquals(0, manager.pools()?.size ?: 0)
        assertEquals(914, manager.styles()?.size ?: 0)
        println("manifest.ncards = ${manifest.ncards}")
        assertEquals(4480944, manifest.ncards)

        val countyPools = manager.countyPools()
        println("countyPools size = ${ countyPools?.size ?: 0 }")
        assertEquals(60, countyPools?.size ?: 0)

        val countyCvrPools = manager.countyCvrPools()
        println("countyCvrPools size = ${ countyCvrPools?.size ?: 0 }")
        assertEquals(60, countyCvrPools?.size ?: 0)
    }

    @Test
    fun testRead() {
        val countyAuditRecord = AuditRecord.read(corlaData)!!
        assertTrue(countyAuditRecord is CountyAuditRecord)

        // when you call this, you have to read through the mvrs !
        val mvrsByCounty = countyAuditRecord.countMvrsByCounty()
        println("mvrsByCounty size = ${mvrsByCounty.size}")
        println("mvrsByCounty = ${mvrsByCounty}")
        mvrsByCounty.forEach { println("   $it")}
        assertEquals(60, mvrsByCounty.size)

        val mvrsForCounty = countyAuditRecord.readCountyMvrsAndTabulate(countyName = "Jefferson")
        println("'Jefferson' mvrsForCounty size = ${mvrsForCounty.size}")
        println("   nmvrs, contest")
        mvrsForCounty.forEach { (nmvrs,  contest)  ->  println("   ${nfn(nmvrs, 5)}, $contest")}

        assertEquals(57, mvrsForCounty.size)
    }
}