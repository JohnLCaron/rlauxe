package org.cryptobiotic.rlauxe.corlaCounty

import org.cryptobiotic.rlauxe.auditcenter.ManifestBatch

// Find Manifest entries (imprinted_ids) from a PRN, using modulo ncvrs, to match ColoradoRLA
data class CountyManifest(val county: String, val batches: List<ManifestBatch>) {
    val batchBound: List<Int>
    val nbatches = batches.size
    val nballotCards: Int

    init {
        var batchNumeric = true
        var alphaOrder = true
        var numOrder = true
        var prevAlpha = ""
        var prevNum = 0
        batches.forEach {
            val aid = "${it.tabulatorNum}-${it.batchId}"
            if (aid < prevAlpha)
                alphaOrder = false
            prevAlpha = aid

            if (batchNumeric) {
                try {
                    val nid = it.tabulatorNum * 1000 + it.batchId.toInt()
                    if (nid < prevNum)
                        numOrder = false
                    prevNum = nid
                } catch (e: NumberFormatException) {
                    batchNumeric = false
                }
            }
        }

        var cumul = 0
        batchBound = batches.map {
            cumul += it.nballotCards
            cumul
        }
        nballotCards = cumul

        // val ok = if (alphaOrder || (batchNumeric && numOrder)) "" else "***"
        // println("${trunc(county, 15)} alphaOrder = $alphaOrder batchNumeric=$batchNumeric numOrder = $numOrder $ok")
    }

    fun idFromIndex(index: Int, manifestStartIdx: Int = 0): String {
        var batchIdx = 0
        while (batchIdx < nbatches && index >= batchBound[batchIdx] ) {
            batchIdx++
        }
        if (batchIdx >= nbatches) {
            throw RuntimeException("county $county index=$index out of bounds")
        }
        val batch = batches[batchIdx]
        val indexInBatch = if (batchIdx == 0) index else index - batchBound[batchIdx-1]

        val batchStartIdx = if (batchIdx == 0) manifestStartIdx else batchBound[batchIdx-1] + manifestStartIdx
        val batchEndIdx = batchBound[batchIdx] + manifestStartIdx

        return "$county: ${batch.tabulatorNum}-${batch.batchId}-${indexInBatch}"
    }
}

data class StateManifest(val manifests: List<CountyManifest>) {
    val manifestBound: List<Int>
    val nmanifests = manifests.size
    val totalCards: Int

    init {
        var cumul = 0
        manifestBound = manifests.map {
            // println("${it.county} = (${cumul}, ${cumul+ it.nballotCards})")
            cumul += it.nballotCards
            cumul
        }
        totalCards = cumul
    }

    fun idFromIndex(index: Int): String {
        var manifestIdx = 0
        while (manifestIdx < nmanifests && index >= manifestBound[manifestIdx] ) {
            manifestIdx++
        }
        if (manifestIdx >= nmanifests) {
            throw RuntimeException("state index=$index out of bounds")
        }
        val manifest = manifests[manifestIdx]
        val indexInManifest = if (manifestIdx == 0) index else index - manifestBound[manifestIdx-1]
        val manifestStartIdx = if (manifestIdx == 0) 0 else manifestBound[manifestIdx-1]
        val manifestEndIdx = manifestBound[manifestIdx]
        return manifest.idFromIndex(indexInManifest, manifestStartIdx)
    }

    fun batchInterator() = BatchIterator()

    inner class BatchIterator(): Iterator<ManifestBatch> {
        var countyManifests = manifests.iterator()
        var currentBatches: Iterator<ManifestBatch>

        init {
            val currentCounty = countyManifests.next()
            currentBatches = currentCounty.batches.iterator()
        }
        override fun next(): ManifestBatch {
            return currentBatches.next()
        }
        override fun hasNext(): Boolean {
            if (currentBatches.hasNext()) return true
            if (!countyManifests.hasNext()) return false
            val currentCounty = countyManifests.next()
            currentBatches = currentCounty.batches.iterator()
            return true
        }
    }
}