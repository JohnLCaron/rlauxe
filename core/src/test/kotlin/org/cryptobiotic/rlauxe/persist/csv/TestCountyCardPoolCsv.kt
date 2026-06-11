package org.cryptobiotic.rlauxe.persist.csv

import kotlin.test.Test
import kotlin.test.assertEquals

import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.audit.CardStyle
import org.cryptobiotic.rlauxe.audit.CountyPoolMultipleStyles
import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.util.makeContestsWithUndervotesAndPhantoms
import org.cryptobiotic.rlauxe.util.tabulateCvrs
import kotlin.collections.forEach

class TestCountyCardPoolCsv {

    @Test
    fun testRegVotes() {
        val infos = mutableMapOf<Int, ContestInfo>()
        // val regPool = makeRegPool(infos)

        val target = listOf(makeRegPool(infos), makeRegPool(infos), makeRegPool(infos),)

        val csvFile = "$testdataDir/tests/scratch/countyCardPoolCsvFile.csv"
        writeCountyCardPoolCsvFile(target, csvFile)

        val roundtrip = readCountyCardPoolCsvFile(csvFile, target.first().styles)
        assertEquals(target.size, roundtrip.size)

        roundtrip.forEachIndexed { pidx, rpool ->
            val tpool = target[pidx]
            println(tpool)
            println(rpool)

            rpool.contestTabs.forEach { rtab ->
                val ttab = tpool.contestTabs[rtab.contestId]
                assertEquals(ttab, rtab)
            }

            assertEquals(tpool, rpool)
            println("--------------------------------------------------")
        }

        // assertEquals(target, roundtrip)
    }

    var poolId = 0
    fun makeRegPool(infos: MutableMap<Int, ContestInfo>): CountyPoolMultipleStyles {
        val candVotes = mutableListOf<Map<Int, Int>>()
        candVotes.add(mapOf(0 to 200, 1 to 123, 2 to 17))
        candVotes.add(mapOf(0 to 71, 1 to 123, 2 to 0, 3 to 77, 4 to 99))
        candVotes.add(mapOf(0 to 102, 1 to 111))

        val undervotes = listOf(15, 123, 3)
        val phantoms = listOf(2, 7, 0)
        val voteForNs = listOf(1, 2, 1)
        val (contests, cvrs) = makeContestsWithUndervotesAndPhantoms(
            candVotes,
            undervotes = undervotes, phantoms = phantoms, voteForNs = voteForNs
        )
        val style = CardStyle(99, setOf(0, 1, 2))

        val myinfos = contests.associate { Pair(it.id, it.info) }
        val tabs: Map<Int, ContestTabulation> = tabulateCvrs(cvrs.iterator(), myinfos)
        infos.putAll(myinfos)

        poolId++
        return CountyPoolMultipleStyles(
            "testRegVotes", poolId, tabs.values.toList(), 42,
            listOf(style)
        )
    }
}
