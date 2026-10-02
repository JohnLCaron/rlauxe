package org.cryptobiotic.rlauxe.dhondt

import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.util.listToMap
import kotlin.test.Test

class TestDhondtAssorter {

    @Test
    fun testDhondt() {
        val info = ContestInfo(
            name = "FlandreEast",
            id = 0,
            choiceFunction = SocialChoiceFunction.DHONDT,
            candidateNames = listToMap("Vooruit", "CD&V"),
            minFraction = .05,
        )

        //data class DhondtParty(val partyName: String, val id: Int, val totalVotes: Int,
        //                       val lastSeatWon: Int?, val firstSeatLost: Int?, val isBelowMin: Boolean) {
        val winner = DhondtParty("Vooruit", 28, 127758, 3, 4, false, 8)
        val loser = DhondtParty("CD&V", 4, 125871, 6,7, false, 11)
        val dassort = DhondtBuilder.makeDhAssorterFromParty(info, winner, loser, Nc = 1083369)

        dassort.wtf()
    }
}