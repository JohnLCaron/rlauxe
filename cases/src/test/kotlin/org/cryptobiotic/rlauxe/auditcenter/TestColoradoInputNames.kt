package org.cryptobiotic.rlauxe.auditcenter

import org.cryptobiotic.rlauxe.corla.CanonicalContest
import org.cryptobiotic.rlauxe.corla.ColoradoInput
import org.cryptobiotic.rlauxe.corla.readColoradoContestRoundCsv
import org.cryptobiotic.rlauxe.corla.readColoradoElectionDetail
import org.cryptobiotic.rlauxe.corla.readContestComparisonCsv
import org.cryptobiotic.rlauxe.corla.readCountyTabulateCsv
import org.cryptobiotic.rlauxe.corla.readGeneralCanonicalList
import org.cryptobiotic.rlauxe.corla.readResultsReportContest
import org.cryptobiotic.rlauxe.corla.readTargetedContestsCsv
import kotlin.test.Test
import kotlin.test.assertTrue

// check name consistency in ColoradoInput
class TestColoradoInputNames {
    val input: ColoradoInput = Colorado2020General()

    val canonical = readGeneralCanonicalList(input.generalCanonicalFile).associateBy { it.contestName }
    val canonicalContestNames = canonical.map{ it.key }

    @Test
    fun checkCanonicalHasCountyTabulate() {
        val extras = mutableListOf<CanonicalContest>()

        val contests = readCountyTabulateCsv(input.tabulateCountyFile)
        println("\n--------------------------------------------------------------------------")
        println("generalCanonicalFile missing contests/choices from tabulateCountyFile ${input.tabulateCountyFile}:")
        contests.values.forEach { contest ->
            if (!canonical.contains(contest.contestName)) {
                println("  missing contest '${contest.contestName}'")
                val addIt = CanonicalContest(contest.contestName, contest.choices.keys.toList())
                addIt.counties.addAll(contest.counties())
                extras.add(addIt)
            } else {
                val canonicalChoices: Set<String> = canonical[contest.contestName]!!.choices.toSet()
                contest.choices.forEach {
                    if (!canonicalChoices.contains(it.key)) println("    missing choice  '${it.key}' in contest '${contest.contestName}'")
                }
            }
        }

        println("\nadd the following to canonicalContests")
        extras.forEach { println("  $it") }
        println()
    }

    @Test
    fun checkCountyTabulateHasCanonical() {
        println("\n--------------------------------------------------------------------------")
        val missing = mutableListOf<String>()
        val contests = readCountyTabulateCsv(input.tabulateCountyFile)
        canonical.values.forEach {
            if (!contests.contains(it.contestName)) {
                println("countyTabulate missing canonical '${it.contestName}'")
                missing.add(it.contestName)
            }
        }
        println()
        missing.forEach { println("result.remove(\"$it\")") }
    }

    @Test
    fun checkCountyTabulateAndCanonicalContests() {
        println("\n--------------------------------------------------------------------------")
        val countyTabs = readCountyTabulateCsv(input.tabulateCountyFile)
        val countiesFromTab = countyTabs.values.map { it.counties() }.flatten().toSet().toList()

        println("compare canonical contest counties and CountyTabulateCsv")
        compareLists(input.counties(), countiesFromTab, "canonical", "countyTabulateCsv")
    }

    @Test
    fun checkContestRoundAndCanonicalContests() {
        println("\n--------------------------------------------------------------------------")
        val contestRounds = input.roundContests.values.map { it.contestName }

        println("compare canonical contests and ContestRoundCsv")
        compareLists(canonicalContestNames, contestRounds, "canonical", "ContestRoundCsv")
    }

    @Test
    fun checkCanonicalHasContestComparison() {
        println("\n--------------------------------------------------------------------------")
        val contestMvrs = input.contestMvrs.map { it.contestName }

        println("generalCanonicalFile missing mvrComparisonContest")
        contestMvrs.forEach {
            if (!canonical.contains(it)) println("  '${it}'")
        }
    }

    // now correct ColoradoInput and run this
    @Test
    fun checkCorrectedCanonicalContests() {
        input.canonicalContests().forEach { cc ->
            assertTrue(input.contestTabsByCounty.contains(cc.key), "contestTabsByCounty '${cc.key}' from canonical")
            assertTrue(input.roundContests.contains(cc.key), "roundContests '${cc.key}' from canonical")
        }
    }

    @Test
    fun reportCorrectedCanonicalContests() {
        val inputCanonical = input.canonicalContests()

        input.contestTabsByCounty.forEach {
            if (!inputCanonical.contains(it.key)) println("canonical missing '${it.key}' from contestTabsByCounty")
        }

        input.roundContests.forEach {
            if (!inputCanonical.contains(it.key)) println( "canonical missing '${it.key}' from roundContests")
        }

        input.contestMvrs.forEach {
            if (!inputCanonical.contains(it.contestName)) println("canonical missing '${it.contestName}' from contestMvrs")
        }

        inputCanonical.forEach { cc ->
            assertTrue(input.contestTabsByCounty.contains(cc.key), "contestTabsByCounty '${cc.key}' from canonical")
            assertTrue(input.roundContests.contains(cc.key), "roundContests '${cc.key}' from canonical")
        }
    }
}