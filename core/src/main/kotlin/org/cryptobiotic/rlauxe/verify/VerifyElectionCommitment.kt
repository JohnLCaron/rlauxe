package org.cryptobiotic.rlauxe.verify

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.unwrap
import com.github.michaelbull.result.unwrapError
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.Batch.Companion.useVotes
import org.cryptobiotic.rlauxe.audit.BatchIF
import org.cryptobiotic.rlauxe.audit.ElectionInfo
import org.cryptobiotic.rlauxe.audit.MergeBatchesIntoCardManifestIterable
import org.cryptobiotic.rlauxe.betting.TestH0Status
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.csv.readCardPoolCsvFile
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIterator
import org.cryptobiotic.rlauxe.persist.json.readBatchesJsonFile
import org.cryptobiotic.rlauxe.persist.json.readContestsJsonFile
import org.cryptobiotic.rlauxe.persist.json.readElectionInfoJsonFile
import org.cryptobiotic.rlauxe.util.CloseableIterable
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.util.ErrorMessages
import java.nio.file.Files
import kotlin.io.path.Path

class VerifyElectionCommitment(val auditDir: String) {
    val publisher = Publisher(auditDir)
    val election: ElectionCommitment
    val auditType: AuditType
    val contests: List<ContestWithAssertions>
    val infos: Map<Int, ContestInfo>
    val batchSet: Set<BatchIF>
    val cardManifest: CloseableIterable<AuditableCard>

    init {
        val result = readElectionCommitment(publisher)
        election = if (result.isOk) result.unwrap() else {
            println(result.unwrapError())
            throw RuntimeException(result.unwrapError().toString())
        }

        auditType = election.electionInfo.auditType
        contests = election.contests
        infos = contests.map{ it.contest.info() }.associateBy { it.id }
        batchSet = election.batches.toSet()
        cardManifest = election.cardManifest
    }

    fun verify(): VerifyResults {
        val results = VerifyResults()
        results.addMessage("---VerifyElection on $auditDir")
        if (contests.size == 1) results.addMessage("  ${contests.first()} ")

        val contestSummary = verifyCardManifest(auditType, contests, cardManifest, infos, batchSet, results)

        // OA
        if (auditType.isOA()) {
            val cardPools = if (!Files.exists(Path(publisher.cardPoolsFile()))) null
                            else readCardPoolCsvFile(publisher.cardPoolsFile(), infos)

            if (cardPools != null) {
                verifyOAagainstCards(contests, contestSummary, cardPools, infos, results)
                verifyOAassortAvg(contests, cardManifest.iterator(), results)
                verifyOApools(contests, contestSummary, cardPools, results)
            }
        }

        // CLCA
        if (auditType.isClca()) {
            verifyClcaAgainstCards(contests, contestSummary, results)
            verifyClcaAssortAvg(contests, cardManifest.iterator(), results)
        }
        return results
    }

}

data class ElectionCommitment(val electionInfo: ElectionInfo, val contests: List<ContestWithAssertions>, val batches: List<BatchIF>,
    val cardManifest: CloseableIterable<AuditableCard> )

// TODO: use AuditRecord
fun readElectionCommitment(publisher: Publisher): Result<ElectionCommitment, ErrorMessages> {
    val errs = ErrorMessages("readElectionRecord from '${publisher.auditDir}'")

    val electionInfoResult = readElectionInfoJsonFile(publisher.electionInfoFile())
    val electionInfo = if (electionInfoResult.isOk) electionInfoResult.unwrap() else {
        errs.addNested(electionInfoResult.unwrapError())
        null
    }

    val contestsResults = readContestsJsonFile(publisher.contestsFile())
    val contests = if (contestsResults.isOk) contestsResults.unwrap()  else {
        errs.addNested(contestsResults.unwrapError())
        null
    }

    val infos = contests!!.map { it.contest.info() }.associateBy { it.id }
    val pools = if (!Files.exists(Path(publisher.cardPoolsFile()))) null
        else readCardPoolCsvFile(publisher.cardPoolsFile(), infos)

    val batches = pools ?: if (!Files.exists(Path(publisher.batchesFile()))) emptyList() else {
            val batchesResult = readBatchesJsonFile(publisher.batchesFile())
            if (batchesResult.isOk) batchesResult.unwrap() else {
                errs.addNested(batchesResult.unwrapError())
                emptyList()
            }
        }

    val cardManifest: CloseableIterable<AuditableCard> =
        MergeBatchesIntoCardManifestIterable(
            CloseableIterable { readCardsCsvIterator(publisher.cardManifestFile()) },
            batches,
        )

    return if (errs.hasErrors()) Err(errs) else
        Ok(ElectionCommitment(electionInfo!!, contests!!, batches, cardManifest))
}

