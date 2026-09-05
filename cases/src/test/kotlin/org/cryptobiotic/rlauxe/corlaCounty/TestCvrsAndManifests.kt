package org.cryptobiotic.rlauxe.corlaCounty

import kotlin.test.Test

class TestCvrsAndManifests {

    @Test
    fun testMorgan26match() {
        val input = Morgan26Input()
        compareCvrsAndManifests(input)
    }

}