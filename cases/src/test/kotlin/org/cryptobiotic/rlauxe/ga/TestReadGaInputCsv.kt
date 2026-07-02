package org.cryptobiotic.rlauxe.ga

import kotlin.collections.forEach
import kotlin.io.path.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.test.Test

class TestReadGaInputCsv {
    val topdir = "/home/stormy/datadrive/github/nealmcb/rla-review-arlo/2026-05-19-primary/extracted"

    @Test
    fun readGaCountyInputCsv() {
        readGaCountyInputCsv(topdir)
    }


    @Test
    fun testReadGaCountyInputCsvOrg() {
        val contests = mutableSetOf<String>()
        val candidates  = mutableSetOf<String>()

        var count = 0
        val manifests = "$topdir/manifests"
        Path(manifests).listDirectoryEntries().sorted().forEach { countyPath ->
            val countyName = countyPath.name
            // if (countyName !in listOf("BURKE", "CHATHAM", "FULTON")) {
                try {
                    val batches = readGaCountyInputCsvOrg(countyName)
                    batches.forEach {
                        it.candCount.keys.forEach { key ->
                            contests.add(key.contest)
                            candidates.add(key.candName)
                        }
                    }
                } catch (e: Exception) {
                    println("*** ${e.message}")
                }
            // }
            count++
        }
        println("  $count counties")
        println()
        println("Contests = $contests")
        println("Candidates")
        candidates.sorted().forEach { println("   $it") }
    }

    fun readGaCountyInputCsvOrg(countyName: String): List<CountyBatch> {
        println("Read County $countyName")
        val manifests = "$topdir/manifests/$countyName"
        val manifestData = Path(manifests).listDirectoryEntries().first()
        val batches = readCountyManifest(manifestData.toString())

        val candidate_totals = "$topdir/candidate_totals/$countyName"
        val candData = Path(candidate_totals).listDirectoryEntries().first()
        readCandidateTotals(candData.toString(), batches)

        //batches.forEach {
        //    println(it)
        //}
        return batches
    }
}