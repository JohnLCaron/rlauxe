package org.cryptobiotic.rlauxe.dominion

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.CardStyle
import org.cryptobiotic.rlauxe.core.ContestIF
import org.cryptobiotic.rlauxe.auditcenter.ColoradoInput
import org.cryptobiotic.rlauxe.util.AuditableCardBuilder
import kotlin.collections.set

// convert DominionCvrExportCsv
// each export is specific to a County.
class DominionConverter(val county: String, export: DominionCvrCsvSummary, contests: List<ContestIF>, coloradoInput: ColoradoInput) {
                           //val styleNameMap: Map<Set<Int>, String>) {
    private val exportToCanonLookup = mutableMapOf<Int, ExportToCanonLookup>() // export contestId -> SchemaToCanonLookup
     val cardStyles: Map<Set<Int>, CardStyle> // canonicalContestIdSet -> cardStyle

    init {
        val contestMap: Map<String, ContestIF> = contests.associateBy { it.name }
        val schemaInfos: List<ExportContestInfo> = export.makeContestInfo() // specific to this exported file

        val gotCanon = mutableMapOf<Int, String>()
        // each contest in the schema must be matched to a ContestIF by name
        var countMissing = 0
        var countMissingCand = 0
        schemaInfos.forEach { schemaInfo ->
            val canonicalContest = coloradoInput.matchCanonicalContest(county, schemaInfo.name) // clean up your act, sheesh
            if (canonicalContest == null) {
                println("  *** missing schema contest: '${schemaInfo.name}' in county $county")
                countMissing++
            } else {
                val contest = contestMap[canonicalContest.contestName]!!
                if (gotCanon.contains(contest.id))
                    println("  *** ${contest.id} has duplicate contest: '${schemaInfo.name}' and '${gotCanon[contest.id]}' ")
                gotCanon[contest.id] = schemaInfo.name

                val candPairs = mutableListOf<Pair<Int, Int>>()
                val info = contest.info()

                schemaInfo.candidateNames.filter { it.key != "Write-In"}.forEach { (exportCandidate, schemaCandId) ->
                    // dont bother making a map
                    val canonCandidateName = coloradoInput.matchCanonicalCandidate(county, canonicalContest, exportCandidate)
                    val canonCandId = info.candidateNames[canonCandidateName] // what if this fails ??
                    if (canonCandId == null) {
                        println("  ** missing export candidate: '${exportCandidate}' in contest '${contest.name}' county $county")
                        countMissingCand++
                    } else {
                        candPairs.add ( Pair(schemaCandId, canonCandId))
                    }
                }
                val lookupSize = schemaInfo.candidateNames.map { it.value }.max()
                val candLookup = IntArray(lookupSize+1) { 0 }
                candPairs.forEach{ (schemaCandId, canonCandId) -> candLookup.set(schemaCandId, canonCandId) }
                exportToCanonLookup[schemaInfo.id] = ExportToCanonLookup(contest.id, candLookup)
            }
        }

        // data class ExportCardStyle(val name: String, val contests: Set<Int>, var count: Int = 0)
        // but the ids are export internal contest Ids; we need map export internal contestIds -> rlauxe ContestId
        // which is what we just built !!
        // turn those into CardStyle

        cardStyles = export.exportCardStyles.mapIndexed { idx, it ->
            val canonicalContestIdSet = convertExportCardStyleToCanonical(it)
            val cleanupName = truncateCommas(it.name)
            val cardStyle = CardStyle("$county-${cleanupName}", cardStyleId++, canonicalContestIdSet.toIntArray(), true)
            cardStyle.ncards = it.count
            Pair(canonicalContestIdSet, cardStyle)
        }.toMap()
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

    // or to CvrExport ??
    fun convertToCard(dcvr: CastVoteRecord): AuditableCard {
        // must convert to canincal contestIDs to use cardStyles
        val contestSchemaIdSet = dcvr.contestVotes.map { it.contestId }.toSet()
        val canonicalIdSet = convertExportContestIdSetToCanonical(contestSchemaIdSet)
        val cardStyle = cardStyles[canonicalIdSet]
        val useCardStyleId = cardStyle?.id() ?: CardStyle.fromCvrStyle.id
        val cvrb = AuditableCardBuilder(dcvr.imprintedId, null,  0, 0L, false, styleId=useCardStyleId, poolId=null, votesIn=null)
        // have to map both contestId and candVotes
        dcvr.contestVotes.forEach{ contestVote ->
            val lookup = exportToCanonLookup[contestVote.contestId]
            if (lookup != null) {
                val cannonCandidateIds = contestVote.candVotes.map { lookup.candLookup[it] }
                cvrb.replaceContestVotes(lookup.canonContestId, cannonCandidateIds.toIntArray() )
            }
        }
        return cvrb.build()
    }

    companion object {
        var cardStyleId = 1
    }
}

private data class ExportToCanonLookup(val canonContestId: Int, val candLookup: IntArray )

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
                if (columns[col].choice != "Write-in") { // remove write-ins
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