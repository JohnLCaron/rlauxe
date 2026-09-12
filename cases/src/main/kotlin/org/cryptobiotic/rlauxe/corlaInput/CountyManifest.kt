package org.cryptobiotic.rlauxe.corlaInput

import org.cryptobiotic.rlauxe.auditcenter.ManifestBatch
import org.cryptobiotic.rlauxe.auditcenter.readCountyManifestCsv
import org.cryptobiotic.rlauxe.cvr.CorlaCvrsIF
import org.cryptobiotic.rlauxe.cvr.CvrRow
import org.cryptobiotic.rlauxe.util.nfz
import kotlin.collections.forEach

interface ManifestEntry: Comparable<ManifestEntry> {
    fun imprintedId(): String
    fun location(): String
    fun matched(): Boolean
    fun setMatched(match: Boolean)
    fun tab(): Int // needed ??
}

data class GarfieldEntry(val tab: Int, val batch: String, val record: Int, val location: String): ManifestEntry {
    private var matched = false // did we find a match yet?

    constructor(cvr: CvrRow) : this(cvr.tabulatorNum, cvr.batchId, cvr.recordId, "") {
        if (cvr.imprintedId != imprintedId())
            println("Garfield ${cvr.imprintedId} != ${imprintedId()}")
    }

    override fun imprintedId(): String {
        val plusValue = 10_000 + 2*record+1
        return "$batch+$plusValue"
    }
    override fun location() = location
    override fun matched() = matched
    override fun setMatched(match: Boolean) { matched = match }
    override fun tab() = 1

    override fun compareTo(other: ManifestEntry): Int {
        return imprintedId().compareTo(other.imprintedId())
    }
}

class GarfieldManifest(manifestSource: String): CountyManifest(manifestSource), Iterable<ManifestEntry> {

    override fun makeEntry(tab: Int, batch: String, record: Int, location: String) : ManifestEntry {
        return GarfieldEntry(tab, batch, record, location)
    }

    override fun makeEntry(cvrrow: CvrRow) : ManifestEntry {
        return GarfieldEntry(cvrrow)
    }

}


data class ManifestId(val tab: Int, val batch: String, val record: Int, val location: String): ManifestEntry {
    private var matched = false // did we find a match yet?
    private val id = "$tab-$batch-$record"
    private val sorter: String

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
    override fun matched() = matched
    override fun setMatched(match: Boolean) { matched = match }
    override fun tab() = tab

    override fun compareTo(other: ManifestEntry): Int {
        return sorter.compareTo((other as ManifestId).sorter)
    }
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

    open fun makeEntry(tab: Int, batch: String, record: Int, location: String) : ManifestEntry {
        return ManifestId(tab, batch, record, location)
    }

    open fun makeEntry(cvrrow: CvrRow) : ManifestEntry {
        return ManifestId(cvrrow)
    }

    override fun iterator(): Iterator<ManifestEntry> = MEiterator(manifestBatches.iterator())

    inner class MEiterator(val batchIterator: Iterator<ManifestBatch>) : Iterator<ManifestEntry> {
        var batch: ManifestBatch? = null
        var recordNo = 1

        init {
            if (batchIterator.hasNext()) batch = batchIterator.next()
        }

        override fun next(): ManifestEntry {
            return makeEntry(batch!!.tabulatorNum, batch!!.batchId, recordNo++, batch!!.location)
        }

        override fun hasNext(): Boolean {
            if (recordNo <= batch!!.nballotCards) return true
            if (batchIterator.hasNext()) {
                batch = batchIterator.next()
                recordNo = 1
                return true
            }
            batch = null
            return false
        }
    }

    fun manifestCounts(corlaCvrs: CorlaCvrsIF, report: MutableList<String>? = null, showUnmatched:Boolean = false): ManifestCounts {
        val cvrs = corlaCvrs.cvrs()

        val manifestIdMap = mutableMapOf<String, ManifestEntry>()
        val meiter = MEiterator(manifestBatches.iterator())
        while (meiter.hasNext()) {
            val me2 = meiter.next()
            manifestIdMap[me2.imprintedId()] = me2
        }

        var countMiss = 0 // count of Cvrs not in the manifest
        var countDup = 0  // count of duplicate ids in the Cvrs
        val missedIds = mutableListOf<ManifestEntry>()
        cvrs.forEach { cvrrow ->
            val manifestMatch = manifestIdMap[cvrrow.imprintedId]
            if (manifestMatch != null) {
                if (manifestMatch.matched()) countDup++
                manifestMatch.setMatched(true)
            } else {
                countMiss++
                missedIds.add(makeEntry(cvrrow))
            }
        }

        // check redacted rows are in manifest (and missing)
        var countUnknownRedaction = 0
        var countRedactionDup = 0
        corlaCvrs.redactedCvrs().forEach { cvrrow ->
            val manifestMatch = manifestIdMap[cvrrow.imprintedId]
            if (manifestMatch != null) {
                if (manifestMatch.matched()) countRedactionDup++
            } else {
                countUnknownRedaction++
                throw RuntimeException("redaction ${cvrrow.imprintedId} not in manifest")
            }
        }


        var unmatched = 0
        val redactedIDs = mutableListOf<ManifestEntry>()
        manifestIdMap.values.forEach { mid ->
            if (!mid.matched()) {
                redactedIDs.add(mid)
                unmatched++
            }
        }

        if (report != null) {
            var count = 1
            var currentTab =  0
            missedIds.sorted().forEach { mid ->
                if (mid.tab() != currentTab) {
                    report.add("")
                    count = 1
                }
                currentTab = mid.tab()
                report.add("$count  didnt find cvr imprintedId '${mid.imprintedId()}' in manifest")
                count++
            }

            if (showUnmatched) {
                report.add("")
                var count = 1
                manifestIdMap.values.forEach { mid ->
                    if (!mid.matched()) {
                        report.add("$count  mvr '${mid.imprintedId()}' has no match in the CVRs")
                        count++
                    }
                }
            }

            report.add("")
            report.add("cvrs not found in manifest= $countMiss")
            report.add("cvrs found in Manifest=${manifestIdMap.size - countMiss}")
            report.add("redactedCvrs not found in manifest= $countUnknownRedaction")

            val countUnmatched = manifestIdMap.values.count { !it.matched() }
            report.add("countMiss=$countMiss countUnmatched=$countUnmatched countDup=${countDup + countRedactionDup}")
            report.add("-------------------------------------------------------------------------------")
        }
        val countCvrsInManifest = totalCards - unmatched
        return ManifestCounts(unmatched, countCvrsInManifest, manifestIdMap, redactedIDs)
    }
}

data class ManifestCounts(
    val unmatched: Int,                       // count of Manifest entries not in the Cvrs; presumed to be == redacted CVRs
    val countCvrsInManifest: Int,             // count of Cvrs that are in the Manifest
    val match: Map<String, ManifestEntry>,    // imprintedId -> ManifestEntry
    val redactedIds: List<ManifestEntry>      // didnt match a cvr, assume to be in the redactions
)