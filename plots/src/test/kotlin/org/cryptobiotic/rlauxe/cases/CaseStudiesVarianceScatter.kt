package org.cryptobiotic.rlauxe.cases

import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.persist.validateOutputDir
import org.cryptobiotic.rlauxe.rlaplots.ScaleType
import org.cryptobiotic.rlauxe.rlaplots.genericScatter
import org.cryptobiotic.rlauxe.estimateOld.makeDeciles
import org.cryptobiotic.rlauxe.util.nfn
import org.jetbrains.kotlinx.kandy.letsplot.settings.Symbol
import org.jetbrains.kotlinx.kandy.util.color.Color
import kotlin.String
import kotlin.io.path.Path
import kotlin.test.Test

class CaseStudiesVarianceScatter {

    @Test
    fun caseStudiesBoulder() {
        val name = "Boulder2024AuditVarianceScatter"
        val dirName = "$testdataDir/plots/boulder2024/$name"

        validateOutputDir(Path(dirName))
        caseStudiesVariancePlots(
            name,
            dirName,
            "$testdataDir/cases/boulder24/clca/audit",
            "$testdataDir/cases/boulder24oa2/",
            false
        )
    }

    @Test
    fun caseStudiesSF() {
        val name = "SF2024AuditVarianceTrueMargins"
        val dirName = "$testdataDir/plots/sf2024/$name"

        validateOutputDir(Path(dirName))
        caseStudiesVariancePlots(
            name,
            dirName,
            "$testdataDir/cases/sf2024/clca/audit2",
            "$testdataDir/cases/sf2024oa/",
            false
        )
    }

    fun caseStudiesVariancePlots(name: String, dirName: String, clcaAuditDir: String, oaAuditDir: String, useClcaMargins: Boolean) {
        val nruns = 20
        val catPoints = mutableListOf<CatPoint>()
        val (totalClca, clcaAssertionMap) = readAuditRecord(clcaAuditDir, "CLCA")!!
        catPoints.addAll( clcaAssertionMap.values)
        println("CLCA has ${clcaAssertionMap.size} assertions")
        val clcaMargins = if (useClcaMargins) clcaAssertionMap.mapValues { it.value.margin } else null

        val totalOA = mutableListOf<Int>()
        repeat(20) { run ->
            val pair = readAuditRecord("$oaAuditDir/audit${run+1}", "OneAudit")
            println("OneAudit ${run+1} has ${clcaAssertionMap.size} assertions")
            if (pair != null) {
                totalOA.add(pair.first)
                val cats = if (clcaMargins == null) pair.second.values else {
                    pair.second.map { (key, value) ->
                        value.copy(margin = clcaMargins[key]!!)
                    }
                }
                catPoints.addAll(cats)
            }
        }
        val deciles = makeDeciles(totalOA)

        // add fake one to show the plot better
        catPoints.add(CatPoint("OneAudit", 1, .001))

        println("$name in $dirName :")
        println("totalClca    = ${nfn(totalClca,6)}")
        println("totalOA avg  = ${nfn(totalOA.average().toInt(),6)} ${deciles} ")

        val title = "$name nmvrs vs margin, no errors"
        val subtitle = "compare CLCA and $nruns OneAudit's nmvrs"
        val scaleType = ScaleType.LogLog

        genericScatter(
            titleS = title,
            subtitleS = subtitle,
            writeFile="$dirName/${name}${scaleType.name}",
            data = catPoints,
            xname = "margin",
            yname = "nmvrs",
            catName = "type",
            xfld = { it.margin },
            yfld = { it.samplesUsed.toDouble() },
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

data class CatPoint(val cat: String, val samplesUsed: Int, val margin: Double)

fun readAuditRecord(auditDir: String, cat: String, marginOverride:Map<Int, Double>? = null): Pair<Int, Map<String, CatPoint>>? {
    val auditRecordResult = AuditRecord.readFromResult(auditDir)
    if (auditRecordResult.isErr) {
        println(auditRecordResult)
        return null
    }
    val auditRecord = auditRecordResult.unwrap() as AuditRecord
    val auditRounds = auditRecord.rounds

    var totalMvrs = 0
    val allAssertions = mutableMapOf<String, CatPoint>()
    auditRounds.forEach { auditRound ->
        totalMvrs += auditRound.newmvrs
        val contestRounds = auditRound.contestRounds
        contestRounds.forEach { contestRound ->
            contestRound.assertionRounds.forEach { assertionRound ->  // could just use minAsertion
                val margin = if (marginOverride == null) assertionRound.assertion.assorter.dilutedMargin() else
                    marginOverride[assertionRound.assertion.id().hashCode()] ?: 0.0
                if (assertionRound.auditResult != null) {
                    allAssertions[assertionRound.assertion.assorter.hashcodeDesc()] = CatPoint(cat, assertionRound.auditResult!!.samplesUsed, margin)
                }
            }
        }
    }
    println("read $auditDir totalMvrs=$totalMvrs prevSamples=${auditRecord.previousMvrs.size} ")
    return Pair(totalMvrs, allAssertions)
}