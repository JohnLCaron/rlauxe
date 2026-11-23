package org.cryptobiotic.rlauxe.corlaInput

import org.cryptobiotic.rlauxe.corla.ManifestEntries
import org.cryptobiotic.rlauxe.corla.GarfieldManifestEntries
import org.cryptobiotic.rlauxe.util.Locations.auditcenter
import org.cryptobiotic.rlauxe.util.Locations.cases

class Statewide2020Redacted(
        ac:String?=auditcenter,
    ): Statewide2020General() {

    override val year = "2020r"

    override fun corlaCountyInput(countyName: String): CountyInput? {
        if (cvrsMissing.contains(countyName)) return County2020InputNoCvrs(countyName)
        if (countyName == "Gunnison") return County2020Redacted(countyName)
        return if (counties().contains(countyName))
            County2020Redacted(countyName,) else null
    }

}

class County2020Redacted(
    override val countyName: String,
): CountyInput {
    val countyNameZ = countyName.replace(" ", "")
    val cvrdir = "$cases/corla/2020r/cvrs"

    override val electionName = "${countyName}2020"
    override val manifestSource = if (countyName == "Gunnison") "$manifestDir/Manifest-${countyNameZ}.csv"
                                  else "$manifestDir/manifest-${countyNameZ}.csv"

    override val cvrsSource = "$cvrdir/$countyName-Redacted.csv"
    override val year = "2020r"

    override fun readManifestEntries(): ManifestEntries {
        if (countyName == "Garfield") {
            return GarfieldManifestEntries(manifestSource)
        }

        var result = super.readManifestEntries()
        if (countyName == "Douglas") {
            // Douglas,1,Gen-2026,30,295 must be Douglas,1,GEN-2026,30,295
            // Douglas,1,GEn-2045,100,299
            result = result.uppercase()
        }
        return result
    }

    override fun countyPopulation() = countyPopulation
    private val countyPopulation by lazy {
        readManifestEntries().totalCards
    }
    companion object {
        val manifestDir = "$auditcenter/2020/general/round_1"
    }
}
