package org.cryptobiotic.rlauxe.corlaInput

import kotlin.test.Test

class TestCheckCvrsAndManifest {

    @Test
    fun testOneCountyInput() {
        val stateInput = Statewide2020General()
        val input = stateInput.corlaCountyInput("Garfield")!!

                CheckCvrsAndManifest(stateInput, input, compareMissingVotes = true, showMatch = true)
    }

    @Test
    fun testAllCorlaCountyInput() {
        val stateInput = Statewide2020General()
        stateInput.counties().forEach { county ->
                val input = stateInput.corlaCountyInput(county)!!
                CheckCvrsAndManifest(stateInput, input)
        }
    }
}