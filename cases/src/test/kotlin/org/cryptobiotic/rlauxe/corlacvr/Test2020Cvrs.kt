package org.cryptobiotic.rlauxe.corlacvr

import org.cryptobiotic.rlauxe.corlaInput.Statewide2020General
import kotlin.test.Test

class Test2020Cvrs {
    val input = Statewide2020General()

    @Test
    fun testGarfield20Cvrs() {
        val garfield = input.corlaCountyInput("Garfield")!!
        val cvrs = garfield.readCorlaCvrs()
        testCorlaConverterCvrs("Garfield", cvrs, input)
    }

    @Test
    fun testMesa20Cvrs() {
        val countyInput = input.corlaCountyInput("Mesa")!!
        testCorlaConverterCvrs("Mesa", countyInput.readCorlaCvrs(), input)
    }

    @Test
    fun testArapahoeCvrs() {
        val countyInput = input.corlaCountyInput("Arapahoe")!!
        countyInput.readCorlaCvrs()
    }

    @Test
    fun testEagleCardStyles() {
        val countyInput = input.corlaCountyInput("Eagle")!!
        val corlaCvrs = countyInput.readCorlaCvrs()
        corlaCvrs.cardStyles().sortedBy{it.cardType}.forEach {
            println(it)
        }
    }

}