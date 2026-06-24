package org.cryptobiotic.rlauxe.dominion

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.CardPool
import org.cryptobiotic.rlauxe.audit.CardStyle
import org.cryptobiotic.rlauxe.auditcenter.ColoradoInput
import org.cryptobiotic.rlauxe.auditcenter.isWriteIn
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.util.AuditableCardBuilder
import org.cryptobiotic.rlauxe.util.ContestTabulation
import kotlin.collections.set

// convert DominionCvrExportCsv from export ids to canonical ids
// each export is specific to a County.
class DominionConverter(val county: String, export: DominionCvrCsvSummary, val infosByName: Map<String, ContestInfo>, coloradoInput: ColoradoInput) {

    val exportToCanonLookup = mutableMapOf<Int, ExportToCanonLookup>() // export contestId -> ExportToCanonLookup
    val cardStyles: Map<Set<Int>, CardStyle> // canonicalContestIdSet -> cardStyle
    val redactedPools: List<CardPool> // converted to canonical contests and candidates
    val infos = infosByName.mapKeys { it.value.id }

    init {
        // val infosByName: Map<String, ContestIF> = contests.associateBy { it.name }
        val schemaInfos: List<ExportContestInfo> = export.makeContestInfo() // specific to this exported file

        val gotCanon = mutableMapOf<Int, String>()  // canon contest id -> export contest name
        // each contest in the schema must be matched to a ContestIF by name
        var countMissing = 0
        var countMissingCand = 0
        schemaInfos.forEach { schemaInfo ->
            val canonicalContest = coloradoInput.matchCanonicalContest(county, schemaInfo.name) // clean up your act, sheesh
            if (canonicalContest == null) {
                println("  *** missing schema contest: '${schemaInfo.name}' in county $county")
                countMissing++
            } else {
                val info = infosByName[canonicalContest.contestName]!!
                if (gotCanon.contains(info.id))
                    println("  *** ${info.id} has duplicate contest: '${schemaInfo.name}' and '${gotCanon[info.id]}' ")
                gotCanon[info.id] = schemaInfo.name

                val candPairs = mutableListOf<Pair<Int, Int>>()

                schemaInfo.candidateNames.filter { !isWriteIn(it.key) }.forEach { (exportCandidate, schemaCandId) ->
                    // use a lookup instead of a map TODO worth the complexity ??
                    val canonCandidateName = coloradoInput.matchCanonicalCandidate(county, canonicalContest, exportCandidate)
                    // println("$exportCandidate -> $canonCandidateName")
                    if (canonCandidateName == null) {
                        throw Exception("no match on exportCandidateName $exportCandidate")
                    }
                    val canonCandId = info.candidateNames[canonCandidateName] // what if this fails ??
                    if (canonCandId == null) {
                        throw Exception("no match on info.candidateNames: canonCandidateName=$canonCandidateName orgName=$exportCandidate")
                        //println("  ** missing export candidate: '${exportCandidate}' in contest '${info.name}' county $county")
                        //countMissingCand++
                    } else {
                        candPairs.add ( Pair(schemaCandId, canonCandId))
                    }
                }
                val lookupSize = schemaInfo.candidateNames.map { it.value }.max()
                val candLookup = IntArray(lookupSize+1) { -1 } // plus one because its one based
                candPairs.forEach{ (schemaCandId, canonCandId) -> candLookup.set(schemaCandId, canonCandId) }
                val lookup = ExportToCanonLookup(info.id, candLookup)
                exportToCanonLookup[schemaInfo.id] = lookup
            }
            print("")
        }

        // data class ExportCardStyle(val name: String, val contests: Set<Int>, var count: Int = 0)
        // but the ids are export internal contest Ids; we need map export internal contestIds -> rlauxe ContestId
        // which is what we just built !!
        // turn those into CardStyle

        cardStyles = export.exportCardStyles.map { it ->
            val canonicalContestIdSet = convertExportCardStyleToCanonical(it)
            val cleanupName = truncateCommas(it.name)
            val cardStyle = CardStyle("$county-${cleanupName}", cardStyleId++, canonicalContestIdSet.toIntArray(), true)
            cardStyle.ncards = it.countCards
            Pair(canonicalContestIdSet, cardStyle)
        }.toMap()

        // one for each redacted group // TODO could use original style
        redactedPools = export.redactedGroups.map { group ->
            val contestTabs = convertToContestTabulation(group)
            val cleanupName = truncateCommas(group.ballotType)
            CardPool("$county-${cleanupName}.Redacted", cardStyleId++, true, infos, contestTabs, group.minCards())
        }
    }

    // return corresponding contest Ids in canonical
    fun convertExportCardStyleToCanonical(exportCardStyle: ExportCardStyle): Set<Int> {
        val convert = mutableSetOf<Int>()
        exportCardStyle.contests.forEach { exportContestId ->
            val lookup = exportToCanonLookup[exportContestId]
            if (lookup != null) convert.add(lookup.canonContestId)
        }
        // println("$exportCardStyle -> $convert")
        val got = mutableSetOf<Int>()
        convert.forEach {
            got.add(it)
        }
        return got
    }

    fun convertExportContestIdSetToCanonical(exportContestIdSet: Set<Int>): Set<Int> {
        val convert = mutableSetOf<Int>()
        exportContestIdSet.forEach { exportContestId ->
            val lookup = exportToCanonLookup[exportContestId]
            if (lookup != null) convert.add(lookup.canonContestId)
        }
        return convert.toSet()
    }

