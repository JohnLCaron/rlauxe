package org.cryptobiotic.rlauxe.corla

import kotlin.collections.contains
import kotlin.test.Test

class TestContestNames {
    val filename = "src/test/data/corla/2024audit/2024GeneralCanonicalList.csv"
    val canonical = readGeneralCanonicalList(filename).associateBy { it.contestName }
    val extra = mutableListOf<CanonicalContest>()

    // @Test
    fun showGeneralCanonicalList() {
        canonical.values.forEach { println(it.contestName)}
    }

    //        these have been tested to agree with tabulateCountyFile, contestRoundFile, mvrComparisonFile

    @Test
    fun checkCanonicalHasCountyTabulate() {
        val filename = "src/test/data/corla/2024audit/tabulateCounty.csv"
        val contests = readCountyTabulateCsv(filename, { it }, { it })
        println("CountyTabulateCsv $filename missing")
        contests.values.forEach {
            if (!canonical.contains(it.contestName)) {
                println("  '${it}'")
                val addIt = CanonicalContest(it.contestName, it.choices.keys.toList())
                addIt.counties.addAll(it.counties())
                extra.add(addIt)
            } else {
                val canonicalChoices: Set<String> = canonical[it.contestName]!!.choices.toSet()
                it.choices.forEach {
                    if (!canonicalChoices.contains(it.key)) println(" missing choice '${it.key}'")
                }
            }
        }
        println("add these:")
        extra.forEach { println(it) }
        println()
        //  ''Bannock Ballot Issue 6A'
        //  'Yes'= 14 [Douglas=14, ]
        //  'No'= 9 [Douglas=9, ]
        //'
        //  ''Spring Canyon Ballot Issue 6B'
        //  'Yes'= 29 [Douglas=29, ]
        //  'No'= 19 [Douglas=19, ]
        //'
        //add these:
        //CanonicalContest('Bannock Ballot Issue 6A', choices=[Yes, No], counties=[Douglas])
        //CanonicalContest('Spring Canyon Ballot Issue 6B', choices=[Yes, No], counties=[Douglas])
    }

    @Test
    fun checkCountyTabulateHasCanonical() {
        val filename = "src/test/data/corla/2024audit/tabulateCounty.csv"
        val contests = readCountyTabulateCsv(filename, { it }, { it })
        canonical.values.forEach {
            if (!contests.contains(it.contestName)) {
                println("countyTabulate missing canonical '${it}'")
            }
        }
    }

    @Test
    fun checkCanonicalHasContestRound() {
        val filename = "src/test/data/corla/2024audit/round1/contest.csv"
        val contests = readColoradoContestRoundCsv(filename) { it }
        println("ColoradoContestRoundCsv $filename missing")
        contests.values.forEach {
            if (!canonical.contains(it.contestName)) {
                println("  '${it.contestName}'")
            }
        }
        //   'Bannock Ballot Issue 6A'
        //  'Spring Canyon Ballot Issue 6B'
    }

    @Test
    fun checkContestRoundHasCanonical() {
        val filename = "src/test/data/corla/2024audit/round1/contest.csv"
        val contests = readColoradoContestRoundCsv(filename) { it }
        canonical.values.forEach {
            if (!contests.contains(it.contestName)) {
                println("countyRound missing canonical '${it.contestName}'")
            }
        }
    }

    @Test
    fun checkCanonicalHasContestComparison() {
        val filename = "src/test/data/corla/2024audit/round3/contestComparison.csv"
        val (contestMvrs, countyMvrs, countyStyles) = readContestComparisonCsv(filename) { it  }

        println("ContestComparisonCsv $filename missing")
        contestMvrs.forEach {
            if (!canonical.contains(it.contestName)) println("  '${it.contestName}'")
        }
    }

