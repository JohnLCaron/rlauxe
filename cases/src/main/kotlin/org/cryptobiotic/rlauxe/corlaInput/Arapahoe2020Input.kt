package org.cryptobiotic.rlauxe.corlaInput

import org.cryptobiotic.rlauxe.corlaInput.CorlaCounty2020Input.Companion.manifestDir

class Arapahoe2020Input: CorlaCountyInput {
    override val electionName= "Arapahoe 2020 Redacted"
    override val countyName= "Arapahoe"
    override val cvrsSource = "/home/joncifer/Downloads/REDACTED_CVR_Export_20201130085221.csv"
    override val manifestSource = "$manifestDir/manifest-${countyName}.csv"
    override val year = 2020

    // or from manifest ??
    override fun countyPopulation() = Colorado2020General().strataPopulation()[countyName]!!
}