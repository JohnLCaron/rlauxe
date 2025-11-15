package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.util.Closer
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

// data class AuditableCard (
//    val location: String, // info to find the card for a manual audit. Aka ballot identifier.
//    val index: Int,  // index into the original, canonical list of cards
//    val prn: Long,   // psuedo random number
//    val phantom: Boolean,
//    val possibleContests: IntArray, // list of contests that might be on the ballot. TODO replace with cardStyle?
//    val votes: Map<Int, IntArray>?, // for CLCA or OneAudit, a map of contest -> the candidate ids voted; must include undervotes; missing for pooled data or polling audits
//                                                                                // when IRV, ranked first to last
//    val poolId: Int?, // for OneAudit, or for setting style
//    val cardStyle: String? = null, // set style in a way that doesnt interfere with onaudit....
//)
class TestCvrsWithStylesToCards {

    @Test
    fun testCvrsWithStylesToCardsForClca() {
        val cvrr = makeCvr(abs(Random.nextInt()), 2 + Random.nextInt(3), 2 + Random.nextInt(2))

        var auditType = AuditType.CLCA

        // simple hasStyle with or without poolid. aka "cvrs are complete".
        var hasStyle = true
        var hasCardStyles = false
        var hasPoolId = false

        var cvr = cvrr.copy(poolId=null)
        var target = CvrsWithStylesToCards(auditType, cvrsAreComplete=hasStyle, Closer(listOf(cvr).iterator()), phantomCvrs=null, styles=null, )
        var card = target.next()
        testOneTarget("** clca complete cvrs", cvr, card, auditType, hasStyle, hasPoolId, hasCardStyles, null)

        hasPoolId = true
        cvr = cvrr.copy(poolId=1)
        target = CvrsWithStylesToCards(auditType, cvrsAreComplete=hasStyle, Closer(listOf(cvr).iterator()), phantomCvrs=null, styles=null)
        card = target.next()
        testOneTarget("clca hasStyle and poolIds", cvr, card, auditType, hasStyle, hasPoolId, hasCardStyles, expectStyle = null)

        val cardStyle = CardStyle("you", 1, emptyList(), listOf(0,1,2,3,4), null)
        // doesnt make sense to use; hasStyle means use cvr
        hasStyle = true
        hasCardStyles = true
        hasPoolId = true
        cvr = cvrr.copy(poolId=1)
        target = CvrsWithStylesToCards(auditType, cvrsAreComplete=hasStyle, Closer(listOf(cvr).iterator()), phantomCvrs=null, styles=listOf(cardStyle))
        card = target.next()
        testOneTarget("clca hasStyle and poolIds and styles", cvr, card, auditType, hasStyle, hasPoolId, hasCardStyles, expectStyle = cardStyle)

        // noStyle means votes isnt complete, so you need cardStyles and poolIds. should test if votes cubset of cardpool contests
        hasStyle = false
        hasCardStyles = true
        hasPoolId = true
        cvr = cvrr.copy(poolId=1)
        target = CvrsWithStylesToCards(auditType, cvrsAreComplete=hasStyle, Closer(listOf(cvr).iterator()), phantomCvrs=null, styles=listOf(cardStyle))
        card = target.next()
        testOneTarget("** clca incomplete cvrs", cvr, card, auditType, hasStyle, hasPoolId, hasCardStyles, expectStyle = cardStyle)

        // what if you dont supply the poolId? FAIL
        hasStyle = false
        hasCardStyles = true
        hasPoolId = false
        cvr = cvrr.copy(poolId=null)
        target = CvrsWithStylesToCards(auditType, cvrsAreComplete=hasStyle, Closer(listOf(cvr).iterator()), phantomCvrs=null, styles=listOf(cardStyle))
        card = target.next()
        // testOneTarget("", cvr, card, auditType, hasStyle, hasPoolId, hasCardStyles, expectStyle = cardStyle)

        // what if you dont supply the cardStyles? FAIL
        hasStyle = false
        hasCardStyles = false
        hasPoolId = true
        cvr = cvrr.copy(poolId=1)
        target = CvrsWithStylesToCards(auditType, cvrsAreComplete=hasStyle, Closer(listOf(cvr).iterator()), phantomCvrs=null, styles=null)
        card = target.next()
       //  testOneTarget("", cvr, card, auditType, hasStyle, hasPoolId, hasCardStyles, expectStyle = null)
    }

