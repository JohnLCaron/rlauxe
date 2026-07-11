package org.cryptobiotic.rlauxe.strata

import org.cryptobiotic.rlauxe.audit.ContestRound
import org.cryptobiotic.rlauxe.betting.estSampleSizeStandardBet
import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.util.roundToClosest
import kotlin.collections.set
import kotlin.math.max
import kotlin.text.drop

// for one county or a multicounty
data class Strata(
    val strataName: String,// county name or a multicounty contest
    val nmvrs: Int,  // may be wantMvrs or haveMvrs
    val population: Int,
)

//// calculate how many samples each contest has, using Neils algorithm for multicounty samples
// this is used to measure the risk for ColoradoRLA uniform sampling
// countyStrata input holds the single contest counties haveMvrs
// val useAll = auditRound.auditorMaxNewMvrs != null
fun setHaveSampleSize(contestsIncluded: List<ContestRound>, countyStrata: Map<String, Strata>, useAll: Boolean): Map<String, Int> {
    if (useAll) {
        contestsIncluded.forEach { contestRound: ContestRound ->
            val counties = counties(contestRound.contestUA)
            if (counties != null) {
                var sumAcrossCounties = 0
                counties.forEach {
                    sumAcrossCounties += countyStrata[it]?.nmvrs ?: 0
                }
                contestRound.haveSampleSize = sumAcrossCounties
            }
        }
        return countyStrata.mapValues { it.value.nmvrs }
    }
    /* first make the county strata
        val contestMap = contestsIncluded.associateBy { it.name }
    val strataMap = mutableMapOf<String, Strata>()
    haveFromPools.mapValues { (countyName, haveMvrs) ->
        val contest = contestMap[countyName] ?: return@mapValues
        Strata(countyName, haveMvrs, contestMap[it.key])
    } */

    val wantFromPools = mutableMapOf<String, Int>()
    contestsIncluded.forEach { contestRound: ContestRound ->
        val counties = counties(contestRound.contestUA)
        if (counties != null) {
            if (counties.size == 1) {
                contestRound.haveSampleSize = countyStrata[counties.first()]!!.nmvrs
            } else {
                val strata = computeMulticountyStrata(contestRound.name, counties.toSet(), countyStrata)
                contestRound.haveSampleSize = strata.nmvrs
            }
        }
    }
    return wantFromPools
}

// Neals algorithm: use the minimum rate across strata
private fun computeMulticountyStrata(name: String, counties: Set<String>, countyStrata: Map<String, Strata>): Strata {
    var orgSamples = 0
    val minRate = counties.map {
        val s = countyStrata[it]
        if (s == null) 1.0 else {
            orgSamples += s.nmvrs
            s.nmvrs / s.population.toDouble()
        }
    }.min()

    var npop = 0
    var nmvrs = 0
    counties.forEach {
        val strata = countyStrata[it]
        if (strata != null) {
            npop += strata.population
            val truncSamples = roundToClosest(strata.population * minRate)
            nmvrs += truncSamples
        }
    }
    return Strata(name, nmvrs, npop)
}

// get list of counties that this contest has
private fun counties(contestUA: ContestWithAssertions): List<String>? {
    var counties = contestUA.contest.info().metadata.get("CORLAcounties")
    if (counties == null) contestUA.contest.info().metadata.get("Counties")
    if (counties == null) return null
    if (counties.startsWith("["))
        counties = counties.drop(1).dropLast(1) // [county1, county2]
    return counties.split(",".toRegex()).dropLastWhile { it.isEmpty() }
}

////////////////////////////
// unused ??

fun calcCountyStrataWant(contestsIncluded: List<ContestRound>, alpha: Double): List<Strata> {
    val wantFromPools = calcWantedFromCountyPools(contestsIncluded, alpha)

    val countyStrata = mutableListOf<Strata>()
    contestsIncluded.forEach { contestRound: ContestRound ->
        val counties = counties(contestRound.contestUA)
        if (counties != null && counties.size == 1) {
            val county = counties.first()
            val wantFromPool = wantFromPools[county]!!
            countyStrata.add(Strata( county,wantFromPool, contestRound.contestUA.population()))
        }
    }
    return countyStrata
}

private fun calcWantedFromCountyPools(contestsIncluded: List<ContestRound>, alpha: Double): Map<String, Int> {
    val wantFromPools = mutableMapOf<String, Int>()
    contestsIncluded.forEach { contestRound: ContestRound ->
        val counties = counties(contestRound.contestUA)
        if (counties != null && counties.size == 1) {
            val county = counties.first()
            val wantFromPool = wantFromPools.getOrDefault(county, 0)
            val risk = contestRound.auditorWantRisk ?: alpha
            wantFromPools[county] = max(wantFromPool, calcEstMvrs(contestRound.contestUA, risk))
        }
    }
    return wantFromPools
}

private fun calcEstMvrs(contestUA: ContestWithAssertions, alpha: Double): Int {
    val minAssertion = contestUA.minClcaAssertion()
    if (minAssertion == null) return 0
    val noerror = minAssertion.noerror // TODO can we use this ??
    return estSampleSizeStandardBet(contestUA.population(), noerror, alpha)
}