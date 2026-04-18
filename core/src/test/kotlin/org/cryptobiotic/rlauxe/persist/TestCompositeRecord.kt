package org.cryptobiotic.rlauxe.persist

import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.workflow.PersistedWorkflow
import org.junit.Assert.assertTrue
import kotlin.test.Test

class TestCompositeRecord {
    val belgiumData = "$testdataDir/cases/belgium/2024limited"

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

        val anvers = compositeRecord.findComponentWithContest("Anvers")
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

        val anvers = (compositeRecord as CompositeRecord).findComponentWithContest("Anvers")
        println(anvers)
    }
}