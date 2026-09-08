package org.cryptobiotic.rlauxe.corlaCounty

import org.cryptobiotic.rlauxe.auditcenter.Colorado2020General
import org.cryptobiotic.rlauxe.auditcenter.ManifestBatch
import org.cryptobiotic.rlauxe.auditcenter.readCountyManifestCsv
import org.cryptobiotic.rlauxe.cvr.CorlaCvrs
import org.cryptobiotic.rlauxe.cvr.CorlaCvrsIF
import org.cryptobiotic.rlauxe.cvr.Garfield20Cvrs
import org.cryptobiotic.rlauxe.cvr.Redaction
import org.cryptobiotic.rlauxe.cvr.RedactionBoulder
import org.cryptobiotic.rlauxe.cvr.readCorlaCvrs
import org.cryptobiotic.rlauxe.cvr.readCorlaCvrsFromFile
import kotlin.io.path.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries

interface CorlaCountyInput {
    val electionName: String
    val countyName: String
    val manifestSource: String
    val cvrsSource: String

    fun readCorlaCvrs(): CorlaCvrsIF {
        return if (countyName == "Garfield") Garfield20Cvrs(cvrsSource)
            else if (countyName == "Boulder") readCorlaCvrs(cvrsSource, redaction = RedactionBoulder())
            else readCorlaCvrs(cvrsSource, redaction = Redaction())
    }

    fun hasABgroups() = false

    fun readCountyManifest(): List<ManifestBatch> {
        return readCountyManifestCsv(manifestSource)
    }
}

class CorlaCounty2020Input(override val countyName: String): CorlaCountyInput {
    val countyPopulation = stateInput.strataPopulation()[countyName]!!

    override val electionName = "${countyName}2020"
    override val cvrsSource: String
    override val manifestSource: String

    init {
        cvrsSource = countyCvrs[countyName]!!
        manifestSource = "fake"
    }

    // we dont have the manifests for 2020, use fake one where we just set the total number of cards for each county.
    override fun readCountyManifest(): List<ManifestBatch> {
        return listOf(ManifestBatch(
            countyName = countyName,
            tabulatorNum = 1,
            batchId = "1",
            nballotCards = countyPopulation,
            location = countyName
        ))
    }

    companion object {
        val countyCvrs: Map<String, String> = votedatabase2020Counties("/home/stormy/datadrive/votedatabase/cvr/Colorado/")
        val stateInput = Colorado2020General()
    }
}

fun votedatabase2020Counties(votedatabase: String): Map<String, String> {
    val path = Path(votedatabase) // or does votedatabase include

    val cvrdata = mutableListOf<Pair<String, String>>()
    path.listDirectoryEntries().sorted().filter { it.isDirectory() && !it.fileName.toString().startsWith("202")}.forEach { subdir ->
        val county = subdir.fileName.toString()
        // Baca duplicates Huerfano
        // Gunnison is missing contest tabulation
        // Las Animas has only 120 of 8000 cvrs
        // San Juan is missing
        // Monroe, Rooselvelt: no such county in Colorado
        if (county !in listOf("Baca", "Gunnison", "Las Animas", "San Juan", "Monroe", "Roosevelt")) {
            try {
                val filename = "${subdir}/cvr.csv" // entry.toString()
                cvrdata.add(Pair(county, filename))
            } catch (e: Exception) {
                println(e.message)
                throw e
            }
        }
    }
    return cvrdata.toMap()
}

// OOOps manifests not available
fun auditcenter2020Manifests(auditcenter: String): Map<String, String> {
    val path = Path(auditcenter) // or does votedatabase include

    val manifests = mutableListOf<Pair<String, String>>()
    path.listDirectoryEntries().sorted().filter { it.isDirectory() && !it.fileName.toString().startsWith("202")}.forEach { subdir ->
        val county = subdir.fileName.toString()
        // Baca duplicates Huerfano
        // Gunnison is missing contest tabulation
        // Las Animas has only 120 of 8000 cvrs
        // San Juan is missing
        // Monroe, Rooselvelt: no such county in Colorado
        if (county !in listOf("Baca", "Gunnison", "Las Animas", "San Juan", "Monroe", "Roosevelt")) {
            try {
                val filename = "${subdir}/cvr.csv" // entry.toString()
                manifests.add(Pair(county, filename))
            } catch (e: Exception) {
                println(e.message)
                throw e
            }
        }
    }
    return manifests.toMap()
}
