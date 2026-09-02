package org.cryptobiotic.rlauxe.boulder

import org.cryptobiotic.rlauxe.auditcenter.auditcenter

class Boulder25Input: BoulderInput  {
    override val electionName= "Boulder2025"
    override val manifestSource = "$auditcenter/2025/files/BoulderManifest.csv"
    override val cvrsSource = "src/test/data/Boulder2025/Redacted-CVR-PUBLIC.csv"
    override val sovoSource = "src/test/data/Boulder2025/2025C-Boulder-County-Official-Statement-of-Votes.csv"
}