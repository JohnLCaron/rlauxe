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
    val oaDir = "$testdataDir/plots/oadist"
    val pollDir = "$testdataDir/plots/polldist"

    @Test
    fun plotNmvrsHistogram() {
        // from MakeVarianceData.makeGaVariance()
        val dataset1 = listOf(748, 791, 1434, 642, 779, 611, 672, 1666, 669, 1434, 932, 2815, 660, 638, 2651, 1761, 641, 1802, 1906, 1375, 1839, 597, 1137, 2471, 3508, 1402, 1006, 671, 736, 762, 598, 591, 1311, 1237, 1220, 689, 1936, 1096, 1326, 693, 588, 616, 1270, 685, 2111, 1386, 625, 761, 691, 693, 1139, 778, 1431, 754, 758, 2333, 745, 744, 751, 3850, 1053, 815, 1750, 572, 563, 2246, 1244, 1157, 681, 2842, 2248, 708, 709, 2744, 618, 1250, 908, 1977, 1446, 2241, 1574, 3821, 2630, 708, 1882, 1567, 2980, 2347, 1834, 640, 1137, 2323, 740, 737, 621, 2424, 1521, 2566, 595, 830)
        val dataset2 = listOf(10547, 2451, 1797, 1916, 1775, 2687, 10482, 12044, 2032, 14909, 15551, 6963, 4891, 2354, 12467, 17732, 5833, 4632, 12198, 2139,
            3905, 1939, 11545, 12123, 2157, 12060, 9370, 12069, 12609, 5503, 7074, 2253, 1780, 2352, 2379, 9503, 2060, 1697, 1453, 2605, 1810, 2314, 2057,
            9424, 12292, 1727, 5457, 5123, 17226, 10335, 2786, 9642, 11665, 2446, 9082, 12027, 4636, 16626, 10594, 1906, 6247, 4520, 6063, 7004, 1821, 2909,
            11226, 11720, 11609, 11927, 8152, 4416, 1982, 2062, 2166, 2141, 1787, 2329, 4694, 2849, 6510, 7970, 18313, 11674, 7709, 10751, 5604, 1746, 2097,
            6526, 8533, 2125, 1823, 9314, 12360, 2100, 2441, 19820, 3701, 9901)
        val dataset = listOf(14544, 6636, 2233, 4381, 4948, 1958, 1895, 10819, 7091, 30886, 1769, 1988, 9659, 2373, 4371, 12414, 3863, 18780, 2149, 35410,
            6790, 1952, 3606, 1718, 1896, 2035, 1901, 5672, 2080, 6656, 7524, 2226, 1840, 2830, 14879, 8171, 1692, 2301, 32179, 13690, 28856, 14335, 1874,
            34714, 7493, 2129, 1986, 1919, 5016, 2005, 5981, 14495, 2018, 9022, 14567, 5325, 1448, 1921, 1797, 2499, 4998, 1763, 12399, 7604, 11585, 2059,
            2427, 2361, 8621, 2079, 25567, 3923, 15896, 3939, 1519, 2615, 2096, 3602, 2659, 53842, 19783, 5515, 2488, 26072, 2211, 2271, 13219, 12915, 7342,
            12787, 2744, 2153, 2732, 2259, 1518, 1315, 2544, 2369, 2565, 9117)

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
        val dataset1 = listOf(1, 1, 2, 1, 1, 1, 1, 2, 1, 2, 2, 2, 1, 1, 3, 2, 1, 2, 3, 2, 2, 1, 2, 2, 3, 2, 2, 1, 1, 1, 1, 1, 3, 2, 2, 1,
            2, 2, 2, 1, 1, 1, 2, 1, 2, 2, 1, 1, 1, 1, 2, 1, 2, 1, 1, 3, 1, 1, 1, 3, 2, 1, 2, 1, 1, 4, 2, 2, 1, 3, 3, 1, 1, 2, 1, 2, 2,
            2, 2, 2, 2, 3, 3, 1, 2, 2, 2, 2, 2, 1, 2, 2, 2, 1, 1, 2, 2, 2, 1, 1)
        val dataset2 = listOf(2, 2, 1, 1, 1, 2, 2, 2, 2, 3, 3, 2, 2, 1, 2, 3, 2, 2, 2, 1, 2, 2, 2, 2, 1, 2, 2, 2, 2, 4, 2, 1, 1, 1, 1, 2, 1, 1, 1, 2, 1, 1,
            1, 2, 2, 1, 2, 2, 3, 2, 1, 2, 2, 1, 2, 2, 2, 3, 2, 1, 2, 2, 2, 2, 1, 1, 2, 2, 2, 2, 2, 3, 1, 1, 1, 1, 1, 1, 2, 3, 2, 2, 3, 2, 2,
            2, 2, 1, 1, 2, 2, 1, 1, 2, 2, 1, 1, 3, 2, 2)
        val dataset = listOf(2, 2, 1, 4, 2, 1, 1, 2, 2, 3, 1, 1, 3, 1, 2, 2, 3, 2, 1, 3, 2, 1, 2, 1, 1, 1, 1, 2, 1, 2, 2, 1, 1, 1, 2, 2, 1, 1, 3, 2,
            3, 3, 1, 4, 3, 1, 1, 1, 2, 1, 4, 2, 1, 3, 2, 2, 1, 1, 1, 1, 2, 1, 2, 2, 3, 1, 1, 1, 2, 1, 4, 2, 2, 2, 1, 1, 1, 2, 1, 4, 2, 2, 1, 3, 1, 1,
            2, 2, 2, 2, 2, 1, 1, 1, 1, 1, 1, 1, 1, 2)

        val name = "NroundsHistogram"
        makeHistogram(dataset, "Number of Rounds (nruns = ${dataset.size}, nsims=100)",
            "Ga2026Primary from manifests and candidate_totals",
            "number of rounds", "$oaDir/$name")
    }

    @Test
    fun plotNmvrsPollingHistogram() {
        // from MakeVarianceData.makeGaVariance()
        val dataset1 = listOf(2564, 3337, 1944, 2814, 2320, 3561, 1155, 4687, 2250, 2400, 2408, 1841, 3823, 1577, 2359, 1671, 2141, 1705, 1952, 3582, 1987,
            2189, 2514, 1765, 2885, 1995, 2271, 3303, 1662, 2766, 2436, 2332, 1604, 3004, 1916, 1957, 2519, 2200, 2115, 3557, 1306, 2044, 3403, 1245, 2159,
            1793, 1784, 2648, 3071, 4935, 2230, 1447, 2041, 1454, 1345, 2132, 2458, 1788, 2081, 4042, 2638, 1971, 2198, 1094, 2414, 2320, 1972, 2988, 2341,
            6539, 1866, 1851, 1229, 2391, 2654, 2491, 3915, 3122, 2970, 2290, 2336, 3442, 2125, 2005, 5268, 1726, 1815, 3511, 2114, 1753, 2029, 2431, 1787,
            3171, 1895, 4777, 1714, 2334, 1535, 2915)

        val dataset2 = listOf(14544, 6636, 2233, 4381, 4948, 1958, 1895, 10819, 7091, 30886, 1769, 1988, 9659, 2373, 4371, 12414, 3863, 18780, 2149, 35410,
            6790, 1952, 3606, 1718, 1896, 2035, 1901, 5672, 2080, 6656, 7524, 2226, 1840, 2830, 14879, 8171, 1692, 2301, 32179, 13690, 28856, 14335, 1874,
            34714, 7493, 2129, 1986, 1919, 5016, 2005, 5981, 14495, 2018, 9022, 14567, 5325, 1448, 1921, 1797, 2499, 4998, 1763, 12399, 7604, 11585, 2059,
            2427, 2361, 8621, 2079, 25567, 3923, 15896, 3939, 1519, 2615, 2096, 3602, 2659, 53842, 19783, 5515, 2488, 26072, 2211, 2271, 13219, 12915, 7342,
            12787, 2744, 2153, 2732, 2259, 1518, 1315, 2544, 2369, 2565, 9117)

        val dataset = listOf(7614, 7545, 7148, 7755, 6557, 7663, 9836, 9750, 15030, 12746, 7498, 14142, 23200, 12388, 7091, 7282, 24985, 12309, 11943, 10353,
            8423, 7694, 6979, 15984, 5952, 6697, 6738, 8983, 6891, 7738, 7562, 12661, 9563, 6562, 21446, 17436, 6154, 11593, 7365, 7923, 7377, 12732, 6178,
            17916, 7700, 16109, 16276, 6997, 6462, 13812, 16617, 9452, 6739, 13111, 15348, 6447, 11126, 6530, 8974, 7735, 6207, 6858, 13328, 9451, 11138, 12184,
            7613, 19585, 6794, 4059, 14106, 11433, 7527, 12561, 7137, 16601, 14762, 10251, 14515, 7708, 12295, 7522, 10969, 7700, 6809, 17430, 8557, 16901, 8147,
            15979, 5874, 6991, 7559, 6860, 15033, 8738, 8559, 7965, 10532, 7000)

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
            "Sample Size Percent (nruns = ${dataset.size}, nsims=100)",
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
        val dataset1 = listOf(2, 3, 1, 2, 1, 2, 1, 3, 1, 1, 1, 1, 2, 1, 1, 1, 1, 3, 1, 2, 1, 1, 2, 1, 2, 1, 1, 2, 1, 1, 1, 1, 1, 2, 2,
            2, 1, 2, 2, 2, 1, 1, 2, 1, 2, 1, 1, 1, 2, 3, 1, 1, 2, 1, 1, 1, 1, 1, 2, 2, 2, 1, 2, 1, 2, 1, 1, 2, 1, 4, 1, 2, 1, 2, 2, 1,
            3, 3, 2, 1, 1, 2, 4, 1, 3, 1, 1, 1, 1, 1, 2, 2, 1, 2, 2, 2, 1, 1, 1, 1)
        val dataset2 = listOf(2, 1, 1, 2, 1, 1, 1, 2, 1, 2, 2, 2, 1, 1, 2, 2, 1, 4, 2, 1, 1, 2, 1, 2, 2, 1, 2, 3, 2, 1, 2, 2, 2, 2, 1, 1,
            2, 1, 2, 2, 2, 3, 1, 1, 1, 1, 1, 2, 1, 2, 2, 3, 1, 1, 1, 1, 1, 2, 1, 2, 1, 2, 1, 2, 2, 1, 2, 2, 2, 2, 1, 2, 2, 2, 2, 2, 2, 1,
            1, 1, 1, 1, 1, 1, 2, 2, 3, 2, 2, 2, 2, 1, 2, 2, 2, 1, 2, 2, 1, 2)
        val dataset = listOf(1, 2, 1, 1, 1, 1, 1, 2, 3, 2, 1, 2, 3, 2, 1, 1, 4, 2, 2, 2, 2, 1, 1, 3, 1, 1, 1, 1, 1, 1, 1, 2, 1, 1, 4, 2, 1,
            2, 1, 1, 1, 2, 1, 2, 1, 2, 3, 1, 1, 2, 3, 2, 1, 2, 2, 1, 2, 1, 2, 1, 1, 1, 2, 2, 2, 2, 1, 3, 1, 1, 2, 2, 1, 2, 1, 3, 3, 2, 2, 2,
            2, 1, 2, 1, 1, 3, 1, 2, 1, 3, 1, 1, 1, 1, 2, 1, 1, 1, 2, 1)

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
