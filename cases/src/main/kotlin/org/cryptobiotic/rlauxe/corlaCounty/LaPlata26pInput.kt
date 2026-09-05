package org.cryptobiotic.rlauxe.corlaCounty

import org.cryptobiotic.rlauxe.auditcenter.auditcenter
import org.cryptobiotic.rlauxe.cvr.CorlaCvrs
import org.cryptobiotic.rlauxe.cvr.Redaction
import org.cryptobiotic.rlauxe.cvr.readCorlaCvrs

class LaPlata26pInput: CorlaCountyInput {
    override val electionName= "LaPlata2026p"
    override val countyName= "La Plata"
    override val cvrsSource = "$auditcenter/2026/primary/observerfiles/LaPlata_RedactedTest_NoContestBalance_CVR_Export_20260709081424.csv"
    override val manifestSource = "$auditcenter/2026/primary/observerfiles/LaPlata_BallotManifest.csv"
    val otherManifest = "$auditcenter/2026/primary/files/LaPlata.csv"
}