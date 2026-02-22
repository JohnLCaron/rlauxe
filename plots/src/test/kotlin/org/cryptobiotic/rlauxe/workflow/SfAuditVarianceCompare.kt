package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.persist.validateOutputDir
import org.cryptobiotic.rlauxe.rlaplots.ScaleType
import org.cryptobiotic.rlauxe.rlaplots.genericScatter
import org.jetbrains.kotlinx.kandy.util.color.Color
import kotlin.io.path.Path
import kotlin.test.Test

// compare audit variance across SF, SFoa and SFoaNS
class SfAuditVarianceCompare {
    val nruns = 500 // no variance when there are no errors

    val name = "sf2024AuditVarianceCompare"
    val dirName = "$testdataDir/plots/sf2024/$name"

    init {
        validateOutputDir(Path(dirName))
    }

    @Test
    fun genSfAuditVarianceComparePlots() {
        val allAssertions = mutableListOf<AssertionAndCat>()
        val clcaAssertions = readAssertionAndTotal("$testdataDir/cases/sf2024/clca/audit", "CLCA").second
        allAssertions.addAll( clcaAssertions)
        allAssertions.addAll( readAssertionAndTotal("$testdataDir/cases/sf2024/oa/audit", "OneAudit").second)

        // overrride the margins
        val marginOverride = clcaAssertions.associate { it.assertion.assertion.id().hashCode() to it.assertion.assertion.assorter.dilutedMargin() }
        // dont have oaNS
        // allAssertions.addAll( readAssertionAndTotal("$testdataDir/cases/sf2024/oaNS/audit", "OneAuditNS", marginOverride).second)

        val title = "$name est nmvrs vs margin, no errors"
        val subtitle = "compare SF 2024 audit variances"
        val scaleType = ScaleType.LogLog

        genericScatter(
            titleS = title,
            subtitleS = subtitle,
            writeFile="$dirName/${name}${scaleType.name}",
            data = allAssertions,
            xname = "margin",
            yname = "estimated samples",
            catName = "type",
            xfld = { it.margin },
            yfld = { it.samplesUsed().toDouble() },
            catfld = { it.cat },
            scaleType=scaleType,
            colors=mapOf("CLCA" to Color.RED,
                "OneAudit" to Color.LIGHT_BLUE,
            ),
        )
    }
}