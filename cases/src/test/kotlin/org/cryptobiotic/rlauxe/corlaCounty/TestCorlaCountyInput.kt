package org.cryptobiotic.rlauxe.corlaCounty

import org.cryptobiotic.rlauxe.boulder.Boulder26pInput
import kotlin.test.Test

class TestCorlaCountyInput {

    @Test
    fun testCorlaCountyInput() {
        val input = Boulder26pInput()
        compareCvrsAndManifests(input)
    }

}