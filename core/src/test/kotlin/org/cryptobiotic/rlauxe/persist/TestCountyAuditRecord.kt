package org.cryptobiotic.rlauxe.persist

import org.cryptobiotic.rlauxe.util.Locations.cases
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.util.roundToClosest
import org.cryptobiotic.rlauxe.workflow.PersistedWorkflow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestCountyAuditRecord {
    val corlaData = "$cases/corla/2020"

    @Test
    fun testReadFrom() {
        val countyAuditRecord = CountyAuditRecord.readFrom(corlaData)!!

        val workflow = PersistedWorkflow(countyAuditRecord, mvrWrite = false)
        val manager = workflow.mvrManager()
        val manifest = manager.sortedManifest()

        assertEquals(18, manager.pools()?.size ?: 0)
        assertEquals(953, manager.styles()?.size ?: 0)
        println("manifest.ncards = ${manifest.ncards}")
        assertEquals(4125823, manifest.ncards)

        val countyPools = manager.countyAcPools()
        println("countyPools size = ${ countyPools?.size ?: 0 }")
        assertEquals(63, countyPools?.size ?: 0)

        val countyCvrPools = manager.countyCvrPools()
        println("countyCvrPools size = ${ countyCvrPools?.size ?: 0 }")
        assertEquals(0, countyCvrPools?.size ?: 0)
    }

    @Test
    fun testRead() {
        val countyAuditRecord = AuditRecord.read(corlaData)!! as CountyAuditRecord

        // when you call this, you have to read through the mvrs !
        val mvrsByCounty = countyAuditRecord.countMvrsByCounty()
        println("mvrsByCounty size = ${mvrsByCounty.size}")
        println("mvrsByCounty = ${mvrsByCounty}")
        mvrsByCounty.forEach { println("   $it")}
        assertEquals(62, mvrsByCounty.size)

        /* val mvrsForCounty = countyAuditRecord.readCountyMvrsAndTabulate(countyName = "Jefferson")
        println("'Jefferson' mvrsForCounty size = ${mvrsForCounty.size}")
        println("   nmvrs, contest")
        mvrsForCounty.forEach { (nmvrs,  contest)  ->  println("   ${nfn(nmvrs, 5)}, $contest")}
        assertEquals(57, mvrsForCounty.size) */
    }

    @Test
    fun readCountyFiles() {
        val countyAuditRecord = AuditRecord.read(corlaData)!!
        assertTrue(countyAuditRecord is CountyAuditRecord)

        // when you call this, you have to read through the mvrs !
        val countyContestData = countyAuditRecord.countyContestData
        val contestCounties =  mutableMapOf<Int, MutableList<CountyContestData>>()
        for (countyContest in countyContestData) {
            val counties = contestCounties.getOrPut(countyContest.id) { mutableListOf() }
            counties.add(countyContest)
        }
        val counties = contestCounties[382]!!
        val myCounty = counties.find{ it.countyName == "Chaffee"}!!
        val sumAll = counties.sumOf { it.nPop }
        val pct = myCounty.nPop/sumAll.toDouble()
        val pctn = roundToClosest( pct)
        println("pct = $pct == $pctn")
    }
}