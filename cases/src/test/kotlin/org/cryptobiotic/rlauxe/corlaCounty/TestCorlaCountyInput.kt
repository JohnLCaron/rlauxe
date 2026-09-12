package org.cryptobiotic.rlauxe.corlaCounty

import org.cryptobiotic.rlauxe.boulder.Boulder26pInput
import org.cryptobiotic.rlauxe.corlaInput.Colorado2020General
import org.cryptobiotic.rlauxe.corlaInput.CorlaCounty2020Input
import kotlin.test.Test

class TestCorlaCountyInput {

    @Test
    fun testOneCorlaCountyInput() {
        val stateInput = Colorado2020General()
        val input = stateInput.corlaCountyInput("Garfield")!!

        CheckCvrsAndManifest(stateInput, input)
    }

    @Test
    fun testAllCorlaCountyInput() {
        val stateInput = Colorado2020General()
        stateInput.counties().forEach { county ->
            val input = stateInput.corlaCountyInput(county)!!
            CheckCvrsAndManifest(stateInput, input)
        }
    }

}