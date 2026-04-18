package org.cryptobiotic.rlauxe.cases

import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.persist.validateOutputDir
import org.cryptobiotic.rlauxe.rlaplots.ScaleType
import org.cryptobiotic.rlauxe.rlaplots.genericScatter
import org.cryptobiotic.rlauxe.estimateOld.makeDeciles
import org.cryptobiotic.rlauxe.util.Welford
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.nfn
import org.jetbrains.kotlinx.kandy.letsplot.settings.Symbol
import org.jetbrains.kotlinx.kandy.util.color.Color
import kotlin.String
import kotlin.io.path.Path
import kotlin.math.sqrt
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
            null,
            false
        )
    }

    @Test
    fun caseStudiesSF() {
        val name = "SF2024AuditVarianceScatter"
        val dirName = "$testdataDir/plots/sf2024/$name"

        validateOutputDir(Path(dirName))
        caseStudiesVariancePlots(
            name,
            dirName,
            "$testdataDir/cases/sf2024/clca/audit",
            "$testdataDir/cases/sf2024oa",
            "$testdataDir/cases/sf2024oasp",
            true
        )
    }

    fun caseStudiesVariancePlots(name: String, dirName: String, clcaAuditDir: String, oaAuditDir: String, spAuditDir: String?,
                                 useClcaMargins: Boolean) {
        val nruns = 20
        val skip = listOf(14, 28)
        val catPoints = mutableListOf<CatPoint>()

        val (totalClca, clcaAssertionMap) = readAuditRecord(clcaAuditDir, "CLCA")!!
        catPoints.addAll( clcaAssertionMap.values)
        println("CLCA has ${clcaAssertionMap.size} assertions")
        val clcaMargins = if (useClcaMargins) clcaAssertionMap.mapValues { it.value.margin } else null

        val readResults = mutableListOf<Map<String, CatPoint>>()
        readResults.add(clcaAssertionMap)

        val totalOA = mutableListOf<Int>()
        val oneshotOA = mutableListOf<Int>()
        repeat(nruns) { run ->
            val auditdir = "$oaAuditDir/audit${run+1}"
            val pair = readAuditRecord(auditdir, "OneAudit")
            println("OneAudit ${run+1} has ${clcaAssertionMap.size} assertions")
            if (pair != null) {
                val (totalMvrs, allAssertions) = pair
                totalOA.add(totalMvrs)
                val cats = if (clcaMargins == null) allAssertions.values else {
                    allAssertions.map { (hashcodeDesc, catPoint) ->
                        catPoint.copy(margin = clcaMargins[hashcodeDesc]!!)
                    }
                }
                catPoints.addAll(cats)
                readResults.add(allAssertions)
            }
            // oneshotOA.add(OneShotAudit(auditdir).run(skip, writeFile=Publisher(auditdir).privateOneshotFile())) // TODO embed OneShotAudit into generation for speed of plotting
        }

        val totalSP = mutableListOf<Int>()
        val oneshotSP = mutableListOf<Int>()
        repeat(nruns) { run ->
            val auditdir = "$spAuditDir/audit${run+1}"
            val pair = readAuditRecord(auditdir, "PrecinctStyleOA")
            println("PrecinctStyleOA ${run+1} has ${clcaAssertionMap.size} assertions")
            if (pair != null) {
                val (totalMvrs, allAssertions) = pair
                totalSP.add(totalMvrs)
                val cats = if (clcaMargins == null) allAssertions.values else {
                    allAssertions.map { (hashcodeDesc, catPoint) ->
                        catPoint.copy(margin = clcaMargins[hashcodeDesc]!!)
                    }
                }
                catPoints.addAll(cats)
                readResults.add(allAssertions)
            }
            // oneshotSP.add(OneShotAudit(auditdir).run(skip, writeFile=Publisher(auditdir).privateOneshotFile()))
        }

        println("$name in $dirName :")
        println("totalClca    = ${nfn(totalClca,6)}")
        println("totalOA avg  = ${nfn(totalOA.average().toInt(),6)} ${makeDeciles(totalOA)} ")
        println("totalSP avg  = ${nfn(totalSP.average().toInt(),6)} ${makeDeciles(totalSP)} ")
        println("totalOA stdev  = ${dfn(totalOA.stddev(),3)}")
        println("totalSP stdev  = ${dfn(totalSP.stddev(),3)}")
        println()
        println("oneshotOA avg  = ${nfn(oneshotOA.average().toInt(),6)} ${makeDeciles(oneshotOA)} ")
        println("oneshotSP avg  = ${nfn(oneshotSP.average().toInt(),6)} ${makeDeciles(oneshotSP)} ")
        println("oneshotOA stdev  = ${dfn(oneshotOA.stddev(),3)}")
        println("oneshotSP stdev  = ${dfn(oneshotSP.stddev(),3)}")

        ////////////////////////////////////////////
        // add fake point to show the plot better
        catPoints.add(CatPoint("OneAudit", 1, .002))

        val title = "$name nmvrs vs margin, no errors"
        val subtitle = "compare CLCA and 2 types of OneAudit, cutoff=2500"
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
            symbols=mapOf(
                "CLCA" to Symbol.CIRCLE_OPEN,
                "OneAudit" to Symbol.CIRCLE_SMALL,
                "PrecinctStyleOA" to Symbol.CIRCLE_SMALL,
            ),
            colors=mapOf(
                "CLCA" to Color.RED,
                "OneAudit" to Color.LIGHT_BLUE,
                "PrecinctStyleOA" to Color.PURPLE,
            ),
        )

        ////////////////////////////////////////////
        // redo that plot using the deciles

        val named = "SF2024AuditVarianceDeciles"
        val titled = "$named nmvrs vs margin, deciles"

        // catPoint: List<CatPoint(val cat (assorterDesc): String, val samplesUsed: Int, val margin: Double)>
        val catPointMap = mutableMapOf<String, MutableList<CatPoint>>()
        readResults.forEach { assertionMap: Map<String, CatPoint> ->
            assertionMap.forEach { (hashcodeDesc, catPoint) ->
                val list = catPointMap.getOrPut(hashcodeDesc) { mutableListOf() }
                list.add( catPoint)
            }
        }

        val catPointsd = mutableListOf<CatPoint>()
        catPointsd.addAll( clcaAssertionMap.values)
        catPointMap.forEach { (assort: String, list: List<CatPoint>) ->
            val decilesOA = makeDeciles(list.filter { it.cat == "OneAudit"}.map { it.samplesUsed })
            val margin = clcaMargins!![assort]
            decilesOA.forEach { catPointsd.add( CatPoint("OneAudit", it, margin!!)) }

            val decilesSP = makeDeciles(list.filter { it.cat == "PrecinctStyleOA"}.map { it.samplesUsed })
            decilesSP.forEach { catPointsd.add( CatPoint("PrecinctStyleOA", it, margin!!)) }
        }
        catPointsd.addAll( clcaAssertionMap.values)

        // add fake point to show the plot better
        catPointsd.add(CatPoint("OneAudit", 1, .002))
        val scaleTyped = ScaleType.Linear

        genericScatter(
            titleS = titled,
            subtitleS = subtitle,
            writeFile="$dirName/${named}${scaleTyped.name}",
            data = catPointsd,
            xname = "margin",
            yname = "nmvrs",
            catName = "type",
            xfld = { it.margin },
            yfld = { it.samplesUsed.toDouble() },
            catfld = { it.cat },
            scaleType=scaleTyped,
            symbols=mapOf(
                "CLCA" to Symbol.CIRCLE_OPEN,
                "OneAudit" to Symbol.CIRCLE_SMALL,
                "PrecinctStyleOA" to Symbol.CIRCLE_SMALL,
            ),
            colors=mapOf(
                "CLCA" to Color.RED,
                "OneAudit" to Color.LIGHT_BLUE,
                "PrecinctStyleOA" to Color.PURPLE,
            ),
        )
    }
}

fun List<Int>.stddev(): Double {
    val welford = Welford()
    this.forEach { welford.update(it.toDouble()) }
    return sqrt(welford.variance())
}

data class CatPoint(val cat: String, val samplesUsed: Int, val margin: Double)

fun readAuditRecord(auditDir: String, cat: String, marginOverride:Map<Int, Double>? = null): Pair<Int, Map<String, CatPoint>>? {
    val auditRecordResult = AuditRecord.readWithResult(auditDir)
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
    println("read $auditDir totalMvrs=$totalMvrs")
    return Pair(totalMvrs, allAssertions)
}