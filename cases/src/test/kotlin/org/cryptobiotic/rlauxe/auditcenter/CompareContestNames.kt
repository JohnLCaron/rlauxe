package org.cryptobiotic.rlauxe.auditcenter

import org.cryptobiotic.rlauxe.corla.Colorado2024Input
import org.cryptobiotic.rlauxe.corla.ColoradoInput
import org.cryptobiotic.rlauxe.corla.readColoradoContestRoundCsv
import org.cryptobiotic.rlauxe.corla.readContestComparisonCsv
import org.cryptobiotic.rlauxe.corla.readCountyTabulateCsv
import org.cryptobiotic.rlauxe.corla.readGeneralCanonicalList
import kotlin.test.Test

// all identical names
class CompareContestNames {
    val input1: ColoradoInput = Colorado2024Input()
    val input2: ColoradoInput = Colorado2024AuditCenterInput()

    @Test
    fun compareGeneralCanonicalList() {
        val canon1 = readGeneralCanonicalList(input1.generalCanonicalFile)
        val canon2 = readGeneralCanonicalList(input2.generalCanonicalFile)

        println("compare canonical contest names")
        compareLists(
            canon1.map { it.contestName },
            canon2.map { it.contestName },
        )

        println("compare canonical county names")
        val counties1 = canon1.map { it.counties }.flatten().toSet().toList()
        val counties2 = canon2.map { it.counties }.flatten().toSet().toList()

        compareLists(counties1, counties2)
    }

    @Test
    fun compareGeneralCountyTabulate() {
        val countyTab1 = readCountyTabulateCsv(input1.tabulateCountyFile)
        val countyTab2 = readCountyTabulateCsv(input2.tabulateCountyFile)

        println("compare countyTab contest names")
        compareLists(
            countyTab1.map { it.key },
            countyTab2.map { it.key },
        )
    }

    @Test
    fun compareGeneralCountyRound() {
        val countyRound1 = readColoradoContestRoundCsv(input1.contestRoundFile)
        val countyRound2 = readColoradoContestRoundCsv(input2.contestRoundFile)

        println("compare contestRound contest names")
        compareLists(
            countyRound1.map { it.key },
            countyRound2.map { it.key },
        )
    }

    @Test
    fun compareMvrContests() {
        val mvr1 = readContestComparisonCsv(input1.mvrComparisonFile)
        val mvr2 = readContestComparisonCsv(input2.mvrComparisonFile)

        println("compare mvr contest names")
        compareLists(
            mvr1.contestMvrs.map { it.contestName },
            mvr2.contestMvrs.map { it.contestName },
        )

        println("compare mvr county names")
        compareLists(
            mvr1.countyMvrs.map { it.countyName },
            mvr2.countyMvrs.map { it.countyName },
        )

        println("compare mvr style names")
        compareLists(
            mvr1.stylesByCounty.map { it.countyName },
            mvr2.stylesByCounty.map { it.countyName },
        )
    }
}

fun compareLists(list1: List<String>, list2: List<String>, name1: String = "canonical1", name2: String = "canonical2") {
    list1.forEach {
        if (!list2.contains(it)) {
            println(" $name2 doesnt have '${it}' from $name1")
        }
    }
    list2.forEach {
        if (!list1.contains(it)) {
            println(" $name1 doesnt have '${it}' from $name2")
        }
    }
}