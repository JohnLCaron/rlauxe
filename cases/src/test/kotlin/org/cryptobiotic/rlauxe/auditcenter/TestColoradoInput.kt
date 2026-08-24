package org.cryptobiotic.rlauxe.auditcenter

import kotlin.test.Test

// check name consistency in ColoradoInput
class TestColoradoInput {
    val input: ColoradoInput = Colorado2026PMerged()
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

    @Test
    fun showRoundContests() {
        println("there are ${input.roundContests().size} roundContests")
        input.roundContests().forEach { println( it )}
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
    fun showMergedInfo() {
        println("there are ${input.mergedInfo().mergedContestInfo.size} mergedContestInfo")
        input.mergedInfo().mergedContestInfo.forEach { println( "  $it" )}

        println("\nthere are ${input.mergedInfo().strataInfo.size} strataInfo")
        input.mergedInfo().strataInfo.forEach { println( "  $it" )}

        println("\nthere are ${input.mergedInfo().statewideContests.size} statewideContests")
        input.mergedInfo().statewideContests.forEach { println( "  $it" )}
    }

    @Test
    fun checkCanonicalNames() {
        CheckCanonicalNames(input)
    }

}