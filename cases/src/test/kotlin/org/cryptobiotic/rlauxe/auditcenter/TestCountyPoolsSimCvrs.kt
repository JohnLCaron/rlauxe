package org.cryptobiotic.rlauxe.auditcenter

import org.cryptobiotic.rlauxe.util.Locations.cases
import org.cryptobiotic.rlauxe.corla.CountyElectionSimCvrs
import org.cryptobiotic.rlauxe.corlaInput.Statewide2024General
import kotlin.test.Test

class TestCountyPoolsSimCvrs {

    @Test
    fun testCountyElectionSimCvrs() {
        val topdir = "$cases/corla/corla2024test"

        CountyElectionSimCvrs(
            Statewide2024General(), topdir, name = "testCountyElectionSimCvrs",
            hasStyle = true,
            onlyCounty = "Elbert",
        )
    }
}