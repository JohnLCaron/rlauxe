package org.cryptobiotic.rlauxe.auditcenter

import kotlin.test.Test

// check name consistency in ColoradoInput
class TestColoradoInput {
    val input: ColoradoInput = Colorado2026PwithCvrs()
    val canonical = input.canonicalContests()

    @Test
    fun showCanonicalContests() {
        canonical.forEach { println( it )}
        println("there are ${canonical.size} auditcenter contests")
    }

    @Test
    fun showCanonicalCounties() {
        val counties = canonical.values.map { it.counties }.flatten().toSet().toList().sorted()
        counties.forEach { println( it )}
        println("there are ${counties.size} auditcenter counties")
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
    fun showContestsFromMvrs() {
        println("there are ${input.contestsFromMvrs.size} contestsFromMvrs")
        input.contestsFromMvrs.forEach { println( it )}
    }

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
        mergedInfo.mergedContestInfo.forEach { println("  ${it.contestName} == ${it.nc}") }
    }

    @Test
    fun showStrataInfo() {
        println("\nthere are ${input.mergedInfo().strataInfo.size} strataInfo")
        input.mergedInfo().strataInfo.forEach { println("  $it") }
    }

    @Test
    fun showStatewideContests() {
        println("\nthere are ${input.mergedInfo().statewideContests.size} statewideContests")
        input.mergedInfo().statewideContests.forEach { println( "  $it" )}
    }

    @Test
    fun showStrataPopulation() {
        println("\nthere are ${input.strataPopulation().size} strataPopulation")
        input.strataPopulation().forEach { println("  $it") }
    }

    @Test
    fun checkCanonicalNames() {
        CheckCanonicalNames(input)
    }

}