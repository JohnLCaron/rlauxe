package org.cryptobiotic.rlauxe.cvr

import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.commons.csv.CSVRecord
import org.cryptobiotic.rlauxe.util.roundUp
import kotlin.math.max
import kotlin.text.lowercase
import kotlin.text.startsWith

private val logger = KotlinLogging.logger("Redaction")

// make this pluggable
class RedactionBoulder(val show: Boolean = false) : RedactionIF {
    override var nlines = 0

    // "src/test/data/Boulder2024/2024-Boulder-County-General-Recount-Redacted-Cast-Vote-Record.csv"
    // "src/test/data/Boulder2025/Redacted-CVR-PUBLIC.csv"
    override fun isRedaction(line: CSVRecord, corlaCvrs: CorlaCvrs): Boolean {
        val ballotStyle = corlaCvrs.readColumn(line,"BallotType") ?: "noBallotType"

        if (line.get(0).startsWith("Redacted")) { // Boulder >= 2024?; but not "RCV Redacted ..." which can be treated like a normal CVR
            val isA = line.get(0).contains("A cards")
            val isB = line.get(0).contains("B cards")
            val ballotStylePlus = ballotStyle + if (isA) "-A" else if (isB) "-B" else ""
            val redactedGroup = RedactedGroup(ballotStylePlus, corlaCvrs.schema.voteForNs).addVotes(corlaCvrs.schema, line)
            corlaCvrs.ballotStyles.add(redactedGroup)
            nlines++
            if (show) println("  ** redact: $line")
            return true

        } else if (line.get(0).startsWith("RCV Redacted")) { // Boulder >= 2024? IRV
            val cvr = CvrRow(
                nlines,
                0,
                "N/A",
                0,
                "N/A",
                ballotStyle,
                null,
            )
            corlaCvrs.cvrs.add(cvr.addVotes(corlaCvrs.schema, line, corlaCvrs.lineno))  // IRV redacted vote
            corlaCvrs.ballotStyles.add(cvr)
            return true

        }
        return false
    }
}