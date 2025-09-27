package org.cryptobiotic.rlauxe.boulder

import org.cryptobiotic.rlauxe.dominion.DominionCvrExportCsv
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.dominion.RedactedGroup
import org.cryptobiotic.rlauxe.dominion.readDominionCvrExportCsv
import org.cryptobiotic.rlauxe.oneaudit.*
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.csv.writeAuditableCardCsvFile
import org.cryptobiotic.rlauxe.persist.json.writeAuditConfigJsonFile
import org.cryptobiotic.rlauxe.persist.json.writeContestsJsonFile
import org.cryptobiotic.rlauxe.raire.RaireContestUnderAudit
import org.cryptobiotic.rlauxe.util.*

// UseOneAudit, redacted ballots are in pools. simulate CVRS out of redacted votes for use as the MVRs.
// Cant do IRV
class BoulderElectionOAsim(
    export: DominionCvrExportCsv,
    sovo: BoulderStatementOfVotes,
    quiet: Boolean = true,
): BoulderElection(export,sovo, quiet)
{
    val oaContests = makeOAContests().associate { it.info.id to it}
    val cardPools: List<CardPool> = convertRedactedToCardPools()
    val redactedCvrs = makeRedactedCvrs()
    val allCvrs = cvrs + redactedCvrs // TODO could be CvrExport ??

    fun makeOAContests(): List<OneAuditContest> {
        val countCvrVotes = countCvrVotes()
        val countRedactedVotes = countRedactedVotes()

        val oaContests = mutableListOf<OneAuditContest>()
        infoList.forEach { info ->
            val sovoContest = sovo.contests.find { it.contestTitle == info.name }
            if (sovoContest != null) {
                val oaContest = OneAuditContest(info, sovoContest, countCvrVotes[info.id]!!, countRedactedVotes[info.id]!!)
                oaContest.allocateUndervotes(export.redacted)
                oaContests.add( oaContest)
            }
            else println("*** cant find '${info.name}' in BoulderStatementOfVotes")
        }
        return oaContests
    }

    // first call makeOAContests(), to have oaContest.undervotes
    fun convertRedactedToCardPools(): List<CardPool> {
        val result = mutableListOf<CardPool>()

        export.redacted.forEachIndexed { redactedIdx, redacted : RedactedGroup ->
            // each group becomes a pool
            val cardPool = CardPool(redacted.ballotType, redactedIdx, emptySet(), infoMap)

            val tabs = cardPool.contestTabulations  // contestId -> candidateId -> nvotes
            redacted.contestVotes.forEach { (contestId, candidateCounts) ->
                val info = infoMap[contestId]!!
                val tab = ContestTabulation(info.voteForN)
                tabs[contestId] = tab
                candidateCounts.forEach { (cand, vote) -> tab.addVote(cand, vote)}

                //  redVotes = redacted.votes.map { it.value }.sum()
                val redVotes = candidateCounts.map { it.value }.sum()
                //  redNcards = (redVotes + redUndervotes) / info.voteForN
                val oaContest = oaContests[contestId]!!

                // TODO using allocated missing undervotes across pools: In a real audit, this must be provided.
                tab.ncards = (redVotes + oaContest.allocUndervotes[redacted.ballotType]!!) / info.voteForN
            }
            result.add(cardPool)
        }
        return result
    }

    override fun makeRedactedCvrs(show: Boolean) : List<Cvr> { // contestId -> candidateId -> nvotes
        val rcvrs = mutableListOf<Cvr>()
        cardPools.forEach { cardPool ->
            rcvrs.addAll(makeRedactedCvrs(cardPool, show))
        }

        val voteForN = oaContests.mapValues { it.value.info.voteForN }
        val rcvrTabs = tabulateCvrs(rcvrs.iterator(), voteForN).toSortedMap()
        rcvrTabs.forEach { contestId, contestTab ->
            val oaContest: OneAuditContest = oaContests[contestId]!!
            val redUndervotes = oaContest.redUndervotes
            if (show) {
                println("contestId=${contestId}")
                println("  redacted= ${oaContest.red.votes}")
                println("  contestTab=${contestTab.votes.toSortedMap()}")
                println("  oaContest.undervotes= ${redUndervotes} == contestTab.undervotes = ${contestTab.undervotes}")
                println()
            }
            require(checkEquivilentVotes(oaContest.red.votes, contestTab.votes))
            if (voteForN[contestId] == 1) require(redUndervotes == contestTab.undervotes) // TODO
        }

        return rcvrs
    }

    // the redacted Cvrs simulate the real CVRS that are in the pools, for testing and estimation
    // for a real OneAudit, only the pool averages are used. CLCA can only be used for testing, not for a real audit.
    // we could use CardPool instead of redacted, would be more resusable
    private fun makeRedactedCvrs(cardPool: CardPool, show: Boolean) : List<Cvr> { // contestId -> candidateId -> nvotes

        val contestVotes = mutableMapOf<Int, VotesAndUndervotes>() // contestId -> VotesAndUndervotes
        cardPool.contestTabulations.forEach { (contestId, contestTab) ->
            val oaContest: OneAuditContest = oaContests[contestId]!!
            val underVotes = oaContest.allocUndervotes[cardPool.poolName]!!
            contestVotes[contestId] = VotesAndUndervotes(contestTab.votes, underVotes, oaContest.info.voteForN)
        }

        val cvrs = makeVunderCvrs(contestVotes, poolId = cardPool.poolId) // TODO test

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

        val voteForN = oaContests.mapValues { it.value.info.voteForN }
        val cvrTab = tabulateCvrs(cvrs.iterator(), voteForN).toSortedMap()
        cvrTab.forEach { contestId, contestTab ->
            val oaContest: OneAuditContest = oaContests[contestId]!!
            if (show) {
                println("contestId=${contestId} group=${cardPool.poolName}")
                println("  redacted= ${contestTab.votes[contestId]}")
                println("  oaContest.undervotes= ${oaContest.allocUndervotes[cardPool.poolName]} == ${contestTab.undervotes}")
                println("  contestTab=$contestTab")
                println()
            }
            require(checkEquivilentVotes(cardPool.contestTabulations[contestId]!!.votes, contestTab.votes))
        }

        return cvrs
    }

    fun makeContestsUA(): Pair<List<Contest>, List<RaireContestUnderAudit>> {
        if (!quiet) println("ncontests with info = ${infoList.size}")

        val voteForN = oaContests.mapValues { it.value.info.voteForN }
        val allTab = tabulateCvrs(allCvrs.iterator(), voteForN).toSortedMap()

        val regContests = infoList.filter { it.choiceFunction != SocialChoiceFunction.IRV }.map { info ->
            val contestTab = allTab[info.id]!!
            val useNc = oaContests[info.id]?.nballots() ?: contestTab.ncards
            val useNcast = contestTab.ncards

            val votesIn = contestTab.votes.filter { info.candidateIds.contains(it.key) } // remove Write-Ins
            Contest(info, votesIn, useNc, useNcast)
        }

        if (!quiet) {
            println("Regular contests (No IRV)) = ${regContests.size}")
            regContests.forEach { contest ->
                println(contest.show2())
            }
        }
        return Pair(regContests, emptyList()) // no IRV contests
    }

}

