package org.cryptobiotic.rlauxe.corlaInput

import org.cryptobiotic.rlauxe.corlaCounty.CountyManifestEntries
import org.cryptobiotic.rlauxe.corlacvr.CorlaCountyCvrsIF
import org.cryptobiotic.rlauxe.corlacvr.Garfield2020CountyCvrs
import org.cryptobiotic.rlauxe.corlacvr.Redaction
import org.cryptobiotic.rlauxe.corlacvr.RedactionBoulder
import org.cryptobiotic.rlauxe.corlacvr.RedactionStrategy
import org.cryptobiotic.rlauxe.corlacvr.readCountyCvrs

// CountyInput has cvrs, presumably redacted, and a CountyManifest.
// it knows the county's total number of ballots
interface CountyInput: ColoradoInput {
    val electionName: String
    val countyName: String
    val manifestSource: String
    val cvrsSource: String
    val year: Int // four digits: eg 1992, 2026

    // TODO cache this I think ?? Or does it need to stream ??
    fun readCorlaCvrs(): CorlaCountyCvrsIF {
        when {
            (year == 2020) -> when {
                (countyName == "Garfield") -> Garfield2020CountyCvrs(cvrsSource)
                (countyName == "Boulder") -> readCountyCvrs(cvrsSource, redaction = RedactionBoulder())
                (countyName == "La Plata") -> readCountyCvrs(cvrsSource, redaction = Redaction(RedactionStrategy(true)))
                (countyName == "Morgan") -> readCountyCvrs(cvrsSource, redaction = Redaction(RedactionStrategy(true)))
                else -> readCountyCvrs(cvrsSource, redaction = Redaction())
            }
        }
        return readCountyCvrs(cvrsSource, redaction = Redaction())
    }

    // not needed I think
    fun hasABgroups() = false

    fun readCountyManifest(): CountyManifestEntries {
        return CountyManifestEntries(manifestSource)
    }

    fun countyPopulation(): Int
    fun contestNameCleanup(name: String) = name
    fun candidateNameCleanup(name: String) = name

    override fun name() = electionName
    override fun ncontests() = -1  // unknown at the moment
    override fun nuncontested() = -1 // unknown at the moment
}
