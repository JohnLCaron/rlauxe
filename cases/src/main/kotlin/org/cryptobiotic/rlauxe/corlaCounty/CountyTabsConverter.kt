package org.cryptobiotic.rlauxe.corlaCounty

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.auditcenter.CountyContestVotes
import org.cryptobiotic.rlauxe.auditcenter.CountyTabAllContests
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.corlaInput.StatewideInput
import org.cryptobiotic.rlauxe.corlaInput.isWriteIn
import org.cryptobiotic.rlauxe.corlacvr.ContestVotes
import org.cryptobiotic.rlauxe.corlacvr.RedactedGroup
import org.cryptobiotic.rlauxe.util.ContestTabulation
import kotlin.collections.set

private val logger = KotlinLogging.logger("CountyCardConverter")

// data class CountyTabAllContests(val countyName: String) {
//    val contests = Map<String, CountyContestVotes>() // contestName (canonical I think) -> CountyContestVotes
//    var ncards = 0
// data class CountyContestVotes(val contestName: String) {
//    val choices = Map<String, Int>() // choice name (not canonical) -> votes in this county and contest
//    var ncards = 0
// infosByName is from canonical contests
class CountyTabsConverter(val county: String,
                          val countyTabs: CountyTabAllContests,
                          val stateInput: StatewideInput,
                          val infosByName: Map<String, ContestInfo>) {

    // map county contest to canon contest, then an array mapping county cand id to canonical candidate id
    val countyToCanonLookup = mutableMapOf<Int, ExportToCanonLookup>() // county contestId -> ExportToCanonLookup
    val canonNameToLookup = mutableMapOf<String, ExportToCanonLookup>() // county contestId -> ExportToCanonLookup

    // val cardStyles: Map<Set<Int>, CardStyle> // canonicalContestIdSet -> cardStyle
    // val redactedPools: List<CardPool> // converted to canonical contests and candidates: obsolete use CvrsFromManifest
    val infos = infosByName.mapKeys { it.value.id }

    val countyContestId = mutableMapOf<String, Int>() // county contest name to county contest id
    val countyContestName = mutableMapOf<Int, String>() // county contest id to county contest name
    val countyContestCanonId = mutableMapOf<String, Int>() // county contest name to canonical contest id

    init {
        val countyContestInfos = makeContestInfo(countyTabs).values.toList() // specific to county

        val canonIdToScontest = mutableMapOf<Int, String>()  // canon contest id -> countyTabs contest name
        // each contest in the county must be matched to a canonicalContest by name
        var countMissing = 0
        countyContestInfos.forEach { countyContestInfo ->
            val canonicalContest =
                stateInput.matchCanonicalContest(county, countyContestInfo.name) // clean up your act, sheesh
            if (canonicalContest == null) {
                logger.debug { "  *** missing county contest: '${countyContestInfo.name}' from county $county" }
                countMissing++
            } else {
                val info = infosByName[canonicalContest.contestName]
                if (null == info) {
                    logger.error { " infosByName doesnt have canonicalContest '${canonicalContest.contestName}'" }
                }
                require(info != null)
                if (canonIdToScontest.contains(info.id))
                    logger.warn { "  *** ${info.id} has duplicate contest: '${countyContestInfo.name}' and '${canonIdToScontest[info.id]}' " }
                canonIdToScontest[info.id] = countyContestInfo.name

                countyContestId[countyContestInfo.name] = countyContestInfo.id
                countyContestCanonId[countyContestInfo.name] = info.id
                countyContestName[countyContestInfo.id] = countyContestInfo.name

                val candPairs = mutableListOf<Pair<Int, Int>>()

                countyContestInfo.candidateNames.filter { !isWriteIn(it.key) }
                    .forEach { (exportCandidate, countyCandId) ->
                        // use a lookup instead of a map TODO worth the complexity ??
                        val canonCandidateName =
                            stateInput.matchCanonicalCandidate(county, canonicalContest, exportCandidate)
                        if (canonCandidateName == null) {
                            logger.error { "no match on exportCandidateName '$exportCandidate' from county $county contest ${countyContestInfo.name}" }
                            stateInput.matchCanonicalCandidate(county, canonicalContest, exportCandidate)
                            throw Exception("no match on exportCandidateName '$exportCandidate' from county $county contest ${countyContestInfo.name}")
                        }
                        val canonCandId = info.candidateNames[canonCandidateName] // what if this fails ??
                        if (canonCandId == null) {
                            logger.error { "no match on info.candidateNames: canonCandidateName=$canonCandidateName orgName=$exportCandidate" }
                            throw Exception("no match on info.candidateNames: canonCandidateName=$canonCandidateName orgName=$exportCandidate")
                        } else {
                            candPairs.add(Pair(countyCandId, canonCandId))
                        }
                    }
                val lookupSize = countyContestInfo.candidateNames.map { it.value }.max()
                val candLookup = IntArray(lookupSize + 1) { -1 } // plus one because its one based
                candPairs.forEach { (countyCandId, canonCandId) -> candLookup.set(countyCandId, canonCandId) }
                val lookup = ExportToCanonLookup(info.id, candLookup)
                countyToCanonLookup[countyContestInfo.id] = lookup
                canonNameToLookup[info.name] = lookup
            }
        }
    }

    fun convertContestId(countyContestId: Int): Int {
        return countyToCanonLookup[countyContestId]?.canonContestId ?: -1
    }

    fun convertCountyIdSet(countyIdSet: Set<Int>): Set<Int> {
        val convert = mutableSetOf<Int>()
        countyIdSet.forEach { countyId ->
            val lookup = countyToCanonLookup[countyId]!!
            convert.add(lookup.canonContestId)
        }
        return convert.toSet()
    }

    fun convertCountyVotes(countyVotes: List<ContestVotes>): Map<Int, IntArray> {
        val votes = mutableMapOf<Int, IntArray>()
        // have to map both contestId and candVotes
        countyVotes.forEach { contestVote ->
            val lookup = countyToCanonLookup[contestVote.contestId]
            if (lookup != null) {
                val cannonCandidateIds = contestVote.votedFor.map { lookup.candLookup[it] }
                votes[lookup.canonContestId] = cannonCandidateIds.toIntArray()
            } else {
                // the contest was removed from canonical contests
                logger.debug { "cant find county contest id=${contestVote.contestId}] in county '$county', presumed removed" }
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
            val contestName = countyContestName[contestId]!!
            val canonicalContest = stateInput.matchCanonicalContest(county, contestName)!!
            val info = infosByName[canonicalContest.contestName]!!

            val nz = contestTab.votes.values.sum()  // skip contests with no votes
            val lookup = countyToCanonLookup[contestId]
            if (nz > 0 && lookup != null) {
                val canonCands: Map<Int, Int> = lookup.convertCands(contestTab.votes)
                val contestTabulation =
                    ContestTabulation(infos[lookup.canonContestId]!!, canonCands, contestTab.ncards())
                canonTabs[lookup.canonContestId] = contestTabulation
            }
        }
        return canonTabs
    }

    fun convertTab(tab: ContestTabulation): ContestTabulation {
        // have to map both contestId and candVotes
        val lookup = countyToCanonLookup[tab.contestId]!!
        val canonCands: Map<Int, Int> = lookup.convertCands(tab.votes)
        return ContestTabulation(infos[lookup.canonContestId]!!, canonCands, tab.ncards())
    }

    // you must use when creating Pools from RedactedGroup that map to canonical contests
    fun convertToContestTabulation(rgroup: RedactedGroup): Map<Int, ContestTabulation> {
        // have to map both contestId and candVotes
        // contestVotes = mutableMapOf<Int, MutableMap<Int, Int>>
        val canonTabs = mutableMapOf<Int, ContestTabulation>()
        rgroup.candVotes.forEach { (contestId, rcands) ->
            val nz = rcands.values.sum()  // skip contests with no votes
            val lookup = countyToCanonLookup[contestId]
            if (nz > 0 && lookup != null) {
                val cannonCands: Map<Int, Int> = lookup.convertCands(rcands)
                val contestTabulation =
                    ContestTabulation(infos[lookup.canonContestId]!!, cannonCands, rgroup.minCards())
                canonTabs[lookup.canonContestId] = contestTabulation
            }
        }
        return canonTabs
    }

    // CountyTabAllContests is the auditcenter reported vote tab. But we are back to the problem of not knowing ncard(county, contest)
    fun convertToContestTabulation(countyTab: CountyTabAllContests): Map<Int, ContestTabulation> {
        val canonTabs = mutableMapOf<Int, ContestTabulation>()
        countyTab.contests.forEach { (contestName, contestVotes: CountyContestVotes) ->
            val canonicalContest = stateInput.matchCanonicalContest(county, contestName)!!
            val info = infosByName[canonicalContest.contestName]!!

            // TODO where to get ncards ?????
            val contestTabulation = contestVotes.makeContestTabulationCorla(info, canonicalContest, 0)
            canonTabs[info.id] = contestTabulation
        }
        return canonTabs
    }

    // data class CountyTabAllContests(val countyName: String) {
//    val contests = Map<String, CountyContestVotes>() // contestName (canonical I think) -> CountyContestVotes
//    var ncards = 0
// data class CountyContestVotes(val contestName: String) {
//    val choices = Map<String, Int>() // choice name (not canonical) -> votes in this county and contest
//    var ncards = 0
    fun makeContestInfo(tabs: CountyTabAllContests): Map<Int, CorlaContestInfo> {
        var contestIdx = 0

        return tabs.contests.map { (contestName, contestVotes) ->
            val canonicalContest = stateInput.matchCanonicalContest(county, contestName)!!
            val info = infosByName[canonicalContest.contestName]!!

            val candidateMap = mutableMapOf<String, Int>()
            var choiceIdx = 0
            contestVotes.choices.forEach { (choiceName, _) ->
                if (!isWriteIn(choiceName)) { // remove write-ins
                    candidateMap[choiceName] = choiceIdx
                }
                choiceIdx++
            }

            // val (name, nwinners) = if (exportContest.isIRV) parseIrvContestName(exportContest.contestName) else parseContestNameAndVoteFor(exportContest.contestName)
            CorlaContestInfo(contestName, contestIdx++, candidateMap.toSortedMap(), info.isIrv,
                info.voteForN
            )
        }.associateBy { it.id }
    }
}
