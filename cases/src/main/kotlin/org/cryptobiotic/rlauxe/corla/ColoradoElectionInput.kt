package org.cryptobiotic.rlauxe.corla

object Colorado2024Input {
    // not used
    val precinctFile = "src/test/data/corla/2024election/2024GeneralPrecinctLevelResults.zip"

    // canonical contests and choices
    val generalCanonicalFile = "src/test/data/corla/2024audit/2024GeneralCanonicalList.csv"
    val canonicalContests: Map<String, CanonicalContest> by lazy {
        val result: MutableMap<String, CanonicalContest> =
            readGeneralCanonicalList(generalCanonicalFile).associateBy { it.contestName }.toMutableMap()
        //add these missing contests:
        val extras = listOf(
            CanonicalContest("Bannock Ballot Issue 6A", choices = listOf("Yes", "No")).addCounties(listOf("Douglas")),
            CanonicalContest(
                "Spring Canyon Ballot Issue 6B",
                choices = listOf("Yes", "No")
            ).addCounties(listOf("Douglas")),
        )
        // remove these contests
        result.remove("La Plata County Surveyor")

        extras.forEach { result[it.contestName] = it }
        result.toSortedMap()
    }

    //// contest formation
    val tabulateCountyFile = "src/test/data/corla/2024audit/tabulateCounty.csv"
    val contestTabsByCounty: Map<String, ContestTabByCounty> by lazy {
        readCountyTabulateCsv(tabulateCountyFile, { it }, { it })
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
        readContestComparisonCsv(mvrComparisonFile) { it }
    }

    // data class CardComparisonResults(
    //    val contestMvrs: List<ContestMvrs>,
    //    val countyMvrs: List<CountyMvrs>,
    //    val stylesByCounty: List<CountyStyles>
    //)
    val contestMvrs: List<ContestMvrs> by lazy { cardComparison.contestMvrs }
    val countyMvrs: List<CountyMvrs> by lazy { cardComparison.countyMvrs }
    val countyStyles: List<CountyStyles> by lazy { cardComparison.stylesByCounty }

    val mergedInfo: MergedInfo by lazy { mergeContestInfo() } // mergedContestInfo, strataInfo, statewideContests
    val mergedContestMap: Map<String, MergedContestInfo> by lazy { mergedInfo.mergedContestInfo.associateBy { it.contestName } }
    val strataMap: Map<String, StrataInfo> by lazy { mergedInfo.strataInfo.associateBy { it.strataName } }
    val statewideContests: List<CorlaContestRoundCsv> by lazy { mergedInfo.statewideContests }
}

data class MergedContestInfo(
    // canonical
    val contestName: String,
    val choices: List<String>,
    val counties: Set<String>,

    // contestRound
    val auditReason: AuditReason,
    val npop:Int,
    val nc:Int,
    val voteForN: Int,
    val nsamples: Int,
    val marginInVotes: Int,

    // mvr file
    val countyMvrs: Int,
    val statewideMvrs: Int,
)

data class StrataInfo(
    val strataName: String,
    val nmvrs: Int,
    val Npop: Int,
)

data class MergedInfo(
    val mergedContestInfo: List<MergedContestInfo>,
    val strataInfo: List<StrataInfo>,
    val statewideContests: List<CorlaContestRoundCsv>,
)

fun mergeContestInfo(): MergedInfo {
    val canonical = Colorado2024Input.canonicalContests
    val contests = Colorado2024Input.roundContests
    val compareMap = Colorado2024Input.contestMvrs.associateBy { it.contestName }
    val countyMap = Colorado2024Input.countyMvrs.associateBy { it.countyName }

    val mergedContestInfo = canonical.values.map {
        val round = contests[it.contestName]
        val compare = compareMap[it.contestName]

        MergedContestInfo(
            it.contestName,
            it.choices,
            it.counties,

            round?.auditReason ?: AuditReason.none,
            round?.ballotCardCount ?: 0,
            round?.contestBallotCardCount ?: 0,
            round?.nwinners ?: 1,
            round?.optimisticSamplesToAudit ?: 0,
            round?.minMargin ?: 0,

            compare ?. countMvr ?: 0,
            compare ?. countStatewide ?: 0,
        )
    }

    // pick out the contests that are the targeted ones; should have a single contest
    val strataInfo = mutableListOf<StrataInfo>()
    val statewideContests = mutableListOf<CorlaContestRoundCsv>()
    canonical.values.forEach {
        val round = contests[it.contestName]
        if (round != null && round.auditReason == AuditReason.county_wide_contest) {
            if (it.counties.size != 1)
                println("*** ${it.contestName} has multiple counties: ${it.counties}")
            val county = it.counties.first()
            val countyMvr = countyMap[county]!!

            val countyInfo = StrataInfo(
                county,
                countyMvr.countMvr,
                round.ballotCardCount
            )
            strataInfo.add(countyInfo)
        }
        if (round != null && round.auditReason == AuditReason.state_wide_contest) {
            statewideContests.add(round)
        }
    }
    val totalCards = strataInfo.sumOf { it.Npop }
    val stateMvrCount = mergedContestInfo.filter { it.auditReason == AuditReason.state_wide_contest}.maxOf { it.statewideMvrs }
    strataInfo.add(StrataInfo("Statewide", nmvrs = stateMvrCount, Npop= totalCards, ))

    return MergedInfo(mergedContestInfo, strataInfo, statewideContests)
}