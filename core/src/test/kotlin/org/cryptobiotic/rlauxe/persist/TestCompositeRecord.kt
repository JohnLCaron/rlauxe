package org.cryptobiotic.rlauxe.persist

import org.cryptobiotic.rlauxe.testdataDir
import kotlin.test.Test

class TestCompositeRecord {
    val belgiumData = "$testdataDir/cases/belgium/2024"

    @Test
    fun testRead() {
        val target = CompositeRecord.readFrom(belgiumData)
        println(target)
    }
}