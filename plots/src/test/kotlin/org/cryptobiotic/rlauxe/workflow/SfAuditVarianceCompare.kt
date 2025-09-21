package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.AssertionRound
import org.cryptobiotic.rlauxe.persist.AuditRecord.Companion.readFrom
import org.cryptobiotic.rlauxe.persist.validateOutputDir
import org.cryptobiotic.rlauxe.rlaplots.ScaleType
import org.cryptobiotic.rlauxe.rlaplots.genericPlotter
import org.cryptobiotic.rlauxe.rlaplots.genericScatter
import org.cryptobiotic.rlauxe.rlaplots.wrsScatterPlot
import org.jetbrains.kotlinx.kandy.util.color.Color
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
        val clcaAssertions = readAudit("/home/stormy/rla/cases/sf2024/audit", "CLCA")
        allAssertions.addAll( clcaAssertions)
        allAssertions.addAll( readAudit("/home/stormy/rla/cases/sf2024oa/audit", "OneAudit"))

        // overrride the margins
        val marginOverride = clcaAssertions.associate { it.assertion.assertion.id().hashCode() to it.assertion.assertion.assorter.reportedMargin() }
        allAssertions.addAll( readAudit("/home/stormy/rla/cases/sf2024oaNS/audit", "OneAuditNS", marginOverride))

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
            yfld = { it.assertion.estSampleSize.toDouble() },
            catfld = { it.cat },
            scaleType=scaleType,
        )
    }
}


data class AssertionAndCat(val assertion: AssertionRound, val cat: String, val margin: Double)

fun readAudit(auditDir: String, cat: String, marginOverride:Map<Int, Double>? = null): List<AssertionAndCat> {
    val auditRecord = readFrom(auditDir)
    val auditRounds = auditRecord.rounds
    require( auditRounds.size >= 1)
    val auditRound = auditRounds[0]

    val allAssertions = mutableListOf<AssertionAndCat>()
    val contestRounds = auditRound.contestRounds
    contestRounds.forEach { contestRound ->
        contestRound.assertionRounds.forEach { assertionRound ->
            val margin = if (marginOverride == null) assertionRound.assertion.assorter.reportedMargin() else
                marginOverride[assertionRound.assertion.id().hashCode()] ?: 0.0
            allAssertions.add( AssertionAndCat(assertionRound, cat, margin))
        }
    }
    return allAssertions
}