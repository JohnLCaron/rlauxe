package org.cryptobiotic.rlauxe.corlacvr

import org.cryptobiotic.rlauxe.boulder.Boulder23Input
import org.cryptobiotic.rlauxe.corlaCounty.CheckCvrsAndManifest
import org.cryptobiotic.rlauxe.corlaInput.Colorado2020General
import org.cryptobiotic.rlauxe.corlaInput.Colorado2026PwithCvrs
import kotlin.test.Test

class TestCorlaCvrs {

    @Test
    fun testOneCorlaCountyInput() {
        val countyCvrs = CountyCvrs(Boulder23Input())
        countyCvrs.showRedactedCvrs()
        countyCvrs.manifestCounts(show = true)
        countyCvrs.showTabulations()
    }
}