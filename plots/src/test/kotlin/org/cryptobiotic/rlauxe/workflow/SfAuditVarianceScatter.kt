package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.testdataDir
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.unwrap

import org.cryptobiotic.rlauxe.audit.AssertionRound
import org.cryptobiotic.rlauxe.audit.AuditRound
import org.cryptobiotic.rlauxe.audit.AuditRoundIF
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.validateOutputDir
import org.cryptobiotic.rlauxe.rlaplots.ScaleType
import org.cryptobiotic.rlauxe.rlaplots.genericScatter
import org.cryptobiotic.rlauxe.util.nfn
import org.jetbrains.kotlinx.kandy.util.color.Color
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.fail

// compare audit variance across SF and SFoa
class SfAuditVarianceScatter {
    val nruns = 10 // no variance when there are no errors

    val name = "sf2024AuditVarianceScatter"
    val dirName = "$testdataDir/plots/sf2024/$name"

    init {
        validateOutputDir(Path(dirName))
    }

    @Test
    fun genSfAuditVarianceComparePlots() {
        val allAssertions = mutableListOf<AssertionAndCat>()
        val (totalClca, clcaAssertions) = readAssertionAndTotal("$testdataDir/cases/sf2024/audit0", "CLCA")!!
        val marginOverride = clcaAssertions.associate { it.assertion.assertion.id().hashCode() to it.assertion.assertion.assorter.dilutedMargin() }
        allAssertions.addAll( clcaAssertions)

        val totalOA = mutableListOf<Int>()
        repeat(10) { run ->
            val pair = readAssertionAndTotal("$testdataDir/cases/sf2024oa/audit$run", "OneAudit")
            if (pair != null) {
                totalOA.add(pair.first)
                allAssertions.addAll(pair.second)
            }
        }

        println("totalClca    = ${nfn(totalClca,6)}")
        println("totalOA avg  = ${nfn(totalOA.average().toInt(),6)}  ${totalOA.sorted()} ")

        val title = "$name est nmvrs vs margin, no errors"
        val subtitle = "compare SF 2024 audit variances, Ntrials=$nruns useRealSample"
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
            yfld = { it.assertion.estMvrs.toDouble() },
            catfld = { it.cat },
            scaleType=scaleType,
            colors=mapOf("CLCA" to Color.RED,
                "OneAudit" to Color.LIGHT_BLUE,
            ),
        )
    }
}

data class AssertionAndCat(val assertion: AssertionRound, val cat: String, val margin: Double) {
    fun samplesUsed(): Int {
        return assertion.auditResult!!.samplesUsed
    }
}

fun readAssertionAndTotal(auditDir: String, cat: String, marginOverride:Map<Int, Double>? = null): Pair<Int, List<AssertionAndCat>>? {
    val auditRecord = AuditRecord.readFromResult(auditDir)
    if (auditRecord.isErr) {
        return null
    }

    val auditRounds = auditRecord.unwrap().rounds
    var totalMvrs = 0
    val allAssertions = mutableMapOf<Int, AssertionAndCat>()
    auditRounds.forEach { auditRound ->
        totalMvrs += auditRound.newmvrs
        val contestRounds = auditRound.contestRounds
        contestRounds.forEach { contestRound ->
            contestRound.assertionRounds.forEach { assertionRound ->
                val margin = if (marginOverride == null) assertionRound.assertion.assorter.dilutedMargin() else
                    marginOverride[assertionRound.assertion.id().hashCode()] ?: 0.0
                if (assertionRound.auditResult != null) {
                    allAssertions[contestRound.contestUA.id] = AssertionAndCat(assertionRound, cat, margin)
                }
            }
        }
    }
    println("read $auditDir")
    return Pair(totalMvrs, allAssertions.values.toList())
}