    fun convertToCard(dcvr: CastVoteRecord): AuditableCard {
        // must convert to canoncal contestIDs to use cardStyles
        val contestSchemaIdSet = dcvr.contestVotes.map { it.contestId }.toSet()
        val canonicalIdSet = convertExportContestIdSetToCanonical(contestSchemaIdSet)
        val cardStyle = cardStyles[canonicalIdSet]
        val useCardStyleId = cardStyle?.id() ?: CardStyle.fromCvrStyle.id
        val cvrb = AuditableCardBuilder(dcvr.imprintedId, null,  0, 0L, false, styleId=useCardStyleId, poolId=null, votesIn=null)
        // have to map both contestId and candVotes
        dcvr.contestVotes.forEach{ contestVote ->
            val lookup = exportToCanonLookup[contestVote.contestId]
            if (lookup != null) {
                val cannonCandidateIds = contestVote.candVotes.map { lookup.candLookup[it] }.filter { it >= 0 }
                cvrb.replaceContestVotes(lookup.canonContestId, cannonCandidateIds.toIntArray() )
            } else {
                throw Exception("cant find contest")
            }
        }
        return cvrb.build()
    }

    fun convertToContestTabulation(rgroup: DominionRedactedGroup): Map<Int, ContestTabulation> {
        // have to map both contestId and candVotes
        // contestVotes = mutableMapOf<Int, MutableMap<Int, Int>>
        val canonVotes = mutableMapOf<Int, ContestTabulation>()
        rgroup.contestVotes.forEach{ (contestId, rcands) ->
            val nz = rcands.values.sum()  // skip contests with no votes
            val lookup = exportToCanonLookup[contestId]
            if (nz > 0 && lookup != null) {
                val cannonCands: Map<Int, Int> = lookup.convertCands(rcands)
                val contestTabulation = ContestTabulation(infos[lookup.canonContestId]!!, cannonCands, rgroup.minCards())
                canonVotes[lookup.canonContestId] = contestTabulation
            }
        }
        return canonVotes
    }

    companion object {
        // all the cardStyles come out of here, so we can track the ids here
        var cardStyleId = 1
    }
}

// for a canonicalContest, lookup export candidate -> canonical candidate
data class ExportToCanonLookup(val canonContestId: Int, val candLookup: IntArray ) {

    // not 1-1 so cant use mapKeys. For example Write-In candidate was removed
    fun <T> convertCands(inp: Map<Int,T>): Map<Int,T> {
        val result = mutableMapOf<Int, T>()
        inp.forEach {
            val newCandId = candLookup[it.key]
            if (newCandId >= 0 ) result[newCandId] = it.value
        }
        return result
    }

    // val cannonCandidateIds = contestVote.candVotes.map { lookup.candLookup[it] }.filter { it >= 0 }
    fun convertCands(contestVotes: ContestVotes): List<Int> {
        val result = mutableListOf<Int>()
        contestVotes.candVotes.forEach {
            val newCandId = candLookup[it]
            if (newCandId >= 0 ) result.add(newCandId)
        }
        return result
    }
}

/////////////////////////////////////////////////////////////////////////
// make schema specific ContestInfo from export.schema.contests; uses local contestId and candidateId
// Use DominionCvrConverter to convert to global ContestInfo

data class ExportContestInfo(
    val name: String,
    val id: Int,
    val candidateNames: Map<String, Int>,
    val isIrv: Boolean,
    val nwinners: Int) {
    val candidateIdToName: Map<Int, String> = candidateNames.entries.associate {(k,v) -> v to k }
}

fun DominionCvrCsvSummary.makeContestInfo(): List<ExportContestInfo> {
    val columns = this.schema.columns

    return this.schema.contests.map { exportContest ->

        val candidateMap = if (!exportContest.isIRV) {
            val candidateMap1 = mutableMapOf<String, Int>()
            var candIdx = 0
            for (col in exportContest.startCol..exportContest.startCol + exportContest.ncols - 1) {
                if (!isWriteIn(columns[col].choice)) { // remove write-ins
                    candidateMap1[columns[col].choice] = candIdx
                }
                candIdx++
            }
            candidateMap1

        } else { // there are ncand x ncand columns, so need something different here
            val candidates = mutableListOf<String>()
            for (col in exportContest.startCol..exportContest.startCol + exportContest.ncols - 1) {
                candidates.add(columns[col].choice)
            }
            val pairs = mutableListOf<Pair<String, Int>>()
            repeat(exportContest.nchoices) { idx ->
                pairs.add(Pair(candidates[idx], idx))
            }
            pairs.toMap()
        }

        val (name, nwinners) = if (exportContest.isIRV) parseIrvContestName(exportContest.contestName) else parseContestNameAndVoteFor(exportContest.contestName)
        ExportContestInfo( name, exportContest.contestIdx, candidateMap, exportContest.isIRV, nwinners)
    }
}

fun parseContestNameAndVoteFor(name: String) : Pair<String, Int> {
    if (name.contains("(Vote For1")) {
        val clean = name.substringBefore("(")
        return Pair(clean.trim(), 1)
    }
    if (!name.contains("(Vote For=")) return Pair(name.trim(), 1)

    val tokens = name.split("(Vote For=")
    require(tokens.size == 2) { "unexpected contest name $name" }
    val namet = tokens[0].trim()
    val ncand = tokens[1].substringBefore(")").toInt()
    return Pair(namet, ncand)
}

// City of Boulder Mayoral Candidates (Number of positions=1, Number of ranks=4)
fun parseIrvContestName(name: String) : Pair<String, Int> {
    if (!name.contains("(Number of positions=")) return Pair(name.trim(), 1)

    val tokens = name.split("(Number of positions=")
    require(tokens.size == 2) { "unexpected contest name $name" }
    val namet = tokens[0].trim()
    val ncand = tokens[1].substringBefore(",").toInt()
    return Pair(namet, ncand)
}