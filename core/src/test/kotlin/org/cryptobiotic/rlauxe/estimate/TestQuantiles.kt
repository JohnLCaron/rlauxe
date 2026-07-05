package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.util.Quantiles.percentiles
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.estimateOld.probability
import org.cryptobiotic.rlauxe.estimateOld.quantile
import org.cryptobiotic.rlauxe.util.Welford
import org.cryptobiotic.rlauxe.util.calcDeciles
import org.cryptobiotic.rlauxe.util.calcDecilesFromInt
import org.cryptobiotic.rlauxe.util.calcProbabilityMassFunction
import org.cryptobiotic.rlauxe.util.showDeciles
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals

class TestQuantiles {

    @Test
    fun testQuantile() {
        val data = listOf(847, 868, 565, 319, 882, 782, 312, 570, 392, 111)
        val sdata = data.sorted()
        println("sorted = $sdata")
        repeat(10) {
            val pct = .10 * (it + 1)
            val q = quantile(data, pct)
            println(" ${(df(100 * pct))}% quantile = $q")
            if (it < 9) assertEquals(sdata[it+1], q)
        }
    }

    @Test
    fun testFindQuantile() {
        val data = listOf(137, 194, 198, 229, 241, 252, 266, 386, 668, 896, 919, 1135, 2131, 2631, 3364, 3651, 3651, 4127, 7473, 9629).sorted()
        val sdata = data.sorted()
        println("sorted = $sdata")
        repeat(10) {
            val pct = .05 +  .1 * (it)
            val q = findQuantile(sdata, pct)
            println(" ${(df(100 * pct))}% quantile = $q")
            // if (it < 9) assertEquals(sdata[it+1], q)
        }
    }

    @Test
    fun testQuantiles() {
        val data = listOf(137, 194, 198, 229, 241, 252, 266, 386, 668, 896, 919, 1135, 2131, 2631, 3364, 3651, 3651, 4127, 7473, 9629)
        println("sorted = $data")
        repeat(101) { pct ->
            val wtf: Double = percentiles().index(pct).compute(*data.toIntArray())
            println("percent=$pct = $wtf")
        }
    }

    @Test
    fun testDecilesFromInt() {
        val data = listOf(137, 194, 198, 229, 241, 252, 266, 386, 668, 896, 919, 1135, 2131, 2631, 3364, 3651, 3651, 4127, 7473, 9629)
        val deciles = calcDecilesFromInt(data)
        println("deciles = $deciles")
    }

    @Test
    fun testDeciles() {
        val data = listOf(.0137, .0194, .0198, .0229, .0241, .0252, .0266, .0386, .0668, .0896, .0919, .1135, .2131, .2631, .3364, .3651, .3651, .4127, .7473, .9629)
        val deciles = calcDeciles(data)
        println("deciles = $deciles")
        println("showDeciles = ${showDeciles(data,2)}")
    }

    @Test
    fun testProbabilityFromRealAudit() {
        var deciles = listOf(57, 58, 58, 58, 60, 60, 61, 62, 62, 65)
        val p = probability(deciles, 53)
        assertEquals(9, p)

        deciles = listOf(55, 55, 55, 57, 58, 58, 60, 63, 65, 66)
        assertEquals(30, probability(deciles, 55))
        assertEquals(35, probability(deciles, 56))
        assertEquals(40, probability(deciles, 57))
        assertEquals(90, probability(deciles, 65))
    }

    @Test
    fun testCalcProbabilityMassFunction() {
        val dataset = listOf(748, 791, 1434, 642, 779, 611, 672, 1666, 669, 1434, 932, 2815, 660, 638, 2651, 1761, 641, 1802, 1906, 1375, 1839, 597, 1137, 2471, 3508, 1402, 1006, 671, 736, 762, 598, 591, 1311, 1237, 1220, 689, 1936, 1096, 1326, 693, 588, 616, 1270, 685, 2111, 1386, 625, 761, 691, 693, 1139, 778, 1431, 754, 758, 2333, 745, 744, 751, 3850, 1053, 815, 1750, 572, 563, 2246, 1244, 1157, 681, 2842, 2248, 708, 709, 2744, 618, 1250, 908, 1977, 1446, 2241, 1574, 3821, 2630, 708, 1882, 1567, 2980, 2347, 1834, 640, 1137, 2323, 740, 737, 621, 2424, 1521, 2566, 595, 830)
        val ddataset = dataset.map { it.toDouble()}
        val welford = Welford()
        ddataset.forEach { welford.update(it)}
        println(welford)
        println(calcDecilesFromInt(dataset))

        println("\nPMF 10")
        calcProbabilityMassFunction(dataset, 10 )
        println("\nPMF 20")
        calcProbabilityMassFunction(dataset, 20 )
        println("\nPMF 30")
        calcProbabilityMassFunction(dataset, 30 )
    }

}

// outdated
fun findQuantile(sortedData: List<Int>, quantile: Double): Int {
    require(sortedData.isNotEmpty())

    val deciles = mutableListOf<Int>()
    val n = sortedData.size
    repeat(9) {
        val quantile = .10 * (it + 1)
        val p = min((quantile * n).toInt(), n - 1)
        deciles.add(sortedData[p])
    }
    deciles.add(sortedData.last() + 1)

    require(quantile in 0.0..1.0)

    // edge cases
    if (quantile == 0.0) return sortedData.first()
    if (quantile == 100.0) return sortedData.last()

    // rounding down. TODO interpolate; see Deciles ??
    val p = min((quantile * sortedData.size).toInt(), sortedData.size-1)
    return sortedData[p]
}