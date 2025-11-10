package org.cryptobiotic.rlauxe.workflow

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap

import org.cryptobiotic.rlauxe.audit.AssertionRound
import org.cryptobiotic.rlauxe.audit.AuditRound
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.validateOutputDir
import org.cryptobiotic.rlauxe.rlaplots.ScaleType
import org.cryptobiotic.rlauxe.rlaplots.genericScatter
import org.cryptobiotic.rlauxe.util.nfn
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.fail

// comnpare audit variance across SF, SFoa and SFaNS
class SfAuditVarianceScatter {
    val nruns = 10 // no variance when there are no errors

    val name = "sf2024AuditVarianceScatter"
    val dirName = "/home/stormy/rla/plots/sf2024/$name"

    init {
        validateOutputDir(Path(dirName))
    }

    @Test
    fun genSfAuditVarianceComparePlots() {
        val allAssertions = mutableListOf<AssertionAndCat>()
        val (totalClca, clcaAssertions) = readAssertionAndTotal("/home/stormy/rla/cases/sf2024/audit0", "CLCA")
        val marginOverride = clcaAssertions.associate { it.assertion.assertion.id().hashCode() to it.assertion.assertion.assorter.reportedMargin() }
        allAssertions.addAll( clcaAssertions)

        val totalOA = mutableListOf<Int>()
        repeat(10) { run ->
            val (total, clcaAssertions) = readAssertionAndTotal("/home/stormy/rla/cases/sf2024oa/audit$run", "OneAudit")
            allAssertions.addAll(clcaAssertions)
            totalOA.add(total)
        }

        val totalOANS = mutableListOf<Int>()
        repeat(10) { run ->
            // overrride the margins
            val (total, clcaAssertions) = readAssertionAndTotal("/home/stormy/rla/cases/sf2024oaNS/audit$run", "OneAuditNS", marginOverride)
            allAssertions.addAll(clcaAssertions)
            totalOANS.add(total)
        }
        println("totalClca    = ${nfn(totalClca,6)}")
        println("totalOA avg  = ${nfn(totalOA.average().toInt(),6)}  ${totalOA.sorted()} ")
        println("totalOANS avg= ${nfn(totalOANS.average().toInt(), 6)} ${totalOANS.sorted()}")

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
            yfld = { it.assertion.estSampleSize.toDouble() },
            catfld = { it.cat },
            scaleType=scaleType,
        )
    }
}

data class AssertionAndCat(val assertion: AssertionRound, val cat: String, val margin: Double) {
    fun samplesUsed(): Int {
        return assertion.auditResult!!.samplesUsed
    }
}

fun readAssertionAndTotal(auditDir: String, cat: String, marginOverride:Map<Int, Double>? = null): Pair<Int, List<AssertionAndCat>> {
    val auditRecord = AuditRecord.readFromResult(auditDir)
    if (auditRecord is Err) {
        fail()
    }

    val auditRounds = auditRecord.unwrap().rounds
    require( auditRounds.size >= 1)
    val auditRound: AuditRound = auditRounds[0]

    val allAssertions = mutableListOf<AssertionAndCat>()
    val contestRounds = auditRound.contestRounds
    contestRounds.forEach { contestRound ->
        contestRound.assertionRounds.forEach { assertionRound ->
            val margin = if (marginOverride == null) assertionRound.assertion.assorter.reportedMargin() else
                marginOverride[assertionRound.assertion.id().hashCode()] ?: 0.0
            if (assertionRound.auditResult != null) {
                allAssertions.add(AssertionAndCat(assertionRound, cat, margin))
            }
        }
    }
    return Pair(auditRound.nmvrs, allAssertions)
}