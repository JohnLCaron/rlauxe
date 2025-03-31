package org.cryptobiotic.rlauxe.sf


import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.persist.csv.IteratorCvrsCsvStream
import org.cryptobiotic.rlauxe.persist.json.*
import org.cryptobiotic.rlauxe.raire.VoteConsolidator
import org.cryptobiotic.rlauxe.util.*

fun createSfElectionFromCvrs(
    auditDir: String,
    contestManifestFile: String,
    candidateManifestFile: String,
    cvrsZipFile: String,
    auditConfigIn: AuditConfig? = null
) {
    // clearDirectory(Path.of(auditDir))

    val stopwatch = Stopwatch()

    val resultContestM: Result<ContestManifestJson, ErrorMessages> = readContestManifestJson(contestManifestFile)
    val contestManifest = if (resultContestM is Ok) resultContestM.unwrap()
    else throw RuntimeException("Cannot read ContestManifestJson from ${contestManifestFile} err = $resultContestM")

    val resultCandidateM: Result<CandidateManifestJson, ErrorMessages> = readCandidateManifestJson(candidateManifestFile)
    val candidateManifest = if (resultCandidateM is Ok) resultCandidateM.unwrap()
    else throw RuntimeException("Cannot read CandidateManifestJson from ${candidateManifestFile} err = $resultCandidateM")

    val contestInfos = makeContestInfos(contestManifest, candidateManifest)
    println("contests = ${contestInfos.size}")

    val regularVoteMap = makeRegularContestVotes(cvrsZipFile)
    val contests = makeRegularContests(contestInfos.filter { it.choiceFunction == SocialChoiceFunction.PLURALITY }, regularVoteMap)

    val irvInfos = contestInfos.filter { it.choiceFunction == SocialChoiceFunction.IRV }
    val irvVoteMap = makeIrvContestVotes(irvInfos.associateBy { it.id} , cvrsZipFile)
    val irvContests = makeIrvContests(irvInfos, irvVoteMap)

    val auditConfig = auditConfigIn ?: AuditConfig(
        AuditType.CLCA, hasStyles = true, sampleLimit = 20000, riskLimit = .05,
        clcaConfig = ClcaConfig(strategy = ClcaStrategyType.previous)
    )

    val contestsUA = contests.map { ContestUnderAudit(it, isComparison=true, auditConfig.hasStyles).makeClcaAssertions() }
    val allContests = contestsUA + irvContests
    // these checks may modify the contest status
    checkContestsCorrectlyFormed(auditConfig, contestsUA)

    val publisher = Publisher(auditDir)
    writeContestsJsonFile(allContests, publisher.contestsFile())
    println("   writeContestsJsonFile ${publisher.contestsFile()}")
    writeAuditConfigJsonFile(auditConfig, publisher.auditConfigFile())
    println("   writeAuditConfigJsonFile ${publisher.auditConfigFile()}")

    println("took = $stopwatch")
}

val quiet = false

fun makeContestInfos(contestManifest: ContestManifestJson, candidateManifest: CandidateManifestJson): List<ContestInfo> {
    return contestManifest.List.map { contestM: ContestM ->
        val candidateNames =
            candidateManifest.List.filter { it.ContestId == contestM.Id }.associate { candidateM: CandidateM ->
                Pair(candidateM.Description, candidateM.Id)
            }
        ContestInfo(
            contestM.Description,
            contestM.Id,
            candidateNames,
            if (contestM.NumOfRanks == 0) SocialChoiceFunction.PLURALITY else SocialChoiceFunction.IRV,
            contestM.VoteFor
        )
    }
}

// sum all of the cvrs
fun makeRegularContestVotes(cvrsZipFile: String): Map<Int, ContestVotes> {
    val contestVotes = mutableMapOf<Int, ContestVotes>()

    var count = 0
    val reader = ZipReader(cvrsZipFile)
    val input = reader.inputStream()
    val cvrIter = IteratorCvrsCsvStream(input)

    while (cvrIter.hasNext()) {
        val cvr: Cvr = cvrIter.next().cvr
        cvr.votes.forEach { (contestId, choiceIds) ->
            val contestVote = contestVotes.getOrPut(contestId) { ContestVotes(contestId) }
            contestVote.countBallots++
            choiceIds.forEach {
                val nvotes = contestVote.votes[it] ?: 0
                contestVote.votes[it] = nvotes + 1
            }
            count++
            if (count % 10000 == 0) print("$count ")
            if (count % 100000 == 0) println()
        }
    }
    println(" read ${count} cvrs")
    return contestVotes
}

data class ContestVotes(val contestId: Int) {
    val votes = mutableMapOf<Int, Int>()
    var countBallots = 0
}

fun makeRegularContests(contestInfos: List<ContestInfo>, contestVotes: Map<Int, ContestVotes>): List<Contest> {
    val contests = mutableListOf<Contest>()
    contestInfos.forEach { info: ContestInfo ->
        val contestVote = contestVotes[info.id]
        if (contestVote == null) {
            println("*** Cant find contest '${info.id}' in contestVotes")
        } else {
            contests.add( Contest(info, contestVote.votes, contestVote.countBallots, 0))
        }
    }
    return contests
}

// The candidate Ids go from 0 ... ncandidates-1, use the ordering from ContestInfo.candidateIds
data class IrvContestVotes(val irvContestInfo: ContestInfo) {
    val candidateIdMap = irvContestInfo.candidateIds.mapIndexed { idx, candidateId -> Pair(candidateId, idx) }.toMap()
    val vc = VoteConsolidator()
    var countBallots = 0

    fun addVote(votes: IntArray) {
        val mappedVotes = votes.map { candidateIdMap[it]!! }
        vc.addVote(mappedVotes.toIntArray())
    }
}

fun makeIrvContestVotes(irvContests: Map<Int, ContestInfo>, cvrsZipFile: String): Map<Int, IrvContestVotes> {
    val contestVotes = mutableMapOf<Int, IrvContestVotes>()

    var count = 0
    val reader = ZipReader(cvrsZipFile)
    val input = reader.inputStream()
    val cvrIter = IteratorCvrsCsvStream(input)

    println("makeIrvContestVotes")
    while (cvrIter.hasNext()) {
        val cvr: Cvr = cvrIter.next().cvr
        cvr.votes.forEach { (contestId, choiceIds) ->
            if (irvContests.contains(contestId)) {
                val irvContest = irvContests[contestId]!!
                val contestVote = contestVotes.getOrPut(contestId) { IrvContestVotes(irvContest) }
                contestVote.countBallots++
                if (!choiceIds.isEmpty()) {
                    contestVote.addVote(choiceIds)
                }
                count++
                if (count % 10000 == 0) print("$count ")
                if (count % 100000 == 0) println()
            }
        }
    }
    println(" read ${count} cvrs")
    return contestVotes
}

fun makeIrvContests(contestInfos: List<ContestInfo>, contestVotes: Map<Int, IrvContestVotes>): List<ContestUnderAudit> {
    val contests = mutableListOf<ContestUnderAudit>()
    contestInfos.forEach { info: ContestInfo ->
        val irvContestVotes = contestVotes[info.id]
        if (irvContestVotes == null) {
            println("*** Cant find contest '${info.id}' in irvContestVotes")
        } else {
            val rcontest = org.cryptobiotic.rlauxe.raire.makeRaireContest(
                info,
                irvContestVotes.vc,
                Nc = irvContestVotes.countBallots,
                Np = 0,
            )
            contests.add(rcontest)
        }
    }
    return contests
}

