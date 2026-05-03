package org.cryptobiotic.rlauxe.corla

object Colorado2024Input {
    val contestComparisonFile = "src/test/data/corla/2024audit/round3/contestComparison.csv"
    val contestRoundFile = "src/test/data/corla/2024audit/round1/contest.csv"
    val tabulateCountyFile = "src/test/data/corla/2024audit/tabulateCounty.csv"

    val canonicalFile = "src/test/data/corla/2024audit/2024GeneralCanonicalList.csv"
    val resultsReportSummaryFile = "src/test/data/corla/2024audit/round1/ResultsReportSummary.csv"
    val detailXmlFile = "src/test/data/corla/2024election/detail.xml"
    val precinctFile = "src/test/data/corla/2024election/2024GeneralPrecinctLevelResults.zip"

    val canonicalContests: List<CountyTabulateCsv> = readCountyTabulateCsv(tabulateCountyFile)
    val roundContests: List<CorlaContestRoundCsv> = readColoradoContestRoundCsv(contestRoundFile) // 725
    val countyStyles = readContestComparisonCsv(contestComparisonFile)
    val electionDetailXml: ElectionDetailXml = readColoradoElectionDetail(detailXmlFile)
    val resultsContests: List<ResultsReportContest> = readResultsReportContest(resultsReportSummaryFile)
}