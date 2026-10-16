package org.cryptobiotic.rlauxe.boulder

import org.cryptobiotic.rlauxe.corlaInput.auditcenter
import org.cryptobiotic.rlauxe.corlaInput.removeParen
import kotlin.math.min

class Boulder23Input: BoulderInput {
    override val electionName = "Boulder2023"
    override val manifestSource = "$auditcenter/2023/coordinated/round_1/Boulder.csv"
    override val cvrsSource = "/resources/data/cases/boulder2023/Redacted-2023Coordinated-CVR.csv"
    override val sovoSource = "/resources/data/cases/boulder2023/2023C-Boulder-County-Official-Statement-of-Votes.csv"
    val sovoRcvSource = "/resources/data/cases/boulder2023/2023C-Boulder-County-Official-Statement-of-Votes-RCV.csv"
    override val year = 2023

    // or use sovo ??
    override fun countyPopulation(): Int {
        val manifests = readCountyManifest()
        return manifests.totalCards
    }

    override fun sovo(): BoulderStatementOfVotes {
        val sovo = readBoulderStatementOfVotes(sovoSource, "Boulder2023")
        val sovoRcv = readBoulderStatementOfVotes(sovoRcvSource, "Boulder2023Rcv")
        return BoulderStatementOfVotes.combine(listOf(sovoRcv, sovo))
    }

    override fun contestNameCleanup(name: String): String {
        if (name == "City of Longmont - Mayor") return "City of Longmont Mayor"
        if (name.startsWith("Estes Park School")) return removeParen(name)
        if (name.startsWith("Thompson R2-J"))
            return removeParen(name.replace("R2-J", "R-2J"))
        return name
    }

    override fun candidateNameCleanup(name: String): String {
        if (name == "Jorge Chávez")
            return "Jorge Chavez"
        return name
    }
}

