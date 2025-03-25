package org.cryptobiotic.rlaux.corla

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import org.cryptobiotic.rlauxe.core.betPayoffSamples
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.sfn
import java.io.File
import java.nio.charset.Charset
import kotlin.collections.reversed
import kotlin.text.appendLine

// Colorado Audit Round Contest
// https://www.coloradosos.gov/pubs/elections/RLA/2024/general/round1/contest.csv
// "corla/src/test/data/2024audit/round1/contest.csv"
//  0           1            2                   3
// contest_name,audit_reason,random_audit_status,winners_allowed,
//   4                 5                         6       7
//   ballot_card_count,contest_ballot_card_count,winners,min_margin,
//   8
//   risk_limit,audited_sample_count,two_vote_over_count,one_vote_over_count,one_vote_under_count,two_vote_under_count, disagreement_count,other_count,
//   16     17                      18                19
//   gamma,overstatements,optimistic_samples_to_audit,estimated_samples_to_audit

// City of Lafayette Ballot Question 2A,opportunistic_benefits,in_progress,1,
// 396121,18689,"""Yes/For""",3743,
// 0.03000000,0,0,0,0,0,0,0,
// 1.03905000,0,772,772

data class ColoradoAuditRoundContest(
    val contestName: String,
    val nwinners: Int,
    val ballotCardCount: Int,
    val contestBallotCardCount: Int,
    val winners: String,
    val minMargin: Int,
    val riskLimit: Double,
    val gamma: Double,
    val optimisticSamplesToAudit: Int,
    val estimatedSamplesToAudit: Int,
) {
    fun showEstimation() {
        // TODO they use ballotCardCount instead of contestBallotCardCount for some reason
        var dilutedMargin = minMargin.toDouble() / ballotCardCount
        if (dilutedMargin > 0) {
            val est = optimistic(riskLimit, dilutedMargin, gamma)
            val (bet, payoff, samples) = betPayoffSamples(ballotCardCount, risk=riskLimit, assorterMargin=dilutedMargin, 0.0)

            println("dilutedMargin = $dilutedMargin estSamples = ${est.toInt()} corlaEst=$optimisticSamplesToAudit rauxEst=$samples")
            require(optimisticSamplesToAudit == est.toInt())
            // println("   rauxe bet = $bet payoff = $payoff rauxeEst=$samples")
        }
    }
}

fun readColoradoContestRoundCsv(filename: String): List<ColoradoAuditRoundContest> {
    val file = File(filename)
    val parser = CSVParser.parse(file, Charset.forName("ISO-8859-1"), CSVFormat.RFC4180)
    val records = parser.iterator()

    // we expect the first line to be the headers
    val headerRecord = records.next()
    val header = headerRecord.toList().joinToString(", ")
    println(header)

    // subsequent lines contain ballot manifest info
    val contests = mutableListOf<ColoradoAuditRoundContest>()

    var line: CSVRecord? = null
    try {
        while (records.hasNext()) {
            line = records.next()!!
            val bmi = ColoradoAuditRoundContest(
                line.get(0),         // contest_name,audit_reason,random_audit_status,winners_allowed,
                line.get(3).toInt(),
                line.get(4).toInt(), // // ballot_card_count,contest_ballot_card_count,winners,min_margin,
                line.get(5).toInt(),
                line.get(6),
                line.get(7).toInt(),    // minMargin
                line.get(8).toDouble(), // riskLimit
                line.get(16).toDouble(), // gamma
                line.get(18).toInt(),
                line.get(19).toInt(),
            )
            contests.add(bmi)
            // println(bmi)
        }
    } catch (ex: Exception) {
        println(line)
        ex.printStackTrace()
    }

    return contests
}
