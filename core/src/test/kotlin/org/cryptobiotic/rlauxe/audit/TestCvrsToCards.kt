package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.oneaudit.CardPoolTest
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TestCvrsToCards {

    // TODO REDO with mvrsToAuditableCardsList
    @Test
    fun testCvrsWithStylesToCardsForClca() {
        val cvrr = makeCvr(abs(Random.nextInt()), 2 + Random.nextInt(3), 2 + Random.nextInt(2))

        val auditType = AuditType.CLCA

        // simple hasStyle with or without poolid. aka "cvrs are complete".
        var hasCardStyles = false

        var cvr = cvrr.copy(poolId=null)
        // with no population, only the cvrs are present.
        var target = mvrsToAuditableCardsTest(auditType, listOf(cvr), batches=null)
        var card = target.first()
        testOneTarget("** clca complete cvrs", cvr, card, auditType, hasCardStyles, CardStyle.fromCvr)

        cvr = cvrr.copy(poolId=1)
        target = mvrsToAuditableCardsTest(auditType, listOf(cvr), batches=null)
        card = target.first()
        testOneTarget("clca hasStyle and poolIds", cvr, card, auditType, hasCardStyles, expectStyle = CardStyle.fromCvr)

        val cardStyle = CardStyle("you", 1, intArrayOf(0,1,2,3,4), false)
        // doesnt make sense to use; hasStyle means use cvr
        hasCardStyles = true
        cvr = cvrr.copy(poolId=1)
        target = mvrsToAuditableCardsTest(auditType, listOf(cvr), batches=listOf(cardStyle))
        card = target.first()
        testOneTarget("clca hasStyle and poolIds and styles", cvr, card, auditType, hasCardStyles, cardStyle.name(), expectBatch = cardStyle)

        // noStyle means votes isnt complete, so you need cardStyles and poolIds. should test if votes cubset of cardpool contests
        hasCardStyles = true
        cvr = cvrr.copy(poolId=1)
        target = mvrsToAuditableCardsTest(auditType, listOf(cvr), batches=listOf(cardStyle))
        card = target.first()
        testOneTarget("** clca incomplete cvrs", cvr, card, auditType, hasCardStyles, cardStyle.name(), expectBatch = cardStyle)

        // what if you dont supply the poolId?
        cvr = cvrr.copy(poolId=null)
        target = mvrsToAuditableCardsTest(auditType, listOf(cvr), batches=listOf(cardStyle))
        card = target.first()
        assertEquals(CardStyle.fromCvr, card.styleName(), "no poolId")

        // what if you dont supply the cardStyles? FAIL
        cvr = cvrr.copy(poolId=1)
        target = mvrsToAuditableCardsTest(auditType, listOf(cvr), batches=null)
        card = target.first()
        assertEquals(CardStyle.fromCvr, card.styleName(), "no pools")
    }

    @Test
    fun testCvrsWithStylesToCardsForPolling() {
        val cvrr = makeCvr(abs(Random.nextInt()), 2 + Random.nextInt(3), 2 + Random.nextInt(2), poolId=1)

        val auditType = AuditType.POLLING
        var hasCardStyles = false

        // polling but no batches
        var cvr = cvrr.copy(poolId=null)  // no poolId
        var message = assertFailsWith<RuntimeException> {
            mvrsToAuditableCardsTest(auditType, listOf(cvr), batches = null)
        }.message!!
        assertEquals("cardStyle '_fromCvr' must have non-null votes", message)

        val batch = CardStyle("cardstyle1", 1,intArrayOf(0,1,2,3,4), false)

        // what happens if the poolId doesnt match ??
        cvr = cvrr.copy(poolId=2) // poolId doesnt match
        message = assertFailsWith<RuntimeException> {
            mvrsToAuditableCardsTest(auditType, listOf(cvr), batches = listOf(batch))
        }.message!!
        assertEquals("cardStyle '_fromCvr' must have non-null votes", message)

        // successfully use pool 1
        hasCardStyles = true
        cvr = cvrr.copy(poolId=1)
        var target = mvrsToAuditableCardsTest(auditType, listOf(cvr), batches = listOf(batch))
        testOneTarget("** poll with pool", cvr, target.first(), auditType, hasCardStyles, batch.name(), expectBatch = batch)
    }

    @Test
    fun testCvrsWithStylesToCardsForOA() {
        val cvrr = makeCvr(abs(Random.nextInt()), 2 + Random.nextInt(3), 2 + Random.nextInt(2))
        val cvrc = cvrr.copy(poolId=null)
        val cvrp = cvrr.copy(poolId=2)
        val cvrs = listOf(cvrc, cvrp)

        var auditType = AuditType.ONEAUDIT
        var hasCardStyles = true

        val cardPool = CardPoolTest("oapool2", 2, intArrayOf(0, 1, 2), hasCardStyles, totalCards = 42)
        var target = mvrsToAuditableCardsTest(auditType, cvrs, batches=listOf(cardPool), )
        testOneTarget("oa hasStyle", cvrc, target[0], auditType, hasCardStyles, CardStyle.fromCvr)
        testOneTarget("oa hasStyle pooled", cvrp, target[1], auditType, hasCardStyles, cardPool.name(), expectBatch = cardPool)

        // noStyle means must supply the list of possibleContests for pooled data and cvrs
        hasCardStyles = true
        target = mvrsToAuditableCardsTest(auditType, cvrs, batches=listOf(cardPool), )
        testOneTarget("oa noStyle", cvrc, target[0], auditType, hasCardStyles, CardStyle.fromCvr)
        testOneTarget("oa noStyle pooled", cvrp, target[1], auditType, hasCardStyles, cardPool.name(), expectBatch = cardPool)
    }

    fun testOneTarget(what: String, cvr: Cvr, card: AuditableCard, auditType: AuditType, hasCardStyles: Boolean, expectStyle:String?, expectBatch:CardStyleIF?=null) {
        println("$what [$auditType hasCardStyles:$hasCardStyles]:")
        println("  ${cvr.show()}")
        println("  ${card.show()}")
        println()

        if (auditType.isClca()) {
            assertEquals(cvr, card.toCvr())
            assertNotNull(card.votes)
            assertEquals(cvr.votes, card.votes)
            assertEquals(null, card.poolId(), "poolId")

        } else if (auditType.isPolling()) {
            assertNull(card.votes)

        }  else if (auditType.isOA()) {
            assertEquals(cvr.poolId, card.poolId(), "poolId")
        }

        assertEquals(cvr.id, card.id)
        assertEquals(cvr.phantom, card.phantom)
        assertEquals(expectStyle, card.styleName(), "cardStyle")

        val styleContests = expectBatch?.possibleContests()?.toList()?.toSet() ?: emptySet()
        val expectContests = when (auditType) {
            AuditType.ONEAUDIT -> {
                if (card.votes != null)
                    card.votes.keys.toSet() // not correct; noStyles means cvrs are incomplete,
                                            // but then we need poolId to see what pool; interferes with pooled vs nonpooled cards
                                            // so cant do this case with Cvr as input
                else
                    styleContests
            }
            AuditType.CLCA -> if (hasCardStyles) styleContests else card.votes!!.keys.toSet()
            AuditType.POLLING -> expectBatch?.possibleContests()?.toSet() ?: emptySet()
        }
        if (expectContests != card.possibleContests().toSet())
            print("${card.possibleContests()}")
        assertEquals(expectContests, card.possibleContests().toSet(), "card.contests()")
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
    append(" poolId=${poolId()}")
    append(" contests=${possibleContests().contentToString()}")
    append(" cardStyle=${styleName()}")

}
