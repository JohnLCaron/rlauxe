package org.cryptobiotic.rlauxe.verify

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.unwrap
import com.github.michaelbull.result.unwrapError
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.CardPoolIF
import org.cryptobiotic.rlauxe.audit.CardStyle.Companion.useVotes
import org.cryptobiotic.rlauxe.audit.StyleIF
import org.cryptobiotic.rlauxe.audit.ElectionInfo
import org.cryptobiotic.rlauxe.betting.TestH0Status
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.csv.readCardPoolCsvFile
import org.cryptobiotic.rlauxe.util.CloseableIterable
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.util.ErrorMessages
import org.cryptobiotic.rlauxe.workflow.PersistedMvrManager
import java.nio.file.Files
import kotlin.io.path.Path

class VerifyElectionCommitment(val location: String) {
    val publisher = Publisher(location)
    val election: ElectionCommitment
    val auditType: AuditType
    val contests: List<ContestWithAssertions>
    val infos: Map<Int, ContestInfo>
    val batchSet: Set<StyleIF>
    val cardManifest: CloseableIterable<AuditableCard>

    init {
        val result = readElectionCommitment(location)
        election = if (result.isOk) result.unwrap() else {
            println(result.unwrapError())
            throw RuntimeException(result.unwrapError().toString())
        }

        auditType = election.electionInfo.auditType
        contests = election.contests
        infos = contests.map{ it.contest.info() }.associateBy { it.id }
        val useBatches = election.batches ?: election.pools!!
        batchSet = useBatches.toSet()
        cardManifest = election.cardManifest
    }

    fun verify(): VerifyResults {
        val results = VerifyResults()
        results.addMessage("---VerifyElection on $location")
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

data class ElectionCommitment(val electionInfo: ElectionInfo, val contests: List<ContestWithAssertions>, val batches: List<StyleIF>?,
                              val pools: List<CardPoolIF>?, val cardManifest: CloseableIterable<AuditableCard> )

fun readElectionCommitment(location: String): Result<ElectionCommitment, ErrorMessages> {
    val errs = ErrorMessages("readElectionRecord from '${location}'")

    val auditRecord = AuditRecord.read(location) as AuditRecord
    val mvrManager = PersistedMvrManager(auditRecord)

    val electionInfo = auditRecord.electionInfo
    val config = auditRecord.config
    val contests = auditRecord.contests
    val styles = mvrManager.styles()
    val pools = mvrManager.pools()
    val sortedManifest = mvrManager.sortedManifest()

    return if (errs.hasErrors()) Err(errs) else
        Ok(ElectionCommitment(electionInfo!!, contests, styles, pools, sortedManifest.cards))
}

fun verifyCardManifest(
    auditType: AuditType,
    contestsUA: List<ContestWithAssertions>,
    cards: CloseableIterable<AuditableCard>,
    infos: Map<Int, ContestInfo>,
    batchSet: Set<StyleIF>,
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

            if (card.index() != count) {
                results.addError("card.index ${card.index()} at $count must be sequential starting at 0")
            }

            // 1. Check that all card locations and indices are unique
            if (!locationSet.add(card.id())) {
                results.addError("$count duplicate card.id ${card.id()}")
            }
            if (!indexSet.add(card.index())) {
                results.addError("$count duplicate card.index ${card.index()}")
            }

            // check that batch exists
            if (!useVotes(card.styleId) && !batchSet.contains(card.style())) {
                results.addError("card $count ${card.id()} batch ${card.style()} not in batches")
            }

            // the same as tabulateAuditableCards(), replicate so we can do allCvrVotes, nonpooled, pooled
            infos.forEach { (contestId, info) ->
                val allTab = allCvrVotes.getOrPut(contestId) { ContestTabulation(info) }
                if (card.hasContest(contestId)) {
                    if (card.votes(contestId) != null) { // happens when cardStyle == all
                        val cands = card.votes(contestId)!!
                        allTab.addVotes(cands, card.phantom())
                    } else {
                        if (card.phantom()) allTab.nphantoms++
                        allTab.ncardsTabulated++
                    }

                    if (card.poolId() == null) {
                        val nonpoolTab = nonpooled.getOrPut(contestId) { ContestTabulation(infos[contestId]!!) }
                        if (card.votes(contestId) != null) { // happens when cardStyle == all
                            val cands = card.votes(contestId)!!
                            nonpoolTab.addVotes(cands, card.phantom())  // for IRV
                        } else {
                            if (card.phantom()) nonpoolTab.nphantoms++
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
