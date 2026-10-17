package org.cryptobiotic.rlauxe.corlaInput

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TestReadManifests {

    @Test
    fun testReadManifests() {
        testReadManifests(Statewide2020General())
        testReadManifests(Statewide2022Primary())
        testReadManifests(Statewide2023Coordinated())
        testReadManifests(Statewide2024General())
        testReadManifests(Statewide2025Coordinated())
        testReadManifests(Statewide2026Primary())
        testReadManifests(Statewide2026PMerged())
        testReadManifests(Statewide2026PwithCvrs(), 4, 191429)
    }

    fun testReadManifests(input: StatewideInput, expectCounties: Int? = null, expectCards: Int? = null) {
        println("----------------------------------------")
        println(input.name())
        val sm = input.statewideManifest()
        assertNotNull(sm)
        println("  totalCards in statewideManifest = ${sm.totalCards} ncounties = ${sm.manifests.size}")
        var count = 0
        input.counties().forEach {
            val cm = input.countyManifest(it)
            assertNotNull(cm)
            println("   $it: ncards = ${cm.nballotCards}")
            count += cm.nballotCards
        }
        println("ncounties = ${input.counties().size}")
        assertEquals(input.counties().size, expectCounties ?: sm.manifests.size)
        assertEquals(count, expectCards ?: sm.totalCards)
    }

}