package org.cryptobiotic.rlauxe.corlaInput

import org.cryptobiotic.rlauxe.auditcenter.ComparisonMvr
import org.cryptobiotic.rlauxe.auditcenter.TargetedContestsCsv
import org.cryptobiotic.rlauxe.auditcenter.readCountyManifestCsv
import org.cryptobiotic.rlauxe.auditcenter.readTargetedContestsCsv
import org.cryptobiotic.rlauxe.betting.estSampleSizeStandardBet
import org.cryptobiotic.rlauxe.corla.CorlaPRNG
import org.cryptobiotic.rlauxe.corla.GarfieldManifestEntries
import org.cryptobiotic.rlauxe.corla.ManifestEntries
import org.cryptobiotic.rlauxe.corlaCounty.CountyManifest
import org.cryptobiotic.rlauxe.corlaCounty.StateManifest
import org.cryptobiotic.rlauxe.util.CorlaPrng
import org.cryptobiotic.rlauxe.util.Locations.auditcenter
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.util.noerror
import java.io.File
import kotlin.collections.forEach
import kotlin.io.path.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertNotNull

class CountyYearInputs(
    val cvrdir: String,
    val manifestdir: String,
    val glob: String,
    val year: String,
    val countiesNoCvrs: List<String> = emptyList(),
) {
    val counties: List<String> // TODO canonical order
    val countyManifests = mutableMapOf<String, String>()

    init {
        val path = Path(manifestdir) // or does votedatabase include

        path.listDirectoryEntries(glob=glob).forEach { filepath ->
            val filename = filepath.getFileName().toString()
            val strlen = filename.length
            val county = filename.slice(0 until strlen-12) // 12 is a kludge
            // println(county)
            countyManifests[county] = filepath.toString()
        }
        counties = countyManifests.keys.toList().sorted()
    }

    open fun statewideManifest() = statewideManifest
    private val statewideManifest: StateManifest by lazy {
        val countyManifests = counties.map { county ->
            corlaCountyInput(county)!!.countyManifest
        }
        StateManifest(countyManifests)
    }

    /* fun manifestFile(county: String): String { // ?? needed
        val countyInput = corlaCountyInput(county)
        if (countyInput != null) return countyInput.manifestSource
        val noblanks = county.replace("\\s".toRegex(), "")
        return "${County2020InputNoCvrs.manifestDir}/manifest-${noblanks}.csv"
    } */

    fun corlaCountyInput(countyName: String): CountyYearInput? {
        // if (countiesNoCvrs.contains(countyName)) return County2020InputNoCvrs(countyName)
        // if (countyName == "Gunnison") return County2020Input(countyName)
        return if (counties.contains(countyName)) {
            CountyYearInput(cvrdir, manifestdir, year, countyName,)
        } else null
    }
}

class CountyYearInput(
    val cvrdir: String,
    val manifestdir: String,
    override val year: String,
    override val countyName: String,
): CountyInput {
    val countyNameZ = countyName.replace(" ", "")
    override val electionName = "${countyName}${year}"
    override val manifestSource = "$manifestdir/${countyNameZ}Manifest.csv"
    override val cvrsSource = "$cvrdir/$countyName.csv"
    val countyManifest: CountyManifest

    init {
        val mainfestList = readCountyManifestCsv(manifestSource)
        countyManifest = CountyManifest(countyName, mainfestList)
    }

    override fun readManifestEntries(): ManifestEntries {
        if (countyName == "Garfield") {
            return GarfieldManifestEntries(manifestSource)
        }

        var result = super.readManifestEntries()
        if (countyName == "Douglas") {
            // Douglas,1,Gen-2026,30,295 must be Douglas,1,GEN-2026,30,295
            // Douglas,1,GEn-2045,100,299
            result = result.uppercase() // ??
        }
        return result
    }

    override fun countyPopulation() = countyPopulation
    private val countyPopulation by lazy {
        readManifestEntries().totalCards
    }
}

class PredictSequences {

    @Test
    fun testReadOneCountyInputs() {
        readCountyInputs(CountyYearInputs(cvrdir = "$auditcenter/2026/general/files", manifestdir = "$auditcenter/2026/general/files", glob = "*Manifest.csv", "2026"))
    }

