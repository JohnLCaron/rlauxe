package org.cryptobiotic.rlauxe.auditcenter

import org.cryptobiotic.rlauxe.corla.ColoradoInput
import org.cryptobiotic.rlauxe.corla.CountyContestBuilder
import org.cryptobiotic.rlauxe.corla.CountyStylesFromMvrs
import org.cryptobiotic.rlauxe.dominion.DominionCvrConverter
import org.cryptobiotic.rlauxe.dominion.DominionCvrExport
import org.cryptobiotic.rlauxe.dominion.DominionCvrExportReader
import org.cryptobiotic.rlauxe.dominion.makeContestInfo
import kotlin.test.Test

// compare cardStyles from coloradoInput.countyStyles (taken from mvr files)
// vs what we see in DominionCvrExport (taken from cvrs)
class TestCardStyles {
    val show = false
    val colorado2020 = Colorado2020General()
    val colorado2022 = Colorado2022Primary()

    @Test
    fun test2022CvrSchema() {
        compareCvrSchemaVscoloradoInput("/home/stormy/datadrive/votedatabase/cvr/2022Primaries/Colorado/Boulder CO '22 Primary.csv", colorado2022)
    }

    fun compareCvrSchemaVscoloradoInput(filename: String, coloradoInput: ColoradoInput) {
        // println(CastVoteRecord.header)
        val export: DominionCvrExport = DominionCvrExportReader(filename).read()
        val exportContestInfos = export.makeContestInfo().associateBy { it.id }

        // these contest id's are internal to the export.
        println("export.CardStyles")
        // BallotType(val name: String, val contests: Set<Int>, var count: Int = 0)
        export.exportCardStyles.forEach { type ->
            println("  $type")
        }
        println()

        val contestBuilder = CountyContestBuilder(coloradoInput)
        val contests = contestBuilder.contests
        val dominionConverter = DominionCvrConverter("test", export, contests, coloradoInput, )

        println("dominionConverter.ExportCardStyles")
        dominionConverter.cardStyles.values.forEach {
            println("  ${it.show()}")
        }
        println()

        val contestMap = contests.associateBy{ it.name }
        val contestNameToId = contests.associate{ it.name to it.id }
        val boulderCountyStyles : CountyStylesFromMvrs = coloradoInput.mvrStyles.find { it.countyName == "Boulder" }!!
        // data class CountyStyles(val countyName: String) {
        //    val styles = mutableMapOf<Set<String>, Style>()
        //    var cardCount = 0
        println("voteCenter.Styles")
        boulderCountyStyles.styles.forEach{ (contestNames, style) ->
            // val contestIds = style.contests.map { contestMap[it]!!.id }.sorted()
            println( style.show(contestNameToId, false))
        }
        println()

        println("voteCenter matches to export")
        boulderCountyStyles.styles.forEach { (contestNames, style) ->
            print("  ${style.show(contestNameToId)} -> ")
            // convert from Set<contestName> to Set<contestId>
            val contestIds = contestNames.map { contestMap[it]!!.id }.toSet()
            val cardStyle = dominionConverter.cardStyles[contestIds]
            if (cardStyle != null) {
                println("  ${cardStyle.show()} MATCH")
            } else {
                println("  NO MATCH")
            }
        }
    }

}

fun compareBySet(list1: List<String>, list2: List<String>, name1: String = "canonical1", name2: String = "canonical2") {
    list1.forEach {
        if (!list2.contains(it)) {
            println(" $name2 doesnt have '${it}' from $name1")
        }
    }
    list2.forEach {
        if (!list1.contains(it)) {
            println(" $name1 doesnt have '${it}' from $name2")
        }
    }
}

