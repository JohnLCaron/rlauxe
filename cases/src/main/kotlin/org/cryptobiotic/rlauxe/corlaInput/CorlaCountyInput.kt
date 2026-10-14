package org.cryptobiotic.rlauxe.corlaInput

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.CardStyle
import org.cryptobiotic.rlauxe.auditcenter.CountyTabAllContests
import org.cryptobiotic.rlauxe.corlaCounty.CountyManifestEntries
import org.cryptobiotic.rlauxe.corlacvr.CorlaCountyCvrsIF
import org.cryptobiotic.rlauxe.corlacvr.CvrRow
import org.cryptobiotic.rlauxe.corlacvr.Garfield2020CountyCvrs
import org.cryptobiotic.rlauxe.corlacvr.Redaction
import org.cryptobiotic.rlauxe.corlacvr.RedactionBoulder
import org.cryptobiotic.rlauxe.corlacvr.RedactionStrategy
import org.cryptobiotic.rlauxe.corlacvr.readCountyCvrs
import org.cryptobiotic.rlauxe.util.AuditableCardBuilder
import org.cryptobiotic.rlauxe.util.ContestTabulation

// CorlaCountyInput has cvrs, presumably redacted, and a CountyManifestEntries.
// it knows the county's total number of ballots
// should it be in corlaCounty ??
interface CorlaCountyInput {
    val electionName: String
    val countyName: String
    val manifestSource: String
    val cvrsSource: String
    val year: Int // four digits: eg 1992, 2026

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

    fun hasABgroups() = false

    fun readCountyManifest(): CountyManifestEntries {
        return CountyManifestEntries(manifestSource)
    }

    fun countyPopulation(): Int
}

// NOT USED
// convert from cvr names/ids to canonical
interface CorlaCountyConverterIF {
    fun convertToCard(dcvr: CvrRow, visit: ((AuditableCardBuilder) -> Unit)? = null): AuditableCard
    fun cardStyles(): Map<Set<Int>, CardStyle> // contest ids -> CardStyle

    // return canonical contest id -> ContestTabulation with canonical candidate ids
    // fun convertToContestTabulation(rgroup: RedactedGroup): Map<Int, ContestTabulation> // not used
    fun convertToContestTabulation(countyTab: CountyTabAllContests): Map<Int, ContestTabulation>
}
