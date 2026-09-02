package org.cryptobiotic.rlauxe.boulder

import org.cryptobiotic.rlauxe.auditcenter.readCountyManifestCsv
import org.cryptobiotic.rlauxe.cvr.CvrRow
import org.cryptobiotic.rlauxe.cvr.RedactionBoulder
import org.cryptobiotic.rlauxe.cvr.readCorlaCvrs
import kotlin.test.Test

class TestCvrsAndManifests {

    @Test
    fun testBoulder23match() {
        testCvrsAndManifests(Boulder23Input())
    }

    @Test
    fun testBoulder24match() {
        testCvrsAndManifests(Boulder24Input())
    }

    @Test
    fun testBoulder25match() {
        testCvrsAndManifests(Boulder25Input())
    }

    @Test
    fun testBoulder26match() {
        testCvrsAndManifests(Boulder26pInput())
    }

    fun testCvrsAndManifests(input: BoulderInput) {
        val corlaCvrs = readCorlaCvrs(input.cvrsSource, redaction = RedactionBoulder())

        println("${input.cvrsSource}: nrows = ${corlaCvrs.nrows()} cvrs size = ${corlaCvrs.cvrs().size}")

        // does this matter ?
        if (input.electionName == "Boulder2024") { Boulder24Input.removeContest12FromPool6(corlaCvrs.redactedGroups()) }
        val redactedCards = corlaCvrs.redactedGroups().sumOf {  it.ncards() }
        println("   redacted groups ${corlaCvrs.redactedGroups().size}")
        corlaCvrs.redactedGroups().forEach { println(it)}

        println("   redacted ncards = ${redactedCards}")
        println("   cvrs + redacted ncards = ${redactedCards + corlaCvrs.cvrs().size}")
        val totalCvrs = redactedCards + corlaCvrs.cvrs().size

        val manifestBatches = readCountyManifestCsv(input.manifestSource)
        val sumManifest = manifestBatches.sumOf{ it.nballotCards }
        println("\n${input.manifestSource}")
        println("  sumManifest = $sumManifest")

        println("  sumManifest - cvrs = ${sumManifest - corlaCvrs.cvrs().size}")
        val diff = sumManifest - totalCvrs
        print("  sumManifest - totalCvrs = ${diff}")
        if (diff > 0) print("  ==  more redacted cards? or overvotes that were thrown out ??")
        if (diff < 0) print("  ==  manifest not up to date ??")
        println()

        /////////////////////////////////////////////////////////////////
        val cvrMap = corlaCvrs.cvrs().associate { it.imprintedId to CvrId(it) }
        println("\nmatch manifest cards to cvrs")

        var countMiss = 0
        var countDup = 0
        manifestBatches.forEach { batch ->
            repeat(batch.nballotCards) { recordId ->
                val want = "${batch.tabulatorNum}-${batch.batchId}-${recordId+1}"
                val cvr = cvrMap[want]
                if (cvr != null) {
                    if (cvr.matched) countDup++
                    cvr.matched = true
                } else
                    countMiss++
                    // println("didnt find cvr $want")
            }
        }
        println("  # manifest cards not found in cvrs == $countMiss countDup == $countDup")

        println("\nmatch cvrs to manifest")
        var count = 0
        cvrMap.forEach { (key, id) ->
            if (!id.matched) {
                // println("  $key")
                count++
            }
        }
        println("  # cvrs not found in manifest == $count")
        if (count > 0) println("   probably manifest file was updated but not in auditcenter")

        // println("  countMiss - missing from cvrs == ${countMiss - missing} redactedCards == $redactedCards")
    }

    data class CvrId(val tabulatorNum: Int, val batchId: String, val recordId: Int) {
        var matched = false
        constructor(cvr: CvrRow): this(cvr.tabulatorNum, cvr.batchId, cvr.recordId) {
            if (cvr.imprintedId != "${cvr.tabulatorNum}-${cvr.batchId}-${cvr.recordId}")
                print("")
            require(cvr.imprintedId == "${cvr.tabulatorNum}-${cvr.batchId}-${cvr.recordId}")
        }
    }
}