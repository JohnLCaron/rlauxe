package org.cryptobiotic.rlauxe.boulder

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.dominion.DominionCvrExportCsv
import org.cryptobiotic.rlauxe.dominion.RedactedGroup
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.dominion.readDominionCvrExportCsv
import org.cryptobiotic.rlauxe.oneaudit.*
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.clearDirectory
import org.cryptobiotic.rlauxe.persist.csv.writeAuditableCardCsvFile
import org.cryptobiotic.rlauxe.persist.csv.writeBallotPoolCsvFile
import org.cryptobiotic.rlauxe.persist.json.writeAuditConfigJsonFile
import org.cryptobiotic.rlauxe.persist.json.writeContestsJsonFile
import org.cryptobiotic.rlauxe.util.*
import kotlin.collections.map
import kotlin.collections.set
import kotlin.io.path.Path
import kotlin.math.max

private val logger = KotlinLogging.logger("BoulderElectionOA")

// Use OneAudit, redacted ballots are in pools. use redacted vote counts for pools' assort averages.
// No redacted CVRs. Cant do IRV.
open class BoulderElectionOA(
    export: DominionCvrExportCsv,
    sovo: BoulderStatementOfVotes,
    quiet: Boolean = true,
): BoulderElection(export, sovo, quiet) {

    val cardPools: List<CardPoolWithBallotStyle> = convertRedactedToCardPool() // convertRedactedToCardPoolPaired(export.redacted, infoMap) // convertRedactedToCardPool2()
    val oaContests: Map<Int, OneAuditContestBoulder> = makeOAContests().associate { it.info.id to it}

    init {
        // val cardPoolMap = cardPools.associateBy { it.poolId }
        oaContests.values.forEach { it.adjustPoolInfo(cardPools)}

        // first do contest 0, since it has the fewest undervotes
        val oaContest0 = oaContests[0]!!
        distributeExpectedOvervotes(oaContest0, cardPools)

        // card B
        val oaContest63 = oaContests[63]!!
        distributeExpectedOvervotes(oaContest63, cardPools)
        val totalRedactedBallots = cardPools.sumOf { it.ncards() }
        logger.info { "number of redacted ballots = $totalRedactedBallots in ${cardPools.size} cardPools"}
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
        val countCvrVotes = countCvrVotes()
        val countRedactedVotes = countRedactedVotes()

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

    // write cards TODO add phantoms

    val cards = createSortedCardsFromPools(election.cvrs, election.cardPools, auditConfig.seed)
    writeAuditableCardCsvFile(cards, publisher.cardsCsvFile())
    logger.info{"write ${cards.size} cvrs to ${publisher.cardsCsvFile()}"}

    val contestsUA= election.makeContestsUA(auditConfig.hasStyles)
    addOAClcaAssortersFromMargin(contestsUA as List<OAContestUnderAudit>, election.cardPools.associate { it.poolId to it })

    checkContestsCorrectlyFormed(auditConfig, contestsUA)
    checkContestsWithCvrs(contestsUA, CvrIteratorAdapter(cards.iterator()), ballotPools=ballotPools, show = false)
    checkVotesVsSovo(contestsUA.map { it.contest as Contest}, sovo, mustAgree = false)

    // write contests
    writeContestsJsonFile(contestsUA, publisher.contestsFile())
    logger.info{"write ${contestsUA.size} contests to ${publisher.contestsFile()}"}
    logger.info{"took = $stopwatch\n"}
}
