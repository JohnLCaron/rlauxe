package org.cryptobiotic.rlauxe.boulder

class Boulder23Input: BoulderInput {
    override val electionName= "Boulder2023"
    override val manifestSource = "src/test/data/Boulder2023/Boulder-IRV-Manifest.csv" // TODO seems wrong
    override val cvrsSource = "src/test/data/Boulder2023/Redacted-2023Coordinated-CVR.csv"
    override val sovoSource = "src/test/data/Boulder2023/2023C-Boulder-County-Official-Statement-of-Votes.csv"

    // or use sovo ??
    override fun countyPopulation(): Int {
        val manifests = readCountyManifest()
        return manifests.totalCards
    }

    override fun sovo(): BoulderStatementOfVotes {
        val sovo = readBoulderStatementOfVotes(
            "src/test/data/Boulder2023/2023C-Boulder-County-Official-Statement-of-Votes.csv", "Boulder2023"
        )
        val sovoRcv = readBoulderStatementOfVotes(
            "src/test/data/Boulder2023/2023C-Boulder-County-Official-Statement-of-Votes-RCV.csv", "Boulder2023Rcv"
        )
        return BoulderStatementOfVotes.combine(listOf(sovoRcv, sovo))
    }
}