    @Test
    fun testCvrsWithStylesToCardsForPolling() {
        val cvrr = makeCvr(abs(Random.nextInt()), 2 + Random.nextInt(3), 2 + Random.nextInt(2))

        var auditType = AuditType.POLLING
        var hasStyle = true
        var hasCardStyles = false
        var hasPoolId = false

        var cvr = cvrr.copy(poolId=null)
        var target = CvrsWithStylesToCards(auditType, cvrsAreComplete=hasStyle, Closer(listOf(cvr).iterator()), phantomCvrs=null, styles=null, )
        var card = target.next()
        testOneTarget("polling hasStyle", cvr, card, auditType, hasStyle, hasPoolId, hasCardStyles, null)

        hasPoolId = true
        cvr = cvrr.copy(poolId=1)
        target = CvrsWithStylesToCards(auditType, cvrsAreComplete=hasStyle, Closer(listOf(cvr).iterator()), phantomCvrs=null, styles=null)
        card = target.next()
        testOneTarget("polling hasStyle and poolIds", cvr, card, auditType, hasStyle, hasPoolId, hasCardStyles, expectStyle = null)

        // what happens if the poolId doesnt match ??
        val cardStyle = CardStyle("cardstyle1", 1, emptyList(), listOf(0,1,2,3,4), null)
        hasStyle = true
        hasCardStyles = true
        hasPoolId = true
        cvr = cvrr.copy(poolId=1)
        target = CvrsWithStylesToCards(auditType, cvrsAreComplete=hasStyle, Closer(listOf(cvr).iterator()), phantomCvrs=null, styles=listOf(cardStyle))
        card = target.next()
        testOneTarget("polling hasStyle and poolIds and styles", cvr, card, auditType, hasStyle, hasPoolId, hasCardStyles, expectStyle = cardStyle)

        // noStyle means must supply the list of possibleContests
        hasStyle = false
        hasCardStyles = true
        hasPoolId = true
        cvr = cvrr.copy(poolId=1)
        target = CvrsWithStylesToCards(auditType, cvrsAreComplete=hasStyle, Closer(listOf(cvr).iterator()), phantomCvrs=null, styles=listOf(cardStyle))
        card = target.next()
        testOneTarget("** poll noStyle", cvr, card, auditType, hasStyle, hasPoolId, hasCardStyles, expectStyle = cardStyle)

        // what if you dont supply the poolId? FAIL
        hasStyle = false
        hasCardStyles = true
        hasPoolId = false
        cvr = cvrr.copy(poolId=null)
        target = CvrsWithStylesToCards(auditType, cvrsAreComplete=hasStyle, Closer(listOf(cvr).iterator()), phantomCvrs=null, styles=listOf(cardStyle))
        card = target.next()
        // testOneTarget("", cvr, card, auditType, hasStyle, hasPoolId, hasCardStyles, expectStyle = cardStyle)

        // what if you dont supply the cardStyles? FAIL
        hasStyle = false
        hasCardStyles = false
        hasPoolId = true
        cvr = cvrr.copy(poolId=1)
        target = CvrsWithStylesToCards(auditType, cvrsAreComplete=hasStyle, Closer(listOf(cvr).iterator()), phantomCvrs=null, styles=null)
        card = target.next()
        // testOneTarget("", cvr, card, auditType, hasStyle, hasPoolId, hasCardStyles, expectStyle = null)
    }

