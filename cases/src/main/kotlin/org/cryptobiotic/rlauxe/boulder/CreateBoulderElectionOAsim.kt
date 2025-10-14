package org.cryptobiotic.rlauxe.boulder

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.dominion.DominionCvrExportCsv
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.dominion.readDominionCvrExportCsv
import org.cryptobiotic.rlauxe.estimate.makePhantomCvrs
import org.cryptobiotic.rlauxe.oneaudit.CardPoolWithBallotStyle
import org.cryptobiotic.rlauxe.oneaudit.OAContestUnderAudit
import org.cryptobiotic.rlauxe.oneaudit.addOAClcaAssortersFromMargin
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.clearDirectory
import org.cryptobiotic.rlauxe.persist.csv.writeAuditableCardCsvFile
import org.cryptobiotic.rlauxe.persist.csv.writeBallotPoolCsvFile
import org.cryptobiotic.rlauxe.persist.json.writeAuditConfigJsonFile
import org.cryptobiotic.rlauxe.persist.json.writeContestsJsonFile
import org.cryptobiotic.rlauxe.util.*
import kotlin.io.path.Path
import kotlin.math.max

private val logger = KotlinLogging.logger("BoulderElectionOAsim")

// UseOneAudit, redacted ballots are in pools. simulate CVRS out of redacted votes for use as the MVRs.
class BoulderElectionOAsim(
    export: DominionCvrExportCsv,
    sovo: BoulderStatementOfVotes,
    val clca: Boolean = true,
    quiet: Boolean = true,
): BoulderElectionOA(export, sovo, quiet)
{
    val redactedCvrs = makeRedactedCvrs()
    val allCvrs = cvrs + redactedCvrs

    fun makeRedactedCvrs(show: Boolean = false) : List<Cvr> { // contestId -> candidateId -> nvotes
        val rcvrs = mutableListOf<Cvr>()
        cardPools.forEach { cardPool ->
            rcvrs.addAll(makeRedactedCvrs(cardPool, show))
        }

        val infos = oaContests.mapValues { it.value.info }
        val rcvrTabs = tabulateCvrs(rcvrs.iterator(), infos).toSortedMap()
        rcvrTabs.forEach { contestId, contestTab ->
            val oaContest: OneAuditContestBoulder = oaContests[contestId]!!
            val redUndervotes = oaContest.redUndervotes
            if (show) {
                println("contestId=${contestId}")
                println("  redacted= ${oaContest.red.votes}")
                println("  contestTab=${contestTab.votes.toSortedMap()}")
                println("  oaContest.undervotes= ${redUndervotes} == contestTab.undervotes = ${contestTab.undervotes}")
                println()
            }
            require(checkEquivilentVotes(oaContest.red.votes, contestTab.votes))
            // if (voteForN[contestId] == 1) require(redUndervotes == contestTab.undervotes) // TODO
        }

        return rcvrs
    }

    // the redacted Cvrs simulate the real CVRS that are in the pools, for testing and estimation
    // for a real OneAudit, only the pool averages are used. CLCA can only be used for testing, not for a real audit.
    private fun makeRedactedCvrs(cardPool: CardPoolWithBallotStyle, show: Boolean) : List<Cvr> { // contestId -> candidateId -> nvotes

        val contestVotes = mutableMapOf<Int, VotesAndUndervotes>() // contestId -> VotesAndUndervotes
        cardPool.voteTotals.forEach { (contestId, candVotes) ->
            val oaContest: OneAuditContestBoulder = oaContests[contestId]!!
            val sumVotes = candVotes.map { it.value }.sum()
            val underVotes = cardPool.ncards() * oaContest.info.voteForN - sumVotes
            contestVotes[contestId] = VotesAndUndervotes(candVotes, underVotes, oaContest.info.voteForN)
        }

        val cvrs = makeVunderCvrs(contestVotes, cardPool.poolName, poolId = cardPool.poolId) // TODO test

        // check
        val tabVotes: Map<Int, Map<Int, Int>> = tabulateVotesFromCvrs(cvrs.iterator())
        contestVotes.forEach { (contestId, vunders) ->
            val tv = tabVotes[contestId] ?: emptyMap()
            if (!checkEquivilentVotes(vunders.candVotesSorted, tv)) {
                println("  contestId=${contestId}")
                println("  tabVotes=${tv}")
                println("  vunders= ${vunders.candVotesSorted}")
                require(checkEquivilentVotes(vunders.candVotesSorted, tv))
            }
        }

        val infos = oaContests.mapValues { it.value.info }
        val cvrTab = tabulateCvrs(cvrs.iterator(), infos).toSortedMap()
        cvrTab.forEach { contestId, contestTab ->
            val oaContest: OneAuditContestBoulder = oaContests[contestId]!!
            if (show) {
                println("contestId=${contestId} group=${cardPool.poolName}")
                println("  redacted= ${contestTab.votes[contestId]}")
                println("  oaContest.undervotes= ${oaContest.redVotes} == ${contestTab.undervotes}")
                println("  contestTab=$contestTab")
                println()
            }
            require(checkEquivilentVotes(cardPool.voteTotals[contestId]!!, contestTab.votes))
        }

        return cvrs
    }

    override fun makeContestsUA(hasStyles: Boolean): List<ContestUnderAudit> {
        if (!quiet) println("ncontests with info = ${infoList.size}")

        val regContests = infoList.filter { !it.isIrv }.map { info ->
            val oaContest = oaContests[info.id]!!
            val candVotes = oaContest.candVoteTotals().filter { info.candidateIds.contains(it.key) } // remove Write-Ins
            val ncards = oaContest.sumAllCards()
            val useNc = max( ncards, oaContest.Nc())
            val contest = Contest(info, candVotes, useNc, oaContest.sumAllCards())
            info.metadata["PoolPct"] = (100.0 * oaContest.poolTotalCards() / useNc).toInt()
            if (clca) ContestUnderAudit(contest, hasStyles) else OAContestUnderAudit(contest, hasStyles)
        }

        return regContests
    }

}

