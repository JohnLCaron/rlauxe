package org.cryptobiotic.rlauxe.corlaCounty

import org.cryptobiotic.rlauxe.corlaInput.Arapahoe2020Redacted
import kotlin.test.Test

class TestMatchCountyCvrs {

    @Test
    fun testMatchCountyCvrs() {
        val testMatchCountyCvrs = MatchCountyCvrs(Arapahoe2020Redacted())
        testMatchCountyCvrs.showRedactedCvrs()
        testMatchCountyCvrs.manifestCounts(show = true)
        testMatchCountyCvrs.showTabulations()
    }
}