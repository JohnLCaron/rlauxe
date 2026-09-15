package org.cryptobiotic.rlauxe.corlaInput

import org.cryptobiotic.rlauxe.corlacvr.CorlaRawCvrsIF
import org.cryptobiotic.rlauxe.corlacvr.Garfield2020RawCvrs
import org.cryptobiotic.rlauxe.corlacvr.Redaction
import org.cryptobiotic.rlauxe.corlacvr.RedactionBoulder
import org.cryptobiotic.rlauxe.corlacvr.RedactionStrategy
import org.cryptobiotic.rlauxe.corlacvr.readCorlaCvrs

interface CorlaCountyInput {
    val electionName: String
    val countyName: String
    val manifestSource: String
    val cvrsSource: String

    // TODO dependence on the year
    fun readCorlaCvrs(): CorlaRawCvrsIF {
        return if (countyName == "Garfield") Garfield2020RawCvrs(cvrsSource)
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
