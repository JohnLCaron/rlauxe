package org.cryptobiotic.rlauxe.corlaCounty

import org.cryptobiotic.rlauxe.corlaInput.Arapahoe2020Input
import kotlin.test.Test

class TestMatchCountyCvrs {

    @Test
    fun testMatchCountyCvrs() {
        val testMatchCountyCvrs = MatchCountyCvrs(Arapahoe2020Input())
        testMatchCountyCvrs.showRedactedCvrs()
        testMatchCountyCvrs.manifestCounts(show = true)
        testMatchCountyCvrs.showTabulations()
    }
}