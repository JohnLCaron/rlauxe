package org.cryptobiotic.rlauxe.boulder

import org.cryptobiotic.rlauxe.corlaInput.CountyInput
import org.cryptobiotic.rlauxe.corlacvr.CorlaCountyCvrsIF
import org.cryptobiotic.rlauxe.corlacvr.RedactionBoulder
import org.cryptobiotic.rlauxe.corlacvr.readCountyCvrs

// BoulderInput also has the BoulderStatementOfVotes
interface BoulderInput : CountyInput {
    override val countyName: String
        get() = "Boulder"

    val sovoSource: String

    fun sovo(): BoulderStatementOfVotes {
        return readBoulderSOV(this.sovoSource, this.electionName)
    }

    override fun readCorlaCvrs(): CorlaCountyCvrsIF {
        return readCountyCvrs(cvrsSource, redaction = RedactionBoulder())
    }
}