package org.cryptobiotic.rlauxe.corlaInput

import org.cryptobiotic.rlauxe.corlaInput.County2020Input.Companion.manifestDir

class Arapahoe2020Redacted(val countyPopulation: Int): CountyInput {
    override val electionName= "Arapahoe 2020 Redacted"
    override val countyName= "Arapahoe"
    override val cvrsSource = "/home/stormy/Downloads/REDACTED_CVR_Export_20201130085221.csv"
    override val manifestSource = "$manifestDir/manifest-${countyName}.csv"
    override val year = "2020"

    override fun countyPopulation() = countyPopulation
}