////////////////////////////////////////////////////////////////////
// Create a OneAudit where pools are from the redacted cvrs
fun createBoulderElectionOAsim(
    cvrExportFile: String,
    sovoFile: String,
    auditDir: String,
    clca: Boolean = false,
    riskLimit: Double = 0.03,
    minRecountMargin: Double = .005,
    auditConfigIn: AuditConfig? = null,
    clear: Boolean = true) {

    if (clear) clearDirectory(Path(auditDir))
    val stopwatch = Stopwatch()

    val variation = if (sovoFile.contains("2024")) "Boulder2024" else "Boulder2023"
    val sovo = readBoulderStatementOfVotes(sovoFile, variation)

    val export: DominionCvrExportCsv = readDominionCvrExportCsv(cvrExportFile, "Boulder")
    val election = BoulderElectionOAsim(export, sovo, clca = clca)

    val rcvrVotes: Map<Int, Map<Int, Int>> = tabulateVotesFromCvrs(election.redactedCvrs.iterator())
    logger.info { "added ${election.redactedCvrs.size} redacted cvrs with ${rcvrVotes.values.sumOf { it.values.sum() }} total votes" }

    val publisher = Publisher(auditDir)
    val auditConfig = if (auditConfigIn != null) auditConfigIn
    else if (clca) {
        AuditConfig(
            AuditType.CLCA,
            hasStyles = true,
            riskLimit = riskLimit,
            sampleLimit = 20000,
            minRecountMargin = minRecountMargin,
            nsimEst = 10,
            clcaConfig = ClcaConfig(ClcaStrategyType.optimalComparison)
        )
    } else {
        AuditConfig(
            AuditType.ONEAUDIT,
            hasStyles = true,
            riskLimit = riskLimit,
            sampleLimit = 20000,
            minRecountMargin = minRecountMargin,
            nsimEst = 10,
            oaConfig = OneAuditConfig(OneAuditStrategyType.optimalComparison, useFirst = true)
        )
    }
    writeAuditConfigJsonFile(auditConfig, publisher.auditConfigFile())

    // write ballot pools
    val ballotPools = election.cardPools.map { it.toBallotPools() }.flatten()
    writeBallotPoolCsvFile(ballotPools, publisher.ballotPoolsFile())
    logger.info{"write ${ballotPools.size} ballotPools to ${publisher.ballotPoolsFile()}"}

    // form contests
    val contestsUA = election.makeContestsUA(auditConfig.hasStyles)
    if (clca) {
        contestsUA.forEach { it.addClcaAssertionsFromReportedMargin() }
    } else {
        addOAClcaAssortersFromMargin(contestsUA as List<OAContestUnderAudit>, election.cardPools.associateBy { it.poolId })
    }

    val phantoms = makePhantomCvrs(contestsUA.map { it.contest} )
    val allCvrs =  election.allCvrs + phantoms

    val cards = createSortedCards(allCvrs, auditConfig.seed)
    writeAuditableCardCsvFile(cards, publisher.cardsCsvFile())
    logger.info{"write ${cards.size} cvrs to ${publisher.cardsCsvFile()}"}


    checkContestsCorrectlyFormed(auditConfig, contestsUA)
    checkContestsWithCvrs(contestsUA, CvrIteratorAdapter(cards.iterator()), show = false)
    checkVotesVsSovo(contestsUA.map { it.contest as Contest}, sovo, mustAgree = false)

    writeContestsJsonFile(contestsUA, publisher.contestsFile())
    logger.info{"write ${contestsUA.size} contests to ${publisher.contestsFile()}"}
    println("took = $stopwatch\n")
}