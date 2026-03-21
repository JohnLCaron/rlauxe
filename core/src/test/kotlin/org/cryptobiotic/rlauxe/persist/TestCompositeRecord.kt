package org.cryptobiotic.rlauxe.persist

import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.workflow.PersistedWorkflow
import kotlin.test.Test

class TestCompositeRecord {
    val belgiumData = "$testdataDir/cases/belgium/2024"

    @Test
    fun testRead() {
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
}