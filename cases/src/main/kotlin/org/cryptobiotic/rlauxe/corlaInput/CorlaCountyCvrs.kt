package org.cryptobiotic.rlauxe.corlaInput

import org.cryptobiotic.rlauxe.cvr.CorlaCvrsIF
import org.cryptobiotic.rlauxe.cvr.Garfield20Cvrs
import org.cryptobiotic.rlauxe.cvr.Redaction
import org.cryptobiotic.rlauxe.cvr.RedactionBoulder
import org.cryptobiotic.rlauxe.cvr.RedactionStrategy
import org.cryptobiotic.rlauxe.cvr.readCorlaCvrs

interface CorlaCountyCvrs {
    val electionName: String
    val countyName: String
    val manifestSource: String
    val cvrsSource: String

    // TODO dependence on the year
    fun readCorlaCvrs(): CorlaCvrsIF {
        return if (countyName == "Garfield") Garfield20Cvrs(cvrsSource)
            else if (countyName == "Boulder") readCorlaCvrs(cvrsSource, redaction = RedactionBoulder())
            else if (countyName == "La Plata") readCorlaCvrs(cvrsSource, redaction = Redaction(RedactionStrategy(true)))
            else if (countyName == "Morgan") readCorlaCvrs(cvrsSource, redaction = Redaction(RedactionStrategy(true)))
            else readCorlaCvrs(cvrsSource, redaction = Redaction())
    }

    fun hasABgroups() = false

    fun readCountyManifest(): CountyManifest {
        return CountyManifest(manifestSource)
    }

    fun countyPopulation(): Int
}
