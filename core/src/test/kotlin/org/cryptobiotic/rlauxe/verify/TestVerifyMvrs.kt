package org.cryptobiotic.rlauxe.verify

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.Batch
import org.cryptobiotic.rlauxe.audit.makeCvr
import org.cryptobiotic.rlauxe.util.ErrorMessages
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestVerifyMvrs {

    @Test
    fun testMatch() {
        val N = 2
        val cards = mutableListOf<AuditableCard>()
        repeat(N) {
            val id = Random.nextInt()
            val cvr = makeCvr(id, 11, 15)
            cards.add(AuditableCard(cvr.id, it, prn=it.toLong(), cvr.phantom, cvr.votes, cvr.poolId, batchName="cvr"))
        }

        val errs = ErrorMessages("testMatch")
        verifyMvrCardPairs(cards.zip(cards), errs)
        if (errs.hasErrors())
         println("testMatch hasErrors")
        assertFalse(errs.hasErrors())
    }

    @Test
    fun testDontMatch() {
        val N = 2
        val cards = mutableListOf<AuditableCard>()
        repeat(N) {
            val id = Random.nextInt()
            val cvr = makeCvr(id, 11, 15)
            cards.add(AuditableCard(cvr.id, it, prn=it.toLong(), cvr.phantom, cvr.votes, cvr.poolId, batchName="cvr"))
        }

        val rcards = cards.reversed()
        val errs = ErrorMessages("testDontMatch")
        verifyMvrCardPairs(cards.zip(rcards), errs)
        assertTrue(errs.hasErrors())
    }

    @Test
    fun testHasStyle() {
        val N = 5
        val cards = mutableListOf<AuditableCard>()
        repeat(N) {
            val id = Random.nextInt()
            val cvr = makeCvr(id, 11, 15)
            cards.add(AuditableCard(cvr.id, it, prn=it.toLong(), cvr.phantom, cvr.votes, cvr.poolId, batchName="cvr"))
        }

        val rcards = listOf(
            cards[0].copy(batchName="hasStyle",batch=Batch("hasStyle", 1, cards[0].contests(), true)),
            cards[1].copy(batchName="hasStyle",batch=Batch("hasStyle", 1, removeOne(cards[1].contests()), true)),
            cards[2].copy(batchName="noStyle",batch=Batch("hasStyle", 1, removeOne(cards[2].contests()), false)),
            cards[3].copy(batchName="hasStyle",batch=Batch("hasStyle", 1, addOne(cards[3].contests()), true)),
            cards[4].copy(batchName="noStyle",batch=Batch("hasStyle", 1, addOne(cards[4].contests()), false)),
        )
        val errs = ErrorMessages("testHasStyle")
        verifyMvrCardPairs(cards.zip(rcards), errs)
        println("$errs")

        val errMessage = errs.toString()
        assertFalse(errMessage.contains("sample 0 has errors"))
        assertTrue(errMessage.contains("sample 1 has errors"))
        assertTrue(errMessage.contains("sample 2 has errors"))
        assertTrue(errMessage.contains("sample 3 has errors"))
        assertFalse(errMessage.contains("sample 4 has errors"))
    }

    fun removeOne(c: IntArray) = IntArray(c.size-1) { c.get(it) }
    fun addOne(c: IntArray) = IntArray(c.size+1) { if (it < c.size) c.get(it) else 42 }
}