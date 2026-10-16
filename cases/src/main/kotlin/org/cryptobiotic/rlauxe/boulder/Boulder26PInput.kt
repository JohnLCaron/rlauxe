package org.cryptobiotic.rlauxe.boulder

import org.cryptobiotic.rlauxe.corlaInput.Statewide2026Primary
import org.cryptobiotic.rlauxe.corlaInput.auditcenter

class Boulder26PInput: BoulderInput  {
    override val electionName= "Boulder2026p"
    override val cvrsSource = "/resources/data/cases/boulder2026p/2026P-Redacted-CVR-Public.csv"
    override val manifestSource = "$auditcenter/2026/primary/files/Boulder.csv"
    override val sovoSource = "/resources/data/cases/boulder2026p/2026P-Boulder-County-Official-Statement-of-Votes.csv"
    override val year = 2026 // TODO primary vs general

    // or use sovo ??
    override fun countyPopulation() = Statewide2026Primary().strataPopulation()[countyName]!!
}