    fun readCountyInputs(inputs: CountyYearInputs, expectCounties: Int? = null, expectCards: Int? = null) {
        val filename = "/home/joncifer/rla/cases/corla/2026/cvrs/2026GeneralRLAContestList.csv"
        val targets = readTargetedContestsCsv(filename) { it }
        val countySamples = mutableMapOf<String, Int>()
        targets.forEach { target ->
            val countyNameZ = target.countyName.replace(" ", "")
            val currentMax = countySamples.getOrDefault(countyNameZ, 0)
            countySamples[countyNameZ] = max(currentMax, target.estimatedSamplesToAudit)
        }
        println("statewide nsamples=${countySamples["Colorado"]}") // max or sum ??

        println("----------------------------------------")
        var totalSamples = countySamples["Colorado"] ?: 0
        var totalBatches = 0
        var totalCards = 0
        inputs.counties.forEach { county ->
            val countyInput = inputs.corlaCountyInput(county)
            assertNotNull(countyInput)
            val me: ManifestEntries = countyInput.readManifestEntries()
            val nsamples = countySamples[county]
            println("${county}: nbatches = ${me.manifestBatches.size} ncards = ${me.totalCards} nsamples=$nsamples")
            totalSamples += nsamples ?: 0
            totalBatches += me.manifestBatches.size
            totalCards += me.totalCards
        }
        println()
        println("ncounties = ${inputs.counties.size}")
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
        val population = 4384669
        val statewideNmvrs = 76

        val allprns = mutableListOf<String>()

        inputs.counties.forEach { county ->
            val nsamples = countySamples[county]!!
            val countyManifest = inputs.corlaCountyInput(county)!!.countyManifest
            val countyPrng = CorlaPrngCounty(seed, countyManifest, county, nsamples)
            allprns.addAll(countyPrng.prnIds)
            countPrns += countyPrng.prnIds.size
        }

        val statePrng = CorlaPrngState(seed, inputs.statewideManifest(), population = totalCards, statewideNmvrs = statewideNmvrs)
        allprns.addAll(statePrng.prnIds)
        countPrns += statePrng.prnIds.size

        File("/home/joncifer/rla/temp/predict.txt").printWriter().use { out ->
            allprns.sorted().forEach { out.println(it) }
        }
        println()
        println("countPrns = $countPrns")
    }

    class CorlaPrngState(val seed: String, val stateManifest: StateManifest, val population: Int, val statewideNmvrs: Int) {
        val prnIds: List<String>
        val prnIdSet: Set<String>

        init {
            val prng = CorlaPrng(seed, 1, population) // without replacement (!)

            val prns = mutableListOf<Int>()
            repeat(statewideNmvrs) { prns.add(prng.next()) }
            println("  prns size=${prns.size} unique=${prns.toSet().size}")

            println("  stateManifest.totalCards=${stateManifest.totalCards}")

            prnIds = prns.map { prn -> stateManifest.idFromIndex(prn) }
            prnIdSet = prns.map { prn -> stateManifest.idFromIndex(prn) }.toSet()
            println("  ids size=${prnIds.size} unique=${prnIdSet.size}")
        }

        fun claim(id: String): Boolean {
            return prnIdSet.contains(id)
        }
    }

    class CorlaPrngCounty(val seed: String, val countyManifest: CountyManifest, val county: String, countyMvrs: Int) {
        val prnIds: List<String>
        val prnIdSet: Set<String>

        init {
            println("-------------- $county")
            // modulus max - min + 1 = 1_444_047
            val prng = CorlaPrng(seed, 1, countyManifest.nballotCards) // without replacement (!)
            println("  county population=${countyManifest.nballotCards} nmvrs =${countyMvrs}") // 2020 doesnt know which are statewide

            val prns = mutableListOf<Int>()
            repeat(countyMvrs) { prns.add(prng.next()) }
            println("  prns size=${prns.size} unique=${prns.toSet().size}")

            prnIds = prns.map { prn -> countyManifest.idFromIndex(prn, 0) }
            prnIdSet = prnIds.toSet()
            println("  ids size=${prnIds.size} unique=${prnIdSet.size}")
        }

        fun claim(id: String, mvr: ComparisonMvr): Boolean {
            return prnIdSet.contains(id)
        }
    }

}