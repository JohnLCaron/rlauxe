package org.cryptobiotic.rlauxe.corlacvr

import org.cryptobiotic.rlauxe.corlaInput.Colorado2020General
import kotlin.test.Test

class Test2020Cvrs {

    @Test
    fun testGarfield20Cvrs() {
        val input = Colorado2020General()
        val garfield = input.corlaCountyCvrs("Garfield")!!
        val cvrs = garfield.readCorlaCvrs()
        testCorlaConverterCvrs("Garfield", cvrs, input)
    }

    @Test
    fun testMesa20Cvrs() {
        val input = Colorado2020General()
        val countyInput = input.corlaCountyCvrs("Mesa")!!
        testCorlaConverterCvrs("Mesa", countyInput.readCorlaCvrs(), input)
    }

}