/*
each county is an independent pool of ballots, with sperate card styles.
generate the CardStyle, then adjust CardStyle.count until totals match the county subtotals
we know contest.Nc

we dont know the contest undercounts = Nu

for each contest (do we know contest.Nc? only if contest is contained in the county)

    contest.Nc = sum(CardStyle.count) over cardstyle containing that contest

also

   total cards = sum(CardStyle.count)

this is a m x n matrix, to solve for CardStyle.count


=============================================
export.CardStyles match to votecenter.MvrStyle
  CardStyle(name=test-DS-04D, contests=[46, 63, 64, 65, 66, 67, 68, 69, 205, 428, 479, 488, 502, 655, 674], count= 17147
                  MvrStyle(0, contests=[46, 63, 64, 65, 66, 67, 68, 69, 205, 428, 479, 488, 502, 655, 674], count= 35

  CardStyle(name=test-DS-01D, contests=[46, 63, 64, 65, 66, 67, 68, 69, 205, 428, 479, 488, 498, 655, 674], count= 14329
                  MvrStyle(5, contests=[46, 63, 64, 65, 66, 67, 68, 69, 205, 428, 479, 488, 498, 655, 674], count= 26

  CardStyle(name=test-DS-07D, contests=[46, 63, 64, 65, 66, 67, 68, 69, 205, 428, 479, 488, 500, 655, 674], count= 12667
                  MvrStyle(2, contests=[46, 63, 64, 65, 66, 67, 68, 69, 205, 428, 479, 488, 500, 655, 674], count= 36


  CardStyle(name=test-DS-10D, contests=[46, 63, 64, 65, 66, 67, 68, 69, 205, 428, 479, 488, 582, 655, 674], count= 5416
                  MvrStyle(1, contests=[46, 63, 64, 65, 66, 67, 68, 69, 205, 428, 479, 488, 582, 655, 674], count= 5

  CardStyle(name=test-DS-16D, contests=[46, 63, 64, 65, 66, 67, 68, 69, 205, 428, 479, 488, 516, 655, 674], count= 4092
                  MvrStyle(4, contests=[46, 63, 64, 65, 66, 67, 68, 69, 205, 428, 479, 488, 516, 655, 674], count= 6

------------------
export 675 (United States Senator - REP ) -> votecenter 62 (Boulder County - United States Senator - REP (target = true))

  CardStyle(name=test-DS-08R, contests=[47, 206, 429, 480, 489, 501, 656, 675], count= 6864
              MvrStyle(6, contests=[47, 62, 206, 429, 480, 489, 501, 656], count= 16

  CardStyle(name=test-DS-17R, contests=[47, 206, 429, 480, 489, 517, 656, 675], count= 2888
             MvrStyle(11, contests=[47, 62, 206, 429, 480, 489, 517, 656], count= 4

  CardStyle(name=test-DS-02R, contests=[47, 206, 429, 480, 489, 499, 656, 675], count= 2330
              MvrStyle(7, contests=[47, 62, 206, 429, 480, 489, 499, 656], count= 6

  CardStyle(name=test-DS-20R, contests=[47, 206, 429, 480, 489, 503, 627, 656, 675], count= 111
            MvrStyle(10, contests=[47, 62, 206, 429, 480, 489, 503, 656], count= 5  (also missing 627 = State Senator - District 15 - REP)

  CardStyle(name=test-DS-14R, contests=[47, 206, 429, 480, 489, 583, 627, 656, 675], count= 1817
              MvrStyle(9, contests=[47, 62, 206, 429, 480, 489, 583, 627, 656], count= 8

  CardStyle(name=test-DS-11R, contests=[47, 206, 429, 480, 489, 583, 656, 675], count= 923
              MvrStyle(8, contests=[47, 62, 206, 429, 480, 489, 583, 656], count= 4

  CardStyle(name=test-DS-05R, contests=[47, 206, 429, 480, 489, 503, 656, 675], count= 5725
              MvrStyle(8, contests=[47, 62, 206, 429, 480, 489, 583, 656], count= 4


------------------
  no mvr sample

  CardStyle(name=test-DS-13D, contests=[46, 63, 64, 65, 66, 67, 68, 69, 205, 428, 479, 488, 582, 626, 655, 674], count= 4582
  CardStyle(name=test-DS-19D, contests=[46, 63, 64, 65, 66, 67, 68, 69, 205, 428, 479, 488, 502, 626, 655, 674], count= 363


boulderCountyStyles
 */