fun verifyCardManifest(
    auditType: AuditType,
    contestsUA: List<ContestWithAssertions>,
    cards: CloseableIterable<AuditableCard>,
    infos: Map<Int, ContestInfo>,
    batchSet: Set<BatchIF>,
    results: VerifyResults,
): ContestSummary {
    results.addMessage("verifyCardManifest")

    val allCvrVotes = mutableMapOf<Int, ContestTabulation>()
    val nonpooled = mutableMapOf<Int, ContestTabulation>()
    val pooled = mutableMapOf<Int, ContestTabulation>()

    val locationSet = mutableSetOf<String>()
    val indexSet = mutableSetOf<Int>()

    var count = 0
    cards.iterator().use { cardIter ->
        while (cardIter.hasNext()) {
            val card = cardIter.next()

            if (card.index != count) {
                results.addError("card.index ${card.index} at $count must be sequential starting at 0")
            }

            // 1. Check that all card locations and indices are unique
            if (!locationSet.add(card.location)) {
                results.addError("$count duplicate card.location ${card.location}")
            }
            if (!indexSet.add(card.index)) {
                results.addError("$count duplicate card.index ${card.index}")
            }

            // check that batch exists
            if (!useVotes(card.cardStyle.name()) && !batchSet.contains(card.cardStyle)) {
                results.addError("card $count ${card.location} batch ${card.cardStyle} not in batches")
            }

            // the same as tabulateAuditableCards(), replicate so we can do allCvrVotes, nonpooled, pooled
            infos.forEach { (contestId, info) ->
                val allTab = allCvrVotes.getOrPut(contestId) { ContestTabulation(info) }
                if (card.hasContest(contestId)) {
                    if (card.votes != null && card.votes[contestId] != null) { // happens when cardStyle == all
                        val cands = card.votes[contestId]!!
                        allTab.addVotes(cands, card.phantom)
                    } else {
                        if (card.phantom) allTab.nphantoms++
                        allTab.ncardsTabulated++
                    }

                    if (card.poolId() == null) {
                        val nonpoolTab = nonpooled.getOrPut(contestId) { ContestTabulation(infos[contestId]!!) }
                        if (card.votes != null && card.votes[contestId] != null) { // happens when cardStyle == all
                            val cands = card.votes[contestId]!!
                            nonpoolTab.addVotes(cands, card.phantom)  // for IRV
                        } else {
                            if (card.phantom) nonpoolTab.nphantoms++
                            nonpoolTab.ncardsTabulated++
                        }
                    } else {
                        val poolTab = pooled.getOrPut(contestId) { ContestTabulation(infos[contestId]!!) }
                        poolTab.ncardsTabulated++
                    }
                }
            }
            count++
        }
    }
    if (!results.hasErrors) {
        results.addMessage("  verified that $count cards in the Manifest are ordered with no duplicate locations or indices")
    }

    // check if tabulation agrees with diluted count
    contestsUA.forEach {
        val tab = allCvrVotes[it.id]!!
        if (tab.ncardsTabulated != it.Npop) {
            results.addError("contest ${it.id} Npop ${it.Npop} disagree with cards = ${tab.ncardsTabulated}")
        }
    }

    // 3. If hasStyle, check that the count of phantom cards containing a contest = Contest.Nc - Contest.Ncast.
    // 4. If hasStyle, check that the count of non-phantom cards containing a contest = Contest.Ncast.
    if (auditType.isClca()) {
        var allOk = true
        contestsUA.forEach { contestUA ->
            val contestTab = allCvrVotes[contestUA.id]
            if (contestTab == null) {
                results.addError("contest ${contestUA.id} not found in tabulated cards")
                allOk = false

            } else {
                // 4. check that the count of phantom cards containing a contest = Contest.Nc - Contest.Ncast.
                if (contestUA.Nphantoms != contestTab.nphantoms) {
                    results.addError("contest ${contestUA.id} Nphantoms ${contestUA.Nphantoms} disagree with cards = ${contestTab.nphantoms}")
                    contestUA.preAuditStatus = TestH0Status.ContestMisformed
                    allOk = false
                }
            }
        }
        if (allOk) results.addMessage("  verified that contest.Nc and Np agree with manifest")
    }

    return ContestSummary(allCvrVotes, nonpooled, pooled)

}
