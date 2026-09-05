package org.cryptobiotic.rlauxe.boulder

import org.cryptobiotic.rlauxe.auditcenter.auditcenter
import org.cryptobiotic.rlauxe.cvr.CorlaCvrs
import org.cryptobiotic.rlauxe.cvr.RedactedGroup
import org.cryptobiotic.rlauxe.cvr.RedactionBoulder
import org.cryptobiotic.rlauxe.cvr.readCorlaCvrs

class Boulder24Input: BoulderInput {
    override val electionName= "Boulder2024"
    override val manifestSource = "$auditcenter/2024/general/ballotManifests/BoulderBallotManifest.csv"
    override val cvrsSource = "/resources/data/cases/boulder2024/2024-Boulder-County-General-Redacted-Cast-Vote-Record.zip"
    override val sovoSource = "/resources/data/cases/boulder2024/2024G-Boulder-County-Official-Statement-of-Votes.csv"

    override fun readCorlaCvrs(): CorlaCvrs {
       val org = readCorlaCvrs(cvrsSource, redaction = RedactionBoulder())
        removeContest12FromPool6(org.redactedGroups())
        return org
    }

    override fun hasABgroups() = true

    companion object {
        // from cases/src/test/kotlin/org/cryptobiotic/rlauxe/boulder/TestBoulderUndervotes.kt
        // with this exception, redacted groups match existing CardStyle:
        // RedactedGroup '06, 33, & 36-A', contestIds=[0, 1, 2, 3, 5, 10, 11, 12, 13, 14, 15, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42], totalVotes=8012
        //*** rgroup '06, 33, & 36-A'
        // [0, 1, 2, 3, 5, 10, 11, 12, 13, 14, 15, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42] !=
        // [0, 1, 2, 3, 5, 10, 11, 13, 14, 15, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42] (6-A)
        //
        // still, we will assume that all ballots in a group have the same CardStyle, which makes it easier to generate accurate simulated CVRs.
        // this wrongly includes contest 12,
        fun removeContest12FromPool6(redacteds: List<RedactedGroup>) { //}: List<RedactedGroup> {
            redacteds.map { redacted: RedactedGroup ->
                // correct bug adding contest 12 to pool 06:
                if (redacted.ballotType.startsWith("06")) {
                    redacted.contestVotes.remove(12)
                }
            }
        }
    }
}