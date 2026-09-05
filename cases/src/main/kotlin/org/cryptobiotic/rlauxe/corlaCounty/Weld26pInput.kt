package org.cryptobiotic.rlauxe.corlaCounty

import org.cryptobiotic.rlauxe.auditcenter.auditcenter
import org.cryptobiotic.rlauxe.cvr.CorlaCvrs
import org.cryptobiotic.rlauxe.cvr.Redaction
import org.cryptobiotic.rlauxe.cvr.readCorlaCvrs

class Weld26pInput: CorlaCountyInput {
    override val electionName= "Weld2026p"
    override val countyName= "Weld"
    override val cvrsSource = "$auditcenter/2026/primary/observerfiles/Weld_CVR_Export_20260709140755.csv"
    override val manifestSource = "$auditcenter/2026/primary/observerfiles/Weld_BallotManifest.csv"
    val otherManifest = "$auditcenter/2026/primary/files/Weld.csv"
}