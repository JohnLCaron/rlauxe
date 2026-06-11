package org.cryptobiotic.rlauxe.dominion

import org.cryptobiotic.rlauxe.audit.AuditableCardM
import org.cryptobiotic.rlauxe.audit.CardStyle
import org.cryptobiotic.rlauxe.core.ContestIF
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.corla.ColoradoInput
import org.cryptobiotic.rlauxe.util.AuditableCardMBuilder
import kotlin.collections.set


// each export is specific to a County; but we will call it a pool
class DominionCvrConverter(val poolName: String, export: DominionCvrExport, contests: List<ContestIF>, coloradoInput: ColoradoInput) {
                           //val styleNameMap: Map<Set<Int>, String>) {
    private val exportToCanonLookup = mutableMapOf<Int, SchemaToCanonLookup>() // export contestId -> SchemaToCanonLookup
     val cardStyles: Map<Set<Int>, CardStyle>

    init {
        val canonicalMap: Map<String, ContestIF> = contests.associateBy { it.name }
        val schemaInfos: List<ContestInfo> = export.makeContestInfo() // specific to this exported file

        val gotCanon = mutableSetOf<Int>()
        // each contest in the schema must be matched to a ContestIF by name
        var countMissing = 0
        var countMissingCand = 0
        schemaInfos.forEach { schemaInfo ->
            val schemaName = coloradoInput.contestNameCleanup(schemaInfo.name) // clean up your act, sheesh
            val canonicalContest = canonicalMap[schemaName] // find matching contest
            if (canonicalContest == null) {
                println("canonical doesnt have this schema contest: '${schemaName}'")
                countMissing++
            } else {
                // println("schema ${schemaInfo.id} -> canon ${canonicalContest.id} ")
                //println("                       '$schemaName' -> '${canonicalContest.name}'")
                if (gotCanon.contains(canonicalContest.id))
                    print("BAD")
                gotCanon.add(canonicalContest.id)

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
                exportToCanonLookup[schemaInfo.id] = SchemaToCanonLookup(canonicalContest.id, candLookup)
            }
        }

        // data class ExportCardStyle(val name: String, val contests: Set<Int>, var count: Int = 0)
        // but the ids are export internal contest Ids; we need map export internal contestIds -> rlauxe ContestId
        // which is what we just built !!
        // turn those into CardStyle

        // TODO pass in startIdx
        val startIdx = 0
        cardStyles = export.exportCardStyles.mapIndexed { idx, it ->
            val canonicalContests = convertCardStyleToCanonical(it)
            val cardStyle = CardStyle("$poolName-${it.name}", startIdx + idx, canonicalContests.toIntArray(), true)
            cardStyle.ncards = it.count
            Pair(canonicalContests, cardStyle)
        }.toMap()

    }

    // return corresponding contest Ids in canonical
    fun convertCardStyleToCanonical(exportCardStyle: ExportCardStyle): Set<Int> {
        val convert = exportCardStyle.contests.map { exportContestId ->
            val lookup = exportToCanonLookup[exportContestId]!!
            lookup.canonContestId
        }
        println("$exportCardStyle -> $convert")
        val got = mutableSetOf<Int>()
        convert.forEach {
            if (got.contains(it))
                print("")
            got.add(it)
        }
        return got
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
        val cardStyle = cardStyles[contestSet]
        val styleName = cardStyle?.name ?: "missing"
        val cvrb = AuditableCardMBuilder(dcvr.imprintedId, null,  0, 0L, false, styleName, null, null)
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

//  export.CardStyles
//  ExportCardStyle(name=DS-13D, contests=[0, 1, 2, 3, 4, 5, 6, 7, 12, 13, 14, 15, 16, 17, 18, 19], count=4582)
//  ExportCardStyle(name=DS-19D, contests=[0, 1, 2, 3, 4, 5, 6, 7, 10, 13, 14, 15, 16, 17, 18, 19], count=363)
//
//number of contestBuilders = 695
//2026-06-10 10:50:47.154 INFO  *** there are no winners for 'Alamosa County Coroner - REP' (21) candidates=[0] choiceFunction=PLURALITY nwinners=1 voteForN=1
//schema 0 -> canon 674
//schema 1 -> canon 428
//schema 2 -> canon 205
//schema 3 -> canon 479
//schema 4 -> canon 655
//schema 5 -> canon 46
//schema 6 -> canon 488
//schema 7 -> canon 626
//schema 8 -> canon 498
//schema 9 -> canon 500
//schema 10 -> canon 502
//schema 11 -> canon 516
//schema 12 -> canon 582
//schema 13 -> canon 65
//schema 14 -> canon 64
//schema 15 -> canon 69
//schema 16 -> canon 63
//schema 17 -> canon 67
//schema 18 -> canon 68
//schema 19 -> canon 66
//schema 20 -> canon 675
//schema 21 -> canon 429
//schema 22 -> canon 206
//schema 23 -> canon 480
//schema 24 -> canon 656
//schema 25 -> canon 47
//schema 26 -> canon 489
//schema 27 -> canon 627
//schema 28 -> canon 499
//schema 29 -> canon 501
//schema 30 -> canon 503
//schema 31 -> canon 517
//schema 32 -> canon 583

//ExportCardStyle(name=DS-13D, contests=[0, 1, 2, 3, 4, 5, 6, 7, 12, 13, 14, 15, 16, 17, 18, 19], count=4582) -> [674, 428, 205, 479, 655, 46, 488, 626, 582, 65, 64, 69, 63, 67, 68, 66]
//ExportCardStyle(name=DS-19D, contests=[0, 1, 2, 3, 4, 5, 6, 7, 10, 13, 14, 15, 16, 17, 18, 19], count=363) -> [674, 428, 205, 479, 655, 46, 488, 626, 502, 65, 64, 69, 63, 67, 68, 66]
//
// dominionConverter.ExportCardStyles
//  CardStyle(name=test-DS-13D, contests=[46, 63, 64, 65, 66, 67, 68, 69, 205, 428, 479, 488, 582, 626, 655, 674], count= 4582
//  CardStyle(name=test-DS-19D, contests=[46, 63, 64, 65, 66, 67, 68, 69, 205, 428, 479, 488, 502, 626, 655, 674], count= 363