package org.cryptobiotic.rlauxe.boulder

import org.cryptobiotic.rlauxe.dominion.DominionCvrExport
import org.cryptobiotic.rlauxe.dominion.RedactedGroup
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.dominion.readDominionCvrExport
import org.cryptobiotic.rlauxe.oneaudit.*
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.clearDirectory
import org.cryptobiotic.rlauxe.persist.csv.writeAuditableCardCsvFile
import org.cryptobiotic.rlauxe.persist.json.writeAuditConfigJsonFile
import org.cryptobiotic.rlauxe.persist.json.writeContestsJsonFile
import org.cryptobiotic.rlauxe.util.*
import java.nio.file.Path
import kotlin.math.max

// using sovo just to define the contests. TODO: tests there are some votes
class BoulderElectionOneAuditFromCvrs(
    val export: DominionCvrExport,
    val sovo: BoulderStatementOfVotes,
    val includeCvrs: Boolean = true,
    val quiet: Boolean = false
) {
    val cvrs: List<Cvr> = export.cvrs.map { it.convert() }
    val infos = makeContestInfo().associateBy { it.id }
    val cvrCounts = cvrCounts() // contestId -> ContestTabulation
    // the pools have ncards reflecting the same pct undervotes as the cvrs
    val poolsByContest = makePoolsFromRedacted(cvrCounts) // contestId -> List<BallotPool>

    // make ContestInfo from BoulderStatementOfVotes, and matching export.schema.contests
    fun makeContestInfo(): List<ContestInfo> {
        val columns = export.schema.columns

        return sovo.contests.map { sovoContest ->
            val exportContest = export.schema.contests.find { it.contestName.startsWith(sovoContest.contestTitle) }!!

            val candidateMap = if (!exportContest.isIRV) {
                val candidateMap1 = mutableMapOf<String, Int>()
                var candIdx = 0
                for (col in exportContest.startCol..exportContest.startCol + exportContest.ncols - 1) {
                    // if (columns[col].choice != "Write-in") { // remove write-ins
                        candidateMap1[columns[col].choice] = candIdx
                    // }
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

    /* TODO
    fun makeContests(): List<OneAuditContest> {
        if (!quiet) println("ncontests with info = ${infos.size}")
        val sortedInfos = infos.values.sortedBy { it.id }

        // no IRV contests
        val oaContests = sortedInfos.filter { it.choiceFunction != SocialChoiceFunction.IRV }.map { info ->
            val contestTabulation: ContestTabulation? = cvrCounts[info.id]
            val sovContest = sovo.contests.find {
                it.contestTitle == info.name
            }
            if (sovContest == null) {
                println("*** cant find '${info.name}' in BoulderStatementOfVotes")
            }
            // remove Write-Ins
            // val votesIn = contestCount.votes.filter { info.candidateIds.contains(it.key) }

            val pools = poolsByContest[info.id]!!
            OneAuditContest.make(info,
                contestTabulation ?.votes ?: emptyMap(),
                contestTabulation ?.ncards ?: 0,
                pools ?: emptyList(),
                Nc = 0,
                Ncast = 0)// TODO
        }

        if (!quiet) {
            println("\nRegular contests (No IRV)) = ${oaContests.size}")
            oaContests.forEach { contest ->
                println(contest)
            }
        }

        return oaContests
    }

     */

    // make contest votes from the export.cvrs
    fun cvrCounts() : Map<Int, ContestTabulation> { // contestId -> candidateId -> nvotes
        val votes = mutableMapOf<Int, ContestTabulation>()

        if (includeCvrs) {
            export.cvrs.forEach { cvr ->
                cvr.contestVotes.forEach { contestVote ->
                    val tab = votes.getOrPut(contestVote.contestId) { ContestTabulation() }
                    tab.addVotes(contestVote.candVotes.toIntArray())
                }
            }
        }
        return votes
    }

    // each RedactedVotes is a pool with multiple contests in it.
    // convert to a Map<ContestId, List<BallotPool>>
    // use the undervotes pct in cvrCounts to add undervotes to the pools
    fun makePoolsFromRedacted(cvrCounts: Map<Int, ContestTabulation> ): Map<Int, List<BallotPool>> {
        val result = mutableMapOf<Int, MutableList<BallotPool>>()

        export.redacted.forEachIndexed { redactedCount, redacted ->
            // redacted.contestVotes: contestId -> candidateId -> nvotes
            redacted.contestVotes.forEach { (contestId, votes) ->
                val info = infos[contestId]
                if (info == null) {
                    println("Dont have contestId $contestId")
                } else {
                    val contestPools = result.getOrPut(contestId) { mutableListOf() }
                    votes.forEach { cand, vote ->
                        if (!info.candidateIds.contains(cand))
                            println("Contest ${contestId} missing candidate $cand vote=$vote")
                    }
                    // data class BallotPool(
                    //    val name: String,
                    //    val id: Int,
                    //    val contest:Int,
                    //    val ncards: Int,          // ncards for this contest in this pool
                    //    val votes: Map<Int, Int>, // candid -> nvotes // the diff from ncards tell you the undervotes

                    //                 undervotes = info.voteForN * (iNc - Np) - nvotes
                    val nvotes = votes.values.sum()
                    val contestTab = cvrCounts[contestId]!!

                    // use the same undervote pct as in the cvrs TODO bogus! They need to supply this value.
                    val undervotePct = contestTab.undervotePct(info.voteForN)
                    // undervotes = undervotesPct * ncards
                    // ncards = (undervotes + nvotes) / voteForN
                    // ncards = (undervotesPct * ncards + nvotes) / voteForN
                    // ncards (1 - undervotesPct / voteForN ) =  nvotes / voteForN
                    // ncards = (nvotes / voteForN ) / (1 - undervotesPct / voteForN )
                    // ncards = nvotes / (voteForN - undervotesPct)

                    val packedCards = roundToInt( nvotes / (info.voteForN - undervotePct))
                    val ncards = max(packedCards, votes.values.max()) // cant have less cards than votes
                    if (ncards > packedCards)
                        println("ncards > packedCards")

                    // TODO does Ballot Pool need Np ?
                    // use the index as the poolId
                    contestPools.add(BallotPool("pool$redactedCount", redactedCount, contestId, ncards, votes))
                }
            }
        }
        return result
    }

    fun makeRedactedCvrs(show: Boolean = false) : List<Cvr> { // contestId -> candidateId -> nvotes
        val rcvrs = mutableListOf<Cvr>()
        export.redacted.forEachIndexed { redactedIdx, redacted ->
            rcvrs.addAll(makeRedactedCvrs(redactedIdx, redacted, show))
        }
        return rcvrs
    }

    // make cvrs for one redactedGroup
    // A redactedGroup corresponds to one poolId; it will have an arbitrary number of contests in it
    fun makeRedactedCvrs(poolId: Int, redacted: RedactedGroup, show: Boolean) : List<Cvr> { // contestId -> candidateId -> nvotes

        if (show) {
            println("ballotStyle = ${redacted.ballotType}")
            redacted.contestVotes.forEach { (key, votes) ->
                println("  contest $key = ${votes.values.sum()}")
            }
            val expectedCvrs = redacted.contestVotes.values.map { it.values.sum() }.max()
            println("expectedCvrs= $expectedCvrs\n")
        }

        val contestVotes = mutableMapOf<Int, VotesAndUndervotes>()
        redacted.contestVotes.entries.forEach { (contestId, candidateCount) ->
            val ballotPool: BallotPool = poolsByContest[contestId]!!.find { it.poolId == poolId }!!
            require(candidateCount == ballotPool.votes)
            val info = infos[contestId]!!
            contestVotes[contestId] = ballotPool.votesAndUndervotes(info.voteForN)
        }

        val cvrs = makeVunderCvrs(contestVotes, poolId = poolId)
        // println("makeRedactedCvrs cvrs=${cvrs.size}")
        val tabVotes: Map<Int, Map<Int, Int>> = tabulateVotesFromCvrs(cvrs.iterator())

        contestVotes.forEach { (contestId, vunders) ->
            val tv = tabVotes[contestId] ?: emptyMap()
            if (!checkEquivilentVotes(vunders.candVotesSorted, tv)) {
                println("  tabVotes=${tv}")
                println("  vunders.candVotesSorted ${vunders.candVotesSorted}")
                require(checkEquivilentVotes(vunders.candVotesSorted, tv))
            }

            val tabsWith = tabulateVotesWithUndervotes(cvrs.iterator(), contestId, vunders.votes.size, vunders.voteForN).toSortedMap()
            if (!checkEquivilentVotes(vunders.votesAndUndervotes(), tabsWith)) {
                println("  tabsWith=${tabsWith}")
                println("  vunders.votesAndUndervotes()= ${vunders.votesAndUndervotes()}")
                require(checkEquivilentVotes(vunders.votesAndUndervotes(), tabsWith))
            }
        }
        return cvrs
    }
}

////////////////////////////////////////////////////////////////////
// Create a OneAudit where pools are from the redacted cvrs
/* Optionally only has pools (ie "batch level comparison audit") includeCvrs = false
fun createBoulderElectionOneAudit(
    cvrExportFile: String,
    sovoFile: String,
    auditDir: String,
    includeCvrs: Boolean = true,
    riskLimit: Double = 0.03,
    minRecountMargin: Double = .01,
    auditConfigIn: AuditConfig? = null) {

    val variation = if (sovoFile.contains("2024")) "Boulder2024" else "Boulder2023"
    val sovo = readBoulderStatementOfVotes(sovoFile, variation)

    clearDirectory(Path.of(auditDir))

    val stopwatch = Stopwatch()
    val export: DominionCvrExport = readDominionCvrExport(cvrExportFile, "Boulder")
    val boulderElection = BoulderElectionOneAuditFromCvrs(export, sovo, includeCvrs = includeCvrs)

    val contests: List<OneAuditContest> = boulderElection.makeContests()
    val publisher = Publisher(auditDir)
    val auditConfig = auditConfigIn ?: AuditConfig(
        AuditType.ONEAUDIT, hasStyles = true, riskLimit = riskLimit, minRecountMargin = minRecountMargin,
    )
    writeAuditConfigJsonFile(auditConfig, publisher.auditConfigFile())

    val redactedCvrs = boulderElection.makeRedactedCvrs()
    val rcvrVotes: Map<Int, Map<Int, Int>> = tabulateVotesFromCvrs(redactedCvrs.iterator())
    println("added ${redactedCvrs.size} redacted cvrs with ${rcvrVotes.values.sumOf { it.values.sum() }} total votes")
    // val allCvrs = electionFromCvrs.cvrs + redactedCvrs

    /////////////////
    val contestsUA: List<OAContestUnderAudit> = contests.map { it.makeContestUnderAudit() }

    checkContestsCorrectlyFormed(auditConfig, contestsUA)

    val allCvrs = if (includeCvrs) boulderElection.cvrs + redactedCvrs else redactedCvrs
    val cards = createSortedCards(allCvrs, auditConfig.seed)
    writeAuditableCardCsvFile(cards, publisher.cardsCsvFile())
    println("   writeCvrsCvsFile ${publisher.cardsCsvFile()} cvrs = ${allCvrs.size}")

    checkContestsWithCvrs(contestsUA, CvrIteratorAdapter(cards.iterator()), show = false)

    writeContestsJsonFile(contestsUA, publisher.contestsFile())
    println("   writeContestsJsonFile ${publisher.contestsFile()}")

    checkAssortAvgs(contestsUA, cards, check = true, show = false) // TODO this is slow, reading cvrs for each contest
    println("took = $stopwatch\n")

    val contest18 = contestsUA.find { it.id == 18 }!!
    val cards18 = cards.filter { it.cvr().hasContest(18)}
    writeAuditableCardCsvFile(cards18, "${publisher.topdir}/sortedCards18.csv")
    println("   writeCvrs18 ${"${publisher.topdir}/sortedCards18.csv"} cvrs = ${cards18.size}")

    val redacted18 = redactedCvrs.filter { it.hasContest(18)}
    println("     redacted18 = ${redacted18.size}")

    println(contest18.contestOA)
    contest18.showPools(redacted18)

    checkAssortAvg(contest18, cards18, show = true)
    checkAssortAvg(contest18, cards, show = true)
}

fun checkAssortAvgs(contests: List<OAContestUnderAudit>, cards: List<AuditableCard>, check: Boolean = true, show: Boolean = false) {
    contests.forEach { contestUA ->
        if (contestUA.minAssertion() != null) {  // single candidates have no assertions
            checkAssortAvg(contestUA, cards, check = check, show = show)
        }
    }
}

// we are making the contest votes from the cvrs. how does it compare with official tally ??
fun checkAssortAvg(contestUA: OAContestUnderAudit, cards: List<AuditableCard>, check: Boolean = true, show: Boolean = false) {
    checkAssorterAvg(contestUA.contestOA, cards.map { it.cvr() }, check = check, show = show)
} */