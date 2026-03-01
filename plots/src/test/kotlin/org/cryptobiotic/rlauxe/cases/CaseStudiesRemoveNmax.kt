package org.cryptobiotic.rlauxe.cases

import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.persist.validateOutputDir
import org.cryptobiotic.rlauxe.rlaplots.ScaleType
import org.cryptobiotic.rlauxe.rlaplots.genericScatter
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.roundUp
import org.jetbrains.kotlinx.kandy.letsplot.settings.Symbol
import org.jetbrains.kotlinx.kandy.util.color.Color
import kotlin.String
import kotlin.io.path.Path
import kotlin.test.Test

class CaseStudiesRemoveNmax {

    @Test
    fun testRead() {
        readAuditNResult("yomoma", oan)
    }

    @Test
    fun caseStudiesBoulder() {
        val name = "Boulder24RemoveNmax"
        val dirName = "$testdataDir/plots/boulder24/$name"
        validateOutputDir(Path(dirName))

        // see MakeBoulderlection.createBoulderRemoveNclca()
        val clcaResults:  Map<Int, AuditResult> = readAuditResult("CLCA", clca).toSortedMap()
        val oaResults:  Map<Int, AuditNResult> = readAuditNResult("OneAudit", oan)

        // create table
        clcaResults.forEach { nRemoved, clca ->
            val oaResult: AuditNResult = oaResults[nRemoved]!!
            val ratio = oaResult.nmvrsAvg / clca.nmvrs
            println("| ${nRemoved} | ${clca.nsuccess} | ${oaResult.nsuccessAvg} | ${clca.nmvrs} | ${roundUp(oaResult.nmvrsAvg)} | ${dfn(ratio, 1)}  | ${oaResult.nmvrs} |")
        }
        println()

        val clcaPoints = clcaResults.values.map { NmvrPoint("CLCA", it.removeN, it.nmvrs) }

        val oaNmvrs = oaResults.values.map { NmvrPoints(it.removeN, it.nmvrsAvg, it.nmvrs) }

        makePlot(name, dirName, clcaPoints, oaNmvrs)
    }

    @Test
    fun caseStudiesSF2() {
        val name = "SF2024RemoveNmax"
        val dirName = "$testdataDir/plots/sf2024/$name"
        validateOutputDir(Path(dirName))

        // see MakeSfElection.createSFremoveNclca()
        val clcaResults:  Map<Int, AuditResult> = readAuditResult("CLCA", clcaSF2).toSortedMap()
        // see MakeSfElection.createSFremoveNoa()
        val oaResults:  Map<Int, AuditNResult> = readAuditNResult("OneAudit", oanSF2)

        // create table
        clcaResults.forEach { nRemoved, clca ->
            val oaResult: AuditNResult = oaResults[nRemoved]!!
            val ratio = oaResult.nmvrsAvg / clca.nmvrs
            println("| ${nRemoved} | ${clca.nsuccess} | ${oaResult.nsuccessAvg} | ${clca.nmvrs} | ${roundUp(oaResult.nmvrsAvg)} | ${dfn(ratio, 1)}  | ${oaResult.nmvrs} |")
        }
        println()

        val clcaPoints = clcaResults.values.map { NmvrPoint("CLCA", it.removeN, it.nmvrs) }

        val oaNmvrs = oaResults.values.map { NmvrPoints(it.removeN, it.nmvrsAvg, it.nmvrs) }

        makePlot(name, dirName, clcaPoints, oaNmvrs)
    }

    fun makePlot(name: String, dirName: String, clcaPoints: List<NmvrPoint>, oaNmvrs: List<NmvrPoints>) {

        val catPoints = mutableListOf<NmvrPoint>()
        catPoints.addAll( clcaPoints)

        oaNmvrs.forEach { oaNmvr ->
            oaNmvr.nmvrs.forEach { nmvr ->
                catPoints.add(NmvrPoint("OneAudit", oaNmvr.nRemoved, nmvr))
            }
        }

        // add fake one to show the plot better
        // catPoints.add(NmvrPoint("OneAudit", 12, .001))

        val title = "$name nmvrs vs nRemoved, no errors"
        val subtitle = "compare CLCA and OneAudit's nmvrs"
        val scaleType = ScaleType.Linear

        genericScatter(
            titleS = title,
            subtitleS = subtitle,
            writeFile="$dirName/${name}${scaleType.name}",
            data = catPoints,
            xname = "n max-estimation contests removed",
            yname = "nmvrs",
            catName = "type",
            xfld = { it.nRemoved.toDouble() },
            yfld = { it.nmvrs.toDouble() },
            catfld = { it.cat },
            scaleType=scaleType,
            symbols=mapOf("CLCA" to Symbol.CIRCLE_OPEN,
                "OneAudit" to Symbol.CIRCLE_SMALL,
            ),
            colors=mapOf("CLCA" to Color.RED,
                "OneAudit" to Color.LIGHT_BLUE,
            ),
        )
    }
}

