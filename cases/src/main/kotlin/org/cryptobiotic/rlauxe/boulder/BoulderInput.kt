package org.cryptobiotic.rlauxe.boulder

import org.cryptobiotic.rlauxe.cvr.CorlaCvrs
import org.cryptobiotic.rlauxe.cvr.RedactionBoulder
import org.cryptobiotic.rlauxe.cvr.readCorlaCvrs

interface BoulderInput {
    val electionName: String
    val manifestSource: String
    val cvrsSource: String
    val sovoSource: String

    fun sovo(): BoulderStatementOfVotes {
        return readBoulderSOV(this.sovoSource, this.electionName)
    }

    fun corlaCvrs(): CorlaCvrs = readCorlaCvrs(cvrsSource, redaction = RedactionBoulder())

    fun hasABgroups() = false
}