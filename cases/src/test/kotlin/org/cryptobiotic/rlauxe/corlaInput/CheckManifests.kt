package org.cryptobiotic.rlauxe.corlaInput

import org.cryptobiotic.rlauxe.auditcenter.BatchCountComparison
import org.cryptobiotic.rlauxe.auditcenter.ManifestBatch
import org.cryptobiotic.rlauxe.auditcenter.readCountyManifestCsv
import org.cryptobiotic.rlauxe.util.Locations.auditcenter
import kotlin.test.Test

class CheckManifests {
    val check = "$auditcenter/2020/general/batch_count_comparison.csv"

    @Test
    fun readOneCountyManifest() {
        val batchCounts : Map<String, List<BatchCountComparison>> = BatchCountComparison.readFromFile(check).groupBy { it.county_name }
        val stateInput = Statewide2020General()

        batchCounts.forEach { (county, batches) ->
            println("-----$county")
            val sortedBatches = batches.sortedBy { "${it.scanner_id}-${it.batch_id}" }
            val batchTotal1 = batches.map { it.count_per_manifest }.sum()
            val batchTotal2 = batches.map { it.count_per_cvr_file }.sum()
            println("   ${batchTotal1 == batchTotal2}: ${batchTotal1} ${batchTotal2} ")

            val countyManifest = stateInput.countyManifest(county)
            if (countyManifest == null) println("no manifest for $county") else {
                val sortedManifest = countyManifest.batches.sortedBy { "${it.tabulatorNum}-${it.batchId}" }
                println("   ${sortedBatches.size == sortedManifest.size}: ${sortedBatches.size} ${sortedManifest.size} ")
                if (sortedBatches.size != sortedManifest.size) show(sortedBatches, sortedManifest)
            }
        }
    }

    fun show(batches: List<BatchCountComparison>, manifest: List<ManifestBatch>) {
        var index = 0
        while (index < batches.size) {
            if (batches[index].show() != manifest[index].show()) {
                println("${batches[index].show() == manifest[index].show()}:  ${batches[index].show()}  |  ${manifest[index].show()}")
            }
            index++
        }
        while (index < manifest.size) {
            println("                       |  ${manifest[index].show()}")
            index++
        }
    }
}