data class NmvrPoint(val cat: String, val nRemoved: Int, val nmvrs: Int)
data class NmvrPoints(val nRemoved: Int, val avg: Double, val nmvrs: List<Int>)


data class AuditResult(val cat: String, val removeN: Int, val nmvrs: Int, val nsuccess: Int)

fun readAuditResult(cat: String, inp: String): Map<Int, AuditResult> {
    val results = mutableMapOf<Int, AuditResult>()
    val lines = inp.split("\n")
    lines.forEach { line ->
        val tokes = line.split("=", ",", ")")
        val n = tokes[1].toInt()
        val nmvrs = tokes[3].toInt()
        val nsuccess = tokes[5].toInt()
        val result = AuditResult(cat, n, nmvrs, nsuccess)
        results[result.removeN] = result
    }
    return results
}

val clca = """
    AuditResult(removeN=0, nmvrs=16292, nsuccess=62)
    AuditResult(removeN=1, nmvrs=8804, nsuccess=61)
    AuditResult(removeN=2, nmvrs=5147, nsuccess=60)
    AuditResult(removeN=3, nmvrs=3070, nsuccess=59)
    AuditResult(removeN=4, nmvrs=1343, nsuccess=58)
    AuditResult(removeN=5, nmvrs=708, nsuccess=57)
    AuditResult(removeN=6, nmvrs=706, nsuccess=56)
    AuditResult(removeN=7, nmvrs=665, nsuccess=55)
    AuditResult(removeN=8, nmvrs=625, nsuccess=54)
    AuditResult(removeN=9, nmvrs=516, nsuccess=53)
    AuditResult(removeN=10, nmvrs=415, nsuccess=52)
""".trimIndent()

val clcaSF = """
    AuditResult(removeN=0, nmvrs=13580, nsuccess=48)
    AuditResult(removeN=1, nmvrs=3480, nsuccess=47)
    AuditResult(removeN=2, nmvrs=1759, nsuccess=46)
    AuditResult(removeN=3, nmvrs=1093, nsuccess=45)
    AuditResult(removeN=4, nmvrs=1019, nsuccess=44)
    AuditResult(removeN=5, nmvrs=827, nsuccess=43)
    AuditResult(removeN=6, nmvrs=737, nsuccess=42)
    AuditResult(removeN=7, nmvrs=724, nsuccess=41)
    AuditResult(removeN=8, nmvrs=598, nsuccess=40)
    AuditResult(removeN=9, nmvrs=552, nsuccess=39)
    AuditResult(removeN=10, nmvrs=444, nsuccess=38)
""".trimIndent()

val clcaSF2 = """
    AuditResult(removeN=2, nmvrs=1771, nsuccess=46)
    AuditResult(removeN=3, nmvrs=1093, nsuccess=45)
    AuditResult(removeN=4, nmvrs=1024, nsuccess=44)
    AuditResult(removeN=1, nmvrs=3498, nsuccess=47)
    AuditResult(removeN=0, nmvrs=13562, nsuccess=48)
    AuditResult(removeN=6, nmvrs=742, nsuccess=42)
    AuditResult(removeN=7, nmvrs=727, nsuccess=41)
    AuditResult(removeN=5, nmvrs=820, nsuccess=43)
    AuditResult(removeN=9, nmvrs=552, nsuccess=39)
    AuditResult(removeN=8, nmvrs=597, nsuccess=40)
    AuditResult(removeN=10, nmvrs=459, nsuccess=38)
""".trimIndent()

data class AuditNResult(val cat: String, val removeN: Int, val nmvrsAvg: Double, val nsuccessAvg: Double, val nmvrs: List<Int>)

fun readAuditNResult(cat: String, inp: String): Map<Int, AuditNResult> {
    val results = mutableMapOf<Int, AuditNResult>()
    val lines = inp.split("\n")
    lines.forEach { line ->
        println(line)
        val tokes = line.split(",").map { it.trim() }
        // tokes.forEach { println("  $it") }
        var idx = 0
        val n = tokes[idx++].toInt()
        val nmvrsAvg = tokes[idx++].toDouble()
        val nsuccessAvg = tokes[idx++].toDouble()
        val ests = mutableListOf<Int>()
        while (idx < tokes.size) {
            val useToken = tokes[idx++].filter {  it != ' ' && it != '[' && it != ']' }
            if (useToken.length > 0)
                ests.add(useToken.toInt())
        }
        val result =  AuditNResult(cat, n, nmvrsAvg, nsuccessAvg, ests)
        println(result)
        results[result.removeN] = result
    }
    return results
}

