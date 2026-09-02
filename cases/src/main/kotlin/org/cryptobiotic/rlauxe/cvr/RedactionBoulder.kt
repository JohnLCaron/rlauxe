package org.cryptobiotic.rlauxe.cvr

import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.commons.csv.CSVRecord
import kotlin.text.startsWith

private val logger = KotlinLogging.logger("Redaction")

// make this pluggable
class RedactionBoulder(val show: Boolean = false) : RedactionIF {
    override var nlines = 0
    val redactedGroups = mutableMapOf<String, RedactedGroup>()
    private val showDontMatch = true

    override fun redactedGroups() =  redactedGroups.values.toList()

    fun addToGroups(redacted:RedactedGroup) {
        val rname =  redacted.ballotType
        val group = redactedGroups[rname]
        if (group == null) {
            redactedGroups[rname] = RedactedGroup.makeAccumulator(redacted, rname)
        } else {
            if (group.contests() == redacted.contests()) {
                group.merge(redacted)
            } else if ((group.contests() - redacted.contests()).size == 0) {
                group.merge(redacted)
            } else if (showDontMatch) {
                println("    redacted ${redacted.ballotType} diff = ${redacted.contests() - group.contests()}, ${group.contests() - redacted.contests()}")
                println("doesnt match c31 = ${redacted.contestVotes[31]}")
            }
        }

        // Boulder 25 has strange anomoly with contest 31 = Coal Creek Canyon Fire Protection District Ballot Issue 7B
        fun add31(redacted:RedactedGroup) {
            // keep the r ??
            val rname =  if (redacted.contestVotes.contains(31)) "${redacted.ballotType}+31" else redacted.ballotType
            val group = redactedGroups[rname]
            if (group == null) {
                redactedGroups[rname] = RedactedGroup.makeAccumulator(redacted, rname)
            } else {
                if (group.contests() == redacted.contests())
                    group.merge(redacted)
                else if (showDontMatch)
                    println("redacted $redacted doesnt match $group; c31 = ${redacted.contestVotes[31]}")
            }
        }
    }

    // "src/test/data/Boulder2024/2024-Boulder-County-General-Recount-Redacted-Cast-Vote-Record.csv"
    // "src/test/data/Boulder2025/Redacted-CVR-PUBLIC.csv"
    override fun isRedaction(line: CSVRecord, corlaCvrs: CorlaCvrs): Boolean {
        val ballotType = corlaCvrs.getBallotType(line)

        if (line.get(0).startsWith("Redacted")) { // Boulder >= 2024?; but not "RCV Redacted ..." which can be treated like a normal CVR
            val isA = line.get(0).contains("A cards")
            val isB = line.get(0).contains("B cards")
            val ballotStylePlus = ballotType + if (isA) "-A" else if (isB) "-B" else ""
            val redactedGroup = RedactedGroup(ballotStylePlus, corlaCvrs.schema.voteForNs).addVotes(corlaCvrs.schema, line)
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

        }  else if (line.get(0).isEmpty())
            { // (2020) Boulder
            if (show) println("  ** redact: isEmpty $line")
            val redactedGroup =
                RedactedGroup("redacted$nlines", corlaCvrs.schema.voteForNs).addVotes(corlaCvrs.schema, line)
            addToGroups(redactedGroup)
            nlines++
            return true
        }

        return false
    }
}