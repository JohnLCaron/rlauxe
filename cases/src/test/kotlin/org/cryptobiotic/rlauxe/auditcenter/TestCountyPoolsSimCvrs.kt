package org.cryptobiotic.rlauxe.auditcenter

import org.cryptobiotic.rlauxe.cases
import kotlin.test.Test

class TestCountyPoolsSimCvrs {

    @Test
    fun testCountyElectionSimCvrs() {
        val topdir = "$cases/corla/corla2024test"

        CountyElectionSimCvrs(Colorado2024General(),  topdir, name="testCountyElectionSimCvrs",
            hasStyle = true,
            onlyCounty = "Elbert",
        )
    }
}