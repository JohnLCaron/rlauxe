# ContestTabsByCounty

````
data class CountyContestTab(val countyName: String) {
    val contests = mutableMapOf<String, ContestTab>()
}

data class ContestTab(val contestName: String) {
    val choices = mutableMapOf<String, Int>() // choice name -> contest choice vote in this county
    val stylesForContest = mutableListOf<Style>()
}
````


````
data class ContestTabByCounty(val contestName: String) {
    val choices = mutableMapOf<String, CountyTabulateChoice>() // choice name -> CountyTabulateChoice
    var totalVotesAllCounties = 0 // total votes across counties
}

// for one choice, all counties
data class CountyTabulateChoice(
    val choiceName: String,
) {
    val counties = mutableListOf<ChoiceVote>()
    var totalVotes = 0 // total votes across counties for this choice
}

// for one county/contest/choice
data class ChoiceVote(
    val countyName: String,
    val contestName: String,
    val choiceName: String,
    val countyVote: Int,
)
````    