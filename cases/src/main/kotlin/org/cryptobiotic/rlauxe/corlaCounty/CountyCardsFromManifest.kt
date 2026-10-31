package org.cryptobiotic.rlauxe.corlaCounty

import org.cryptobiotic.rlauxe.corlaInput.CountyInput
import org.cryptobiotic.rlauxe.corlacvr.CardStyleId
import org.cryptobiotic.rlauxe.corlacvr.CorlaCountyCvrsIF
import org.cryptobiotic.rlauxe.corlacvr.CvrCardStyle
import org.cryptobiotic.rlauxe.corlacvr.CvrRow
import org.cryptobiotic.rlauxe.corlacvr.RedactedGroup
import org.cryptobiotic.rlauxe.corlacvr.cleanCsvString
import org.cryptobiotic.rlauxe.corlacvr.sumContestTabulationsFromCandVotes
import org.cryptobiotic.rlauxe.corlacvr.tabulateCvrRows
import org.cryptobiotic.rlauxe.util.ContestTabulation

// Match manifest and Cvrs without reference to the state files
class CountyCardsFromManifest(val countyInput: CountyInput) {
    val show = false
    val county = countyInput.countyName
    val corlaCvrs: CorlaCountyCvrsIF = countyInput.readCorlaCvrs() // this populates BallotStyles
    val allBallotStyles = corlaCvrs.ballotStyles()

    val infos: Map<Int, CorlaContestInfo>
    val cvrTabs : Map<Int, ContestTabulation>
    val groupTabs : Map<Int, ContestTabulation> // sum of aggGroup if exists, else sum of all groups
    val aggGroup: RedactedGroup?

    // imprintedId -> CvrRow
    val cvrMap: Map<String, CvrRow>
    val redactedWithStyle = mutableMapOf<String, CvrRow>()
    val redactedNoStyle = mutableMapOf<String, CvrRow>()

    // assumes manifest iterator doesnt terminate early
    val manifestEntries = countyInput.readCountyManifest()
    var aggCount = 0
    var missingCount = 0
    var manifestCount = 0

    init {
        // infos local to the county (not converted to canonical)
        infos = corlaCvrs.makeContestInfo()

        aggGroup = corlaCvrs.redaction().groups().find { it.groupName.lowercase() == "aggregated"}
        groupTabs = if (aggGroup != null) { // if agg grup exists, ignore other groups. may have to change this if someone else is adding groups
            // group subtotals and rows with ids.
            val tabs = mutableMapOf<Int, ContestTabulation>()
            aggGroup.candVotes.forEach { (scontestId, candVotes) ->
                tabs.sumContestTabulationsFromCandVotes(infos[scontestId]!!, candVotes) // problem is you dont know ncards until you read through the rows
            }
            tabs
        } else {
            // TODO boulder special case
            val sumAccum = mutableMapOf<Int, ContestTabulation>()
            corlaCvrs.redaction().groups().forEach { group ->
                group.candVotes.forEach { (scontestId, candVotes) ->
                    sumAccum.sumContestTabulationsFromCandVotes(infos[scontestId]!!, candVotes)
                }
            }
            sumAccum
        }

        // filter out non included in manifest. not including redacted
        val cvrsInManifest = corlaCvrs.cvrs().filter{ manifestEntries.contains(it.tabulatorNum, it.batchId, it.recordId) }
        cvrMap = cvrsInManifest.associateBy { it.imprintedId }
        cvrTabs = tabulateCvrRows(cvrsInManifest.iterator(), infos)

        // allBallotStyles are the union of unredacted and redacted cvrs; dont change the original!
        // actually I think we could add redacted ballots to BallotStyles in Redaction(), but still keep
        // redacted ballots separate
        //allBallotStyles.cardStyleMap.putAll(unredactedBallotStyles.cardStyleMap)
        //allBallotStyles.ballotTypes.putAll(unredactedBallotStyles.ballotTypes)

        // do rows have style information: yes if contestVotes != emptyList
        // but this isnt looking at valid ballotType styles...
        corlaCvrs.redaction().redactedRows().forEach { row ->
            if (row.contestVotes.isNotEmpty()) {
                // allBallotStyles.add(row)
                redactedWithStyle[row.imprintedId] = row
            } else {
                redactedNoStyle[row.imprintedId] = row
            }
        }


    }

