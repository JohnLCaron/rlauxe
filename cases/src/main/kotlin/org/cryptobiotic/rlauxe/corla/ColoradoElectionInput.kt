package org.cryptobiotic.rlauxe.corla

object Colorado2024Input {
    // contest formation
    val contestRoundFile = "src/test/data/corla/2024audit/round1/contest.csv"
    val tabulateCountyFile = "src/test/data/corla/2024audit/tabulateCounty.csv"

    val generalCanonicalFile = "src/test/data/corla/2024audit/2024GeneralCanonicalList.csv"
    val resultsReportSummaryFile = "src/test/data/corla/2024audit/round1/ResultsReportSummary.csv"
    val detailXmlFile = "src/test/data/corla/2024election/detail.xml"

    //// generating cvrs
    val contestComparisonFile = "src/test/data/corla/2024audit/round3/contestComparison.csv"
    val precinctFile = "src/test/data/corla/2024election/2024GeneralPrecinctLevelResults.zip"

    //// lazy reading
    val contestsByCounty: List<ContestTabByCounty> by lazy { readCountyTabulateCsv(tabulateCountyFile) }
    val roundContests: List<CorlaContestRoundCsv> by lazy { readColoradoContestRoundCsv(contestRoundFile) } // 725

    val electionDetailXml: ElectionDetailXml by lazy { readColoradoElectionDetail(detailXmlFile) }
    val resultsContests: List<ResultsReportContest> by lazy { readResultsReportContest(resultsReportSummaryFile) }

    // val generalCanonicalContests = readGeneralCanonicalList(generalCanonicalFile)

    val countyStyles: List<CountyStyles> by lazy { readContestComparisonCsv(contestComparisonFile) }
}