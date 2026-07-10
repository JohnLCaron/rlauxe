package org.cryptobiotic.rlauxe.auditcenter

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.dominion.ContestVotes
import org.cryptobiotic.rlauxe.dominion.DominionConverter
import org.cryptobiotic.rlauxe.dominion.DominionCvrExportCsv
import org.cryptobiotic.rlauxe.dominion.readCvrExportsFromFile
import org.cryptobiotic.rlauxe.util.*
import kotlin.collections.map
import kotlin.collections.plus
import kotlin.collections.set

private val logger = KotlinLogging.logger("CreateCountyElection")
private val debugUndervotes = false
private val showCardStyles = true

// obsolete
// How does this differ from CreateColoradoElectionWithCvrs ??
// make ContestInfo from export.schema.contests, so can only be for one county
class CreateCountyElection(
    val county: String,
    val coloradoInput: ColoradoInput,
    val auditType: AuditType,
    val dominionExport: DominionCvrExportCsv,
    val mvrSource: MvrSource = MvrSource.testPrivateMvrs,
    val hasStyle: Boolean = true,
): ElectionBuilder {
    val contests = makeContests()
    val infoList = makeContestInfo().sortedBy{ it.id }
    val infos = infoList.associateBy { it.id }
    val infoByName = infoList.associateBy { it.name }

    val contestTabs: Map<Int, ContestTabulation> = countCvrVotes()
    val contestBuilders: Map<Int, ContestBuilder> = makeContestBuilders().associate { it.info.id to it }
    val ncards: Int

    val contestsUA : List<ContestWithAssertions>
    val simulatedCvrs: List<AuditableCard>  // redacted cvrs
    val allCards: List<AuditableCard>  // redacted cvrs
    val cardStyles: List<StyleIF>

    init {
        // we need to know the diluted Nb before we can create the UAs
        simulatedCvrs = emptyList() // makeRedactedCvrs()

        val dominionConverter = DominionConverter(county, dominionExport, infoByName, coloradoInput)
        val exportCards = dominionExport.cvrs.map { dominionConverter.convertToCard(it) }
        cardStyles = dominionConverter.cardStyles.values.toList()

        val phantoms = makePhantomCards(contests, 0)
        allCards = exportCards + simulatedCvrs + phantoms // TODO leave out phantoms ??


        this.ncards = allCards.size

        val cardIter = Closer (allCards.iterator() )
        val cardTabulation = CardTabulation(cardIter, infos) { }
        val npopMap = cardTabulation.tabs.mapValues { it.value.ncards() }

        // val (npopMap, count2) = tabulateNpopsFromCards(cardIter, infos.values.toList()) // TODO check this, seems wrong
        contestsUA = ContestWithAssertions.make(contests, npopMap, true, hasStyle)
    }

    fun makeCardStyles(): Map<Set<Int>, CardStyle> {
        val result = mutableMapOf<Set<Int>, CardStyle>()
        dominionExport.exportCardStyles.forEachIndexed { idx, bs ->
            result[bs.contests] = CardStyle(bs.name, idx + 1, bs.contests.toIntArray(), true)
        }
        return result
    }

    // make ContestInfo from export.schema.contests
    fun makeContestInfo(): List<ContestInfo> {
        val columns = dominionExport.schema.columns

        return dominionExport.schema.contests.map { exportContest ->

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

    data class ContestBuilder(val info: ContestInfo, val tab: ContestTabulation)

    fun makeContestBuilders(): List<ContestBuilder> {
        return infoList.map { info ->
            val contestTab = contestTabs[info.id]!!
            ContestBuilder(info, contestTab)
        }
    }

    fun countCvrVotes() : Map<Int, ContestTabulation> { // contestId -> candidateId -> nvotes
        val votes = mutableMapOf<Int, ContestTabulation>()

        // TODO do these include undervotes ??
        dominionExport.cvrs.forEach { cvr ->
            cvr.contestVotes.forEach { contestVote: ContestVotes ->
                val tab = votes.getOrPut(contestVote.contestId) { ContestTabulation(infos[contestVote.contestId]!!) }
                tab.addVotes(contestVote.candVotes.toIntArray(), phantom=false)
            }
        }
        return votes
    }

    fun makeContests(): List<ContestIF> {
        return dominionExport.schema.contests.map { exportContest ->
            val cb = contestBuilders[exportContest.contestIdx]!!
            val ncards = cb.tab.ncards()
            // val useNc = max( ncards, oaContest.Nc())

            Contest(cb.info, cb.tab.votes, ncards, ncards)
        }
    }

    override fun electionInfo() = ElectionInfo("CountyElection$auditType", auditType, ncards(), contestsUA.size,
        true, mvrSource=mvrSource)
    override fun contestsUA() = contestsUA
    override fun cardStyles() = cardStyles
    override fun cardPools() = null
    override fun unsortedMvrsInternal() = allCards // mvrsToAuditableCardsListM(allCvrs, cardPools())
    override fun unsortedMvrsExternal() = null

    override fun cards() = Closer( allCards.iterator() )
    override fun ncards() = ncards
}

////////////////////////////////////////////////////////////////////
// Clca: create simulated cvrs for the redacted groups, for a full CLCA audit with hasStyles=true.
// OA: Create a OneAudit where pools are from the redacted cvrs.
fun createCountyElection(
    county: String,
    coloradoInput: ColoradoInput,
    cvrExportFile: String,
    topdir: String,
    creation: AuditCreationConfig,
    roundConfig: AuditRoundConfig,
    mvrSource: MvrSource = MvrSource.testPrivateMvrs,
    startFirstRound: Boolean = true,
) {

    val stopwatch = Stopwatch()
    val export: DominionCvrExportCsv = readCvrExportsFromFile(cvrExportFile)

    val election = CreateCountyElection(county, coloradoInput, creation.auditType, export, mvrSource = mvrSource,
            hasStyle = roundConfig.sampling.sampling == Sampling.consistent)

    createElectionRecord(election, topdir = topdir)
    println("createCountyElection for $county took $stopwatch")

    val config = Config(election.electionInfo(), creation, roundConfig)
    createAuditRecord(config, election, topdir = topdir)

    if (startFirstRound) {
        val result = startFirstRound(topdir)
        if (result.isErr) logger.error { result.toString() }
    }
    logger.info{"startFirstRound took $stopwatch"}
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


