package org.cryptobiotic.rlauxe.verify

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.CardStyle
import org.cryptobiotic.rlauxe.audit.makeCvr
import org.cryptobiotic.rlauxe.util.ErrorMessages
import kotlin.random.Random
import kotlin.test.Test
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
            cards.add(AuditableCard(cvr, it, prn=it.toLong()))
        }

        val errs = ErrorMessages("testMatch")
        verifyMvrCardPairs(cards.zip(cards), errs)
        if (errs.hasErrors())
          println("testMatch $errs")
        assertFalse(errs.hasErrors())
    }

    @Test
    fun testDontMatch() {
        val N = 2
        val cards = mutableListOf<AuditableCard>()
        repeat(N) {
            val id = Random.nextInt()
            val cvr = makeCvr(id, 11, 15)
            cards.add(AuditableCard(cvr, it, prn=it.toLong()))
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
            cards.add(AuditableCard(cvr, it, prn=it.toLong()))
        }

        val rcards = listOf(
            cards[0].copy(style=CardStyle("hasStyle", 1, cards[0].possibleContests(), true)),
            cards[1].copy(style=CardStyle("hasStyle", 1, removeOne(cards[1].possibleContests()), true)),
            cards[2].copy(style=CardStyle("noStyle", 1, removeOne(cards[2].possibleContests()), false)),
            cards[3].copy(style=CardStyle("hasStyle", 1, addOne(cards[3].possibleContests()), true)),
            cards[4].copy(style=CardStyle("noStyle", 1, addOne(cards[4].possibleContests()), false)),
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