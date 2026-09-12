package org.cryptobiotic.rlauxe.corlaInput

import org.cryptobiotic.rlauxe.auditcenter.ManifestBatch
import org.cryptobiotic.rlauxe.auditcenter.readCountyManifestCsv
import org.cryptobiotic.rlauxe.cvr.CvrRow
import org.cryptobiotic.rlauxe.util.nfz
import kotlin.collections.forEach

interface ManifestEntry {
    fun imprintedId(): String
    fun location(): String
}

data class ManifestId2(val tab: Int, val batch: String, val record: Int, val location: String): ManifestEntry {
    var matched = false // did we find a match yet?
    val id = "$tab-$batch-$record"
    val sorter: String

    init {
        var tsorter = ""
        try {
            val batchAsInt = batch.toInt()
            tsorter = (1000_000 * tab + 1000 * batchAsInt + record).toString()
        } catch (e: Throwable) {
            tsorter = nfz(tab,4) + batch + nfz(record,4)
        }
        sorter = tsorter
    }

    constructor(cvr: CvrRow) : this(cvr.tabulatorNum, cvr.batchId, cvr.recordId, "")

    override fun imprintedId() = id
    override fun location() = location
}

class GarfieldManifest(manifestSource: String): CountyManifest(manifestSource), Iterable<ManifestEntry> {

}


open class CountyManifest(val manifestBatches: List<ManifestBatch>): Iterable<ManifestEntry> {
    val totalCards: Int

    init {
        totalCards = manifestBatches.sumOf{ it.nballotCards }
    }

    constructor(manifestSource: String): this(readCountyManifestCsv(manifestSource))

    fun uppercase(): CountyManifest{
        val upperBatches = manifestBatches.map { it.copy( batchId = it.batchId.uppercase() ) }
        return CountyManifest(upperBatches)
    }

    override fun iterator(): Iterator<ManifestEntry> = MEiterator(manifestBatches.iterator())

    class MEiterator(val batchIterator: Iterator<ManifestBatch>) : Iterator<ManifestId2> {
        var batch: ManifestBatch? = null
        var recordNo = 1

        init {
            if (batchIterator.hasNext()) batch = batchIterator.next()
        }

        override fun next(): ManifestId2 {
            return ManifestId2(batch!!.tabulatorNum, batch!!.batchId, recordNo++, batch!!.location)
        }

        override fun hasNext(): Boolean {
            if (recordNo < batch!!.nballotCards) return true
            if (batchIterator.hasNext()) {
                batch = batchIterator.next()
                recordNo = 1
                return true
            }
            batch = null
            return false
        }
    }

    fun checkManifestVsCvrs(cvrs: List<CvrRow>, report: MutableList<String>? = null, showUnmatched:Boolean = false): ManifestIds2 {

        val manifestIdMap = mutableMapOf<String, ManifestId2>()
        val meiter = MEiterator(manifestBatches.iterator())
        while (meiter.hasNext()) {
            val me2 = meiter.next()
            manifestIdMap[me2.imprintedId()] = me2
        }

        var countMiss = 0 // count of Cvrs not in the manifest
        var countDup = 0  // count of duplicate ids in the Cvrs
        val missedIds = mutableListOf<ManifestId2>()
        cvrs.forEach { cvrrow ->
            val manifestMatch = manifestIdMap[cvrrow.imprintedId]
            if (manifestMatch != null) {
                if (manifestMatch.matched) countDup++
                manifestMatch.matched = true
            } else {
                countMiss++
                missedIds.add(ManifestId2(cvrrow))
            }
        }

        var unmatched = 0
        val redactedIDs = mutableListOf<ManifestId2>()
        manifestIdMap.values.forEach { mid ->
            if (!mid.matched) {
                redactedIDs.add(mid)
                unmatched++
            }
        }

        if (report != null) {
            missedIds.sortBy{ it.sorter }
            var count = 1
            var currentTab =  0
            missedIds.forEach { mid ->
                if (mid.tab != currentTab) {
                    report.add("")
                    count = 1
                }
                currentTab = mid.tab
                report.add("$count  didnt find cvr imprintedId '${mid.id}' in manifest")
                count++
            }

            if (showUnmatched) {
                report.add("")
                var count = 1
                manifestIdMap.values.forEach { mid ->
                    if (!mid.matched) {
                        report.add("$count  mvr '${mid.id}' has no match in the CVRs")
                        count++
                    }
                }
            }

            report.add("")
            report.add("cards not found in manifest= $countMiss; duplicate imprintedIds= $countDup")
            val countUnmatched = manifestIdMap.values.count { !it.matched }
            report.add("countMiss=$countMiss countUnmatched=$countUnmatched countDup=$countDup")
            report.add("-------------------------------------------------------------------------------")
        }
        return ManifestIds2(unmatched, manifestIdMap, redactedIDs)
    }
}

data class ManifestIds2(
    val unmatched: Int,                     // count of Manifest entries not in the Cvrs; presumed to be == redacted CVRs
    val match: Map<String, ManifestEntry>,    // imprintedId -> ManifestId2
    val redactedIds: List<ManifestEntry>      // didnt match a cvr, assume to be in the redactions
)