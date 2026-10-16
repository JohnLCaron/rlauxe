package org.cryptobiotic.rlauxe.corlaInput

import kotlin.test.Test

class TestCheckCvrsAndManifest {

    @Test
    fun testOneCorlaCountyInput() {
        val stateInput = Statewide2026PwithCvrs()
        val input = stateInput.corlaCountyInput("Weld")!!

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