package org.cryptobiotic.rlauxe.boulder

import org.cryptobiotic.rlauxe.corlaCounty.CorlaCountyInput
import org.cryptobiotic.rlauxe.cvr.CorlaCvrs
import org.cryptobiotic.rlauxe.cvr.RedactionBoulder
import org.cryptobiotic.rlauxe.cvr.readCorlaCvrs

interface BoulderInput : CorlaCountyInput {
    override val countyName: String
        get() = "Boulder"

    val sovoSource: String

    fun sovo(): BoulderStatementOfVotes {
        return readBoulderSOV(this.sovoSource, this.electionName)
    }

    override fun readCorlaCvrs(): CorlaCvrs = readCorlaCvrs(cvrsSource, redaction = RedactionBoulder())
}