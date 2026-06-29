package org.cryptobiotic.rlauxe.utils

import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.util.ContestTabulation
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach

fun countPhantoms(contestTabSums: Map<Int, ContestTabulation>, contestNcs: Map<Int, Int>): Map<Int, Int> {
    val result = mutableMapOf<Int, Int>()
    contestTabSums.forEach { (_, contestSumTab) ->
        val useNc = contestNcs[contestSumTab.contestId] ?: contestSumTab.ncardsTabulated
        val Ncast = contestSumTab.ncardsTabulated
        result[contestSumTab.contestId] = useNc - Ncast
    }
    return result
}





