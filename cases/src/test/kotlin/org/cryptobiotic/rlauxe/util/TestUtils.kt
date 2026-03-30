package org.cryptobiotic.rlauxe.util

import org.cryptobiotic.rlauxe.estimate.MultiContestTestData
import org.cryptobiotic.rlauxe.utils.tabulateCardsAndCount
import org.junit.jupiter.api.Assertions.assertEquals
import kotlin.test.Test

class TestUtils {
    @Test
    fun testTabulateCardsAndCount() {
        val test = MultiContestTestData(20, 11, 20000)
        val ( mvrs, cards, pools, styles) = test.makeMvrCardAndPops()
        val infos = test.contests.map { it.info }.associateBy { it.id }

        val (tabs, count) = tabulateCardsAndCount(Closer(cards.iterator()), infos)
        assertEquals(cards.size, count)

        val tab2 = tabulateAuditableCards(Closer(cards.iterator()), infos)
        tabs.forEach { println(it) }

        assertEquals(tabs, tab2)
    }
}