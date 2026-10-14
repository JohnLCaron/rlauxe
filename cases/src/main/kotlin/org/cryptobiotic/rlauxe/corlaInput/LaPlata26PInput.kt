package org.cryptobiotic.rlauxe.corlaInput

class LaPlata26PInput: CorlaCountyInput {
    override val electionName= "LaPlata2026p"
    override val countyName= "La Plata"
    override val cvrsSource = "$auditcenter/2026/primary/observerfiles/LaPlata_RedactedTest_NoContestBalance_CVR_Export_20260709081424.csv"
    override val manifestSource = "$auditcenter/2026/primary/observerfiles/LaPlata_BallotManifest.csv"
    override val year = 2026

    val otherManifest = "$auditcenter/2026/primary/files/LaPlata.csv"

    // or from manifest ?
    override fun countyPopulation() = Colorado2026Primary().strataPopulation()[countyName]!!
}