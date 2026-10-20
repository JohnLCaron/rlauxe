package org.cryptobiotic.rlauxe.corlaCounty

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.CardStyle
import org.cryptobiotic.rlauxe.auditcenter.CountyContestVotes
import org.cryptobiotic.rlauxe.auditcenter.CountyTabAllContests
import org.cryptobiotic.rlauxe.corlaInput.StatewideInput
import org.cryptobiotic.rlauxe.corlaInput.isWriteIn
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.corlacvr.ContestVotes
import org.cryptobiotic.rlauxe.corlacvr.CorlaCountyCvrsIF
import org.cryptobiotic.rlauxe.corlacvr.CvrCardStyle
import org.cryptobiotic.rlauxe.corlacvr.CvrRow
import org.cryptobiotic.rlauxe.corlacvr.RedactedGroup
import org.cryptobiotic.rlauxe.util.AuditableCardBuilder
import org.cryptobiotic.rlauxe.util.ContestTabulation
import kotlin.collections.set
import kotlin.text.get

private val logger = KotlinLogging.logger("CountyCardConverter")

// convert CorlaCountyCvrsIF from CVRs ids to canonical ids in statewideInput
// each CorlaCountyCvrsIF is specific to a County.
// infosByName is from canonical contests
class CountyCardConverter(val county: String,
                          val cardsFromManifest: CountyCardsFromManifest,
                          val statewideInput: StatewideInput,
                          val infosByName: Map<String, ContestInfo>,
                          startingStyleId: Int = 1) {

    // map export contest to canon contest, then an array mapping export cand id to canonical candidate id
    val schemaToCanonLookup = mutableMapOf<Int, ExportToCanonLookup>() // export contestId -> ExportToCanonLookup
    val canonNameToLookup = mutableMapOf<String, ExportToCanonLookup>() // export contestId -> ExportToCanonLookup
    val cardStyles: Map<Set<Int>, CardStyle> // canonicalContestIdSet -> cardStyle
    // val redactedPools: List<CardPool> // converted to canonical contests and candidates: obsolete use CvrsFromManifest
    val infos = infosByName.mapKeys { it.value.id }

    val schemaContestId = mutableMapOf<String, Int>() // export contest name to export contest id
    val schemaContestName = mutableMapOf<Int, String>() // export contest id to export contest name
    val schemaContestCanonId = mutableMapOf<String, Int>() // export contest name to canonical contest id
    var cardStyleId = startingStyleId

    init {
        // val infosByName: Map<String, ContestIF> = contests.associateBy { it.name }
        val schemaContestInfos = cardsFromManifest.corlaCvrs.makeContestInfo() // specific to this cvr file

        val gotCanon = mutableMapOf<Int, String>()  // canon contest id -> export contest name
        // each contest in the schema must be matched to a ContestIF by name
        var countMissing = 0
        var countMissingCand = 0
        schemaContestInfos.forEach { schemaContestInfo ->
            val canonicalContest = statewideInput.matchCanonicalContest(county, schemaContestInfo.name) // clean up your act, sheesh
            if (canonicalContest == null) {
                logger.debug{"  *** missing schema contest: '${schemaContestInfo.name}' from county $county"}
                // statewideInput.canonicalContestMungedNames.keys.sorted().forEach { println(" $it") }
                // statewideInput.matchCanonicalContest(county, schemaContestInfo.name)
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
                schemaContestName[schemaContestInfo.id] = schemaContestInfo.name

                val candPairs = mutableListOf<Pair<Int, Int>>()

                schemaContestInfo.candidateNames.filter { !isWriteIn(it.key) }.forEach { (exportCandidate, schemaCandId) ->
                    // use a lookup instead of a map TODO worth the complexity ??
                    val canonCandidateName = statewideInput.matchCanonicalCandidate(county, canonicalContest, exportCandidate)
                    if (canonCandidateName == null) {
                        logger.error{"no match on exportCandidateName '$exportCandidate' from county $county contest ${schemaContestInfo.name}"}
                        statewideInput.matchCanonicalCandidate(county, canonicalContest, exportCandidate)
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

        // data class ExportCardStyle(val name: String, val contests: Set<Int>, var count: Int = 0)
        // but the ids are export internal contest Ids; we need map export internal contestIds -> rlauxe ContestId
        // which is what we just built !!
        // turn those into CardStyle

        cardStyles = cardsFromManifest.getCountyStyles().map { cvrCardStyle ->
            val canonicalContestIdSet = convertExportCardStyleToCanonical(cvrCardStyle)
            val cardStyle = CardStyle("$county-${cvrCardStyle.cardType}", cardStyleId++, canonicalContestIdSet.toIntArray(), true)
            cardStyle.ncards = cvrCardStyle.ncards
            Pair(canonicalContestIdSet, cardStyle)
        }.toMap()

        /* one for each redacted group // obsolete
        redactedPools = corlaRawCvrs.redactedGroups().map { group ->
            val contestTabs = convertToContestTabulation(group)
            val cleanupName = truncateCommas(group.groupName)
            CardPool("$county-${cleanupName}.Redacted", cardStyleId++, true, infos, contestTabs, group.minCards())
        } */
        print("")
    }

    fun convertContestId(schemaContestId: Int): Int {
        return schemaToCanonLookup[schemaContestId]?.canonContestId ?: -1
    }

    // return corresponding contest Ids in canonical
    fun convertExportCardStyleToCanonical(exportCardStyle: CvrCardStyle): Set<Int> {
        val convert = mutableSetOf<Int>()
        exportCardStyle.contestIds.forEach { exportContestId ->
            val lookup = schemaToCanonLookup[exportContestId]
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
            val lookup = schemaToCanonLookup[exportContestId]
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
            val lookup = schemaToCanonLookup[contestVote.contestId]
            if (lookup != null) {
                val cannonCandidateIds = contestVote.votedFor.map { lookup.candLookup[it] }.filter { it >= 0 }
                cvrb.replaceContestVotes(lookup.canonContestId, cannonCandidateIds.toIntArray() )
            } else {
                // the contest was removed from canonical contests
                logger.debug{"cant find exportToCanonLookup[${contestVote.contestId}] in county '$county'"}
            }
        }
        if (visit != null) visit(cvrb)
        return cvrb.build()
    }

    // convert local tabs to canonical tabs
    fun convertTabs(tabs: Map<Int, ContestTabulation>): Map<Int, ContestTabulation> {
        // have to map both contestId and candVotes
        // contestVotes = mutableMapOf<Int, MutableMap<Int, Int>>
        val canonTabs = mutableMapOf<Int, ContestTabulation>()
        tabs.forEach { (contestId: Int, contestTab: ContestTabulation) ->
            val contestName = schemaContestName[contestId]!!
            val canonicalContest = statewideInput.matchCanonicalContest(county, contestName)!!
            val info = infosByName[canonicalContest.contestName]!!

            val nz = contestTab.votes.values.sum()  // skip contests with no votes
            val lookup = schemaToCanonLookup[contestId]
            if (nz > 0 && lookup != null) {
                val cannonCands: Map<Int, Int> = lookup.convertCands(contestTab.votes)
                val contestTabulation = ContestTabulation(infos[lookup.canonContestId]!!, cannonCands, contestTab.ncards())
                canonTabs[lookup.canonContestId] = contestTabulation
            }
        }
        return canonTabs
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
            val canonicalContest = statewideInput.matchCanonicalContest(county, contestName)!!
            val info = infosByName[canonicalContest.contestName]!!

            // TODO where to get ncards ?????
            val contestTabulation = contestVotes.makeContestTabulationCorla(info, canonicalContest, 0)
            canonTabs[info.id] = contestTabulation
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
        contestVotes.votedFor.forEach {
            val newCandId = candLookup[it]
            if (newCandId >= 0 ) result.add(newCandId)
        }
        return result
    }
}

/////////////////////////////////////////////////////////////////////////
// make schema specific ContestInfo from export.schema.contests; uses local contestId and candidateId
// keep seperate from ContestInfo to avoid confusion
data class CorlaContestInfo(
    val name: String,
    val id: Int,
    val candidateNames: Map<String, Int>,
    val isIrv: Boolean,
    val nwinners: Int) {
        val candidateIdToName: Map<Int, String> = candidateNames.entries.associate {(k,v) -> v to k }
}

// data class ContestInfo(
//    val name: String,
//    val id: Int,
//    val candidateNames: Map<String, Int>, // candidate name -> candidate id
//    val choiceFunction: SocialChoiceFunction,
//    val nwinners: Int = 1,              // max number of winners; for Dhondt == nseats; for RUNOFF, may be 1 or 2
//    val voteForN: Int = nwinners,       // how many votes can a user cast in this contest?
//    val minFraction: Double? = null,    // used in threshold, dhondt, runoff
//)

fun CorlaCountyCvrsIF.makeContestInfo(): List<CorlaContestInfo> {
    val columns = this.schema.columns

    return this.schema.contests.map { exportContest ->

        val candidateMap = if (!exportContest.isIRV) {
            val candidateMap1 = mutableMapOf<String, Int>()
            var candIdx = 0
            for (col in exportContest.startCol..exportContest.startCol + exportContest.ncols - 1) {
                if (!isWriteIn(columns[col].choiceName)) { // remove write-ins
                    candidateMap1[columns[col].choiceName] = candIdx
                }
                candIdx++
            }
            candidateMap1

        } else { // isIRV: there are ncand x ncand columns,
            val candidates = mutableListOf<String>()
            for (col in exportContest.startCol..exportContest.startCol + exportContest.ncols - 1) {
                candidates.add(columns[col].choiceName)
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

fun CorlaCountyCvrsIF.makeRealContestInfo(): Map<Int, ContestInfo> {
    return this.makeContestInfo().map { it ->
        ContestInfo(
            it.name, it.id, it.candidateNames,
            if (it.isIrv) SocialChoiceFunction.IRV else SocialChoiceFunction.PLURALITY,
            it.nwinners
        )
    }.associateBy { it.id }
}