////////////////////////////////////////////////////////////////////
// Create a OneAudit where pools are from the redacted cvrs
fun createBoulderElectionOAsim(
    cvrExportFile: String,
    sovoFile: String,
    auditDir: String,
    riskLimit: Double = 0.03,
    minRecountMargin: Double = .01,
    auditConfigIn: AuditConfig? = null)
{
    //  TODO clearDirectory(Path.of(auditDir))
    val stopwatch = Stopwatch()

    val variation = if (sovoFile.contains("2024")) "Boulder2024" else "Boulder2023"
    val sovo = readBoulderStatementOfVotes(sovoFile, variation)

    println("readDominionCvrExport $cvrExportFile")
    val export: DominionCvrExportCsv = readDominionCvrExportCsv(cvrExportFile, "Boulder")
    val election = BoulderElectionOAsim(export, sovo)

    val rcvrVotes: Map<Int, Map<Int, Int>> = tabulateVotesFromCvrs(election.redactedCvrs.iterator())
    println("added ${election.redactedCvrs.size} redacted cvrs with ${rcvrVotes.values.sumOf { it.values.sum() }} total votes")

    val (contests, irvContests) = election.makeContestsUA()

    val publisher = Publisher(auditDir)
    val auditConfig = auditConfigIn ?: AuditConfig(
        AuditType.ONEAUDIT, hasStyles=true, riskLimit=riskLimit, sampleLimit=20000, minRecountMargin=minRecountMargin, nsimEst=10,
        oaConfig = OneAuditConfig(OneAuditStrategyType.optimalComparison, useFirst = true)
    )
    writeAuditConfigJsonFile(auditConfig, publisher.auditConfigFile())

    val cards = createSortedCards(election.allCvrs, auditConfig.seed)
    writeAuditableCardCsvFile(cards, publisher.cardsCsvFile())
    println("   writeCvrsCvsFile ${publisher.cardsCsvFile()} cvrs = ${election.allCvrs.size}")

    /////////////////
    // TODO attach (abstraction of) OneAuditContest ?
    val contestsUA = contests.map {
        OAContestUnderAudit(it, auditConfig.hasStyles)
    }
    addOAClcaAssorters(contestsUA, CvrIteratorAdapter(cards.iterator()), election.cardPools.associate { it.poolId to it })

    checkContestsCorrectlyFormed(auditConfig, contestsUA)
    checkContestsWithCvrs(contestsUA, CvrIteratorAdapter(cards.iterator()), show = false)
    checkVotesVsSovo(contests, sovo)

    writeContestsJsonFile(contestsUA + irvContests, publisher.contestsFile())
    println("   writeContestsJsonFile ${publisher.contestsFile()}")
    println("took = $stopwatch\n")
}