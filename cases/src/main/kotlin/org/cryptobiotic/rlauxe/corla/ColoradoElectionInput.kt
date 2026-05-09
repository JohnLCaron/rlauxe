package org.cryptobiotic.rlauxe.corla

object Colorado2024Input {
    // not used
    val precinctFile = "src/test/data/corla/2024election/2024GeneralPrecinctLevelResults.zip"

    // canonical contests and choices
    val generalCanonicalFile = "src/test/data/corla/2024audit/2024GeneralCanonicalList.csv"
    val canonicalContests: Map<String, CanonicalContest> by lazy {
        val result: MutableMap<String, CanonicalContest> = readGeneralCanonicalList(generalCanonicalFile).associateBy { it.contestName }.toMutableMap()
        //add these missing contests:
        val extras = listOf(
            CanonicalContest("Bannock Ballot Issue 6A", choices=listOf("Yes", "No")).addCounties(listOf("Douglas")),
            CanonicalContest("Spring Canyon Ballot Issue 6B", choices=listOf("Yes", "No")).addCounties(listOf("Douglas")),
        )
        // remove these contests
        result.remove("La Plata County Surveyor")

        extras.forEach { result[it.contestName] = it }
        result.toSortedMap()
    }

    //// contest formation
    val tabulateCountyFile = "src/test/data/corla/2024audit/tabulateCounty.csv"
    val contestTabsByCounty: Map<String, ContestTabByCounty> by lazy {
        readCountyTabulateCsv(tabulateCountyFile, { it } , { it }  )
            //{ contestName -> contestNameCleanup(contestName) },
            // { contestName -> candidateNameCleanup(contestName) })
    }

    val contestRoundFile = "src/test/data/corla/2024audit/round1/contest.csv"
    val roundContests: Map<String, CorlaContestRoundCsv> by lazy {
        readColoradoContestRoundCsv(contestRoundFile) { it }
    } // 725

    //// suplementary info for contests, use when available
    val detailXmlFile = "src/test/data/corla/2024election/detail.xml"
    val detailXmlContests: ElectionDetailXml by lazy { readColoradoElectionDetail(detailXmlFile) }

    val resultsReportSummaryFile = "src/test/data/corla/2024audit/round1/ResultsReportSummary.csv"
    val resultsContests: List<ResultsReportContest> by lazy {
        readResultsReportContest(resultsReportSummaryFile) { it }
    }

    //// generating cvrs
    val mvrComparisonFile = "src/test/data/corla/2024audit/round3/contestComparison.csv"
    val cardComparison: CardComparisonResults by lazy {
        readContestComparisonCsv(mvrComparisonFile)  { it }
    }

    // data class CardComparisonResults(
    //    val contestMvrs: List<ContestMvrs>,
    //    val countyMvrs: List<CountyMvrs>,
    //    val stylesByCounty: List<CountyStyles>
    //)
    val contestMvrs: List<ContestMvrs> by lazy { cardComparison.contestMvrs }
    val countyMvrs: List<CountyMvrs> by lazy { cardComparison.countyMvrs }
    val countyStyles: List<CountyStyles> by lazy { cardComparison.stylesByCounty }
}