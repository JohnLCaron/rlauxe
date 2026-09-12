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

        if (line.get(0).startsWith("Redacted")) {
            // ballot style but no id; merge into groups by style
            // Boulder 2024
            // Redacted and Aggregated - A cards,,,,,,1,101,45,0,1,2,0,0,2,0,0,1,0,0,0,0,91,34,1,2,7,0,0,96,0,0,37,79,2,5,,,90,25,38,84,,,,,,,,,98,,95,0,0,78,37,92,,,,,,,,,,,,,,,,,,,,68,24,63,29,65,24,65,23,57,29,66,19,61,25,63,22,57,25,57,27,57,27,55,28,63,22,98,24,93,26,79,43,113,24,71,44,115,23,57,66,116,18,94,42,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,
            // Redacted and Aggregated - B cards,,,,,,1,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,86,48,62,56,72,51,51,67,65,55,49,46,50,40,49,38,,,,,,,,,,,,,,,,,,,,,,,,,101,19,,
            // Boulder 2025
            // "Redacted",,,,,,"02",,,,,,,,,,,,,,,,,,,,,,0,0,0,0,0,0,0,1,0,0,,,,,,,,,,,0,0,0,0,0,0,,,,,,0,0,1,0,1,0,1,0,1,0,,,,,,,,,,,,,,,,,,
            // "Redacted",,,,,,"09",,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,0,0,,,0,0,1,0,,,,,,,,,,,,,,1,0,1,0,1,0,1,0,,,,,,,0,1,0,1,,,,,,,,
            // Boulder 2026p
            // Redacted,,,,,,08,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,1,1,0,0,0,1,0,0,1,1,1,0,1,,,1,,,,
            // Redacted,,,,,,10,0,1,1,0,1,0,1,1,0,0,0,1,0,1,0,,1,,,1,,,,,1,1,0,1,1,1,1,1,,,,,,,,,,,,,,,,,,,,,,
            val isA = line.get(0).contains("A cards")
            val isB = line.get(0).contains("B cards")
            val ballotStylePlus = ballotType + if (isA) "-A" else if (isB) "-B" else ""
            val redactedGroup = RedactedGroup(ballotStylePlus, line, corlaCvrs.schema)
            addGroup(redactedGroup)
            nRedactedRows++
            if (show) println("  ** redact: $line")
            return true

        } else if (line.get(0).startsWith("RCV Redacted")) {
            // see cases/src/test/data/Boulder2023/RedactedLinesOnly.csv
            // "RCV Redacted & Randomly Sorted",,,,,"DS-01",0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,
            // nRedactedRows++
            if (show) println("  ** discarded RCV Redacted: $line")
            return true

        }  else if (line.get(0).isEmpty()) {
            // ballot style but no id; merge into groups by style
            // (2020) Boulder
            // ,,,,,,DS-27,6,3,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,6,3,0,0,1,0,0,0,0,6,2,2,0,0,0,0,0,5,2,2,0,0,0,0,6,3,5,4,0,0,0,0,0,0,0,0,0,6,7,0,5,2,5,2,6,1,6,1,4,3,5,2,5,2,5,2,4,3,4,6,7,3,0,0,0,0,0,0,0,4,5,6,3,5,5,5,5,6,4,6,3,8,2,3,7,6,3,3,6,6,4,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,6,3
            if (show) println("  ** redact: isEmpty $line")
            val ballotType = corlaCvrs.getBallotType(line)
            val redactedGroup = RedactedGroup(ballotType, line, corlaCvrs.schema)
            addGroup(redactedGroup)
            nRedactedRows++
            return true
        }

        return false
    }
}