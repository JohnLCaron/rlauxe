package org.cryptobiotic.rlauxe.auditcenter

import org.cryptobiotic.rlauxe.cases
import kotlin.test.Test

class TestCountyPoolsSimCvrs {

    @Test
    fun testCountyElectionSimCvrs() {
        val topdir = "$cases/corla/corla2020/test2"

        CountyElectionSimCvrs(Colorado2020General(),  topdir, name="testCountyElectionSimCvrs",
            hasStyle = true,
            onlyCounty = "Logan",
        )
    }
}