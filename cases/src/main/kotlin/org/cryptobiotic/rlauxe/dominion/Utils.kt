package org.cryptobiotic.rlauxe.dominion

import org.cryptobiotic.rlauxe.audit.CardStyle

fun DominionCvrExport.makeCardStyles(county: String, startIdx:Int = 1): Map<Set<Int>, CardStyle> {
    val result = mutableMapOf<Set<Int>, CardStyle>()
    this.ballotTypes.forEachIndexed { idx, bs ->
        result[bs.contests] = CardStyle("$county-${bs.name}", startIdx + idx, bs.contests.toIntArray(), true)
    }
    return result
}