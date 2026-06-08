package org.cryptobiotic.rlauxe.corla

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import java.io.File
import java.nio.charset.Charset

// contest_name,audit_reason,random_audit_status,winners_allowed,ballot_card_count,contest_ballot_card_count,winners,min_margin,risk_limit,audited_sample_count,two_vote_over_count,one_vote_over_count,one_vote_under_count,two_vote_under_count,disagreement_count,other_count,gamma,overstatements,optimistic_samples_to_audit,estimated_samples_to_audit

// Read Colorado Audit Round Contest CSV files, eg
//   https://www.coloradosos.gov/pubs/elections/RLA/2024/general/round1/contest.csv
//   corla/src/test/data/2024audit/round1/contest.csv
//
// // contest_name,audit_reason,random_audit_status,winners_allowed,ballot_card_count,contest_ballot_card_count,winners,min_margin,risk_limit,audited_sample_count,two_vote_over_count,one_vote_over_count,one_vote_under_count,two_vote_under_count,disagreement_count,other_count,gamma,overstatements,optimistic_samples_to_audit,estimated_samples_to_audit
//  0           1            2                   3
// contest_name,audit_reason,random_audit_status,winners_allowed,
//   4                 5                         6       7
//   ballot_card_count,contest_ballot_card_count,winners,min_margin,
//   8
//   risk_limit,audited_sample_count,two_vote_over_count,one_vote_over_count,one_vote_under_count,two_vote_under_count, disagreement_count,other_count,
//   16     17                      18                19
//   gamma,overstatements,optimistic_samples_to_audit,estimated_samples_to_audit

// 0                                    1                       2          3
// City of Lafayette Ballot Question 2A,opportunistic_benefits,in_progress,1,
// 4      5     6             7
// 396121,18689,"""Yes/For""",3743,
// 8
// 0.03000000,0,0,0,0,0,0,0,
// 16           18, 19
// 1.03905000,0,772,772

// Presidential Electors, state_wide_contest,in_progress,1,
// 4746866, 3239722,"""Kamala D. Harris / Tim Walz""", 350348,
// 0.03000000,0,0,0,0,0,0,0,1.03905000,0,99,99

// City of Dacono Council Members,opportunistic_benefits,not_auditable,2,
// 182397,3109,"""Michelle Rogers"",""Jeff Stainbrook""",0,
// 0.03000000,0,0,0,0,0,0,0,1.03905000,0,0,0

// state_wide_contest
// county_wide_contest
// opportunistic_benefits

enum class AuditReason { county_wide_contest, state_wide_contest, opportunistic_benefits, none}
fun getAuditReason(s: String): AuditReason {
    val reason = AuditReason.entries.find { it.name == s }
    if (reason == null) println("dont know AuditReason $s")
    return reason ?: AuditReason.none
}

// contest_name,audit_reason,random_audit_status,winners_allowed,ballot_card_count,contest_ballot_card_count,winners,min_margin,risk_limit,audited_sample_count,two_vote_over_count,one_vote_over_count,one_vote_under_count,two_vote_under_count,disagreement_count,other_count,gamma,overstatements,optimistic_samples_to_audit,estimated_samples_to_audit
// Adams County Assessor - DEM, opportunistic_benefits, not_auditable, 1,72075,37328,"""Ken Musso""",0,0.03000000,0,0,0,0,0,0,0,1.03905000,0,0,0

data class CorlaContestRoundCsv(
    val contestName: String,
    val auditReason: AuditReason,
    val nwinners: Int,
    val ballotCardCount: Int,         // population size = county size when uniform audit
    val contestBallotCardCount: Int,  // Nc = number of cards with this contest on it
    val winners: String,
    val minMargin: Int,
    val riskLimit: Double,
    val gamma: Double,
    val optimisticSamplesToAudit: Int,
    val estimatedSamplesToAudit: Int,
)

fun readColoradoContestRoundCsv(filename: String): Map<String, CorlaContestRoundCsv> {
    val file = File(filename)
    val parser = CSVParser.parse(file, Charset.forName("ISO-8859-1"), CSVFormat.DEFAULT)
    val records = parser.iterator()

    // we expect the first line to be the headers
    val headerRecord = records.next()
    val header = headerRecord.toList().joinToString(", ")
    // println(header)

    val contests = mutableMapOf<String, CorlaContestRoundCsv>()

    // contest_name,audit_reason,random_audit_status,winners_allowed,ballot_card_count,contest_ballot_card_count,winners,min_margin,risk_limit,
    //   audited_sample_count,two_vote_over_count,one_vote_over_count,one_vote_under_count,two_vote_under_count,disagreement_count,other_count,
    //   gamma,overstatements,optimistic_samples_to_audit,estimated_samples_to_audit
    var line: CSVRecord? = null
    try {
        while (records.hasNext()) {
            line = records.next()!!
            val bmi = CorlaContestRoundCsv(
                contestName = line.get(0).trim(),         // contest_name,
                auditReason = getAuditReason(line.get(1).trim()),
                nwinners = line.get(3).toInt(),   // winners_allowed,
                ballotCardCount = line.get(4)
                    .toInt(), // ballot_card_count,contest_ballot_card_count,winners,min_margin,
                contestBallotCardCount = line.get(5).toInt(), // contest_ballot_card_count
                winners = line.get(6), // winners
                minMargin = line.get(7).toInt(),    // minMargin
                riskLimit = line.get(8).toDouble(), // riskLimit
                gamma = line.get(16).toDouble(), // gamma
                optimisticSamplesToAudit = line.get(18).toInt(), // optimistic_samples_to_audit
                estimatedSamplesToAudit = line.get(19).toInt(), // estimated_samples_to_audit
            )
            contests[bmi.contestName] = bmi
        }
    } catch (ex: Exception) {
        println(line)
        ex.printStackTrace()
    }

    return contests.toSortedMap()
}