val oan = """
    0, 53705.4, 58.8, [46930, 53432, 53553, 53706, 54285, 55184, 56259, 60528, 62340, 62341]
    1, 21120.6, 58.3, [20885, 20901, 20965, 21079, 21097, 21185, 21352, 21412, 21453, 21454]
    2, 17092.6, 58.2, [12662, 12719, 17608, 18511, 18557, 18745, 18810, 18832, 22338, 22339]
    3, 10516.2, 58.4, [9297, 9558, 9721, 10184, 10307, 10496, 10646, 11862, 14157, 14158]
    4, 5010.5, 57.6, [4419, 4717, 4892, 4964, 5018, 5025, 5377, 5453, 5938, 5939]
    5, 1385.5, 57.0, [1123, 1230, 1253, 1374, 1449, 1539, 1551, 1663, 1776, 1777]
    6, 1345.8, 56.0, [1102, 1230, 1242, 1360, 1439, 1484, 1485, 1568, 1672, 1673]
    7, 1282.8, 55.0, [958, 1139, 1226, 1361, 1382, 1442, 1448, 1472, 1627, 1628]
    8, 1007.1, 54.0, [667, 681, 709, 849, 1060, 1262, 1359, 1401, 1476, 1477]
    9, 752.1, 53.0, [539, 544, 587, 605, 607, 648, 987, 1099, 1368, 1369]
    10, 480.1, 52.0, [442, 457, 460, 475, 485, 490, 501, 511, 541, 542]
""".trimIndent()

val oanSF = """
    0, 15015.3, 46.3, [6522, 6851, 7800, 11176, 12977, 14759, 21078, 27154, 36046, 36047]
    1, 10266.1, 45.2, [6352, 6417, 6646, 7132, 8749, 9350, 10692, 13905, 28337, 28338]
    2, 9194.2, 44.5, [5347, 5353, 6414, 7528, 8223, 9219, 9433, 12804, 23822, 23823]
    3, 7004.1, 44.1, [3972, 4742, 4853, 5139, 6631, 6835, 10186, 11417, 13866, 13867]
    4, 4588.0, 43.4, [3212, 3272, 3299, 3959, 5127, 5145, 5155, 5839, 8307, 8308]
    5, 4546.0, 42.5, [2844, 3387, 3916, 4369, 4375, 4519, 5849, 6290, 7074, 7075]
    6, 3021.5, 41.7, [2292, 2468, 2595, 2686, 3055, 3064, 3079, 3823, 5014, 5015]
    7, 2265.3, 40.7, [1747, 1779, 1859, 2130, 2246, 2609, 2814, 2890, 2985, 2986]
    8, 2313.6, 39.9, [1516, 1870, 2032, 2092, 2195, 2222, 2994, 3419, 3465, 3466]
    9, 1434.8, 38.8, [1107, 1123, 1176, 1311, 1396, 1509, 1572, 1687, 2483, 2484]
    10, 1205.5, 37.8, [879, 965, 1082, 1181, 1233, 1245, 1444, 1525, 1700, 1701]
""".trimIndent()

val oanSF2 = """
    4, 4497.7, 44.0, [2730, 3517, 3528, 3646, 4975, 5584, 5898, 6053, 6605, 6606]
    3, 4671.2, 45.0, [3696, 4070, 4432, 4462, 4471, 4846, 4981, 5182, 7452, 7453]
    2, 10704.0, 46.0, [9131, 9690, 9823, 10198, 10792, 10924, 11081, 11899, 14608, 14609]
    0, 37995.0, 46.0, [35708, 36864, 37150, 38326, 38428, 38762, 38935, 40279, 40532, 40533]
    5, 2394.8, 43.0, [2214, 2302, 2304, 2317, 2336, 2480, 2505, 2653, 2869, 2870]
    6, 4591.6, 42.0, [2944, 3019, 3100, 3404, 3450, 4287, 4345, 4748, 13793, 13794]
    1, 45049.1, 46.0, [43017, 43493, 44326, 44520, 45617, 46352, 46421, 46598, 47868, 47869]
    7, 2263.4, 41.0, [1689, 1711, 1977, 2039, 2090, 2298, 2670, 2706, 3809, 3810]
    8, 2242.4, 40.0, [1458, 1467, 1574, 1756, 2337, 2641, 3155, 3235, 3367, 3368]
    9, 1912.1, 39.0, [1693, 1715, 1717, 1947, 1973, 1980, 2082, 2165, 2249, 2250]
    10, 886.9, 38.0, [822, 827, 869, 871, 878, 895, 931, 978, 989, 990]
""".trimIndent()

