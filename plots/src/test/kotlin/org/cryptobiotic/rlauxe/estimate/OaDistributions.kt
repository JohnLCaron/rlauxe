package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.persist.validateOutputDir
import org.cryptobiotic.rlauxe.persist.validateOutputDirOfFile
import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.rlaplots.plotHistogram
import org.cryptobiotic.rlauxe.util.Welford
import org.cryptobiotic.rlauxe.util.calcDecilesFromInt
import org.cryptobiotic.rlauxe.util.calcHistogram
import kotlin.io.path.Path
import kotlin.test.Test

// show ProbabilityMassFunction of OneAudit samplesNeeded
class OaDistributions {
    val Nc = 50000
    val nSimTrials = 100
    val margin = .02
    val mvrsFuzzPct = .02
    val simFuzzPct = .04

    val oaDir = "$testdataDir/plots/oadist"
    val pollDir = "$testdataDir/plots/polldist"

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

        makeHistogram(dataset,
            "Sample Size Percent (nruns = ${dataset.size})",
            "Ga2026Primary from manifests and candidate_totals",
            xname = "sample size",
            writeFile = "$oaDir/$name",
        )
    }

    @Test
    fun plotNroundHistogram() {
        // from MakeVarianceData.makeGaVariance()
        val dataset = listOf(1, 1, 2, 1, 1, 1, 1, 2, 1, 2, 2, 2, 1, 1, 3, 2, 1, 2, 3, 2, 2, 1, 2, 2, 3, 2, 2, 1, 1, 1, 1, 1, 3, 2, 2, 1,
            2, 2, 2, 1, 1, 1, 2, 1, 2, 2, 1, 1, 1, 1, 2, 1, 2, 1, 1, 3, 1, 1, 1, 3, 2, 1, 2, 1, 1, 4, 2, 2, 1, 3, 3, 1, 1, 2, 1, 2, 2,
            2, 2, 2, 2, 3, 3, 1, 2, 2, 2, 2, 2, 1, 2, 2, 2, 1, 1, 2, 2, 2, 1, 1)

        val name = "NroundsHistogram"
        makeHistogram(dataset, "Number of Rounds (nruns = ${dataset.size})",
            "Ga2026Primary from manifests and candidate_totals",
            "number of rounds", "$oaDir/$name")
    }

    @Test
    fun plotNmvrsPollingHistogram() {
        // from MakeVarianceData.makeGaVariance()
        val dataset = listOf(2564, 3337, 1944, 2814, 2320, 3561, 1155, 4687, 2250, 2400, 2408, 1841, 3823, 1577, 2359, 1671, 2141, 1705, 1952, 3582, 1987,
            2189, 2514, 1765, 2885, 1995, 2271, 3303, 1662, 2766, 2436, 2332, 1604, 3004, 1916, 1957, 2519, 2200, 2115, 3557, 1306, 2044, 3403, 1245, 2159,
            1793, 1784, 2648, 3071, 4935, 2230, 1447, 2041, 1454, 1345, 2132, 2458, 1788, 2081, 4042, 2638, 1971, 2198, 1094, 2414, 2320, 1972, 2988, 2341,
            6539, 1866, 1851, 1229, 2391, 2654, 2491, 3915, 3122, 2970, 2290, 2336, 3442, 2125, 2005, 5268, 1726, 1815, 3511, 2114, 1753, 2029, 2431, 1787,
            3171, 1895, 4777, 1714, 2334, 1535, 2915)
            // val percent = dataset.map { it / dataset.size.toDouble()}
        val welford = Welford()
        dataset.forEach { welford.update(it.toDouble())}
        println(welford)
        println(calcDecilesFromInt(dataset))

        println("\nHistogram 30")
        val hist: Map<Int, Int> = calcHistogram(dataset, 30 )
        println(hist)

        // plotExample(writeFile = "$dirName/$name")

        val name = "NmvrsPollingHistogram"

        makeHistogram(dataset,
            "Sample Size Percent (nruns = ${dataset.size})",
            "Ga2026Primary Polling from manifests and candidate_totals",
            xname = "sample size",
            writeFile = "$pollDir/$name",
        )

        // val data = pmf.map { Triple( it.key.toDouble(), 100*it.value, "same") }

        // plotCumul(name, "$dirName/$name", "Ga2026Primary from manifests and candidate_totals", data)
    }

    @Test
    fun plotNroundPollingHistogram() {
        // from MakeVarianceData.makeGaVariance()
        val dataset = listOf(2, 3, 1, 2, 1, 2, 1, 3, 1, 1, 1, 1, 2, 1, 1, 1, 1, 3, 1, 2, 1, 1, 2, 1, 2, 1, 1, 2, 1, 1, 1, 1, 1, 2, 2,
            2, 1, 2, 2, 2, 1, 1, 2, 1, 2, 1, 1, 1, 2, 3, 1, 1, 2, 1, 1, 1, 1, 1, 2, 2, 2, 1, 2, 1, 2, 1, 1, 2, 1, 4, 1, 2, 1, 2, 2, 1,
            3, 3, 2, 1, 1, 2, 4, 1, 3, 1, 1, 1, 1, 1, 2, 2, 1, 2, 2, 2, 1, 1, 1, 1)

        val name = "NroundsPollingHistogram"
        makeHistogram(dataset, "Number of Rounds, Polling (nruns = ${dataset.size})",
            "Ga2026Primary Polling from manifests and candidate_totals",
            "number of rounds", "$pollDir/$name")
    }
}


fun makeHistogram(dataset: List<Int>, title: String, subtitle: String, xname: String, writeFile: String) {
    validateOutputDirOfFile(writeFile)

    plotHistogram(
        titleS = title,
        subtitleS = subtitle,
        writeFile = writeFile,
        xname = xname,
        xvalues = dataset,
        yname = "percent",
    )
}
