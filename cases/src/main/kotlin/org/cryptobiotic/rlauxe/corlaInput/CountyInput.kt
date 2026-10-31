package org.cryptobiotic.rlauxe.corlaInput

import org.cryptobiotic.rlauxe.corla.GarfieldManifestEntries
import org.cryptobiotic.rlauxe.corla.ManifestEntries
import org.cryptobiotic.rlauxe.corlacvr.CorlaCountyCvrsIF
import org.cryptobiotic.rlauxe.corlacvr.Garfield2020CountyCvrs
import org.cryptobiotic.rlauxe.corlacvr.Redaction
import org.cryptobiotic.rlauxe.corlacvr.RedactionBoulder
import org.cryptobiotic.rlauxe.corlacvr.RedactionStrategy
import org.cryptobiotic.rlauxe.corlacvr.readCountyCvrs

interface CountyInput: ColoradoInput {
    val electionName: String
    val countyName: String
    val manifestSource: String
    val cvrsSource: String

    // TODO cache this I think ?? Or does it need to stream ??
    fun readCorlaCvrs(): CorlaCountyCvrsIF {
        return readCorlaCvrs(year, countyName, cvrsSource)
    }

    // TODO useful?
    fun hasABgroups() = false

    fun readManifestEntries(): ManifestEntries {
        return if (countyName == "Garfield") GarfieldManifestEntries(manifestSource)
        else ManifestEntries(manifestSource)
    }

    fun countyPopulation(): Int
    fun contestNameCleanup(name: String) = name
    fun candidateNameCleanup(name: String) = name
    fun hasCvrs() = true

    override fun name() = electionName
    override fun ncontests() = -1  // unknown at the moment
    override fun nuncontested() = -1 // unknown at the moment
}

fun readCorlaCvrs(year: String, countyName: String, cvrsSource: String): CorlaCountyCvrsIF {
    if (year == "2020") {
        if (countyName == "Larimer") return readCountyCvrs(cvrsSource, redaction = Redaction(RedactionStrategy(hasStyle = false)))
    }

    if (year.startsWith("2020")) {
        if (countyName == "Garfield") return Garfield2020CountyCvrs(cvrsSource)
    }

    return when {
        (countyName == "Boulder") -> readCountyCvrs(cvrsSource, redaction = RedactionBoulder())
        else -> readCountyCvrs(cvrsSource)
    }
}
