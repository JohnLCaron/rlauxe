package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.util.CloseableIterable
import org.cryptobiotic.rlauxe.util.countContestsFromCvrs
import org.junit.jupiter.api.Assertions.assertEquals
import kotlin.test.Test
import kotlin.test.assertTrue

class TestMakePhantoms {

    @Test
    fun testMakePhantomCvrs() {
        val prefix = "testPhantomCvrs"
        val phantomCount = mapOf(42 to 42, 1 to 40)
        val target = makePhantomCvrs(phantomCount, prefix)
        assertEquals(42, target.size)
        target.forEach {
            assertTrue(it.isPhantom())
            assertTrue(it.id.startsWith(prefix))
        }

        val tabs = countContestsFromCvrs(target.iterator())
        assertEquals(2, tabs.size)
        assertEquals(40, tabs[1])
        assertEquals(42, tabs[42])
    }

    @Test
    fun testCvrsWithPopulationsToCardManifest() {
        val prefix = "testPhantomCvrs"
        val phantomCount = mapOf(42 to 42, 1 to 40)
        val phantomCvrs = makePhantomCvrs(phantomCount, prefix)
        val cvrs = CloseableIterable { phantomCvrs.iterator() }

        val target = CvrsWithPopulationsToCardManifest(AuditType.CLCA,
            cvrs.iterator(), phantomCvrs, null)
        assertEquals(84, target.allCvrs.asSequence().count())

        // class CvrsWithPopulationsToCardManifest(
        //    val type: AuditType,
        //    val cvrs: CloseableIterator<Cvr>,
        //    val phantomCvrs : List<Cvr>?,
        //    populations: List<PopulationIF>?,
        //): CloseableIterator<AuditableCard> {
    }
}