package org.cryptobiotic.rlauxe.corlaCounty

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.auditcenter.CountyContestVotes
import org.cryptobiotic.rlauxe.auditcenter.CountyTabAllContests
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.corlaInput.StatewideInput
import org.cryptobiotic.rlauxe.corlaInput.isWriteIn
import org.cryptobiotic.rlauxe.corlacvr.ContestVotes
import org.cryptobiotic.rlauxe.corlacvr.CorlaCountyCvrsIF
import org.cryptobiotic.rlauxe.corlacvr.RedactedGroup
import org.cryptobiotic.rlauxe.util.ContestTabulation
import kotlin.collections.set

private val logger = KotlinLogging.logger("CountyCardConverter")

// convert CorlaCountyCvrsIF from CVRs ids to canonical ids in statewideInput
// each CorlaCountyCvrsIF is specific to a County.
// infosByName is from canonical contests
class CountyCardConverter(val county: String,
                          val cardsFromManifest: CountyCardsFromManifest,
                          val stateInput: StatewideInput,
                          val infosByName: Map<String, ContestInfo>) {

    // map schema contest to canon contest, then an array mapping schema cand id to canonical candidate id
    val schemaToCanonLookup = mutableMapOf<Int, ExportToCanonLookup>() // schema contestId -> ExportToCanonLookup
    val canonNameToLookup = mutableMapOf<String, ExportToCanonLookup>() // schema contestId -> ExportToCanonLookup
    // val cardStyles: Map<Set<Int>, CardStyle> // canonicalContestIdSet -> cardStyle
    // val redactedPools: List<CardPool> // converted to canonical contests and candidates: obsolete use CvrsFromManifest
    val infos = infosByName.mapKeys { it.value.id }

    val schemaContestId = mutableMapOf<String, Int>() // schema contest name to schema contest id
    val schemaContestName = mutableMapOf<Int, String>() // schema contest id to schema contest name
    val schemaContestCanonId = mutableMapOf<String, Int>() // schema contest name to canonical contest id

    init {
        // val infosByName: Map<String, ContestIF> = contests.associateBy { it.name }
        val schemaContestInfos = cardsFromManifest.corlaCvrs.makeContestInfo().values.toList() // specific to this cvr file

        val canonIdToScontest = mutableMapOf<Int, String>()  // canon contest id -> schema contest name
        // each contest in the schema must be matched to a canonicalContest by name
        var countMissing = 0
        var countMissingCand = 0
        schemaContestInfos.forEach { schemaContestInfo ->
            val canonicalContest = stateInput.matchCanonicalContest(county, schemaContestInfo.name) // clean up your act, sheesh
            if (canonicalContest == null) {
                logger.debug{"  *** missing schema contest: '${schemaContestInfo.name}' from county $county"}
                // statewideInput.canonicalContestMungedNames.keys.sorted().forEach { println(" $it") }
                // statewideInput.matchCanonicalContest(county, schemaContestInfo.name)
                countMissing++
            } else {
                val info = infosByName[canonicalContest.contestName]
                if (null == info) {  // we have a canonicalContest that is not in infosByName
                    // looks like  "County Court Judge - Gunnison County - Burgemeister" got coverted to
                    //             "Gunnison County Court Judge - Burgemeister" -> null"
                    // but neither are in infosByName
                    logger.error { " infosByName doesnt have canonicalContest '${canonicalContest.contestName}'" }
                }
                require(info != null)
                if (canonIdToScontest.contains(info.id))
                    logger.warn{"  *** ${info.id} has duplicate contest: '${schemaContestInfo.name}' and '${canonIdToScontest[info.id]}' "}
                canonIdToScontest[info.id] = schemaContestInfo.name

                schemaContestId[schemaContestInfo.name] = schemaContestInfo.id
                schemaContestCanonId[schemaContestInfo.name] = info.id
                schemaContestName[schemaContestInfo.id] = schemaContestInfo.name

                val candPairs = mutableListOf<Pair<Int, Int>>()

                schemaContestInfo.candidateNames.filter { !isWriteIn(it.key) }.forEach { (exportCandidate, schemaCandId) ->
                    // use a lookup instead of a map TODO worth the complexity ??
                    val canonCandidateName = stateInput.matchCanonicalCandidate(county, canonicalContest, exportCandidate)
                    if (canonCandidateName == null) {
                        logger.error{"no match on exportCandidateName '$exportCandidate' from county $county contest ${schemaContestInfo.name}"}
                        stateInput.matchCanonicalCandidate(county, canonicalContest, exportCandidate)
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
                schemaToCanonLookup[schemaContestInfo.id] = lookup
                canonNameToLookup[info.name] = lookup
            }
        }
    }

    fun convertContestId(schemaContestId: Int): Int {
        return schemaToCanonLookup[schemaContestId]?.canonContestId ?: -1
    }

    fun convertCountyIdSet(countyIdSet: Set<Int>): Set<Int> {
        val convert = mutableSetOf<Int>()
        countyIdSet.forEach { countyId ->
            val lookup = schemaToCanonLookup[countyId]!!
            convert.add(lookup.canonContestId)
        }
        return convert.toSet()
    }

    fun convertCountyVotes(countyVotes: List<ContestVotes>): Map<Int, IntArray> {
        val votes = mutableMapOf<Int, IntArray>()
        // have to map both contestId and candVotes
        countyVotes.forEach { contestVote ->
            val lookup = schemaToCanonLookup[contestVote.contestId]
            if (lookup != null) {
                val cannonCandidateIds = contestVote.votedFor.map { lookup.candLookup[it] }
                votes[lookup.canonContestId] = cannonCandidateIds.toIntArray()
            } else {
                // the contest was removed from canonical contests
                logger.debug{"cant find county contest id=${contestVote.contestId}] in county '$county', presumed removed"}
            }
        }
        return votes
    }

    // convert local tabs to canonical tabs
    fun convertTabs(tabs: Map<Int, ContestTabulation>): Map<Int, ContestTabulation> {
        // have to map both contestId and candVotes
        // contestVotes = mutableMapOf<Int, MutableMap<Int, Int>>
        val canonTabs = mutableMapOf<Int, ContestTabulation>()
        tabs.forEach { (contestId: Int, contestTab: ContestTabulation) ->
            val contestName = schemaContestName[contestId]!!
            val canonicalContest = stateInput.matchCanonicalContest(county, contestName)!!
            val info = infosByName[canonicalContest.contestName]!!

            val nz = contestTab.votes.values.sum()  // skip contests with no votes
            val lookup = schemaToCanonLookup[contestId]
            if (nz > 0 && lookup != null) {
                val canonCands: Map<Int, Int> = lookup.convertCands(contestTab.votes)
                val contestTabulation = ContestTabulation(infos[lookup.canonContestId]!!, canonCands, contestTab.ncards())
                canonTabs[lookup.canonContestId] = contestTabulation
            }
        }
        return canonTabs
    }

    fun convertTab(tab: ContestTabulation): ContestTabulation {
        // have to map both contestId and candVotes
        val lookup = schemaToCanonLookup[tab.contestId]!!
        val canonCands: Map<Int, Int> = lookup.convertCands(tab.votes)
        return ContestTabulation(infos[lookup.canonContestId]!!, canonCands, tab.ncards())
    }

    // you must use when creating Pools from RedactedGroup that map to canonical contests
    fun convertToContestTabulation(rgroup: RedactedGroup): Map<Int, ContestTabulation> {
        // have to map both contestId and candVotes
        // contestVotes = mutableMapOf<Int, MutableMap<Int, Int>>
        val canonTabs = mutableMapOf<Int, ContestTabulation>()
        rgroup.candVotes.forEach{ (contestId, rcands) ->
            val nz = rcands.values.sum()  // skip contests with no votes
            val lookup = schemaToCanonLookup[contestId]
            if (nz > 0 && lookup != null) {
                val cannonCands: Map<Int, Int> = lookup.convertCands(rcands)
                val contestTabulation = ContestTabulation(infos[lookup.canonContestId]!!, cannonCands, rgroup.minCards())
                canonTabs[lookup.canonContestId] = contestTabulation
            }
        }
        return canonTabs
    }

    // CountyTabAllContests is the auditcenter reported vote tab. But we are back to the problem of not knowing ncard(county, contest)
    fun convertToContestTabulation(countyTab: CountyTabAllContests): Map<Int, ContestTabulation> {
        val canonTabs = mutableMapOf<Int, ContestTabulation>()
        countyTab.contests.forEach{ (contestName, contestVotes: CountyContestVotes) ->
            val canonicalContest = stateInput.matchCanonicalContest(county, contestName)!!
            val info = infosByName[canonicalContest.contestName]!!

            // TODO where to get ncards ?????
            val contestTabulation = contestVotes.makeContestTabulationCorla(info, canonicalContest, 0)
            canonTabs[info.id] = contestTabulation
        }
        return canonTabs
    }
}

// for a canonicalContest, lookup schema candidate idx -> canonical candidate id
class ExportToCanonLookup(val canonContestId: Int, val candLookup: IntArray ) {

    // not 1-1 so cant use mapKeys. For example Write-In candidate was removed
    fun <T> convertCands(inp: Map<Int,T>): Map<Int,T> {
        val result = mutableMapOf<Int, T>()
        inp.forEach {
            if (it.key < candLookup.size) {
                val newCandId = candLookup[it.key]
                if (newCandId >= 0) result[newCandId] = it.value
            }
        }
        return result
    }

    // val cannonCandidateIds = contestVote.candVotes.map { lookup.candLookup[it] }.filter { it >= 0 }
    fun convertCands(contestVotes: ContestVotes): List<Int> {
        val result = mutableListOf<Int>()
        contestVotes.votedFor.forEach {
            val newCandId = candLookup[it]
            if (newCandId >= 0 ) result.add(newCandId)
        }
        return result
    }
}

/////////////////////////////////////////////////////////////////////////
// make schema specific ContestInfo from schema.contests; uses local contestId and candidateId
// keep seperate from ContestInfo to avoid confusion
data class CorlaContestInfo(
    val name: String,
    val id: Int,
    val candidateNames: Map<String, Int>,
    val isIrv: Boolean,
    val voteForN: Int) {
        val candidateIdToName: Map<Int, String> = candidateNames.entries.associate {(k,v) -> v to k }
}

fun CorlaCountyCvrsIF.makeContestInfo(): Map<Int, CorlaContestInfo> {

    return this.schema.contests.map { schemaContest ->

        val candidateMap = if (!schemaContest.isIRV) {
            val candidateMap1 = mutableMapOf<String, Int>()

            this.schema.columnsFor(schemaContest).forEach { colInfo ->
                if (!isWriteIn(colInfo.choiceName)) { // remove write-ins
                    candidateMap1[colInfo.choiceName] = colInfo.colIdx - schemaContest.startCol
                }
            }
            candidateMap1

        } else { // isIRV: there are ncand x ncand columns,
            val candidates = mutableListOf<String>()
            this.schema.columnsFor(schemaContest).forEach { colInfo ->
                candidates.add(colInfo.choiceName)
            }

            /* for (col in exportContest.startCol..exportContest.startCol + exportContest.ncols - 1) {
                candidates.add(columns[col].choiceName)
            } */
            val pairs = mutableListOf<Pair<String, Int>>()
            repeat(schemaContest.nchoices) { idx ->
                pairs.add(Pair(candidates[idx], idx))
            }
            pairs.toMap()
        }

        // val (name, nwinners) = if (exportContest.isIRV) parseIrvContestName(exportContest.contestName) else parseContestNameAndVoteFor(exportContest.contestName)
        CorlaContestInfo( schemaContest.contestName, schemaContest.contestIdx, candidateMap.toSortedMap(), schemaContest.isIRV,
            schemaContest.voteForN)
    }.associateBy { it.id }
}