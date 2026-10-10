package org.cryptobiotic.rlauxe.corlaInput

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.CardStyle
import org.cryptobiotic.rlauxe.auditcenter.CountyTabAllContests
import org.cryptobiotic.rlauxe.corlaCounty.CountyCvrManifest
import org.cryptobiotic.rlauxe.corlacvr.CorlaCvrsIF
import org.cryptobiotic.rlauxe.corlacvr.CvrRow
import org.cryptobiotic.rlauxe.corlacvr.Garfield2020RawCvrs
import org.cryptobiotic.rlauxe.corlacvr.Redaction
import org.cryptobiotic.rlauxe.corlacvr.RedactionBoulder
import org.cryptobiotic.rlauxe.corlacvr.RedactionStrategy
import org.cryptobiotic.rlauxe.corlacvr.readCorlaCvrs
import org.cryptobiotic.rlauxe.util.AuditableCardBuilder
import org.cryptobiotic.rlauxe.util.ContestTabulation

// CorlaCountyInput has cvrs, presumably redacted, and a CountyCvrManifest.
// it knows the county's total number of ballots
// should it be in corlaCounty ??
interface CorlaCountyInput {
    val electionName: String
    val countyName: String
    val manifestSource: String
    val cvrsSource: String

    // TODO dependence on the year
    fun readCorlaCvrs(): CorlaCvrsIF {
        return if (countyName == "Garfield") Garfield2020RawCvrs(cvrsSource)
            else if (countyName == "Boulder") readCorlaCvrs(cvrsSource, redaction = RedactionBoulder())
            else if (countyName == "La Plata") readCorlaCvrs(cvrsSource, redaction = Redaction(RedactionStrategy(true)))
            else if (countyName == "Morgan") readCorlaCvrs(cvrsSource, redaction = Redaction(RedactionStrategy(true)))
            else readCorlaCvrs(cvrsSource, redaction = Redaction())
    }

    fun hasABgroups() = false

    fun readCountyManifest(): CountyCvrManifest {
        return CountyCvrManifest(manifestSource)
    }

    fun countyPopulation(): Int
}

// convert from cvr names/ids to canonical
interface CorlaCountyConverterIF {
    fun convertToCard(dcvr: CvrRow, visit: ((AuditableCardBuilder) -> Unit)? = null): AuditableCard
    fun cardStyles(): Map<Set<Int>, CardStyle> // contest ids -> CardStyle

    // return canonical contest id -> ContestTabulation with canonical candidate ids
    // fun convertToContestTabulation(rgroup: RedactedGroup): Map<Int, ContestTabulation> // not used
    fun convertToContestTabulation(countyTab: CountyTabAllContests): Map<Int, ContestTabulation>
}
