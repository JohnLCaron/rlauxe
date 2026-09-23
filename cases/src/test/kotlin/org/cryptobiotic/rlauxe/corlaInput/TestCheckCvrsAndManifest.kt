package org.cryptobiotic.rlauxe.corlaInput

import kotlin.test.Test

class TestCheckCvrsAndManifest {

    @Test
    fun testOneCorlaCountyInput() {
        val stateInput = Colorado2026PwithCvrs()
        val input = stateInput.corlaCountyInput("Boulder")!!

        CheckCvrsAndManifest(stateInput, input, compareMissingVotes = true)
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