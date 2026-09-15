package org.cryptobiotic.rlauxe.boulder

import org.cryptobiotic.rlauxe.corlaInput.CorlaCountyInput
import org.cryptobiotic.rlauxe.corlacvr.CorlaRawCvrs
import org.cryptobiotic.rlauxe.corlacvr.RedactionBoulder
import org.cryptobiotic.rlauxe.corlacvr.readCorlaCvrs

interface BoulderInput : CorlaCountyInput {
    override val countyName: String
        get() = "Boulder"

    val sovoSource: String

    fun sovo(): BoulderStatementOfVotes {
        return readBoulderSOV(this.sovoSource, this.electionName)
    }

    override fun readCorlaCvrs(): CorlaRawCvrs = readCorlaCvrs(cvrsSource, redaction = RedactionBoulder())
}