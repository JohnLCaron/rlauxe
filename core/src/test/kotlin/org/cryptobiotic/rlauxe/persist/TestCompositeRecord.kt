package org.cryptobiotic.rlauxe.persist

import org.cryptobiotic.rlauxe.cases
import org.cryptobiotic.rlauxe.workflow.PersistedWorkflow
import kotlin.test.Test
import kotlin.test.assertTrue

class TestCompositeRecord {
    val belgiumData = "$cases/belgium/belgium2024"

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

        val anvers = compositeRecord.findComponentWithName("Anvers")
        println(anvers)
    }

}