package org.cryptobiotic.rlauxe.cases

import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.persist.validateOutputDir
import org.cryptobiotic.rlauxe.rlaplots.ScaleType
import org.cryptobiotic.rlauxe.rlaplots.genericScatter
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.makeDeciles
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.util.roundUp
import org.jetbrains.kotlinx.kandy.letsplot.settings.Symbol
import org.jetbrains.kotlinx.kandy.util.color.Color
import kotlin.String
import kotlin.io.path.Path
import kotlin.test.Test

// comnpare audit variance across SF, SFoa and SFaNS
class CaseStudiesRemoveNminMargin {

    @Test
    fun caseStudiesSF() {
        val name = "SF2024RemoveNminMargin"
        val dirName = "$testdataDir/plots/sf2024/$name"
        validateOutputDir(Path(dirName))

        // see MakeSfElection.createSFremoveNclca()
        val clcaNmvrs = listOf(3480, 1759, 1103, 1028, 837, 745, 732, 607, 559, 455, 398)
        val clcaPoints = clcaNmvrs.mapIndexed { idx, nmvrs -> NmvrPoint("CLCA",idx+1, nmvrs) }

        // see MakeSfElection.createSFremoveNoa()
        val oaNmvrs = listOf(
            NmvrPoints(1, 10469.9, listOf(8936, 9167, 10097, 10118, 10519, 11353, 11614, 12208, 12428, 12429)),
                    NmvrPoints(2, 9634.5, listOf(7674, 8999, 9120, 9208, 9787, 9972, 10615, 11760, 12056, 12057)),
                    NmvrPoints(3, 6591.5, listOf(5627, 5810, 6008, 6416, 6450, 6813, 7396, 7512, 8267, 8268)),
                    NmvrPoints(4, 7396.4, listOf(6569, 7139, 7230, 7294, 7682, 7690, 7883, 8195, 8566, 8567)),
                    NmvrPoints(5, 4425.4, listOf(4010, 4156, 4207, 4232, 4365, 4451, 4531, 4967, 5364, 5365)),
                    NmvrPoints(6, 4470.0, listOf(3858, 4047, 4260, 4512, 4551, 4664, 4900, 4904, 5175, 5176)),
                    NmvrPoints(7, 4219.5, listOf(3701, 3786, 3853, 4383, 4394, 4396, 4527, 4544, 4939, 4940)),
                    NmvrPoints(8, 1487.0, listOf(1329, 1348, 1418, 1464, 1489, 1527, 1575, 1609, 1783, 1784)),
                    NmvrPoints(9, 1363.6, listOf(1211, 1296, 1297, 1299, 1390, 1407, 1471, 1476, 1650, 1651)),
                    NmvrPoints(10, 1001.3, listOf(937, 957, 960, 982, 1009, 1039, 1054, 1061, 1087, 1088)),
                    NmvrPoints(11, 783.0, listOf(762, 773, 781, 783, 785, 786, 793, 818, 839, 840)),
        )

        // create table
        clcaPoints.forEachIndexed { idx, nmvr ->
            val oaNmvr = oaNmvrs[idx]
            val ratio = oaNmvr.avg / nmvr.nmvrs
            println("| ${idx+1} | ${nmvr.nmvrs} | ${roundUp(oaNmvr.avg)} | ${dfn(ratio, 1)}  | ${oaNmvr.nmvrs} |")
        }

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
            xname = "n min-margin contests removed",
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
