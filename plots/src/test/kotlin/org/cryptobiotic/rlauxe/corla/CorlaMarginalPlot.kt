package org.cryptobiotic.rlauxe.corla

import org.cryptobiotic.rlauxe.audit.AuditRound
import org.cryptobiotic.rlauxe.audit.resampleAndSaveResults
import org.cryptobiotic.rlauxe.betting.TestH0Status
import org.cryptobiotic.rlauxe.cases
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.validateOutputDir
import org.cryptobiotic.rlauxe.rlaplots.ScaleType
import org.cryptobiotic.rlauxe.rlaplots.genericPlotter
import org.cryptobiotic.rlauxe.testdataDir
import kotlin.io.path.Path
import kotlin.test.Test

// show incremental costs of including close contests
// first run in viewer to set up the include flags and risk levels; run "Resample" to write to audit record
class CorlaMarginalPlot {

    @Test
    fun makeCorla26PMarginal() {
        val name = "Corla2024MarginalRelaxed"
        val dirName = "$testdataDir/plots/corla/$name"
        validateOutputDir(Path(dirName))

        val topdir = "$cases/corla/corla2024"
        val corla = AuditRecord.read(topdir) as AuditRecord
        val round1: AuditRound = corla.rounds.first()
        val contests =
            round1.contestRounds.filter { it.status == TestH0Status.InProgress }.sortedBy { it.contestUA.minMargin() }.reversed()
        contests.forEach {
            it.included = false
            it.auditorWantRisk = null
        }
        val contestIter = contests.iterator()

        val mplotData = mutableListOf<MarginalPlotData>()
        mplotData.add( MarginalPlotData(301.0, 4897.0, "corla"))

        var contestNo = 0
        while (contestNo < contests.size) {
            var count = 0
            val limit = if (contestNo < 550) 50 else 10
            while (contestIter.hasNext() && count < limit) {
                contestIter.next().included = true
                count++
                contestNo++
            }
            resampleAndSaveResults(corla, round1)
            mplotData.add( MarginalPlotData(contestNo.toDouble(), round1.nmvrs.toDouble(), "rlauxe"))
            println("$contestNo, ${round1.nmvrs}")
        }
        mplotData.add( MarginalPlotData(contests.size.toDouble(), round1.nmvrs.toDouble(), "rlauxe"))

        val title = "Corla2024 (Relaxed risks) incremental costs of including close contests"
        val subtitle = "(Corla uses 4897 nmvrs for 301 contests under 3%)"
        val scale = ScaleType.Linear
        makeMarginalPlot(
            writeFile = "$dirName/$name.$scale",
            title = title,
            subtitle = subtitle,
            mplotData,
            scale
        )
    }
}

data class MarginalPlotData(val ncontests: Double, val nmvrs: Double, val cat: String)

fun makeMarginalPlot(writeFile: String, title: String, subtitle: String, data: List<MarginalPlotData>, scaleType: ScaleType) {
    genericPlotter(
        titleS = title,
        subtitleS = subtitle,
        writeFile = writeFile,
        scaleType=scaleType,
        data = data,
        xname = "ncontests included, sorted by margin", xfld = { it.ncontests },
        yname = "nmvrs needed", yfld = { it.nmvrs },
        catName = "auditor", catfld = { it.cat },
        addPoints = true,
        addHLineAt= 4897.0,
        addVLineAt= 301.0,
    )
}