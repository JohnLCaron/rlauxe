package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.persist.validateOutputDir
import org.cryptobiotic.rlauxe.rlaplots.ScaleType
import org.cryptobiotic.rlauxe.rlaplots.genericScatter
import kotlin.io.path.Path
import kotlin.test.Test

// comnpare audit variance across SF, SFoa and SFaNS
class SfAuditVarianceCompare {
    val nruns = 500 // no variance when there are no errors

    val name = "sf2024AuditVarianceCompare"
    val dirName = "/home/stormy/rla/plots/sf2024/$name"

    init {
        validateOutputDir(Path(dirName))
    }

    @Test
    fun genSfAuditVarianceComparePlots() {
        val allAssertions = mutableListOf<AssertionAndCat>()
        val clcaAssertions = readAssertionAndTotal("/home/stormy/rla/cases/sf2024/audit", "CLCA").second
        allAssertions.addAll( clcaAssertions)
        allAssertions.addAll( readAssertionAndTotal("/home/stormy/rla/cases/sf2024oa/audit", "OneAudit").second)

        // overrride the margins
        val marginOverride = clcaAssertions.associate { it.assertion.assertion.id().hashCode() to it.assertion.assertion.assorter.reportedMargin() }
        allAssertions.addAll( readAssertionAndTotal("/home/stormy/rla/cases/sf2024oaNS/audit", "OneAuditNS", marginOverride).second)

        val title = "$name est nmvrs vs margin, no errors"
        val subtitle = "compare SF 2024 audit variances, Ntrials=$nruns quantile=80%"
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
        )
    }
}