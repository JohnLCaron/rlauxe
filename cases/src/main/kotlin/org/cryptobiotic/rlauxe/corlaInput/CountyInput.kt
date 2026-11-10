package org.cryptobiotic.rlauxe.corlaInput

import org.cryptobiotic.rlauxe.corla.GarfieldManifestEntries
import org.cryptobiotic.rlauxe.corla.ManifestEntries
import org.cryptobiotic.rlauxe.corlacvr.CorlaCountyCvrsIF
import org.cryptobiotic.rlauxe.corlacvr.Garfield2020CountyCvrs
import org.cryptobiotic.rlauxe.corlacvr.Redaction
import org.cryptobiotic.rlauxe.corlacvr.RedactionBoulder
import org.cryptobiotic.rlauxe.corlacvr.RedactionStrategy
import org.cryptobiotic.rlauxe.corlacvr.readCountyCvrs

interface CountyInput {
    val electionName: String
    val countyName: String
    val manifestSource: String
    val cvrsSource: String
    val year: String

    fun readCorlaCvrs(): CorlaCountyCvrsIF {
        return readCorlaCvrs(year, countyName, cvrsSource)
    }

    fun readManifestEntries(): ManifestEntries {
        // Clear Ballot format; may be the same as Dominion now ?
        return if (countyName == "Garfield" && (year.startsWith("2020"))) GarfieldManifestEntries(manifestSource)
        else ManifestEntries(manifestSource)
    }

    fun countyPopulation(): Int
    fun contestNameCleanup(name: String) = name
    fun candidateNameCleanup(name: String) = name
    fun hasCvrs() = true

    fun name() = electionName
    fun ncontests() = -1  // unknown at the moment
    fun nuncontested() = -1 // unknown at the moment
}

class CountyInputNoCvrs(
    override val countyName: String,
    override val manifestSource: String,
    override val year: String,
): CountyInput {
    override fun hasCvrs() = false

    override val electionName = "${countyName}2024"
    override val cvrsSource = "NONE"

    override fun countyPopulation() = countyPopulation
    private val countyPopulation by lazy {
        readManifestEntries().totalCards
    }
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
