package org.cryptobiotic.rlauxe.co.input

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.auditcenter.AuditReason
import org.cryptobiotic.rlauxe.auditcenter.CanonicalContest
import org.cryptobiotic.rlauxe.auditcenter.getAuditReason
import org.cryptobiotic.rlauxe.co.auditcenter.ContestRound
import org.cryptobiotic.rlauxe.corlaInput.StatewideInput
import kotlin.collections.forEach
import kotlin.collections.set

private val logger = KotlinLogging.logger("ContestMergedInfo")

// Make MergedContestInfo, countyStrata. not sure we need statewideContests ??
// create merged contest from the canonical contests, merge in the round info
// use only canonical names
// add strata info for multi-county contests
data class ContestMergedInfo(
    // canonical
    val canonicalContest: CanonicalContest,
    val contestName: String,
    val choices: List<String>, // canonical choice names
    val counties: Set<String>,

    /// contest round
    val auditReason: AuditReason,
    val corlaPop:Int,   // ballot_card_count = corla population
    val corlaNc:Int,         // contest_ballot_card_count
    val voteForN: Int,  // winners_allowed
    val estSamples: Int,  // estimated_samples_to_audit
    val marginInVotes: Int, // min_margin
    val riskLimit: Double, // risk_limit
) {
    var strata: StrataInfo? = null
    var errors = emptyList<Int>()

    fun addStrata(statewideMvrCount: Map<String, Int>, countyStrata: Map<String, StrataInfo>) {
        strata = when {
            (counties.size == 1) -> countyStrata[counties.first()]!!
            // fun computeStrataMinRate(strataName: String, contestCounties: Set<String>, statewideMvrCount: Map<String, Int>, strataMap: Map<String, StrataInfo>): StrataInfo {
            else -> computeStrataMinRate(contestName, counties, statewideMvrCount, countyStrata)
        }
    }

    fun addErrors(cmc: ContestMvrComparison?) {
        if (cmc == null) return
        errors = listOf(cmc.p2o, cmc.p1o, cmc.p1u, cmc.p2u)
    }
}

data class MergedInfo(
    val mergedContestInfo: List<ContestMergedInfo>,
    val countyStrata: Map<String, StrataInfo>,
    // val statewideContests: List<ContestRound>, // from contest round, not mvrs TODO NOT USED
)

fun makeMergedInfo(input: StatewideInput): MergedInfo {
    val canonical: Map<String, CanonicalContest> = input.canonicalContests() // has the canonical name

    // data class ContestRound(
    //    val contest_name: String,
    //    val audit_reason: String,
    //    val random_audit_status: String,
    //    val winners_allowed: Int,
    //    val ballot_card_count: Int,         // population size = eg county size when uniform audit
    //    val contest_ballot_card_count: Int,  // Nc = number of cards with this contest on it
    //    val winners: String,
    //    val min_margin: Int,
    //    val risk_limit: Double,  // TODO use this?
    //    val audited_sample_count: Int,
    //    val two_vote_over_count: Int,
    //    val one_vote_over_count: Int,
    //    val one_vote_under_count: Int,
    //    val two_vote_under_count: Int,
    //    val disagreement_count: Int,
    //    val gamma: Double,      // and this ?? = 1.03905000
    //    val overstatements: Int,
    //    val optimistic_samples_to_audit: Int, // check if these ever differ
    //    val estimated_samples_to_audit: Int,
    //)
    val contestRounds: List<ContestRound> = ContestRound.readFromFile(input.contestRoundFile, input) // one line for each contest
    val contestRoundsMap = mutableMapOf<String, ContestRound>()
    contestRounds.forEach {
        val ccname = input.convertContestName("contestRound", it.contest_name)
        if (ccname != null) contestRoundsMap[ccname] = it
    }

    /* is there some other way to find single counties besides targetContestsFile, which has nonstandard names ...
    val targetContests: List<TargetContest> = if (input.targetContestsFile == null) emptyList() else
        TargetContest.readFromFile(input.targetContestsFile!!)
    val singleContests = targetContests.filter { it.remarks.startsWith("Single") }.map { "${it.countyName} - ${it.contestName}" }.toSet() */

    // mergedContestInfo in canonical list order, which seems to be alphabetic; this determines contest id ordering.
    val mergedContestInfos = mutableListOf<ContestMergedInfo>()
    canonical.values.forEach { cc: CanonicalContest ->
        val round = contestRoundsMap[cc.contestName]
        if (round != null) {  // TODO no round, no contest ...
            mergedContestInfos.add(
                ContestMergedInfo(
                    cc,
                    cc.contestName,
                    cc.choices,
                    cc.counties,

                    auditReason = getAuditReason(round.audit_reason),
                    corlaPop = round.ballot_card_count,
                    corlaNc = round.contest_ballot_card_count,
                    round.winners_allowed ?: 1,
                    round.optimistic_samples_to_audit ?: 0,
                    round.min_margin,
                    round.risk_limit,
                )
            )
        }
    }

    val strata = makeCountyStrata(input).associateBy { it.strataName }

    // all we want i the error counts to put into mergedContestInfos
    val contestMvrComparison: Map<String, ContestMvrComparison> = ContestMvrComparisons(input, minfos = mergedContestInfos, keepLines = false).mcontests

    val statewideMvrCount: Map<String, Int> = strata.mapValues { it.value.stateMvrs }
    mergedContestInfos.forEach { mcontest ->
        mcontest.addStrata(statewideMvrCount, strata)
        mcontest.addErrors(contestMvrComparison[mcontest.contestName])
    }
    return MergedInfo(mergedContestInfos, strata)
}