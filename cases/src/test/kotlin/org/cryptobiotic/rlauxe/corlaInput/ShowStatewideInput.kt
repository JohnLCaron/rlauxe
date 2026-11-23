package org.cryptobiotic.rlauxe.corlaInput

import kotlin.test.Test

// check name consistency in StatewideInput
class ShowStatewideInput {
    val input: StatewideInput = Statewide2026General()
    val canonical = input.canonicalContests()

    @Test
    fun showCanonicalContests() {
        canonical.forEach { println( it )}
        println("there are ${canonical.size} auditcenter contests\n")

        var miss = 0
        val rounds = input.roundContests()
        canonical.forEach {
            val round = rounds[it.key]
            if (round == null) {
                println("canon ${it.key} missing round")
                miss++
            }
        }
            println(" missing total $miss" )
    }

    @Test
    fun showCanonicalCounties() {
        val counties = input.counties()
        counties.forEach { println( it )}
        println("there are ${counties.size} auditcenter counties")

        val inputCounties = input.counties()
        println("there are ${inputCounties.size} input counties")
        compareLists(counties, inputCounties, "auditcenter Counties", "inputCounties")
    }

    @Test
    fun showRoundContests() {
        println("there are ${input.roundContests().size} roundContests")
        input.roundContests().forEach { println( it )}
    }

    @Test
    fun showContestTabsAllCounties() {
        println("there are ${input.contestTabsAllCounties().size } contestTabsAllCounties")
        input.contestTabsAllCounties().values.forEach { println(" '${it.contestName}' == ${it.counties}")}
    }

    @Test
    fun showCountyTabsAllContests() {
        println("there are ${input.countyTabsAllContests().size} countyTabsAllContests")
        input.countyTabsAllContests().forEach { println( it )}
    }

    @Test
    fun showMvrs() {
        val ccr = input.cardComparison()
        println("there are ${ccr.countyMvrs.size} countyMvrs")
        println("there are ${ccr.mvrs.size} mvrs")
        println("there are ${ccr.stylesByCounty.size} stylesByCounty")
    }

    /*
    @Test
    fun showContestsFromMvrs() {
        println("there are ${input.contestsFromMvrs.size} contestsFromMvrs")
        input.contestsFromMvrs.forEach { println( it )}
    } */

    @Test
    fun showCountiesFromMvrs() {
        println("there are ${input.countiesFromMvrs.size} countiesFromMvrs")
        input.countiesFromMvrs.forEach { println( it )}
    }

    // data class MergedInfo(
    //    val mergedContestInfo: List<MergedContestInfo>,
    //    val strataInfo: List<StrataInfo>,
    //    val statewideContests: List<CorlaContestRoundCsv>,
    //)
    @Test
    fun showMergedContestInfo() {
        println("there are ${input.mergedInfo().mergedContestInfo.size} mergedContestInfo")
        input.mergedInfo().mergedContestInfo.forEach { println("  $it") }
    }

    @Test
    fun showCanonicalContestInfo() {
        val mergedInfo = input.mergedInfo()
        println("there are ${mergedInfo.mergedContestInfo.size} mergedContestInfo")
        mergedInfo.mergedContestInfo.forEach { println("  ${it.contestName} == ${it.corlaNc}") }
    }

    @Test
    fun showStrataInfo() {
        println("\nthere are ${input.mergedInfo().countyStrata.size} strataInfo")
        input.mergedInfo().countyStrata.forEach { println("  $it") }
    }

    @Test
    fun showStrataPopulation() {
        println("\nthere are ${input.countyPopulation().size} strataPopulation")
        input.countyPopulation().forEach { println("  $it") }
    }

    @Test
    fun checkCanonicalNames() {
        CheckCanonicalNames(Statewide2026General())
    }

    @Test
    fun testAllNames() {
        statewideInputs.forEach {
            println("==================================================")
            println(it.name())
            CheckCanonicalNames(it)
        }
    }

}