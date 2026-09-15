package org.cryptobiotic.rlauxe.corlaInput

class Morgan26PInput: CorlaCountyInput {
    override val electionName= "Morgan2026p"
    override val countyName= "Morgan"
    override val cvrsSource = "$auditcenter/2026/primary/observerfiles/Morgan_CVR_Export_20260709092315_Redacted.csv"
    override val manifestSource = "$auditcenter/2026/primary/observerfiles/Morgan_BallotManifest.csv"
    val otherManifest = "$auditcenter/2026/primary/files/Morgan.csv"

    /* TODO automate this ??
    override fun readCorlaCvrs(): CorlaRawCvrs {
        val org = readCorlaCvrs(cvrsSource, redaction = Redaction())
        val orgGroups = org.redactedGroups()
        require(orgGroups.size == 1)
        //   sumManifest - totalCvrs = 15;  single group should be set to 25, currently 10
        orgGroups.first().setNcards(25) // TODO still needed ??
        return org
    } */

    override fun countyPopulation() = stateInput.strataPopulation()[countyName]!!
    val stateInput = Colorado2026Primary()
}