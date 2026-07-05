package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.rlaplots.plotHistogram
import org.cryptobiotic.rlauxe.util.Welford
import org.cryptobiotic.rlauxe.util.calcDecilesFromInt
import org.cryptobiotic.rlauxe.util.calcHistogram
import kotlin.test.Test

// show ProbabilityMassFunction of OneAudit samplesNeeded
class OaDistributions {
    val Nc = 50000
    val nSimTrials = 100
    val margin = .02
    val mvrsFuzzPct = .02
    val simFuzzPct = .04

    val dirName = "$testdataDir/plots/oadist"

    @Test
    fun plotNmvrsHistogram() {
        // from MakeVarianceData.makeGaVariance()
        val dataset = listOf(748, 791, 1434, 642, 779, 611, 672, 1666, 669, 1434, 932, 2815, 660, 638, 2651, 1761, 641, 1802, 1906, 1375, 1839, 597, 1137, 2471, 3508, 1402, 1006, 671, 736, 762, 598, 591, 1311, 1237, 1220, 689, 1936, 1096, 1326, 693, 588, 616, 1270, 685, 2111, 1386, 625, 761, 691, 693, 1139, 778, 1431, 754, 758, 2333, 745, 744, 751, 3850, 1053, 815, 1750, 572, 563, 2246, 1244, 1157, 681, 2842, 2248, 708, 709, 2744, 618, 1250, 908, 1977, 1446, 2241, 1574, 3821, 2630, 708, 1882, 1567, 2980, 2347, 1834, 640, 1137, 2323, 740, 737, 621, 2424, 1521, 2566, 595, 830)
        // val percent = dataset.map { it / dataset.size.toDouble()}
        val welford = Welford()
        dataset.forEach { welford.update(it.toDouble())}
        println(welford)
        println(calcDecilesFromInt(dataset))

        println("\nHistogram 30")
        val hist: Map<Int, Int> = calcHistogram(dataset, 30 )
        println(hist)

        // plotExample(writeFile = "$dirName/$name")

        val name = "NmvrsHistogram"

        plotHistogram(
            titleS = "Sample Size Percent (nruns = ${dataset.size})",
            subtitleS = "Ga2026Primary from manifests and candidate_totals",
            writeFile = "$dirName/$name",
            xname = "sample size",
            xvalues = dataset,
            yname = "percent",
        )

        // val data = pmf.map { Triple( it.key.toDouble(), 100*it.value, "same") }

        // plotCumul(name, "$dirName/$name", "Ga2026Primary from manifests and candidate_totals", data)
    }

    @Test
    fun plotNroundHistogram() {
        // from MakeVarianceData.makeGaVariance()
        val dataset = listOf(1, 1, 2, 1, 1, 1, 1, 2, 1, 2, 2, 2, 1, 1, 3, 2, 1, 2, 3, 2, 2, 1, 2, 2, 3, 2, 2, 1, 1, 1, 1, 1, 3, 2, 2, 1,
            2, 2, 2, 1, 1, 1, 2, 1, 2, 2, 1, 1, 1, 1, 2, 1, 2, 1, 1, 3, 1, 1, 1, 3, 2, 1, 2, 1, 1, 4, 2, 2, 1, 3, 3, 1, 1, 2, 1, 2, 2,
            2, 2, 2, 2, 3, 3, 1, 2, 2, 2, 2, 2, 1, 2, 2, 2, 1, 1, 2, 2, 2, 1, 1)

        val name = "NroundsHistogram"
        plotHistogram(dataset, "Number of Rounds (nruns = ${dataset.size})", "number of rounds", "$dirName/$name")
    }


    fun plotHistogram(dataset: List<Int>, title: String, xname: String, writeFile: String) {
        plotHistogram(
            titleS = title,
            subtitleS = "Ga2026Primary from manifests and candidate_totals",
            writeFile = writeFile,
            xname = xname,
            xvalues = dataset,
            yname = "percent",
        )
    }
}