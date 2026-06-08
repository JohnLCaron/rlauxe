package org.cryptobiotic.rlauxe.dominion

import org.cryptobiotic.rlauxe.audit.AuditableCardM
import org.cryptobiotic.rlauxe.core.ContestIF
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.corla.ColoradoInput
import org.cryptobiotic.rlauxe.util.AuditableCardMBuilder

class DominionCvrConverter(export: DominionCvrExport, contests: List<ContestIF>, coloradoInput: ColoradoInput,
                           val styleNameMap: Map<Set<Int>, String>) {
    private val schemaToCanonLookup = mutableMapOf<Int, SchemaToCanonLookup>() // schema contestId -> SchemaToCanonLookup

    init {
        val canonicalMap: Map<String, ContestIF> = contests.associateBy { it.name }
        val schemaInfos: List<ContestInfo> = export.makeContestInfo() // specific to this exported file

        // each contest in the schema must be matched to a ContestIF by name
        var countMissing = 0
        var countMissingCand = 0
        schemaInfos.forEach { schemaInfo ->
            val schemaName = coloradoInput.contestNameCleanup(schemaInfo.name) // clean up your act, sheesh
            val canonicalContest = canonicalMap[schemaName] // find matching contest
            if (canonicalContest == null) {
                println("dont have this schema contest: '${schemaName}'")
                countMissing++
            } else {
                val candPairs = mutableListOf<Pair<Int, Int>>()
                val canonInfo = canonicalContest.info()
                schemaInfo.candidateNames.forEach { (name, schemaCandId) ->
                    // dont bother making a map
                    val schemaName = coloradoInput.candidateNameCleanup(name)
                    val canonCandId = canonInfo.candidateNames[schemaName] // what if this fails ??
                    if (canonCandId == null) {
                        println("dont have this schema candidate: '${schemaName}' in contest ${canonicalContest.id}")
                        countMissingCand++
                    } else {
                        candPairs.add ( Pair(schemaCandId, canonCandId))
                    }
                }
                val lookupSize = schemaInfo.candidateNames.map { it.value }.max()
                val candLookup = IntArray(lookupSize+1) { 0 }
                candPairs.forEach{ (schemaCandId, canonCandId) -> candLookup.set(schemaCandId, canonCandId) }
                schemaToCanonLookup[schemaInfo.id] = SchemaToCanonLookup(canonicalContest.id, candLookup)
            }
        }
    }

    // or to CvrExport ??
    fun convertToCard(dcvr: CastVoteRecord): AuditableCardM {
        //     val id: String,
        //    val location: String?,
        //    val index: Int,
        //    val prn: Long,
        //    val phantom: Boolean,
        //    val styleName: String? = null,
        //    val poolId: Int? = null,
        //    votesIn: Map<Int, IntArray>?,
        //    val style: StyleIF? = null,

        val contestSet = dcvr.contestVotes.map { it.contestId }.toSet()
        val styleName = styleNameMap[contestSet]
        val cvrb = AuditableCardMBuilder(dcvr.imprintedId, null,  0, 0L, false, styleName, null, null)
        // have to map both contestId and candVotes
        dcvr.contestVotes.forEach{ contestVote ->
            val lookup = schemaToCanonLookup[contestVote.contestId]
            if (lookup != null) {
                val cannonCandidateIds = contestVote.candVotes.map { lookup.candLookup[it] }
                cvrb.replaceContestVotes(lookup.canonContestId, cannonCandidateIds.toIntArray() )
            }
        }
        return cvrb.build()
    }
}

private data class SchemaToCanonLookup(val canonContestId: Int, val candLookup: IntArray )

// make schema specific ContestInfo from export.schema.contests
fun DominionCvrExport.makeContestInfo(): List<ContestInfo> {
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

        val choiceFunction = if (exportContest.isIRV) SocialChoiceFunction.IRV else SocialChoiceFunction.PLURALITY
        val (name, nwinners) = if (exportContest.isIRV) parseIrvContestName(exportContest.contestName) else parseContestNameAndVoteFor(exportContest.contestName)
        ContestInfo( name, exportContest.contestIdx, candidateMap, choiceFunction, nwinners)
    }
}

fun parseContestNameAndVoteFor(name: String) : Pair<String, Int> {
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