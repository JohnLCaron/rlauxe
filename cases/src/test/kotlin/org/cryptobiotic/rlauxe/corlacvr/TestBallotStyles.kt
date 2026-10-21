package org.cryptobiotic.rlauxe.corlacvr

import kotlin.test.Test
import kotlin.test.assertEquals

class TestBallotStyles {

    @Test
    fun testTwoDisjoint() {
        val testStyles = listOf(
            CvrCardStyle("bt", setOf(5, 6, 7)),
            CvrCardStyle("bt", setOf(1, 2, 3, 4,)),
        )
        val result = createCardStylesAndUnion("bt", testStyles)
        result.forEach { println(it) }
        assertEquals(
            listOf(
                CvrCardStyle("bt", setOf(1, 2, 3, 4, 5, 6, 7)),
                CvrCardStyle("bt-A", setOf(1, 2, 3, 4,)),
                CvrCardStyle("bt-B", setOf(5, 6, 7)),
            ),
            result
        )

        val result2 = createCardStylesAndUnion("bt", listOf(
            CvrCardStyle("bt", setOf(1, 2, 3, 4, 5, 6, 7)),
            CvrCardStyle("bt", setOf(1, 2, 3, 4,)),
            CvrCardStyle("bt", setOf(5, 6, 7)),
        ))
        assertEquals(result, result2)
    }

    @Test
    fun testTwoNotDisjoint() {
        val testStyles = listOf(
            CvrCardStyle("bt", setOf(1, 2, 3, 4, 5, 6, 7)),
            CvrCardStyle("bt", setOf(7, 8, 9, 10,)),
        )
        val result = createCardStylesAndUnion("bt", testStyles)
        result.forEach { println(it) }
        assertEquals(
            listOf(
                CvrCardStyle("bt", setOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)),
                CvrCardStyle("bt-1", setOf(1, 2, 3, 4, 5, 6, 7)),
                CvrCardStyle("bt-2", setOf(7, 8, 9, 10,)),
            ),
            result
        )

        val result2 = createCardStylesAndUnion("bt", listOf(
            CvrCardStyle("bt", setOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)),
            CvrCardStyle("bt", setOf(1, 2, 3, 4, 5, 6, 7)),
            CvrCardStyle("bt", setOf(7, 8, 9, 10,)),
        ))
        assertEquals(result, result2)
    }

    @Test
    fun testThreeDisjoint() {
        val testStyles = listOf(
            CvrCardStyle("bt", setOf(41, 42)),
            CvrCardStyle("bt", setOf(5, 6, 7)),
            CvrCardStyle("bt", setOf(1, 2, 3, 4,)),
        )
        val result = createCardStylesAndUnion("bt", testStyles)
        result.forEach { println(it) }
        assertEquals(
            listOf(
                CvrCardStyle("bt", setOf(1, 2, 3, 4, 5, 6, 7, 41, 42)),
                CvrCardStyle("bt-A", setOf(1, 2, 3, 4,)),
                CvrCardStyle("bt-B", setOf(5, 6, 7)),
                CvrCardStyle("bt-C", setOf(41, 42)),
            ),
            result
        )

        val result2 = createCardStylesAndUnion("bt", listOf(
            CvrCardStyle("bt", setOf(41, 42)),
            CvrCardStyle("bt", setOf(1, 2, 3, 4, 5, 6, 7, 41, 42)),
            CvrCardStyle("bt", setOf(5, 6, 7)),
            CvrCardStyle("bt", setOf(1, 2, 3, 4,)),
            ))
        assertEquals(result, result2)
    }

    @Test
    fun testThreeNotDisjoint() {
        val testStyles = listOf(
            CvrCardStyle("bt", setOf(1, 41, 42)),
            CvrCardStyle("bt", setOf(1, 5, 6, 7)),
            CvrCardStyle("bt", setOf(1, 2, 3, 4,)),
        )
        val result = createCardStylesAndUnion("bt", testStyles)
        result.forEach { println(it) }
        assertEquals(
            listOf(
                CvrCardStyle("bt", setOf(1, 2, 3, 4, 5, 6, 7, 41, 42)),
                CvrCardStyle("bt-1", setOf(1, 2, 3, 4,)),
                CvrCardStyle("bt-2", setOf(1, 5, 6, 7)),
                CvrCardStyle("bt-3", setOf(1, 41, 42)),
            ),
            result
        )
        val result2 = createCardStylesAndUnion("bt", listOf(
            CvrCardStyle("bt", setOf(1, 2, 3, 4,)),
            CvrCardStyle("bt", setOf(1, 2, 3, 4, 5, 6, 7, 41, 42)),
            CvrCardStyle("bt", setOf(1, 5, 6, 7)),
            CvrCardStyle("bt", setOf(1, 41, 42)),
        ))
        assertEquals(result, result2)
    }
}