    // local contest Ids
    fun countyStyles(): List<CvrCardStyle> {
        return allBallotStyles.cardStyles()
    }

// 1. one aggregated group and redacted rows with ids and styles (our redactor)
// 1.1 older version: rows have unique ballotType but missing unredacted example, no style (LaPlata 2026p)
// 1.2 older version: rows have unique ballotType but missing unredacted example, no style, no precincts (Morgan 2026p)
// -> if we have one group and rows with styles, the group is the agg pool; the rows use their own style
// -> if we also have rows without styles, put them in the missing pool with subtotal missing votes - agg pool
// we have no examples (yet) of previous redaction having row styles, and also needed further redaction

// multiple redaction groups
// 2.1 aggregated groups by ballotType (derive style), no rows (Boulder 2020)
//     ncards in group is unknown.
// 2.2 aggregated groups by ballotType (has style), no rows (Boulder 2023)
//     ncards in group is unknown.
// 2.3 aggregated groups by ballotType A/B (has style), no rows (Boulder 2024) (TODO use BallotStyles)
//     ncards in group is unknown.
// 2.4 aggregated groups by unique ballotType, rows with no ids with BallotStyle (Boulder 2025/6)
//     the rows are aggregated by ballotType into groups, not kept as rows. ncards in group = nlines
// -> if we have one or more groups and no rows, get the rows from the manifest missing cvrs
// since we dont know which rows belong to which group, we combine the groups into the missing pool.
// the group sum should equals the missing votes

// 3. redacted rows with ids with ballotType
// 3.1 no style (2020: Adams, Arapahoe, Eagle, El Paso, Larimer, Logan, Morgan, Pueblo, Rio Blanco, Summit, Weld, )
// 3.1.1 Broomfield 2025 redacted all of type 1, no unredacted rows with that type
// 3.1.2 Larimer 2025 type 20 split A/B but redacted row only shows type 20
// 3.1.3 Weld 2026p redacted all of several types, no style
// 3.2 has style (2020: Douglas, Jefferson, Pitkin)
// -> if we have rows but no groups, all rows go into the missing pool using missing votes
// use the style for the individual rows, else use the missing pool style

// -> use row style if exists, then try ballotStyle
// -> if ballotTypes are unique, then we know the row style if it references one or hasStyle, otherwise not
// -> aggregated group is not exact unless it contains a single style

// Questions:
//   do rows have style information: yes if votes != emptyMap
//   do ballot types have unique styles: check BallotStyles.ballotTypes list.size == 1

// cards in the agg group go into the agg pool; they always have styles for version 0.3
// I think we can let "all" be the style for version 0.2
// all other redacted cards go into the missing pool
// cards missing from the manifest go into the missing pool

    data class RowForManifest(val row: CvrRow, val style: CvrCardStyle, val poolName: String?)

    fun getRowAndStyle(manifestEntry: ManifestEntry): RowForManifest? {
        val row = cvrMap[manifestEntry.imprintedId()]
        if (row != null) {
            val cleanBallotType = cleanCsvString(row.ballotType) // no commas
            val cvrStyleId = CardStyleId(cleanBallotType, row.contests())
            val cardStyle = allBallotStyles.cardStyleMap[cvrStyleId]
            return RowForManifest(row, cardStyle!!, null)
        }

        val redacted = redactedWithStyle[manifestEntry.imprintedId()]
        if (redacted != null) {
            val cleanBallotType = cleanCsvString(redacted.ballotType) // no commas
            val cvrStyleId = CardStyleId(cleanBallotType, redacted.contests())
            val cardStyle = allBallotStyles.cardStyleMap[cvrStyleId]
            // if we have one group and rows with styles, the group is the agg pool; the rows use their own style
            // -> if we have rows but no groups, all rows go into the missing pool using missing votes
            // use the style for the individual rows, else use the missing pool style
            if (aggGroup == null) missingCount++ else aggCount++
            return RowForManifest(redacted, cardStyle!!, if (aggGroup == null) "missing" else "aggregated")
        }

        val redactedNo = redactedNoStyle[manifestEntry.imprintedId()]
        if (redactedNo != null) {
            // use the ballotType
            val cardStyleList = allBallotStyles.ballotTypes[redactedNo.ballotType]
            if (cardStyleList != null) {
                // this is the union in case there is more than one
                val useStyle = cardStyleList.find { it.cardStyle == redactedNo.ballotType }
                if (useStyle != null ) {
                    missingCount++
                    return RowForManifest(redactedNo, useStyle, "missing")
                }
                throw Exception("expected union already exists")
            }
            // it has an unknown ballotType. call it missing.
            return null // dunno
        }
        // if theres no match for a manifest entry, its "missing"
        return null
    }

    fun iterator() : Iterator<CountyCard> = ManifestCardIterator()

    inner class ManifestCardIterator: Iterator<CountyCard> {
        val iter = countyInput.readCountyManifest().iterator()
        init {
            missingCount = 0
            manifestCount = 0
            aggCount = 0
        }

        override fun next(): CountyCard {
            val manifestEntry: ManifestEntry = iter.next()
            manifestCount++

            val rowForManifest = getRowAndStyle(manifestEntry)
            val result = if (rowForManifest == null) { // then its missing
                missingCount++
                CountyCard(
                    imprintedId = manifestEntry.imprintedId(),
                    location = manifestEntry.location(),
                    style = "missing",
                    votes = emptyList(), // TODO must simulate for mvrs
                    poolName = "missing",
                )

            } else {

                // data class CountyCard (
                //    val imprintedId: String,
                //    val location: String,
                //    val styleName: String, // must have a style or missing
                //    val votes: Map<Int, IntArray>, contest -> votedFor
                //    val poolName: String?, // must be set if its in a OneAudit CardPool
                //)

                CountyCard(
                    imprintedId = manifestEntry.imprintedId(),
                    location = manifestEntry.location(),
                    style = rowForManifest.style.cardStyle,
                    votes = rowForManifest.row.contestVotes, // must simulate redactions for cvrs; so these arent the mvrs
                    poolName = rowForManifest.poolName,
                )
            }
            return result
        }

        override fun hasNext() = iter.hasNext()
    }
}