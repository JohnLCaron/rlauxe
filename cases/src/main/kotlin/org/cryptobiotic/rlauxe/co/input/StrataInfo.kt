package org.cryptobiotic.rlauxe.co.input

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.co.auditcenter.ContestSelection
import org.cryptobiotic.rlauxe.co.corla.CorlaPrng
import org.cryptobiotic.rlauxe.corlaInput.StatewideInput
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.util.roundToClosest
import org.cryptobiotic.rlauxe.util.trunc
import kotlin.math.max

private val logger = KotlinLogging.logger("StrataInfo")

data class StrataInfo(
    val strataName: String, // county or canonical contest name
    val countyMvrs: Int, // the number of mvrs drawn in the county sampling
    val stateMvrs: Int, // the number of mvrs drawn from the state sampling that are in the county or in one of the contest's counties ??
    val population: Int,  // round.ballot_card_count == population
) {
    val nmvrs = countyMvrs + stateMvrs
}

// Neals algorithm: use the minimum rate across strata
// depends only on the set of counties used by the contest and the StrataIno for those contests
fun computeStrataMinRate(strataName: String, contestCounties: Set<String>, statewideMvrCount: Map<String, Int>, strataMap: Map<String, StrataInfo>): StrataInfo {
    var npop = 0
    var countyMvrCount = 0
    var stateMvrCount = 0 // sum of mvrs in the counties that the contest is in
    val rates = contestCounties.map {
        stateMvrCount += statewideMvrCount[it] ?: 0
        val strata = strataMap[it]

        if (strata == null || strata.nmvrs == 0) 1.0 else {
            npop += strata.population
            countyMvrCount += strata.countyMvrs                              // includes both state and county samples that are in this county
            strata.countyMvrs / strata.population.toDouble()        // but it does seem that the state samples should be added without scaling by min rate, at least for targeted contest ??
        }
    }
    val minRate = rates.min()

    // reduuce the county cvrs to the minimum rate, so there is a unifor rate across the counties
    var nmvrs = 0
    contestCounties.forEach {
        val strata = strataMap[it]
        if (strata != null && strata.countyMvrs != 0) {
            val truncSamples = roundToClosest(strata.population * minRate)
            nmvrs += truncSamples
        }
    }
    return StrataInfo(strataName, nmvrs, stateMvrCount, npop)
}

fun makeCountyStrata(input: StatewideInput, show: Boolean = false): List<StrataInfo> {
    val countySamples = mutableMapOf<String, Int>()
    var multi = 0
    // ContestSelection have list of dbids
    val selected: Map<String, ContestSelection> = ContestSelection.readFromFile(input.contestSelectionFile)
    selected.values.filter { it.dbList.isNotEmpty() }.forEach { cs ->
        val cc = input.matchCanonicalContest(cs.contest_name)
        if (cc == null) logger.warn{ "Cant find ${cs.contest_name}"} else {
            // println("${cs.dbList.size} mvrs for ${cc.counties}")
            if (cc.counties.size == 1) {
                val countyName = cc.counties.first()
                if (!input.skipCounties(countyName)) {
                    val cc = countySamples.getOrDefault(countyName, 0)
                    countySamples[countyName] = max(cc, cs.dbList.size)
                }
            }
            else multi = max(multi, cs.dbList.size)
        }
    }

    if (show) {
        println("counties (${countySamples.size}) total = ${countySamples.values.sum()} multi = $multi")
        countySamples.toSortedMap().forEach { println("${nfn(it.value, 3)} for ${trunc(it.key, -15)}") }
    }

    // we need to find what county the statewide samples are in
    val stateSamples = mutableMapOf<String, Int>()
    val stateManifest = input.statewideManifest()
    val prng = CorlaPrng(input.seed, true, 1, stateManifest.totalCards) // without replacement (!)
    repeat(multi) {
        val prn = prng.next()
        val id = stateManifest.idFromIndex(prn)
        val countyName = id.split(":").first()
        val cc = stateSamples.getOrDefault(countyName, 0)
        stateSamples[countyName] = cc + 1
    }

    val result = mutableListOf<StrataInfo>()
    countySamples.toSortedMap().forEach {
        val countyManifest = input.countyManifest(it.key)!!
        result.add(StrataInfo(it.key, it.value, stateSamples[it.key] ?: 0, countyManifest.nballotCards))
    }
    return result
}