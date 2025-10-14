package org.cryptobiotic.rlauxe.boulder

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.dominion.DominionCvrExportCsv
import org.cryptobiotic.rlauxe.dominion.RedactedGroup
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.dominion.ContestVotes
import org.cryptobiotic.rlauxe.dominion.readDominionCvrExportCsv
import org.cryptobiotic.rlauxe.estimate.makePhantomCvrs
import org.cryptobiotic.rlauxe.oneaudit.*
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.clearDirectory
import org.cryptobiotic.rlauxe.persist.csv.writeAuditableCardCsvFile
import org.cryptobiotic.rlauxe.persist.csv.writeBallotPoolCsvFile
import org.cryptobiotic.rlauxe.persist.json.writeAuditConfigJsonFile
import org.cryptobiotic.rlauxe.persist.json.writeContestsJsonFile
import org.cryptobiotic.rlauxe.util.*
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach
import kotlin.collections.get
import kotlin.collections.map
import kotlin.collections.set
import kotlin.io.path.Path
import kotlin.math.max

private val logger = KotlinLogging.logger("BoulderElectionOA")

// Use OneAudit, redacted ballots are in pools. use redacted vote counts for pools' assort averages.
// No redacted CVRs. Cant do IRV.
open class BoulderElectionOA(
    val export: DominionCvrExportCsv,
    val sovo: BoulderStatementOfVotes,
    val quiet: Boolean = true,
) {
    val cvrs: List<Cvr> = export.cvrs.map { it.convert() }
    val infoList = makeContestInfo().sortedBy{ it.id }
    val infoMap = infoList.associateBy { it.id }

    val countCvrVotes = countCvrVotes()
    val countRedactedVotes = countRedactedVotes() // wrong
    val oaContests: Map<Int, OneAuditContestBoulder> = makeOAContests().associate { it.info.id to it}

    val cardPools: List<CardPoolWithBallotStyle> = convertRedactedToCardPool() // convertRedactedToCardPoolPaired(export.redacted, infoMap) // convertRedactedToCardPool2()

    init {
        oaContests.values.forEach { it.adjustPoolInfo(cardPools)}

        // first even up with contest 0, since it has the fewest undervotes
        val oaContest0 = oaContests[0]!!
        distributeExpectedOvervotes(oaContest0, cardPools)
        oaContests.values.forEach { it.adjustPoolInfo(cardPools)}

        // contest 0 has fewest undervotes for card Bs
        val oaContest63 = oaContests[63]!!
        distributeExpectedOvervotes(oaContest63, cardPools)
        oaContests.values.forEach { it.adjustPoolInfo(cardPools)}

        val totalRedactedBallots = cardPools.sumOf { it.ncards() }
        logger.info { "number of redacted ballots = $totalRedactedBallots in ${cardPools.size} cardPools"}
    }

    // make ContestInfo from BoulderStatementOfVotes, and matching export.schema.contests
    fun makeContestInfo(): List<ContestInfo> {
        val columns = export.schema.columns

        return sovo.contests.map { sovoContest ->
            val exportContest = export.schema.contests.find { it.contestName.startsWith(sovoContest.contestTitle) }!!

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
            val (name, nwinners) = if (exportContest.isIRV) parseIrvContestName(exportContest.contestName) else parseContestName(exportContest.contestName)
            ContestInfo( name, exportContest.contestIdx, candidateMap, choiceFunction, nwinners)
        }
    }

    private fun convertRedactedToCardPool(): List<CardPoolWithBallotStyle> {
        return export.redacted.mapIndexed { redactedIdx, redacted: RedactedGroup ->
            // each group becomes a pool
            // correct bug adding contest 12 to pool 06
            val useContestVotes = if (redacted.ballotType.startsWith("06")) {
                    redacted.contestVotes.filter{ (key, value) -> key != 12 }
                } else redacted.contestVotes

            CardPoolWithBallotStyle(cleanCsvString(redacted.ballotType), redactedIdx, useContestVotes.toMap(), infoMap)
        }
    }

    fun makeOAContests(): List<OneAuditContestBoulder> {
        val oa2Contests = mutableListOf<OneAuditContestBoulder>()
        infoList.forEach { info ->
            val sovoContest = sovo.contests.find { it.contestTitle == info.name }
            if (sovoContest != null) {
                oa2Contests.add( OneAuditContestBoulder(info, sovoContest, countCvrVotes[info.id]!!, countRedactedVotes[info.id]!!))
            }
            else logger.warn{"*** cant find contest '${info.name}' in BoulderStatementOfVotes"}
        }

        return oa2Contests
    }

    fun countVotes() : Map<Int, ContestTabulation> { // contestId -> candidateId -> nvotes
        val cvrVotes =  countCvrVotes()
        val redVotes =  countRedactedVotes()
        val allVotes = mutableMapOf<Int, ContestTabulation>()
        allVotes.sumContestTabulations(cvrVotes)
        allVotes.sumContestTabulations(redVotes)
        return allVotes
    }

    fun countCvrVotes() : Map<Int, ContestTabulation> { // contestId -> candidateId -> nvotes
        val votes = mutableMapOf<Int, ContestTabulation>()

        export.cvrs.forEach { cvr ->
            cvr.contestVotes.forEach { contestVote: ContestVotes ->
                val tab = votes.getOrPut(contestVote.contestId) { ContestTabulation(infoMap[contestVote.contestId]!!) }
                tab.addVotes(contestVote.candVotes.toIntArray())
            }
        }
        return votes
    }

    fun countRedactedVotes() : Map<Int, ContestTabulation> { // contestId -> candidateId -> nvotes
        val votes = mutableMapOf<Int, ContestTabulation>()

        export.redacted.forEach { redacted: RedactedGroup ->
            redacted.contestVotes.entries.forEach { (contestId, contestVote) ->
                val tab = votes.getOrPut(contestId) { ContestTabulation(infoMap[contestId]!!) }
                contestVote.forEach { (cand, vote) -> tab.addVote(cand, vote) }
                val info = infoMap[contestId]

                // TODO approx
                tab.ncards += contestVote.map { it.value }.sum() / info!!.voteForN // wrong, dont use
            }
        }
        return votes
    }

    open fun makeContestsUA(hasStyles: Boolean): List<ContestUnderAudit> {
        if (!quiet) println("ncontests with info = ${infoList.size}")

        val regContests = infoList.filter { it.choiceFunction != SocialChoiceFunction.IRV }.map { info ->
            val oaContest = oaContests[info.id]!!
            val candVotes = oaContest.candVoteTotals().filter { info.candidateIds.contains(it.key) } // remove Write-Ins
            val ncards = oaContest.sumAllCards()
            val useNc = max( ncards, oaContest.Nc())
            val contest = Contest(info, candVotes, useNc, ncards)
            info.metadata["PoolPct"] = (100.0 * oaContest.poolTotalCards() / useNc).toInt()
            OAContestUnderAudit(contest, hasStyles)
        }

        val checkContest = 20
        println("^^^ $checkContest      countCvrVotes = ${countCvrVotes[checkContest]}")
        println("^^^ $checkContest countRedactedVotes = ${countRedactedVotes[checkContest]}")
        val poolSum = ContestTabulation(infoMap[checkContest]!!)
        cardPools.filter { it.contains(checkContest)}.forEach {
            val votes = it.voteTotals[checkContest]!!
            votes.forEach { (candId, nvotes) -> poolSum.addVote(candId, nvotes) }
            poolSum.ncards += it.ncards()
            poolSum.undervotes += it.ncards() * (infoMap[checkContest]?.voteForN ?: 1) - votes.map { it.value }.sum()
        }
        println("^^^ $checkContest pooledtab = ${poolSum}")

        return regContests
    }

}

