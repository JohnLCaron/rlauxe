package org.cryptobiotic.rlauxe.persist

import org.cryptobiotic.rlauxe.cases
import org.cryptobiotic.rlauxe.workflow.PersistedWorkflow
import kotlin.test.Test
import kotlin.test.assertTrue

class TestCompositeRecord {
    val belgiumData = "$cases/belgium/belgium2024"
    val corlaUniform = "$cases/corla/corla2020/uniform"

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
    fun testReadComponent() {
        val componentRecord = AuditRecord.read("$belgiumData/Anvers")!!
        println(componentRecord)
    }

    // @Test
    fun testReadCorlaCountyAudit() {
        val record = AuditRecord.read(corlaUniform)!!
        val countyAudit = record as CountyAuditRecord
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