    @Test
    fun checkContestComparisonHasCanonical() {
        val filename = "src/test/data/corla/2024audit/round3/contestComparison.csv"
        val (contestMvrs, countyMvrs, countyStyles) = readContestComparisonCsv(filename) { it  }
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

    // not needed

    //@Test
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

        println("ColoradoElectionDetail $filename missing")
        detail.contests.forEach {
            if (!canonical.contains(it.text)) println("  '${it.text}'")
        }
        //  'Colorado Supreme Court Justice - Márquez'
        //  'Colorado Court of Appeals Judge - Román'
        //  'District Court Judge - 2nd Judicial District - Bailey'
        //  'District Court Judge - 2nd Judicial District - Espinosa'
        //  'District Court Judge - 2nd Judicial District - Grant'
        //  'District Court Judge - 2nd Judicial District - Moses'
        //  'District Court Judge - 2nd Judicial District - Myers'
        //  'District Court Judge - 2nd Judicial District - Schutte'
        //  'District Court Judge - 2nd Judicial District - Scoville'
        //  'District Court Judge - 2nd Judicial District - Trujillo'
        //  'Arapahoe County Court Judge - Hernandez'
        //  'Arapahoe County Court Judge - Williford'
        //  'Bent County Court Judge - Clark'
        //  'Boulder County Court Judge - Martin'
        //  'Broomfield County Court Judge - DeWick'
        //  'Chaffee County Court Judge - Bull'
        //  'Cheyenne County Court Judge - Eiring'
        //  'Clear Creek County Court Judge - Jones'
        //  'Conejos County Court Judge - Kelly'
        //  'Delta County Court Judge - Zeerip'
        //  'Denver County Court Judge - Blackett'
        //  'Denver County Court Judge - Cherry'
        //  'Denver County Court Judge - Faragher'
        //  'Denver County Court Judge - Goble'
        //  'Denver County Court Judge - Pallares'
        //  'Denver County Court Judge - Rodarte'
        //  'Denver County Court Judge - Rudolph'
        //  'Denver County Court Judge - Schwartz'
        //  'Denver County Court Judge - Simonet'
        //  'Denver County Court Judge - Spahn'
        //  'Douglas County Court Judge - Waidler'
        //  'El Paso County Court Judge - Ankeny'
        //  'El Paso County Court Judge - Fennick'
        //  'El Paso County Court Judge - Gerhart'
        //  'El Paso County Court Judge - Katzman'
        //  'El Paso County Court Judge - McKedy'
        //  'Garfield County Court Judge - Roff'
        //  'Gunnison County Court Judge - Burgemeister'
        //  'Jefferson County Court Judge - Burback'
        //  'Jefferson County Court Judge - Carpenter'
        //  'Jefferson County Court Judge - Goman'
        //  'Jefferson County Court Judge - Peper'
        //  'Jefferson County Court Judge - Wheeler'
        //  'Mesa County Court Judge - Grattan'
        //  'Montrose County Court Judge - Beckenhauer'
        //  'Montrose County Court Judge - Harvell'
        //  'Ouray County Court Judge - Thomasson'
        //  'Pitkin County Court Judge - Andrews'
        //  'Pueblo County Court Judge - Silva'
        //  'Pueblo County Court Judge - Vellar'
        //  'Rio Grande County Court Judge - Stenger'
        //  'Routt County Court Judge - Wilson'
        //  'Saguache County Court Judge - Schuenemann'
        //  'San Juan County Court Judge - Edwards'
        //  'Sedgwick County Court Judge - Landry'
        //  'Yuma County Court Judge - Jones'
        //  'City of Aurora Ballot Question 3A'
        //  'Town of Erie Council Member - District 2'
        //  'Byers School District 32J Ballot Issue 5C'
        //  'Holyoke School District RE-1J Ballot Issue 5K'
        //  'Montrose County School District RE-1J Ballot Issue 5A'
        //  'Norwood School District R-2J Ballot Issue 5B'
        //  'Weld County School District RE-8 Ballot Issue 5G'
        //  'Weld County School District RE-8 Ballot Issue 5H'
        //  'Weld County School District RE-10J Ballot Issue 5D'
        //  'Weld County School District RE-3J Ballot Issue 5F'
        //  'Brush Rural Fire Protection District Ballot Issue 7A'
    }
}