////////////////////////////////////////////////////////////////////
// Create a OneAudit where pools are from the redacted cvrs, use pool vote totals for assort average
fun createBoulderElectionOA(
    cvrExportFile: String,
    sovoFile: String,
    auditDir: String,
    riskLimit: Double = 0.03,
    minRecountMargin: Double = .005,
    auditConfigIn: AuditConfig? = null,
    clear: Boolean = true)
{
    if (clear) clearDirectory(Path(auditDir))
    val stopwatch = Stopwatch()

    val variation = if (sovoFile.contains("2024")) "Boulder2024" else "Boulder2023"
    val sovo = readBoulderStatementOfVotes(sovoFile, variation)

    println("readDominionCvrExport $cvrExportFile")
    val export: DominionCvrExportCsv = readDominionCvrExportCsv(cvrExportFile, "Boulder")
    val election = BoulderElectionOA(export, sovo)

    val publisher = Publisher(auditDir)
    val auditConfig = auditConfigIn ?: AuditConfig(
        AuditType.ONEAUDIT, hasStyles=true, riskLimit=riskLimit, sampleLimit=20000, minRecountMargin=minRecountMargin, nsimEst=10,
        oaConfig = OneAuditConfig(OneAuditStrategyType.optimalComparison, useFirst = true)
    )
    writeAuditConfigJsonFile(auditConfig, publisher.auditConfigFile())

    // write ballot pools
    val ballotPools = election.cardPools.map { it.toBallotPools() }.flatten()
    writeBallotPoolCsvFile(ballotPools, publisher.ballotPoolsFile())
    logger.info{"write ${ballotPools.size} ballotPools to ${publisher.ballotPoolsFile()}"}

    val contestsUA= election.makeContestsUA(auditConfig.hasStyles)
    addOAClcaAssortersFromMargin(contestsUA as List<OAContestUnderAudit>, election.cardPools.associate { it.poolId to it })

    val phantoms = makePhantomCvrs(contestsUA.map { it.contest } )
    val allCvrs =  election.cvrs + phantoms

    val cards = createSortedCardsFromPools(allCvrs, election.cardPools, auditConfig.seed)
    writeAuditableCardCsvFile(cards, publisher.cardsCsvFile())
    logger.info{"write ${cards.size} cvrs to ${publisher.cardsCsvFile()}"}

    checkContestsCorrectlyFormed(auditConfig, contestsUA)
    checkContestsWithCvrs(contestsUA, CvrIteratorAdapter(cards.iterator()), ballotPools=ballotPools, show = false)
    checkVotesVsSovo(contestsUA.map { it.contest as Contest}, sovo, mustAgree = false)

    // write contests
    writeContestsJsonFile(contestsUA, publisher.contestsFile())
    logger.info{"write ${contestsUA.size} contests to ${publisher.contestsFile()}"}
    logger.info{"took = $stopwatch\n"}
}

fun parseContestName(name: String) : Pair<String, Int> {
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
