package org.cryptobiotic.rlauxe.corlaInput

class Weld26PInput: CorlaCountyInput {
    override val electionName= "Weld2026p"
    override val countyName= "Weld"
    override val cvrsSource = "$auditcenter/2026/primary/observerfiles/Weld_CVR_Export_20260709140755.csv"
    override val manifestSource = "$auditcenter/2026/primary/observerfiles/Weld_BallotManifest.csv"
    override val year = 2026

    val otherManifest = "$auditcenter/2026/primary/files/Weld.csv"

    // or from manifest ??
    override fun countyPopulation() = Colorado2026Primary().strataPopulation()[countyName]!!
}