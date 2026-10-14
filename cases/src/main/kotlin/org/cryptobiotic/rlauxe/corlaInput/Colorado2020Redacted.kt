package org.cryptobiotic.rlauxe.corlaInput

import org.cryptobiotic.rlauxe.corlaCounty.CountyManifestEntries
import org.cryptobiotic.rlauxe.corlaCounty.GarfieldManifestEntries
import kotlin.io.path.Path
import kotlin.io.path.listDirectoryEntries

class Colorado2020Redacted(ac:String?=auditcenter): Colorado2020General(ac) {
    val redactedCvrsDir = "src/test/data/anon/2020/precinct"
    val countyNames = mutableListOf<String>()

    init {
        val dirPath = Path(redactedCvrsDir)
        dirPath.listDirectoryEntries().sorted().filter { it.fileName.toString().endsWith(".csv") }.forEach { subdir ->
            var countyName = subdir.fileName.toString().split(".")[0]
            countyNames.add(countyName)
            // println(countyName)
        }
    }

    override fun corlaCountyInput(countyName: String, votedatabase: Map<String, String>?): CorlaCountyInput? {
        return if (countyNames.contains(countyName)) County2020Redacted(countyName) else null
    }

    override fun skipCounties(countyName: String) = !countyNames.contains(countyName)
    override fun counties(): List<String> = countyNames

    inner class County2020Redacted(override val countyName: String) : CorlaCountyInput {
        val countyNameZ = countyName.replace(" ", "")

        override val electionName = "${countyName}2020"

        // TODO Gunnison has Manifest-Gunnison.csv; but Gunnison is an excluded county in 2020
        override val manifestSource = "$manifestDir/manifest-${countyNameZ}.csv" // dont have this ....
        override val cvrsSource: String = "$redactedCvrsDir/${countyName}.csv"
        override val year = 2020

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

        override fun countyPopulation() = Colorado2020General().strataPopulation()[countyName]!!
    }
    companion object {
        val manifestDir = "$auditcenter/2020/general/round_1"
    }
}
