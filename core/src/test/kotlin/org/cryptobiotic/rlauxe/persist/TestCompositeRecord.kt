package org.cryptobiotic.rlauxe.persist

import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.workflow.PersistedWorkflow
import org.junit.Assert.assertTrue
import kotlin.test.Test

class TestCompositeRecord {
    val belgiumData = "$testdataDir/cases/belgium/2024limited"
    val corlaUniform = "$testdataDir/cases/corla/uniform"

    @Test
    fun testReadFrom() {
        val compositeRecord = CompositeAuditRecord.readFrom(belgiumData)!!
        println(compositeRecord)

        val workflow = PersistedWorkflow(compositeRecord, mvrWrite = false)
        val manager = workflow.mvrManager()
        println("pools = ${manager.pools()}")
        println("batches = ${manager.styles()}")
        val manifest = manager.sortedManifest()
        println("manifest.ncards = ${manifest.ncards}")

        val anvers = compositeRecord.findComponentWithName("Anvers")
        println(anvers)
    }

    @Test
    fun testRead() {
        val compositeRecord = AuditRecord.read(belgiumData)!!
        println(compositeRecord)
        assertTrue(compositeRecord is CompositeAuditRecord)

        val workflow = PersistedWorkflow(compositeRecord, mvrWrite = false)
        val manager = workflow.mvrManager()
        println("pools = ${manager.pools()}")
        println("batches = ${manager.styles()}")
        val manifest = manager.sortedManifest()
        println("manifest.ncards = ${manifest.ncards}")

        val anvers = (compositeRecord as CompositeAuditRecord).findComponentWithName("Anvers")
        println(anvers)
    }

    @Test
    fun testReadCorlaCountyAudit() {
        val record = AuditRecord.read(corlaUniform)!!
        val countyAudit = record as CountyAudit
        println("contests = ${countyAudit.contests.size}")
        println("countyData = ${countyAudit.countyData.size}")

        val workflow = PersistedWorkflow(countyAudit, mvrWrite = false)
        val manager = workflow.mvrManager()
        println("pools = ${manager.pools()?.size}")
        println("batches = ${manager.styles()?.size}")
        val manifest = manager.sortedManifest()
        println("manifest.ncards = ${manifest.ncards}")
    }

    // @Test
    fun testReadCorlaCountyComposite() {
        val record = AuditRecord.read(corlaUniform)!!
        val countyAudit = record as CountyComposite
        println(countyAudit)
        countyAudit.countyData.forEach { println(it) }

        val workflow = PersistedWorkflow(countyAudit, mvrWrite = false)
        val manager = workflow.mvrManager()
        println("pools = ${manager.pools()?.size}")
        println("batches = ${manager.styles()?.size}")
        val manifest = manager.sortedManifest()
        println("manifest.ncards = ${manifest.ncards}")

        println("components")
        countyAudit.componentRecords.forEach { println(" ${it.name()}")}

        val acomponent = countyAudit.findComponentWithName("El_Paso")
        println("****** found ${acomponent?.name()} ${acomponent?.javaClass?.simpleName}")
        println(acomponent)
    }

}