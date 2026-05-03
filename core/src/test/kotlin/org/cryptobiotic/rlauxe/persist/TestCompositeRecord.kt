package org.cryptobiotic.rlauxe.persist

import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.workflow.PersistedWorkflow
import org.junit.Assert.assertTrue
import kotlin.test.Test

class TestCompositeRecord {
    val belgiumData = "$testdataDir/cases/belgium/2024limited"
    val corlaCounty = "$testdataDir/cases/corla/county"

    @Test
    fun testReadFrom() {
        val compositeRecord = CompositeRecord.readFrom(belgiumData)!!
        println(compositeRecord)

        val workflow = PersistedWorkflow(compositeRecord, mvrWrite = false)
        val manager = workflow.mvrManager()
        println("pools = ${manager.pools()}")
        println("batches = ${manager.batches()}")
        val manifest = manager.sortedManifest()
        println("manifest.ncards = ${manifest.ncards}")

        val anvers = compositeRecord.findComponentWithName("Anvers")
        println(anvers)
    }

    @Test
    fun testRead() {
        val compositeRecord = AuditRecord.read(belgiumData)!!
        println(compositeRecord)
        assertTrue(compositeRecord is CompositeRecord)

        val workflow = PersistedWorkflow(compositeRecord, mvrWrite = false)
        val manager = workflow.mvrManager()
        println("pools = ${manager.pools()}")
        println("batches = ${manager.batches()}")
        val manifest = manager.sortedManifest()
        println("manifest.ncards = ${manifest.ncards}")

        val anvers = (compositeRecord as CompositeRecord).findComponentWithName("Anvers")
        println(anvers)
    }

    @Test
    fun testReadCorla() {
        val record = AuditRecord.read(corlaCounty)!!
        val countyAudit = record as CountyComposite
        println(countyAudit)

        val workflow = PersistedWorkflow(countyAudit, mvrWrite = false)
        val manager = workflow.mvrManager()
        println("pools = ${manager.pools()?.size}")
        println("batches = ${manager.batches()?.size}")
        val manifest = manager.sortedManifest()
        println("manifest.ncards = ${manifest.ncards}")

        println("components")
        countyAudit.componentRecords.forEach { println(" ${it.name()}")}

        val acomponent = countyAudit.findComponentWithName("El_Paso")
        println("****** found ${acomponent?.name()} ${acomponent?.javaClass?.simpleName}")
        println(acomponent)
    }
}