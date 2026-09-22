package org.cryptobiotic.rlauxe.corlacvr

import org.cryptobiotic.rlauxe.boulder.Boulder23Input
import kotlin.test.Test

class TestCorlaCvrs {

    @Test
    fun testOneCorlaCountyInput() {
        val testMatchCountyCvrs = MatchCountyCvrs(Boulder23Input())
        testMatchCountyCvrs.showRedactedCvrs()
        testMatchCountyCvrs.manifestCounts(show = true)
        testMatchCountyCvrs.showTabulations()
    }
}