package org.cryptobiotic.rlauxe.co.corla

import org.cryptobiotic.rlauxe.auditcenter.AuditReason
import org.cryptobiotic.rlauxe.auditcenter.ComparisonMvr
import org.cryptobiotic.rlauxe.co.input.ContestMergedInfo
import org.cryptobiotic.rlauxe.corla.ManifestEntries
import org.cryptobiotic.rlauxe.corlaCounty.CountyManifest
import org.cryptobiotic.rlauxe.corlaCounty.StateManifest
import org.cryptobiotic.rlauxe.corlaInput.Statewide2026General
import org.cryptobiotic.rlauxe.corlaInput.StatewideInput
import kotlin.collections.forEach
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertNotNull

class PredictSequences2 {

    @Test
    fun hasDuplicates() {
        val ids = listOf(
            225473,
            225776,
            225801,
            225439,
            225937,
            225993,
            225652,
            225811,
            226003,
            225226,
            225733,
            225367,
            225919,
            225908,
            225249,
            225636,
            225652,
            225249,
            225928,
            225890,
            225951,
            225293,
            225693,
            225914,
            225169,
            225923,
            225202,
            225266,
            225236,
            225268,
            225889,
            225407,
            225983,
            225651,
            225498,
            225872,
            225448,
            225300,
            225324,
            225712,
            225983,
            225263,
            225740,
            225781,
            226011,
            225671,
            225537,
            225811,
            225181,
            225420,
            225826,
            225436,
            225603,
            225184,
            225439,
            225184,
            225486,
            225523,
            225571,
            225392,
            225504,
            225633,
            225429,
            225421
        )
        val idSet = ids.toSet()
        println("ids = ${ids.size} idSet = ${idSet.size}")
    }

    @Test
    fun predictSequences2() {
        val input = Statewide2026General()
        readCountyInputs(input)
    }

    fun readCountyInputs(input: StatewideInput) {
        // data class CanonicalContest(
        //    val contestName: String,
        //    val choices: List<String>
        //    val counties =  mutableSetOf<String>()
        var targets = 0
        val mcontests: Map<String, ContestMergedInfo> = input.mergedContestMap
        val countySamples = mutableMapOf<String, Int>()
        mcontests.values.forEach { mcontest ->
            if (mcontest.corlaReason == AuditReason.county_wide_contest) {
                val county = mcontest.counties.first()
                val currentMax = countySamples.getOrDefault(county, 0)
                countySamples[county] = max(currentMax, mcontest.corlaEstSamples)
                targets++
            }
            if (mcontest.corlaReason == AuditReason.state_wide_contest) {
                val currentMax = countySamples.getOrDefault("Colorado", 0)
                countySamples["Colorado"] = max(currentMax, mcontest.corlaEstSamples)
                targets++
            }
        }
        println("statewide nsamples=${countySamples["Colorado"]} total targeted contests = $targets") // max or sum ??

        println("----------------------------------------")
        var totalSamples = countySamples["Colorado"] ?: 0
        var totalBatches = 0
        var totalCards = 0

        input.counties().forEach { county ->
            val countyInput = input.corlaCountyInput(county)
            assertNotNull(countyInput)
            val me: ManifestEntries = countyInput.readManifestEntries()
            val nsamples = countySamples[county]
            println("${county}: nbatches = ${me.manifestBatches.size} ncards = ${me.totalCards} nsamples=$nsamples")
            totalSamples += nsamples ?: 0
            totalBatches += me.manifestBatches.size
            totalCards += me.totalCards
        }
        println()
        println("ncounties = ${input.counties().size}")
        println("totalBatches = ${totalBatches}")
        println("totalCards = ${totalCards}")
        println("totalSamples = ${totalSamples}")
        // assertEquals(inputs.counties.size, expectCounties ?: sm.manifests.size)
        // assertEquals(count, expectCards)

        // ncounties = 63
        //totalBatches = 52332
        //totalCards = 4384669
        //////////////////////////////////////////////////////////////////////////
        var countPrns = 0
        val seed = "38589646444703560910"

        val allprns = mutableListOf<String>()

        var countyHasDuplicate = 0
        input.counties().forEach { county ->
            val nsamples = countySamples[county]!!
            val countyManifest = input.countyManifest(county)!!
            val countyPrng = CorlaPrngCounty(seed, countyManifest, county, nsamples)
            allprns.addAll(countyPrng.prnIds)
            countPrns += countyPrng.prnIds.size
            if (countyPrng.prng.duplicates.size > 0) {
                countyHasDuplicate++
                println("duplicateIds = ${countyPrng.duplicateIds}")
            }
        }

        val statePrng = CorlaPrngState(
            seed,
            input.statewideManifest(),
            population = totalCards,
            statewideNmvrs = countySamples["Colorado"]!!
        )
        allprns.addAll(statePrng.prnIds)
        countPrns += statePrng.prnIds.size
        if (statePrng.prng.duplicates.size > 0) {
            println("state duplicateIds = ${statePrng.duplicateIds}")
        }

        //File("/home/joncifer/rla/temp/predict.txt").printWriter().use { out ->
        //     allprns.sorted().forEach { out.println(it) }
        //}
        println()
        println("countPrns = $countPrns countyHasDuplicate=$countyHasDuplicate")
    }

}

class CorlaPrngState(val seed: String, val stateManifest: StateManifest, val population: Int, val statewideNmvrs: Int) {
    val prnIds: List<String>
    val prnIdSet: Set<String>
    val prng: CorlaPrng
    val duplicateIds: List<String>

    init {
        prng = CorlaPrng(seed, true, 1, population) // without replacement (!)

        val prns = mutableListOf<Int>()
        repeat(statewideNmvrs) { prns.add(prng.next()) }

        println("  prns size=${prns.size} unique=${prns.toSet().size} duplicates = ${prng.duplicates.size}")

        println("  stateManifest.totalCards=${stateManifest.totalCards}")

        prnIds = prns.map { prn -> stateManifest.idFromIndex(prn) }
        prnIdSet = prns.map { prn -> stateManifest.idFromIndex(prn) }.toSet()
        println("  ids size=${prnIds.size} unique=${prnIdSet.size}")

        duplicateIds = prng.duplicates.map { prn -> stateManifest.idFromIndex(prn) }
    }

    fun claim(id: String): Boolean {
        return prnIdSet.contains(id)
    }
}

class CorlaPrngCounty(val seed: String, val countyManifest: CountyManifest, val county: String, countyMvrs: Int) {
    val prnIds: List<String>
    val prnIdSet: Set<String>
    val prng: CorlaPrng
    val duplicateIds: List<String>

    init {
        println("-------------- $county")
        // modulus max - min + 1 = 1_444_047
        prng = CorlaPrng(seed, true, 1, countyManifest.nballotCards) // without replacement (!)
        println("  county population=${countyManifest.nballotCards} nmvrs =${countyMvrs}") // 2020 doesnt know which are statewide

        val prns = mutableListOf<Int>()
        repeat(countyMvrs) { prns.add(prng.next()) }
        println("  prns size=${prns.size} unique=${prns.toSet().size} duplicates = ${prng.duplicates.size}")

        prnIds = prns.map { prn -> countyManifest.idFromIndex(prn, 0) }
        prnIdSet = prnIds.toSet()
        println("  ids size=${prnIds.size} unique=${prnIdSet.size}")

        duplicateIds = prng.duplicates.map { prn -> countyManifest.idFromIndex(prn, 0) }
    }

    fun claim(id: String, mvr: ComparisonMvr): Boolean {
        return prnIdSet.contains(id)
    }
}