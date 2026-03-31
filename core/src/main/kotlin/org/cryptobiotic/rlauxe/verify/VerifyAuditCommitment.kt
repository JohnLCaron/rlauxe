package org.cryptobiotic.rlauxe.verify

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.unwrap
import com.github.michaelbull.result.unwrapError
import org.cryptobiotic.rlauxe.audit.AuditCreationConfig
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.BatchIF
import org.cryptobiotic.rlauxe.audit.ElectionInfo
import org.cryptobiotic.rlauxe.audit.MergeBatchesIntoCardManifestIterable
import org.cryptobiotic.rlauxe.betting.TestH0Status
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIterator
import org.cryptobiotic.rlauxe.persist.json.readAuditCreationConfigJsonFile
import org.cryptobiotic.rlauxe.persist.json.readBatchesJsonFile
import org.cryptobiotic.rlauxe.persist.json.readContestsJsonFile
import org.cryptobiotic.rlauxe.persist.json.readElectionInfoJsonFile
import org.cryptobiotic.rlauxe.util.CloseableIterable
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.util.ErrorMessages
import org.cryptobiotic.rlauxe.util.Prng
import java.nio.file.Files
import kotlin.io.path.Path

class VerifyAuditCommitment(val auditDir: String, contestId: Int?, show: Boolean) {
    val audit: AuditCommitment
    val auditType: AuditType
    val contests: List<ContestWithAssertions>
    val infos: Map<Int, ContestInfo>
    val batchSet: Set<BatchIF>

    init {
        val publisher = Publisher(auditDir)
        val result = readAuditCommitment(publisher)
        audit = if (result.isOk) result.unwrap() else {
            println(result.unwrapError())
            throw RuntimeException(result.unwrapError().toString())
        }

        auditType = audit.electionInfo.auditType
        contests = audit.contests
        infos = contests.map{ it.contest.info() }.associateBy { it.id }
        batchSet = audit.batches.toSet()
    }

    fun verify(): VerifyResults {
        val results = VerifyResults()
        results.addMessage("---VerifyElection on $auditDir")
        if (contests.size == 1) results.addMessage("  ${contests.first()} ")

        // checkContestsCorrectlyFormed(ContestSampleControl.NONE, contests, results)
        verifySortedCardManifest(auditType, contests, audit.sortedManifest, infos, batchSet,
            audit.auditCreationConfig.seed, results)

        return results
    }

}

data class AuditCommitment(val electionInfo: ElectionInfo, val auditCreationConfig: AuditCreationConfig,
                           val contests: List<ContestWithAssertions>, val batches: List<BatchIF>,
                           val sortedManifest: CloseableIterable<AuditableCard> )

fun readAuditCommitment(publisher: Publisher): Result<AuditCommitment, ErrorMessages> {
    val errs = ErrorMessages("readElectionRecord from '${publisher.auditDir}'")

    val electionInfoResult = readElectionInfoJsonFile(publisher.electionInfoFile())
    val electionInfo = if (electionInfoResult.isOk) electionInfoResult.unwrap() else {
        errs.addNested(electionInfoResult.unwrapError())
        null
    }

    val auditCreationConfigResult = readAuditCreationConfigJsonFile(publisher.auditCreationConfigFile())
    val auditCreationConfig = if (auditCreationConfigResult.isOk) auditCreationConfigResult.unwrap() else {
        errs.addNested(auditCreationConfigResult.unwrapError())
        null
    }

    val contestsResults = readContestsJsonFile(publisher.contestsFile())
    val contests = if (contestsResults.isOk) contestsResults.unwrap()  else {
        errs.addNested(contestsResults.unwrapError())
        null
    }

    val batches = if (!Files.exists(Path(publisher.batchesFile()))) emptyList() else {
        val batchesResult = readBatchesJsonFile(publisher.batchesFile())
        if (batchesResult.isOk) batchesResult.unwrap() else {
            errs.addNested(batchesResult.unwrapError())
            emptyList()
        }
    }

    val sortedManifest: CloseableIterable<AuditableCard> =
        MergeBatchesIntoCardManifestIterable(
            CloseableIterable { readCardsCsvIterator(publisher.sortedCardsFile()) },
            batches,
        )

    return if (errs.hasErrors()) Err(errs) else
        Ok(AuditCommitment(electionInfo!!, auditCreationConfig!!, contests!!, batches, sortedManifest))
}

fun verifySortedCardManifest(
    auditType: AuditType,
    contestsUA: List<ContestWithAssertions>,
    cards: CloseableIterable<AuditableCard>,
    infos: Map<Int, ContestInfo>,
    batchSet: Set<BatchIF>,
    seed: Long,
    results: VerifyResults,
) {
    results.addMessage("verifySortedCardManifest")

    val allCvrVotes = mutableMapOf<Int, ContestTabulation>()
    val nonpooled = mutableMapOf<Int, ContestTabulation>()
    val pooled = mutableMapOf<Int, ContestTabulation>()

    val locationSet = mutableSetOf<String>()
    val indexSet = mutableSetOf<Int>()
    val indexList = mutableListOf<Pair<Int, Long>>()

    var count = 0
    var lastCard: AuditableCard? = null


    println("verifySortedCardManifest seed= $seed")

    cards.iterator().use { cardIter ->
        while (cardIter.hasNext()) {
            val card = cardIter.next()

            // ll card locations and indices are unique
            if (!locationSet.add(card.location)) {
                results.addError("$count duplicate card.location ${card.location}")
            }
            if (!indexSet.add(card.index)) {
                results.addError("$count duplicate card.index ${card.index}")
            }

            // card prns are in ascending order
            if (lastCard != null) {
                if (card.prn <= lastCard.prn) {
                    results.addError("$count prn out of order lastCard = $lastCard card = ${card}")
                }
            }
            lastCard = card

            indexList.add(Pair(card.index, card.prn))

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

    // Given the seed and the PRNG, check that the PRNs are correct and are assigned sequentially by index.
    var countErrs = 0
    val prng = Prng(seed)
    val indexSorted = indexList.sortedBy { it.first }
    indexSorted.forEachIndexed { idx, it ->
        val prn = prng.next()
        if (it.second != prn)
            countErrs++
    }
    if (countErrs > 0)
        results.addError("  $count cards in the Manifest do not have correct prn: there are $countErrs errors")
    else
        results.addMessage("  verified that $count cards in the Manifest have correct prn")

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
}