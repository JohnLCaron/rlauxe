package org.cryptobiotic.rlauxe.dominion


import org.cryptobiotic.rlauxe.corla.CountyContestBuilder
import org.cryptobiotic.rlauxe.auditcenter.Colorado2020General
import org.cryptobiotic.rlauxe.votedatabase.colorado2020
import kotlin.collections.forEach
import kotlin.test.Test
import kotlin.test.assertEquals

class TestDominionCvrConverter {

    @Test
    fun testDominionCvrConverter() {
        val filename = "$colorado2020/Denver/cvr.csv"
        testDominionCvrConverter(filename)
    }

    fun testDominionCvrConverter(filename: String) {
        val county = "Boulder"
        val export: DominionCvrExport = DominionCvrExportReader(filename).read()
        val schemaInfoMap = export.makeContestInfo().associateBy { it.id }

        val coloradoInput = Colorado2020General()
        val contestBuilder = CountyContestBuilder(coloradoInput)
        val contests = contestBuilder.contests
        val contestMap = contests.associateBy{ it.name }

        val dominionConverter = DominionCvrConverter(county, export, contests, coloradoInput)
        var count = 0
        var countOutOfOrder = 0
        export.cvrs.map { cvr: CastVoteRecord ->
            val card = dominionConverter.convertToCard(cvr)
            assertEquals(cvr.imprintedId, card.id)
            cvr.contestVotes.forEach { contestVote: ContestVotes ->
                val sinfo = schemaInfoMap[contestVote.contestId]!!
                val canonicalContest = coloradoInput.matchCanonicalContest(county, sinfo.name)!!
                val contest = contestMap[canonicalContest.contestName]
                val cvrVotes = card.votes(contest!!.id)
                if (contestVote.candVotes != cvrVotes!!.toList()) {
                    val info = contest.info()
                    val candNames = cvrVotes.map { info.candidateIdToName[it]!! }
                    val scandNames = contestVote.candVotes.map {
                        // coloradoInput.candidateNameCleanup(sinfo.candidateIdToName[it]!!)
                        coloradoInput.matchCanonicalCandidate(county, canonicalContest, sinfo.candidateIdToName[it]!!)
                    }
                    // not a problem to be out of order as long as the names agree
                    assertEquals(candNames, scandNames)
                    countOutOfOrder++
                }
                count++
            }
        }
        println("$count exported cvrs, $countOutOfOrder out of order")
    }
}

/*
17308, 8, 32, 1, 8-32-1, null, DS-05, 0: 0, 1: 0, 3: 0, 5: 3, 9: 0, 12: , 13: 0, 14: 1, 15: 1, 16: 0, 17: 1, 18: 0, 19: 0, 20: 1, 21: 1, 22: 1, 23: 0, 24: 1, 27: 0, 28: 0, 29: 0, 30: 0, 31: 0, 32: 0, 33: 0, 34: , 35: 0, 36: 0, 37: 1, 44: 1, 45: 0, 48: 0,
8-32-1 (false)  377: [0] 641: [0] 441: [0] 543: [2] 478: [0] 159: [] 297: [0] 298: [1] 117: [1] 118: [0] 196: [1] 197: [0] 198: [0] 199: [1] 200: [1] 39: [1] 37: [0] 38: [1] 16: [0] 17: [0] 14: [0] 15: [0] 384: [0] 378: [0] 379: [0] 380: [] 381: [0] 382: [0] 383: [1] 103: [1] 104: [0] 470: [0]
State Senator - District 17 -> State Senator - District 17
(557180) ContestVotes(contestId=5, candVotes=[3]) != 543:  [2]
 */