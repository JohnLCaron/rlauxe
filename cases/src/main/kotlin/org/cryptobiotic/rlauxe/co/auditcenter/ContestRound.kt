package org.cryptobiotic.rlauxe.co.auditcenter

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.cryptobiotic.rlauxe.auditcenter.AuditReason
import org.cryptobiotic.rlauxe.corlaInput.StatewideInput
import org.cryptobiotic.rlauxe.corlaInput.defaultCharset
import org.cryptobiotic.rlauxe.corlacvr.reverseMungedDate
import java.io.File

// one line for each contest ~ 1K

// contest_name,audit_reason,random_audit_status,winners_allowed,ballot_card_count,contest_ballot_card_count,winners,min_margin,risk_limit,audited_sample_count,two_vote_over_count,one_vote_over_count,one_vote_under_count,two_vote_under_count,disagreement_count,other_count,gamma,overstatements,optimistic_samples_to_audit,estimated_samples_to_audit
// Adams County Assessor - DEM, opportunistic_benefits, not_auditable, 1,72075,37328,"""Ken Musso""",0,0.03000000,0,0,0,0,0,0,0,1.03905000,0,0,0
data class ContestRound(
    val contest_name: String,
    val audit_reason: String,
    val random_audit_status: String,
    val winners_allowed: Int,
    val ballot_card_count: Int,         // population size = eg county size when uniform audit
    val contest_ballot_card_count: Int,  // Nc = number of cards with this contest on it
    val winners: String,
    val min_margin: Int,
    val risk_limit: Double,  // TODO use this?
    val audited_sample_count: Int,
    val two_vote_over_count: Int,
    val one_vote_over_count: Int,
    val one_vote_under_count: Int,
    val two_vote_under_count: Int,
    val disagreement_count: Int,
    val gamma: Double,      // and this ?? = 1.03905000
    val overstatements: Int,
    val optimistic_samples_to_audit: Int, // check if these ever differ
    val estimated_samples_to_audit: Int,
) {

    companion object {
        fun readFromFile(filename: String, input: StatewideInput): List<ContestRound> {
            val results = mutableListOf<ContestRound>()

            val file = File(filename)
            val format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreSurroundingSpaces(true) // Automatically trims leading and trailing spaces
                .get()

            CSVParser.parse(file, defaultCharset, format).use { parser ->
                for (record in parser) {
                    val rcontestName = record.get("contest_name")
                    val ccname = input.convertContestName("ContestRound", rcontestName) ?: rcontestName

                    val contest = ContestRound(
                        contest_name = ccname,
                        audit_reason = record.get("audit_reason"),
                        random_audit_status = record.get("random_audit_status"),
                        winners_allowed = record.get("winners_allowed").toInt(),
                        ballot_card_count = record.get("ballot_card_count").toInt(),
                        contest_ballot_card_count = record.get("contest_ballot_card_count").toInt(),
                        winners = record.get("winners"),
                        min_margin = record.get("min_margin").toInt(),
                        risk_limit = record.get("risk_limit").toDouble(),
                        audited_sample_count = record.get("audited_sample_count").toInt(),
                        two_vote_over_count = record.get("two_vote_over_count").toInt(),
                        one_vote_over_count = record.get("one_vote_over_count").toInt(),
                        one_vote_under_count = record.get("one_vote_under_count").toInt(),
                        two_vote_under_count = record.get("two_vote_under_count").toInt(),
                        disagreement_count = record.get("disagreement_count").toInt(),
                        gamma = record.get("gamma").toDouble(),
                        overstatements = record.get("overstatements").toInt(),
                        optimistic_samples_to_audit = record.get("optimistic_samples_to_audit").toInt(),
                        estimated_samples_to_audit = record.get("estimated_samples_to_audit").toInt(),
                    )
                    // println("   $comparison")
                    results.add(contest)
                }
            }
            return results
        }
    }
}