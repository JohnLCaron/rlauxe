package org.cryptobiotic.rlauxe.corlaInput

import org.cryptobiotic.rlauxe.auditcenter.ManifestBatch
import org.cryptobiotic.rlauxe.auditcenter.readCountyManifestCsv
import org.cryptobiotic.rlauxe.cvr.CorlaCvrsIF
import org.cryptobiotic.rlauxe.cvr.Garfield20Cvrs
import org.cryptobiotic.rlauxe.cvr.Redaction
import org.cryptobiotic.rlauxe.cvr.RedactionBoulder
import org.cryptobiotic.rlauxe.cvr.readCorlaCvrs
import kotlin.io.path.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.text.replace

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

    fun readCountyManifest(): CountyManifest {
        return CountyManifest(manifestSource)
    }

    fun countyPopulation(): Int
}

class CorlaCounty2020Input(override val countyName: String): CorlaCountyInput {
    val countyNameZ = countyName.replace(" ", "")
    override val electionName = "${countyName}2020"
    // TODO Gunnison has Manifest-Gunnison.csv; but Gunnison is an excluded county
    override val manifestSource = "$manifestDir/manifest-${countyNameZ}.csv"
    override val cvrsSource: String = countyCvrs[countyName]!!

    override fun readCountyManifest(): CountyManifest {
        if (countyName == "Garfield") {
            return GarfieldManifest(manifestSource)
        }

        var result = super.readCountyManifest()
        if (countyName == "Douglas") {
            // Douglas,1,Gen-2026,30,295 must be Douglas,1,GEN-2026,30,295
            // Douglas,1,GEn-2045,100,299
            result = result.uppercase()
        }
        return result
    }

    override fun countyPopulation() = stateInput.strataPopulation()[countyName]!!

    companion object {
        val manifestDir = "$auditcenter/2020/general/round_1"
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
