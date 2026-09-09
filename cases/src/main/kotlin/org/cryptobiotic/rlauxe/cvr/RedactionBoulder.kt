package org.cryptobiotic.rlauxe.cvr

import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.commons.csv.CSVRecord
import kotlin.text.startsWith

private val logger = KotlinLogging.logger("RedactionBoulder")

// make this pluggable
class RedactionBoulder(show: Boolean = false) : Redaction(show) {

    // "src/test/data/Boulder2024/2024-Boulder-County-General-Recount-Redacted-Cast-Vote-Record.csv"
    // "src/test/data/Boulder2025/Redacted-CVR-PUBLIC.csv"
    override fun isRedaction(line: CSVRecord, corlaCvrs: CorlaCvrs): Boolean {
        val ballotType = corlaCvrs.getBallotType(line)

        if (line.get(0).startsWith("Redacted")) { // Boulder >= 2024?; but not "RCV Redacted ..." which can be treated like a normal CVR
            val isA = line.get(0).contains("A cards")
            val isB = line.get(0).contains("B cards")
            val ballotStylePlus = ballotType + if (isA) "-A" else if (isB) "-B" else ""
            val redactedGroup = RedactedGroup(ballotStylePlus, line, corlaCvrs.schema)
            addToGroups(redactedGroup)
            nlines++
            if (show) println("  ** redact: $line")
            return true

        } else if (line.get(0).startsWith("RCV Redacted")) { // Boulder >= 2024? IRV
            /*val cvr = CvrRow(
                nlines,
                0,
                "N/A",
                0,
                "N/A",
                ballotType,
                null,
            )
            // TODO umm, why is this a regular CVR ??
            corlaCvrs.cvrs.add(cvr.addVotes(corlaCvrs.schema, line, corlaCvrs.nrows()))  // IRV redacted vote
            corlaCvrs.ballotStyles.add(cvr) */
            nlines++
            if (show) println("  ** discarded: $line")
            return true

        }  else if (line.get(0).isEmpty()) { // (2020) Boulder
            if (show) println("  ** redact: isEmpty $line")
            val ballotType = corlaCvrs.getBallotType(line)
            val redactedGroup = RedactedGroup(ballotType, line, corlaCvrs.schema)
            addToGroups(redactedGroup)
            nlines++
            return true
        }

        return false
    }
}