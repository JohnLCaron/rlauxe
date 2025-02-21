package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.estimate.makeCvr
import org.cryptobiotic.rlauxe.estimate.makeCvrsByExactCount
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class TestCvr {

    @Test
    fun testCvr() {
        val cvrs: List<Cvr> = makeCvrsByExactCount(listOf(1, 1))
        assertEquals(2, cvrs.size)
        assertNotEquals(cvrs[0], cvrs[1])
        assertEquals(cvrs[0], cvrs[0])
        assertEquals(cvrs[0].hashCode(), cvrs[0].hashCode())
    }

    @Test
    fun testCvrUnderAudit() {
        val cvrUA = CvrUnderAudit(makeCvr(1), 12345L)
        assertEquals("card (false) 0: [1]", cvrUA.toString())
        assertEquals(0, cvrUA.cvr.hasMarkFor(0, 0))
        assertEquals(1, cvrUA.cvr.hasMarkFor(0, 1))
    }
}