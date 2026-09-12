package org.cryptobiotic.rlauxe.cvr

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.CardPool
import org.cryptobiotic.rlauxe.audit.CardStyle
import org.cryptobiotic.rlauxe.auditcenter.CanonicalContest
import org.cryptobiotic.rlauxe.auditcenter.CountyContestVotes
import org.cryptobiotic.rlauxe.auditcenter.CountyTabAllContests
import org.cryptobiotic.rlauxe.corlaInput.ColoradoInput
import org.cryptobiotic.rlauxe.corlaInput.isWriteIn
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.util.AuditableCardBuilder
import org.cryptobiotic.rlauxe.util.ContestTabulation
import kotlin.collections.set

private val logger = KotlinLogging.logger("CorlaCvrConverter")

// convert CorlaCvrsIF from CVRs ids to canonical ids in coloradoInput
// each CorlaCvrsIF is specific to a County.
// infosByName is from canonical contests
class CorlaCvrConverter(val county: String, val corlaCvrs: CorlaCvrsIF, val infosByName: Map<String, ContestInfo>,
                        val coloradoInput: ColoradoInput, startingStyleId: Int = 1) {

    // map export contest to canon contest, then an array mapping export cand id to canonical candidate id
    val exportToCanonLookup = mutableMapOf<Int, ExportToCanonLookup>() // export contestId -> ExportToCanonLookup
    val canonNameToLookup = mutableMapOf<String, ExportToCanonLookup>() // export contestId -> ExportToCanonLookup
    val cardStyles: Map<Set<Int>, CardStyle> // canonicalContestIdSet -> cardStyle
    // val redactedPools: List<CardPool> // converted to canonical contests and candidates: obsolete use CvrsFromManifest
    val infos = infosByName.mapKeys { it.value.id }

    val schemaContestId = mutableMapOf<String, Int>() // export contest name to export contest id
    val schemaContestCanonId = mutableMapOf<String, Int>() // export contest name to canonical contest id
    var cardStyleId = startingStyleId

    init {
        // val infosByName: Map<String, ContestIF> = contests.associateBy { it.name }
        val schemaContestInfos = corlaCvrs.makeContestInfo() // specific to this cvr file

        val gotCanon = mutableMapOf<Int, String>()  // canon contest id -> export contest name
        // each contest in the schema must be matched to a ContestIF by name
        var countMissing = 0
        var countMissingCand = 0
        schemaContestInfos.forEach { schemaContestInfo ->
            val canonicalContest = coloradoInput.matchCanonicalContest(county, schemaContestInfo.name) // clean up your act, sheesh
            if (canonicalContest == null) {
                logger.warn{"  *** missing schema contest: '${schemaContestInfo.name}' from county $county"}
                // coloradoInput.canonicalContestMungedNames.keys.sorted().forEach { println(" $it") }
                coloradoInput.matchCanonicalContest(county, schemaContestInfo.name)
                countMissing++
            } else {
                val info = infosByName[canonicalContest.contestName]
                if (null == info)
                    logger.error{" infosByName doesnt have canonicalContest '${canonicalContest.contestName}'"}
                require(info != null)
                if (gotCanon.contains(info.id))
                    logger.warn{"  *** ${info.id} has duplicate contest: '${schemaContestInfo.name}' and '${gotCanon[info.id]}' "}
                gotCanon[info.id] = schemaContestInfo.name

                schemaContestId[schemaContestInfo.name] = schemaContestInfo.id
                schemaContestCanonId[schemaContestInfo.name] = info.id

                val candPairs = mutableListOf<Pair<Int, Int>>()

                schemaContestInfo.candidateNames.filter { !isWriteIn(it.key) }.forEach { (exportCandidate, schemaCandId) ->
                    // use a lookup instead of a map TODO worth the complexity ??
                    val canonCandidateName = coloradoInput.matchCanonicalCandidate(county, canonicalContest, exportCandidate)
                    if (canonCandidateName == null) {
                        logger.error{"no match on exportCandidateName '$exportCandidate' from county $county contest ${schemaContestInfo.name}"}
                        coloradoInput.matchCanonicalCandidate(county, canonicalContest, exportCandidate)
                        throw Exception("no match on exportCandidateName '$exportCandidate' from county $county contest ${schemaContestInfo.name}")
                    }
                    val canonCandId = info.candidateNames[canonCandidateName] // what if this fails ??
                    if (canonCandId == null) {
                        logger.error{"no match on info.candidateNames: canonCandidateName=$canonCandidateName orgName=$exportCandidate"}
                        throw Exception("no match on info.candidateNames: canonCandidateName=$canonCandidateName orgName=$exportCandidate")
                    } else {
                        candPairs.add ( Pair(schemaCandId, canonCandId))
                    }
                }
                val lookupSize = schemaContestInfo.candidateNames.map { it.value }.max()
                val candLookup = IntArray(lookupSize+1) { -1 } // plus one because its one based
                candPairs.forEach{ (schemaCandId, canonCandId) -> candLookup.set(schemaCandId, canonCandId) }
                val lookup = ExportToCanonLookup(info.id, candLookup)
                exportToCanonLookup[schemaContestInfo.id] = lookup
                canonNameToLookup[info.name] = lookup
            }
        }

        // data class ExportCardStyle(val name: String, val contests: Set<Int>, var count: Int = 0)
        // but the ids are export internal contest Ids; we need map export internal contestIds -> rlauxe ContestId
        // which is what we just built !!
        // turn those into CardStyle

        cardStyles = corlaCvrs.cardStyles().map { it ->
            val canonicalContestIdSet = convertExportCardStyleToCanonical(it)
            val cleanupName = truncateCommas(it.name)
            val cardStyle = CardStyle("$county-${cleanupName}", cardStyleId++, canonicalContestIdSet.toIntArray(), true)
            cardStyle.ncards = it.countCards
            Pair(canonicalContestIdSet, cardStyle)
        }.toMap()

        /* one for each redacted group // obsolete
        redactedPools = corlaCvrs.redactedGroups().map { group ->
            val contestTabs = convertToContestTabulation(group)
            val cleanupName = truncateCommas(group.groupName)
            CardPool("$county-${cleanupName}.Redacted", cardStyleId++, true, infos, contestTabs, group.minCards())
        } */
        print("")
    }

    // return corresponding contest Ids in canonical
    fun convertExportCardStyleToCanonical(exportCardStyle: CvrCardStyle): Set<Int> {
        val convert = mutableSetOf<Int>()
        exportCardStyle.contestIds.forEach { exportContestId ->
            val lookup = exportToCanonLookup[exportContestId]
            if (lookup != null) convert.add(lookup.canonContestId)
        }
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

    // you must use when converting to cards that map to canonical contests
    fun convertToCard(dcvr: CvrRow, visit: ((AuditableCardBuilder) -> Unit)? = null): AuditableCard {
        // must convert to canonical contestIDs to use cardStyles
        val contestSchemaIdSet = dcvr.contestVotes.map { it.contestId }.toSet()
        val canonicalIdSet = convertExportContestIdSetToCanonical(contestSchemaIdSet)
        val cardStyle = cardStyles[canonicalIdSet]
        val useCardStyleId = cardStyle?.id() ?: throw RuntimeException("Cant find style") // CardStyle.fromCvrStyle.id // can this happen ??
        val cvrb = AuditableCardBuilder(dcvr.imprintedId, null,  0, 0L, false, styleId=useCardStyleId, poolId=null, votesIn=null)
        // have to map both contestId and candVotes
        dcvr.contestVotes.forEach { contestVote ->
            val lookup = exportToCanonLookup[contestVote.contestId]
            if (lookup != null) {
                val cannonCandidateIds = contestVote.candVotes.map { lookup.candLookup[it] }.filter { it >= 0 }
                cvrb.replaceContestVotes(lookup.canonContestId, cannonCandidateIds.toIntArray() )
            } else {
                logger.error{"cant find exportToCanonLookup[${contestVote.contestId}] in county '$county'"}
                throw Exception("cant find contest")
            }
        }
        if (visit != null) visit(cvrb)
        return cvrb.build()
    }

    // you must use when creating Pools from RedactedGroup that map to canonical contests
    fun convertToContestTabulation(rgroup: RedactedGroup): Map<Int, ContestTabulation> {
        // have to map both contestId and candVotes
        // contestVotes = mutableMapOf<Int, MutableMap<Int, Int>>
        val canonTabs = mutableMapOf<Int, ContestTabulation>()
        rgroup.contestVotes.forEach{ (contestId, rcands) ->
            val nz = rcands.values.sum()  // skip contests with no votes
            val lookup = exportToCanonLookup[contestId]
            if (nz > 0 && lookup != null) {
                val cannonCands: Map<Int, Int> = lookup.convertCands(rcands)
                val contestTabulation = ContestTabulation(infos[lookup.canonContestId]!!, cannonCands, rgroup.minCards())
                canonTabs[lookup.canonContestId] = contestTabulation
            }
        }
        return canonTabs
    }

    fun convertToContestTabulation(countyTab: CountyTabAllContests): Map<Int, ContestTabulation> {
        val canonTabs = mutableMapOf<Int, ContestTabulation>()
        countyTab.contests.forEach{ (contestName, contestVotes: CountyContestVotes  ) ->
            val canonicalContest = coloradoInput.matchCanonicalContest(county, contestName)!!
            val info = infosByName[canonicalContest.contestName]!!

            val contestTabulation = contestVotes.makeContestTabulationCorla(info, canonicalContest, 0)
            canonTabs[info.id] = contestTabulation


            /* change vote map of export candidate names to candidate index
            val exportCandMap: Map<Int, Int> =
                contestVotes.choices.mapKeys { corlaCvrs.schema.choiceIdx(it.key) }
            // change vote map of export candidate id to canonical candidate id
            exportCandMap.forEach { (id, vote) ->
                if (id >= lookup.candLookup.size)
                    print("")
            }
            val cannonCands: Map<Int, Int> = lookup.convertCands(exportCandMap)
            val contestTabulation = ContestTabulation(infos[lookup.canonContestId]!!, cannonCands) // dont know ncards
            canonTabs[lookup.canonContestId] = contestTabulation */
        }
        return canonTabs
    }
}

// for a canonicalContest, lookup export candidate idx -> canonical candidate id
class ExportToCanonLookup(val canonContestId: Int, val candLookup: IntArray ) {

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
data class CorlaContestInfo(
    val name: String,
    val id: Int,
    val candidateNames: Map<String, Int>,
    val isIrv: Boolean,
    val nwinners: Int) {
        val candidateIdToName: Map<Int, String> = candidateNames.entries.associate {(k,v) -> v to k }
}

fun CorlaCvrsIF.makeContestInfo(): List<CorlaContestInfo> {
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

        } else { // isIRV: there are ncand x ncand columns,
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

        // val (name, nwinners) = if (exportContest.isIRV) parseIrvContestName(exportContest.contestName) else parseContestNameAndVoteFor(exportContest.contestName)
        CorlaContestInfo( exportContest.contestName, exportContest.contestIdx, candidateMap, exportContest.isIRV,
            exportContest.voteForN)
    }
}