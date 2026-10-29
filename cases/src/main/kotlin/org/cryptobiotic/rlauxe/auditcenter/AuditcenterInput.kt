package org.cryptobiotic.rlauxe.auditcenter

import org.cryptobiotic.rlauxe.util.UploadStatus
import kotlin.io.path.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries

enum class status {FAILED, HASH_MISMATCH, HASH_VERIFIED, IMPORTED}
enum class type {CVRs, manifest}

class AuditcenterInput(
    val topdir: String,
    val manifestdir: String,
    val manifestFileTemplate: String,
    val uploadStatusFile: String,
    val batchCountComparison: String?,
) {
    val uploadStatus: List<UploadStatus>

    init {
        uploadStatus = UploadStatus.readFromFile("$topdir/$uploadStatusFile")
    }

    fun checkStatus(show: Boolean = false): Pair<Set<String>, Set<String>> {
        val countStatus = mutableMapOf<String, Int>()
        val countType = mutableMapOf<String, Int>()
        val countyCvrs = mutableMapOf<String, UploadStatus>()
        val countyManifest = mutableMapOf<String, UploadStatus>()
        uploadStatus.filter{ listOf("HASH_VERIFIED", "IMPORTED").contains(it.status) }.forEach {
            if (it.type == "manifest") countyManifest[it.name] = it // last one wins
            if (it.type == "CVRs") countyCvrs[it.name] = it
            countStatus[it.status] = countStatus.getOrDefault(it.status, 0) + 1
            countType[it.type] = countType.getOrDefault(it.type, 0) + 1
        }
        if (show) {
            println("\nCounty Manifests (${countyManifest.size})")
            countyManifest.toSortedMap().forEach { println("   ${it}") }
            println("\nCounty Cvrs (${countyCvrs.size})")
            countyCvrs.toSortedMap().forEach { println("   ${it}") }
        }
        return Pair(countyManifest.keys.toSet(), countyCvrs.keys.toSet(), )
    }

    fun checkManifests(): Set<String> {
        val counties = mutableSetOf<String>()
        val path = Path("$topdir/$manifestdir")
        if (path.isDirectory()) {
            path.listDirectoryEntries().sorted().forEach { entry ->
                val filename = entry.fileName.toString()
                if (filename.endsWith(".csv")) {
                    val county = testFilename(filename.take(filename.length - 4), manifestFileTemplate)
                    if (county != null) {
                        // println("$county manifest at $filename")
                        counties.add(county)
                    }
                }
            }
        }
        return counties
    }
}

fun testFilename(filename: String, template: String): String? {
    // "namezBallotManifest.csv"
    val remaining = template.replace("namez", "")
    if (filename.contains(remaining)) {
        val county = filename.replace(remaining, "")
        return if (county.first().isUpperCase()) county else null
    }
    return null
}
