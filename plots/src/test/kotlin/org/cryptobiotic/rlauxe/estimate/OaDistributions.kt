package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.persist.validateOutputDirOfFile
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

    val oaDir = "$testdataDir/plots/oadist"
    val pollDir = "$testdataDir/plots/polldist"

    @Test
    fun plotNmvrsHistogram() {
        // from MakeVarianceData.makeGaVariance()
        val datasetPrev = listOf(748, 791, 1434, 642, 779, 611, 672, 1666, 669, 1434, 932, 2815, 660, 638, 2651, 1761, 641, 1802, 1906, 1375, 1839, 597, 1137, 2471, 3508, 1402, 1006, 671, 736, 762, 598, 591, 1311, 1237, 1220, 689, 1936, 1096, 1326, 693, 588, 616, 1270, 685, 2111, 1386, 625, 761, 691, 693, 1139, 778, 1431, 754, 758, 2333, 745, 744, 751, 3850, 1053, 815, 1750, 572, 563, 2246, 1244, 1157, 681, 2842, 2248, 708, 709, 2744, 618, 1250, 908, 1977, 1446, 2241, 1574, 3821, 2630, 708, 1882, 1567, 2980, 2347, 1834, 640, 1137, 2323, 740, 737, 621, 2424, 1521, 2566, 595, 830)
        val dataset = listOf(10547, 2451, 1797, 1916, 1775, 2687, 10482, 12044, 2032, 14909, 15551, 6963, 4891, 2354, 12467, 17732, 5833, 4632, 12198, 2139,
            3905, 1939, 11545, 12123, 2157, 12060, 9370, 12069, 12609, 5503, 7074, 2253, 1780, 2352, 2379, 9503, 2060, 1697, 1453, 2605, 1810, 2314, 2057,
            9424, 12292, 1727, 5457, 5123, 17226, 10335, 2786, 9642, 11665, 2446, 9082, 12027, 4636, 16626, 10594, 1906, 6247, 4520, 6063, 7004, 1821, 2909,
            11226, 11720, 11609, 11927, 8152, 4416, 1982, 2062, 2166, 2141, 1787, 2329, 4694, 2849, 6510, 7970, 18313, 11674, 7709, 10751, 5604, 1746, 2097,
            6526, 8533, 2125, 1823, 9314, 12360, 2100, 2441, 19820, 3701, 9901)
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
        val datasetPrev = listOf(1, 1, 2, 1, 1, 1, 1, 2, 1, 2, 2, 2, 1, 1, 3, 2, 1, 2, 3, 2, 2, 1, 2, 2, 3, 2, 2, 1, 1, 1, 1, 1, 3, 2, 2, 1,
            2, 2, 2, 1, 1, 1, 2, 1, 2, 2, 1, 1, 1, 1, 2, 1, 2, 1, 1, 3, 1, 1, 1, 3, 2, 1, 2, 1, 1, 4, 2, 2, 1, 3, 3, 1, 1, 2, 1, 2, 2,
            2, 2, 2, 2, 3, 3, 1, 2, 2, 2, 2, 2, 1, 2, 2, 2, 1, 1, 2, 2, 2, 1, 1)
        val dataset = listOf(2, 2, 1, 1, 1, 2, 2, 2, 2, 3, 3, 2, 2, 1, 2, 3, 2, 2, 2, 1, 2, 2, 2, 2, 1, 2, 2, 2, 2, 4, 2, 1, 1, 1, 1, 2, 1, 1, 1, 2, 1, 1,
            1, 2, 2, 1, 2, 2, 3, 2, 1, 2, 2, 1, 2, 2, 2, 3, 2, 1, 2, 2, 2, 2, 1, 1, 2, 2, 2, 2, 2, 3, 1, 1, 1, 1, 1, 1, 2, 3, 2, 2, 3, 2, 2,
            2, 2, 1, 1, 2, 2, 1, 1, 2, 2, 1, 1, 3, 2, 2)


        val name = "NroundsHistogram"
        makeHistogram(dataset, "Number of Rounds (nruns = ${dataset.size})",
            "Ga2026Primary from manifests and candidate_totals",
            "number of rounds", "$oaDir/$name")
    }

    @Test
    fun plotNmvrsPollingHistogram() {
        // from MakeVarianceData.makeGaVariance()
        val datasetPrev = listOf(2564, 3337, 1944, 2814, 2320, 3561, 1155, 4687, 2250, 2400, 2408, 1841, 3823, 1577, 2359, 1671, 2141, 1705, 1952, 3582, 1987,
            2189, 2514, 1765, 2885, 1995, 2271, 3303, 1662, 2766, 2436, 2332, 1604, 3004, 1916, 1957, 2519, 2200, 2115, 3557, 1306, 2044, 3403, 1245, 2159,
            1793, 1784, 2648, 3071, 4935, 2230, 1447, 2041, 1454, 1345, 2132, 2458, 1788, 2081, 4042, 2638, 1971, 2198, 1094, 2414, 2320, 1972, 2988, 2341,
            6539, 1866, 1851, 1229, 2391, 2654, 2491, 3915, 3122, 2970, 2290, 2336, 3442, 2125, 2005, 5268, 1726, 1815, 3511, 2114, 1753, 2029, 2431, 1787,
            3171, 1895, 4777, 1714, 2334, 1535, 2915)

        val dataset = listOf(11642, 7011, 8192, 13364, 6663, 4786, 6666, 7983, 651, 11572, 10444, 11520, 7088, 8221, 14866, 15672, 9893, 6781, 7051, 5561,
            7578, 9717, 6244, 15121, 13394, 6152, 4580, 8829, 7750, 7955, 9355, 11130, 12136, 10178, 7612, 7229, 11324, 7292, 14195, 9151, 14047, 13732,
            8990, 8988, 5424, 6996, 6974, 13290, 9527, 10632, 17565, 10588, 7941, 8263, 5451, 7795, 9948, 13549, 9803, 12299, 5699, 14850, 5956, 6690, 11840,
            7600, 12912, 9850, 12709, 14057, 7531, 12461, 7374, 16750, 14253, 15920, 15670, 8299, 4479, 9545, 6756, 5566, 9449, 7386, 10426, 13366, 2242,
            11603, 11469, 14016, 17028, 6224, 9761, 17505, 8408, 5224, 13439, 19904, 6907, 13480)
        // {10=5686, 20=6882, 30=7488, 40=8210, 50=9488, 60=10434, 70=11702, 80=13403, 90=14852, 100=19904}

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
        val datasetPrev = listOf(2, 3, 1, 2, 1, 2, 1, 3, 1, 1, 1, 1, 2, 1, 1, 1, 1, 3, 1, 2, 1, 1, 2, 1, 2, 1, 1, 2, 1, 1, 1, 1, 1, 2, 2,
            2, 1, 2, 2, 2, 1, 1, 2, 1, 2, 1, 1, 1, 2, 3, 1, 1, 2, 1, 1, 1, 1, 1, 2, 2, 2, 1, 2, 1, 2, 1, 1, 2, 1, 4, 1, 2, 1, 2, 2, 1,
            3, 3, 2, 1, 1, 2, 4, 1, 3, 1, 1, 1, 1, 1, 2, 2, 1, 2, 2, 2, 1, 1, 1, 1)
        val dataset = listOf(2, 1, 1, 2, 1, 1, 1, 2, 1, 2, 2, 2, 1, 1, 2, 2, 1, 4, 2, 1, 1, 2, 1, 2, 2, 1, 2, 3, 2, 1, 2, 2, 2, 2, 1, 1,
            2, 1, 2, 2, 2, 3, 1, 1, 1, 1, 1, 2, 1, 2, 2, 3, 1, 1, 1, 1, 1, 2, 1, 2, 1, 2, 1, 2, 2, 1, 2, 2, 2, 2, 1, 2, 2, 2, 2, 2, 2, 1,
            1, 1, 1, 1, 1, 1, 2, 2, 3, 2, 2, 2, 2, 1, 2, 2, 2, 1, 2, 2, 1, 2)
        // {10=1.0, 20=1.0, 30=1.0, 40=1.0, 50=2.0, 60=2.0, 70=2.0, 80=2.0, 90=2.0, 100=4.0}


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