    @Test
    fun testCvrsWithStylesToCardsForOA() {
        val cvrr = makeCvr(abs(Random.nextInt()), 2 + Random.nextInt(3), 2 + Random.nextInt(2))
        val cvrc = cvrr.copy(poolId=null)
        val cvrp = cvrr.copy(poolId=2)
        val cvrs = listOf(cvrc, cvrp)

        var auditType = AuditType.ONEAUDIT
        var hasStyle = true
        var hasCardStyles = true
        var hasPoolId = true

        val cardStyle = CardStyle("oapool2", 2, emptyList(), listOf(0,1,2), null)
        var target = CvrsWithStylesToCards(auditType, cvrsAreComplete=hasStyle, Closer(cvrs.iterator()), phantomCvrs=null, styles=listOf(cardStyle), )
        testOneTarget("oa hasStyle", cvrc, target.next(), auditType, hasStyle, hasPoolId, hasCardStyles, null)
        testOneTarget("oa hasStyle pooled", cvrp, target.next(), auditType, hasStyle, hasPoolId, hasCardStyles, cardStyle)

        // noStyle means must supply the list of possibleContests for pooled data and cvrs
        hasStyle = false
        hasCardStyles = true
        hasPoolId = true
        target = CvrsWithStylesToCards(auditType, cvrsAreComplete=hasStyle, Closer(cvrs.iterator()), phantomCvrs=null, styles=listOf(cardStyle), )
        testOneTarget("oa noStyle", cvrc, target.next(), auditType, hasStyle, hasPoolId, hasCardStyles, null)
        testOneTarget("oa noStyle pooled", cvrp, target.next(), auditType, hasStyle, hasPoolId, hasCardStyles, cardStyle)
    }


    fun testOneTarget(what: String, cvr: Cvr, card: AuditableCard, auditType: AuditType, hasStyle: Boolean, hasPoolId: Boolean, hasCardStyles: Boolean, expectStyle:CardStyle?) {
        println("$what [$auditType hasStyle=$hasStyle hasPoolId:$hasPoolId hasCardStyles:$hasCardStyles]:")
        println("  ${cvr.show()}")
        println("  ${card.show()}")
        println()

        if (auditType.isClca()) {
            assertEquals(cvr, card.cvr())
            assertNotNull(card.votes)
            assertEquals(cvr.votes, card.votes)

        } else if (auditType.isPolling()) {
            assertNull(card.votes)
        }

        assertEquals(cvr.id, card.location)
        assertEquals(cvr.phantom, card.phantom)
        assertEquals(cvr.poolId, card.poolId, "poolId")
        assertEquals(expectStyle?.name(), card.cardStyle, "cardStyle")

        val expectPossibleContests = when (auditType) {
            AuditType.ONEAUDIT -> if (card.poolId == null && hasStyle) emptySet() else expectStyle?.contests()?.toSet() ?: emptySet()
            AuditType.CLCA -> if (hasStyle) emptySet() else expectStyle?.contests()?.toSet() ?: emptySet()
            AuditType.POLLING -> expectStyle?.contests()?.toSet() ?: cvr.contests().toSet()
        }
        assertEquals(expectPossibleContests, card.possibleContests.toSet(), "possibleContests")

        val expectContests = when (auditType) {
            AuditType.ONEAUDIT -> {
                if (card.votes != null)
                    card.votes.keys.toSet() // not correct; noStyles means cvrs are incomplete,
                                            // but then we need poolId to see what pool; interferes with pooled vs nonpooled cards
                                            // so cant do this case with Cvr as input
                else
                    expectPossibleContests
            }
            AuditType.CLCA -> if (hasStyle) card.votes!!.keys.toSet() else expectPossibleContests
            AuditType.POLLING -> expectStyle?.contests()?.toSet() ?: cvr.contests().toSet()
        }
        assertEquals(expectContests, card.contests().toSet(), "card.contests()")
    }
}

fun Cvr.show() = buildString {
    append("$id phantom=$phantom votes={")
    votes.toSortedMap().forEach { (id, cands) -> append("$id: ${cands.contentToString()}, ") }
    append("} poolId=$poolId")
}

fun AuditableCard.show() = buildString {
    append("$location phantom=$phantom")
    if (votes != null) {
        append(" votes={")
        votes.toSortedMap().forEach { (id, cands) -> append("$id: ${cands.contentToString()}, ") }
        append("}")
    }
    append(" poolId=$poolId")
    append(" possibleContests=${possibleContests.contentToString()}")
    append(" contests=${contests().contentToString()}")
    append(" cardStyle=$cardStyle")

}
