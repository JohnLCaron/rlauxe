package org.cryptobiotic.rlauxe.auditcenter

import org.cryptobiotic.rlauxe.corlaInput.Colorado2020General
import org.cryptobiotic.rlauxe.corlaInput.Colorado2026Primary
import org.cryptobiotic.rlauxe.corlaInput.ColoradoInput
import org.cryptobiotic.rlauxe.corlaInput.auditcenter
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.util.sfn
import kotlin.test.Test

class TestReadCountyManifests {
    val input: ColoradoInput = Colorado2026Primary()

    @Test
    fun readAllCountyManifests() {
        val corlaInput = Colorado2026Primary()
        val manifestDir = "${auditcenter}/2026/primary/files"
        val countySet = corlaInput.counties().toSet()
        val manifests = readAuditcenterManifests(manifestDir, countySet)
        val stratas = corlaInput.strataPopulation()

        println ("${sfn("CountyName", 20)}, sumCards, population, diff")
        var totalPopulation = 0
        var totalCards = 0
        var totalDiff = 0
        manifests.forEach { (countyName, batches) ->
            val sumCountyCards = batches.sumOf{ it.nballotCards }
            val population = stratas[countyName] ?: 0
            val diff = population - sumCountyCards
            println ("${sfn(countyName, 20)},  ${nfn(sumCountyCards, 6)},  ${nfn(population, 6)} ${nfn(diff, 6)}")
            totalCards += sumCountyCards
            totalPopulation += population
        }
        println ("${sfn("Total", 20)}, ${nfn(totalCards, 7)}, ${nfn(totalPopulation, 7)}, ${nfn(totalDiff, 5)} ")

        val notfound = countySet - manifests.keys
        if (notfound.isNotEmpty()) {
            println ("\ncounties not found = ${notfound} ")
        } else {
            println("\nfound all counties")
        }
    }

    @Test
    fun readOneCountyManifest() {
        val corlaInput = Colorado2020General()
        val manifestFile = "${auditcenter}/2020/general/round_1/manifest-ElPaso.csv"
        val manifestBatches = readCountyManifestCsv(manifestFile)
        val stratas = corlaInput.strataPopulation()

        val sumCards = manifestBatches.sumOf{ it.nballotCards }

        println("$manifestFile: sumCards = $sumCards")
    }

}