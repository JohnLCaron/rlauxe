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

class TestColoradoInput {
    val input: ColoradoInput = Colorado2024AuditCenterInput()

    val canonical = readGeneralCanonicalList(input.generalCanonicalFile).associateBy { it.contestName }

    @Test
    fun showCanonicalContests() {
        canonical.values.forEach { println( it.contestName )}
        println("there are ${canonical.size} canonical contests")
    }

    @Test
    fun showCanonicalCounties() {
        val counties = canonical.values.map { it.counties }.flatten().toSet().toList().sorted()
        counties.forEach { println( it )}
        println("there are ${counties.size} canonical counties")
    }

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
    fun checkCountyTabulateHasCanonicalContests() {
        println("\n--------------------------------------------------------------------------")
        val countyTabs = readCountyTabulateCsv(input.tabulateCountyFile)
        val countiesFromTab = countyTabs.values.map { it.counties() }.flatten().toSet().toList()

        println("compare canonical counties and countiesFromTabs")
        compareLists(input.counties(), countiesFromTab, "canonical", "countiesFromTabs")
    }

    @Test
    fun checkCanonicalHasContestRound() {
        println("\n--------------------------------------------------------------------------")
        val contests = readColoradoContestRoundCsv(input.contestRoundFile)
        println("generalCanonicalFile ${input.contestRoundFile} missing roundContest:")
        contests.values.forEach {
            if (!canonical.contains(it.contestName)) {
                println("  '${it.contestName}'")
            }
        }
        //  'Adams County Ballot Issue 1A'
    }

    @Test
    fun checkContestRoundHasCanonical() {
        println("\n--------------------------------------------------------------------------")
        val contests = readColoradoContestRoundCsv(input.contestRoundFile)
        canonical.values.forEach {
            if (!contests.contains(it.contestName)) {
                println("countyRound missing canonical '${it.contestName}'")
            }
        }
    }

    @Test
    fun checkCanonicalHasContestComparison() {
        println("\n--------------------------------------------------------------------------")
        val (contestMvrs, countyMvrs, countyStyles) = readContestComparisonCsv(input.mvrComparisonFile)

        println("generalCanonicalFile ${input.mvrComparisonFile} missing mvrComparisonContest")
        contestMvrs.forEach {
            if (!canonical.contains(it.contestName)) println("  '${it.contestName}'")
        }
    }

    // now correct the canonical files and run this
    @Test
    fun checkCorrectedCanonicalContests() {
        val canonical = input.canonicalContests()

        val contestTabs = input.contestTabsByCounty
        contestTabs.forEach {
            assertTrue(canonical.contains(it.key))
        }

        val roundContests = input.roundContests
        roundContests.forEach {
            assertTrue(canonical.contains(it.key), "canonical missing roundContest '${it.key}'")
        }

        input.contestMvrs.forEach {
            assertTrue(canonical.contains(it.contestName))
        }

        canonical.forEach {
            assertTrue(contestTabs.contains(it.key))
            assertTrue(roundContests.contains(it.key))
        }

    }

    // @Test comparision contests are a subset
    fun checkContestComparisonHasCanonical() {
        println("\n--------------------------------------------------------------------------")
        val (contestMvrs, countyMvrs, countyStyles) = readContestComparisonCsv(input.mvrComparisonFile)
        val mvrMap = contestMvrs.associateBy{ it.contestName }
        var count = 0
        canonical.values.forEach {
            if (!mvrMap.contains(it.contestName)) {
                println("contestComparison missing canonical '${it.contestName}'")
                count++
            }
        }
        println("missing $count")
    }


    //// not needed

    // @Test
    fun checkResultsReportContest() {
        val filename = "src/test/data/corla/2024audit/round1/ResultsReportSummary.csv"
        val contests = readResultsReportContest(filename) { it }
        println("ResultsReportSummary $filename missing")
        contests.forEach {
            if (!canonical.contains(it.contestName)) println("  '${it.contestName}'")
        }
        //   'Bannock Ballot Issue 6A'
        //  'Spring Canyon Ballot Issue 6B'
    }

    //@Test
    fun checkTargetedContestsCsv() {
        val filename = "src/test/data/corla/2024audit/targetedContests.csv"
        val targets = readTargetedContestsCsv(filename) { it }

        println("ContestComparisonCsv $filename missing")
        targets.forEach {
            if (!canonical.contains(it.contestName)) println("  '${it.contestName}'")
        }
        //  'Bent County Commissioner - District 1'
        //  'City and County of Broomfield Ballot Question 2G'
        //  'Cheyenne County Court Judge - Eiring'
        //  'Conejos County Commissioner - District 3'
        //  'Costilla County Commissioner - District 3'
        //  'Custer County Commissioner - District 2'
        //  'Delta County Court Judge - Zeerip'
        //  'City and County of Denver Ballot Issue 2Q'
        //  'Fremont County Commissioner - District 3'
        //  'Garfield County Commissioner - District 2'
        //  'Hinsdale County Commissioner - District 1'
        //  'North Park School District R-1 Ballot Issue 4A'
        //  'La Plata County Commissioner - District 3'
        //  'Las Animas County Commissioner - District 2'
        //  'Montezuma County Ballot Issue 1A'
        //  'Park County Commissioner - District 1'
        //  'Pitkin County Ballot Issue 1A'
        //  'Rio Grande County Court Judge - Stenger'
        //  'Saguache County Commissioner - District 1'
        //  'San Miguel County Ballot Measure 1A'
        //  'Yuma County Court Judge - Jones'
    }

    //@Test
    fun checkColoradoElectionDetail() {
        val detailXmlFile = "src/test/data/corla/2024election/detail.xml"
        val detail = readColoradoElectionDetail(detailXmlFile)

        println("ColoradoElectionDetail missing")
        detail.contests.forEach {
            if (!canonical.contains(it.text)) println("  '${it.text}'")
        }
    }
}