package org.cryptobiotic.rlauxe.corlaCounty

import org.cryptobiotic.rlauxe.auditcenter.ManifestBatch

// Find Manifest entries (imprinted_ids) from a PRN modulo ncvrs
data class CountyManifest(val county: String, val batches: List<ManifestBatch>) {
    val batchBound: List<Int>
    val nbatches = batches.size
    val nballotCards: Int

    init {
        var cumul = 0
        batchBound = batches.map {
            cumul += it.nballotCards
            cumul
        }
        nballotCards = cumul
    }

    fun idFromIndex(index: Int): String {
        var batchIdx = 0
        while (batchIdx < nbatches && index > batchBound[batchIdx] ) {
            batchIdx++
        }
        if (batchIdx >= nbatches) {
            throw RuntimeException("county $county index=$index out of bounds")
        }
        val batch = batches[batchIdx]
        val indexInBatch = if (batchIdx == 0) index else index - batchBound[batchIdx-1]

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
            cumul += it.nballotCards
            cumul
        }
        totalCards = cumul
    }

    fun idFromIndex(index: Int): String {
        var manifestIdx = 0
        while (manifestIdx < nmanifests && index > manifestBound[manifestIdx] ) {
            manifestIdx++
        }
        if (manifestIdx >= nmanifests) {
            throw RuntimeException("state index=$index out of bounds")
        }
        val manifest = manifests[manifestIdx]
        val indexInManifest = if (manifestIdx == 0) index else index - manifestBound[manifestIdx-1]

        return manifest.idFromIndex(indexInManifest)
    }
}