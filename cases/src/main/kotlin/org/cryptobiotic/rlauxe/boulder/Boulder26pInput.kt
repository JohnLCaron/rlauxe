package org.cryptobiotic.rlauxe.boulder

import org.cryptobiotic.rlauxe.corlaInput.auditcenter

class Boulder26pInput: BoulderInput  {
    override val electionName= "Boulder2026p"
    override val cvrsSource = "/resources/data/cases/boulder26p/2026P-Redacted-CVR-Public.csv"
    override val manifestSource = "$auditcenter/2026/primary/files/Boulder.csv"
    override val sovoSource = "/resources/data/cases/boulder26p/2026P-Boulder-County-Official-Statement-of-Votes.csv"
}