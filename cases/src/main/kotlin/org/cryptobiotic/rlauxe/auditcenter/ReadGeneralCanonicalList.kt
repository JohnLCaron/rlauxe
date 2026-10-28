package org.cryptobiotic.rlauxe.auditcenter

import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import org.cryptobiotic.rlauxe.corlaInput.StatewideInput
import org.cryptobiotic.rlauxe.corlaInput.yesnoCanon
import java.io.File
import java.nio.charset.Charset

private val logger = KotlinLogging.logger("readGeneralCanonicalList")

// 2024GeneralCanonicalList.csv seems to have all the contests and candidates
//
//      CountyName,ContestName,ContestChoices
//      El Paso,City of Colorado Springs Ballot Question 300,"Yes/For,No/Against"

// canonical contest name and choice names from "canonical list" file
// sometimes arbitrary, but all variants are canonicalized to it for consistency across counties
class CanonicalContest(
    val stateInput : StatewideInput,
    val contestName: String,
    choicesIn: List<String>
) {
    val counties =  mutableSetOf<String>()
    val choices = choicesIn.map { yesnoCanon(it) }

    fun addCounty(county: String, choices: List<String>) {
        if (this.choices != choices) {
            var ok = true
            this.choices.zip(choices).forEach { (me, you) ->
                if (yesnoCanon(me) != yesnoCanon(you))
                ok = false
            }
            if (!ok) logger.info{"'$contestName' $county choices do not match $counties: ${this.choices} != $choices"}
        }
        val added = counties.add(county)
        if (!added) {
            logger.info{"'$contestName' already has this county $county"}
        }
    }

    fun addCounties(counties: List<String>): CanonicalContest {
        this.counties.addAll(counties)
        return this
    }

    fun matchCanonicalCandidate(candidateName: String): String? {
        return stateInput.matchCanonicalCandidate(counties.first(), this, candidateName)
    }

    override fun toString(): String {
        val s1 = buildString{ choices.forEach { append("\"$it\",") } }
        val s2 = buildString{ counties.forEach { append("\"$it\",") } }
        return "CanonicalContest(this, \"$contestName\", listOf($s1)).addCounties(listOf($s2))"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CanonicalContest

        if (stateInput != other.stateInput) return false
        if (contestName != other.contestName) return false
        if (counties != other.counties) return false
        if (choices != other.choices) return false

        return true
    }

    override fun hashCode(): Int {
        var result = stateInput.hashCode()
        result = 31 * result + contestName.hashCode()
        result = 31 * result + counties.hashCode()
        result = 31 * result + choices.hashCode()
        return result
    }
}

fun readGeneralCanonicalList(filename: String, stateInput : StatewideInput): List<CanonicalContest> {
    val file = File(filename)
    val parser = CSVParser.parse(file, Charset.forName("ISO-8859-1"), CSVFormat.DEFAULT)
    val records = parser.iterator()

    // we expect the first line to be the headers
    records.next()
    val headerRecord = records.next()
    val header = headerRecord.toList().joinToString(", ")
    // println(header)

    val contests = mutableMapOf<String, CanonicalContest>()

    // contest_name,audit_reason,random_audit_status,winners_allowed,ballot_card_count,contest_ballot_card_count,winners,min_margin,risk_limit,
    //   audited_sample_count,two_vote_over_count,one_vote_over_count,one_vote_under_count,two_vote_under_count,disagreement_count,other_count,
    //   gamma,overstatements,optimistic_samples_to_audit,estimated_samples_to_audit
    var line: CSVRecord? = null
    try {
        while (records.hasNext()) {
            line = records.next()!!
            val countyName = line.get(0).trim()
            val contestName = line.get(1).trim()
            val choices = line.get(2).split(",").map { it.trim() }
            val cc = contests.getOrPut(contestName) { CanonicalContest(stateInput, contestName, choices) }
            cc.addCounty(countyName, choices)
        }
    } catch (ex: Exception) {
        println(line)
        ex.printStackTrace()
    }

    return contests.values.toList()
}
