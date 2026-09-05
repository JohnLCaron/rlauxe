package org.cryptobiotic.rlauxe.corlaCounty

import org.cryptobiotic.rlauxe.boulder.Boulder26pInput
import kotlin.test.Test

class TestCvrsAndManifests {

    @Test
    fun testCorlaCountyInputMatch() {
        val input = Boulder26pInput()
        compareCvrsAndManifests(input)
    }

}