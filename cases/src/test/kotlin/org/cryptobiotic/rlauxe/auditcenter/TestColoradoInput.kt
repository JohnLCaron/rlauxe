package org.cryptobiotic.rlauxe.auditcenter

import org.cryptobiotic.rlauxe.corla.ColoradoInput
import kotlin.test.Test

// check name consistency in ColoradoInput
class TestColoradoInput {
    val input: ColoradoInput = Colorado2020General()
    val canonical = input.canonicalContests()

    @Test
    fun showCanonicalContests() {
        canonical.values.map{ it.contestName }.sorted().forEach { println( it )}
        println("there are ${canonical.size} auditcenter contests")
    }

    @Test
    fun showCanonicalCounties() {
        val counties = canonical.values.map { it.counties }.flatten().toSet().toList().sorted()
        counties.forEach { println( it )}
        println("there are ${counties.size} auditcenter counties")
    }

}