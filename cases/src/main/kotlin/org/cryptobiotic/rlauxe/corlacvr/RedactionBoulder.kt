package org.cryptobiotic.rlauxe.corlacvr

import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.commons.csv.CSVRecord
import kotlin.text.startsWith

private val logger = KotlinLogging.logger("RedactionBoulder")

// TODO redaction strategy may depend on the year ??
// ok start with 2026p
// Redacted and Consolidated 18 Ballots,,,,,,"01, 19, and 25",8,10,15,13,5,12,5,17,9,4,4,1,8,5,3,9,8,,10,,,,,7,18,17,10,7,16,16,16,16,,,,,,,,,,,,,,,,,,,,,,
//Redacted and Consolidated 23 Ballots,,,,,,"02, 20, 26, and STATE",,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,17,15,2,0,12,11,0,0,18,18,8,14,15,8,5,,,8,,
//Redacted and Consolidated 9 Ballots,,,,,,31 and STATE,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,5,4,,,,,,,,,,,,,,,,,,,,
//Redacted and Consolidated 9 Ballots,,,,,,32,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,6,3
//Redacted and Consolidated 9 Ballots,,,,,,STATE,7,2,,6,1,8,1,6,0,7,2,0,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,
//Redacted,,,,,,14,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,1,1,0,0,1,0,0,0,1,1,0,1,1,,1,,1,,,
class RedactionBoulder(show: Boolean = false) : Redaction(RedactionStrategy(justGroups=true, hasStyle = true), show=show) {

    // 2020, 2023, 2024
    override fun isRedaction(line: CSVRecord, corlaRawCvrs: CorlaCountyCvrsIF): Boolean {
        val ballotType = corlaRawCvrs.getBallotType(line)

        if (line.get(0).startsWith("Redacted and Consolidated")) { // 2025, 2026
            val redactedGroup = RedactedGroup(ballotType, line, corlaRawCvrs)
            addGroup(redactedGroup)
            linesIgnored++
            if (show) println("  ** redact and aggregate: $line")
            return true

        } else if (line.get(0).startsWith("Redacted")) { // 2025, 2026: has ballotType but no id
            val redactedGroup = RedactedGroup(ballotType, line, corlaRawCvrs)
            addGroup(redactedGroup)
            // addRedactedLine(line, corlaRawCvrs, findNonNull = false)
            linesIgnored++
            return true

        } else if (line.get(0).startsWith("Redacted and Aggregated")) { // 2024
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
            val redactedGroup = RedactedGroup(ballotStylePlus, line, corlaRawCvrs)
            addGroup(redactedGroup)
            linesIgnored++
            if (show) println("  ** redact: $line")
            return true

        } else if (line.get(0).startsWith("Redacted & Aggregated")) { // 2023
// "Redacted & Aggregated",,,,,"DS-01",,,,,,,,,,,,,,,,,16,10,9,8,10,14,24,27,20,16,,,,,,,,,,,,,,,,,,,,,,,,,,,,,19,16,3,17,14,14,17,4,7,22,,,,,,,,,,,,,,,37,12,43,7,40,6,37,9,36,10,35,7,28,17,,,,,,,,,,,,,,,,,,,,,,,,,,,,,
//"Redacted & Aggregated",,,,,"DS-02",,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,9,28,11,25,12,10,,,,9,13,20,3,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,32,17,35,21,44,12,36,16,33,18,,,,,,,,,,,10,41,15,36,12,38,,,,,,,,,,,,,,,,,,,
//"Redacted & Aggregated",,,,,"DS-03",,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,6,28,4,19,10,9,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,24,10,26,13,32,8,30,9,28,11,,,,,,,,,,,19,18,19,18,17,19,,,,,,,,,,,,,,,,,,,
            val redactedGroup = RedactedGroup(ballotType, line, corlaRawCvrs)
            addGroup(redactedGroup)
            linesIgnored++
            if (show) println("  ** redact: $line")
            return true

        } else if (line.get(0).startsWith("RCV Redacted")) { // incomprehensible
            // see cases/src/test/data/Boulder2023/RedactedLinesOnly.csv
            // "RCV Redacted & Randomly Sorted",,,,,"DS-01",0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,
            // nRedactedRows++
            if (show) println("  ** discarded RCV Redacted: $line")
            return true

        }  else if (line.get(0).isEmpty()) { // 2020
            // ballot style but no id; merge into groups by style
            // (2020) Boulder: has 0's everywhere (no style info). no redacted rows.
            // ,,,,,,DS-27,6,3,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,6,3,0,0,1,0,0,0,0,6,2,2,0,0,0,0,0,5,2,2,0,0,0,0,6,3,5,4,0,0,0,0,0,0,0,0,0,6,7,0,5,2,5,2,6,1,6,1,4,3,5,2,5,2,5,2,4,3,4,6,7,3,0,0,0,0,0,0,0,4,5,6,3,5,5,5,5,6,4,6,3,8,2,3,7,6,3,3,6,6,4,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,6,3
            if (show) println("  ** redact: isEmpty $line")
            val ballotType = corlaRawCvrs.getBallotType(line)
            val redactedGroup = RedactedGroup(ballotType, line, corlaRawCvrs)
            addGroup(redactedGroup)
            linesIgnored++
            return true
        }

        return false
    }
}