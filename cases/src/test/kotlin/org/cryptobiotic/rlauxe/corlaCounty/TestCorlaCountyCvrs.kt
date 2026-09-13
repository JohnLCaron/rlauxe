package org.cryptobiotic.rlauxe.corlaCounty

import org.cryptobiotic.rlauxe.corlaInput.Colorado2020General
import org.cryptobiotic.rlauxe.corlaInput.Colorado2026PwithCvrs
import kotlin.test.Test

class TestCorlaCountyCvrs {

    @Test
    fun testOneCorlaCountyInput() {
        val stateInput = Colorado2026PwithCvrs()
        val input = stateInput.corlaCountyCvrs("Boulder")!!

        CheckCvrsAndManifest(stateInput, input, compareMissingVotes=true)
    }

    @Test
    fun testAllCorlaCountyInput() {
        val stateInput = Colorado2020General()
        stateInput.counties().forEach { county ->
            val input = stateInput.corlaCountyCvrs(county)!!
            CheckCvrsAndManifest(stateInput, input)
        }
    }

}