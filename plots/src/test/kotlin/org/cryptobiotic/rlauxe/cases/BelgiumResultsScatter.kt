package org.cryptobiotic.rlauxe.cases

import org.cryptobiotic.rlauxe.core.AboveThreshold
import org.cryptobiotic.rlauxe.core.BelowThreshold
import org.cryptobiotic.rlauxe.dhondt.DHondtAssorter
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.CompositeRecord
import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.persist.validateOutputDir
import org.cryptobiotic.rlauxe.rlaplots.ScaleType
import org.cryptobiotic.rlauxe.rlaplots.genericScatter
import kotlin.io.path.Path
import kotlin.test.Test

// comnpare audit variance across SF, SFoa and SFaNS
class BelgiumResultsScatter {
    val belgiumDataDir = "$testdataDir/cases/belgium/2024"
    val belgiumData : CompositeRecord

    val name = "BelgiumResultsScatter"
    val dirName = "$testdataDir/plots/cases"

    init {
        belgiumData = CompositeRecord.readFrom(belgiumDataDir) as CompositeRecord

        validateOutputDir(Path(dirName))
    }

    @Test
    fun genBelgiumResultsScatterPlots() {
        val all = readSampleAndCat(belgiumData)
        println("total assertions = ${all.size}")
        println("dhondt assertions = ${all.filter { it.cat == "DHondt"}.count() }")
        println("above assertions = ${all.filter { it.cat == "Above"}.count() }")
        println("below assertions = ${all.filter { it.cat == "Below"}.count() }")
        println("samples < 1000 = ${all.filter { it.sampleSize <= 1000 }.count() }")

        val title = "SamplesUsed vs noerror assort value"
        val subtitle = "all 426 Belgium 2024 election assertions"
        val scaleType = ScaleType.LogLog

        genericScatter(
            titleS = title,
            subtitleS = subtitle,
            writeFile="$dirName/${name}${scaleType.name}",
            data = all,
            xname = "noerror = 1/(2-v/u)",
            yname = "samples used",
            catName = "type",
            xfld = { it.noerror },
            yfld = { it.sampleSize.toDouble() },
            catfld = { it.cat },
            scaleType=scaleType,
        )
    }
}

data class SampleAndCat(val sampleSize: Int, val cat: String, val noerror: Double)

fun readSampleAndCat(belgiumData : CompositeRecord): List<SampleAndCat> {
    val result = mutableListOf<SampleAndCat>()
    belgiumData.componentRecords.forEach { auditRecord: AuditRecord ->
        val lastRound = auditRecord.rounds.last()
        lastRound.contestRounds.forEach { contestRound ->
            contestRound.assertionRounds.forEach { assertionRound ->
                val assorter = assertionRound.assertion.assorter
                val noerror = assorter.noerror()
                val sampleSize = if (assertionRound.auditResult != null) assertionRound.auditResult!!.samplesUsed else 0
                val cat = when (assorter) {
                    is DHondtAssorter -> "DHondt"
                    is BelowThreshold -> "Below"
                    is AboveThreshold -> "Above"
                    else -> "unknown"
                }
                result.add(SampleAndCat(sampleSize, cat, noerror))
            }
        }
    }
    return result
}