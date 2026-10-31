package org.cryptobiotic.rlauxe.corlaInput

import org.cryptobiotic.rlauxe.corlaCounty.CountyManifestEntries
import org.cryptobiotic.rlauxe.corlaCounty.GarfieldManifestEntries
import org.cryptobiotic.rlauxe.util.Locations.auditcenter
import kotlin.io.path.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries

private val votedatabaseDirDefault = "/home/stormy/datadrive/votedatabase"

class County2020InputVotedatabase(
    override val countyName: String,
    val countyPopulation : Int, // TODO change to use manifest so its self contained
    votedatabaseDirIn: String?,
): CountyInput {
    val countyNameZ = countyName.replace(" ", "")
    val votedatabaseDir = votedatabaseDirIn ?: votedatabaseDirDefault
    val votedatabase: Map<String, String> = votedatabase2020Counties("$votedatabaseDir/cvr/Colorado/")

    override val electionName = "${countyName}2020"
    // TODO Gunnison has Manifest-Gunnison.csv; but Gunnison is an excluded county in 2020
    override val manifestSource = if (countyName == "Gunnison") "$manifestDir/Manifest-${countyNameZ}.csv"
                                  else "$manifestDir/manifest-${countyNameZ}.csv"
    override val cvrsSource = votedatabase[countyName]!!
    override val year = "2020"

    override fun readCountyManifest(): CountyManifestEntries {
        if (countyName == "Garfield") {
            return GarfieldManifestEntries(manifestSource)
        }

        var result = super.readCountyManifest()
        if (countyName == "Douglas") {
            // Douglas,1,Gen-2026,30,295 must be Douglas,1,GEN-2026,30,295
            // Douglas,1,GEn-2045,100,299
            result = result.uppercase()
        }
        return result
    }

    override fun countyPopulation() = countyPopulation

    companion object {
        val manifestDir = "$auditcenter/2020/general/round_1"
    }
}

fun votedatabase2020Counties(votedatabase: String): Map<String, String> {
    val path = Path(votedatabase) // or does votedatabase include

    val cvrdata = mutableListOf<Pair<String, String>>()
    path.listDirectoryEntries().sorted().filter { it.isDirectory() && !it.fileName.toString().startsWith("202")}.forEach { subdir ->
        val county = subdir.fileName.toString()
        // Baca duplicates Huerfano
        // Gunnison is missing contest tabulation (FIXED)
        // Las Animas has only 120 of 8000 cvrs (OK)
        // San Juan is excused
        // Monroe, Rooselvelt: no such county in Colorado
        if (county !in listOf("Baca", "San Juan", "Monroe", "Roosevelt")) {
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