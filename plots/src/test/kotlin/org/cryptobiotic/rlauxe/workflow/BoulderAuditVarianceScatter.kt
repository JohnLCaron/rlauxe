package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.persist.validateOutputDir
import org.cryptobiotic.rlauxe.rlaplots.ScaleType
import org.cryptobiotic.rlauxe.rlaplots.genericScatter
import org.cryptobiotic.rlauxe.util.nfn
import kotlin.io.path.Path
import kotlin.test.Test

// comnpare audit variance across SF, SFoa and SFaNS
class BoulderAuditVarianceScatter {
    val nruns = 10 // no variance when there are no errors

    val name = "Boulder2024AuditVarianceScatter"
    val dirName = "/home/stormy/rla/plots/boulder2024/$name"

    init {
        validateOutputDir(Path(dirName))
    }

    @Test
    fun genAuditVarianceComparePlots() {
        val allAssertions = mutableListOf<AssertionAndCat>()
        val (totalClca, clcaAssertions) = readAssertionAndTotal("/home/stormy/rla/cases/boulder24clca", "CLCA")
        allAssertions.addAll( clcaAssertions)

        val totalOA = mutableListOf<Int>()
        repeat(10) { run ->
            val (total, clcaAssertions) = readAssertionAndTotal("/home/stormy/rla/cases/boulder24oa/audit$run", "OneAudit")
            allAssertions.addAll(clcaAssertions)
            totalOA.add(total)
        }

        println("totalClca    = ${nfn(totalClca,6)}")
        println("totalOA avg  = ${nfn(totalOA.average().toInt(),6)}  ${totalOA.sorted()} ")

        val title = "$name est nmvrs vs margin, no errors"
        val subtitle = "compare Boulder 2024 audit variances, Ntrials=$nruns useRealSample"
        val scaleType = ScaleType.LogLinear

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