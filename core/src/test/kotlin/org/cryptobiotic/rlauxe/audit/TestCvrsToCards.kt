package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.oneaudit.CardPoolTest
import org.cryptobiotic.rlauxe.util.Prng
import kotlin.test.Test
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
        var target = mvrsToAuditableCardsTestM(auditType, listOf(cvr), styles=null)
        var card = target.first()
        testOneTarget("** clca complete cvrs", cvr, card, auditType, hasCardStyles, CardStyle.fromCvrStyle.id)

        cvr = cvrr.copy(poolId=1)
        target = mvrsToAuditableCardsTestM(auditType, listOf(cvr), styles=null)
        card = target.first()
        testOneTarget("clca hasStyle and poolIds", cvr, card, auditType, hasCardStyles, CardStyle.fromCvrStyle.id)

        val cardStyle = CardStyle("you", 1, intArrayOf(0,1,2,3,4), false)
        // doesnt make sense to use; hasStyle means use cvr
        hasCardStyles = true
        cvr = cvrr.copy(poolId=1)
        target = mvrsToAuditableCardsTestM(auditType, listOf(cvr), styles=listOf(cardStyle))
        card = target.first()
        testOneTarget("clca hasStyle and poolIds and styles", cvr, card, auditType, hasCardStyles, cardStyle.id(), expectBatch = cardStyle)

        // noStyle means votes isnt complete, so you need cardStyles and poolIds. should test if votes cubset of cardpool contests
        hasCardStyles = true
        cvr = cvrr.copy(poolId=1)
        target = mvrsToAuditableCardsTestM(auditType, listOf(cvr), styles=listOf(cardStyle))
        card = target.first()
        testOneTarget("** clca incomplete cvrs", cvr, card, auditType, hasCardStyles, cardStyle.id(), expectBatch = cardStyle)

        // what if you dont supply the poolId?
        cvr = cvrr.copy(poolId=null)
        target = mvrsToAuditableCardsTestM(auditType, listOf(cvr), styles=listOf(cardStyle))
        card = target.first()
        assertEquals(CardStyle.fromCvrStyle.id(), card.styleId, "no styleId")

        // what if you dont supply the cardStyles? FAIL
        cvr = cvrr.copy(poolId=1)
        target = mvrsToAuditableCardsTestM(auditType, listOf(cvr), styles=null)
        card = target.first()
        assertEquals(CardStyle.fromCvrStyle.id(), card.styleId, "no styleId")
    }

    @Test
    fun testCvrsWithStylesToCardsForPolling() {
        val cvrr = makeCvr(abs(Random.nextInt()), 2 + Random.nextInt(3), 2 + Random.nextInt(2), poolId=1)

        val auditType = AuditType.POLLING
        var hasCardStyles = false

        // polling but no batches

        var cvr = cvrr.copy(poolId=null)  // no poolId

        var message = assertFailsWith<RuntimeException> {
            mvrsToAuditableCardsTestM(auditType, listOf(cvr), styles= null)
        }.message!!
        assertEquals("card with style fromCvr or phantom must have non-null votes", message)

        val batch = CardStyle("cardstyle1", 1,intArrayOf(0,1,2,3,4), false)

        // what happens if the poolId doesnt match ??
        cvr = cvrr.copy(poolId=2) // poolId doesnt match
        message = assertFailsWith<RuntimeException> {
            mvrsToAuditableCardsTestM(auditType, listOf(cvr), styles= listOf(batch))
        }.message!!
        assertEquals("card with style fromCvr or phantom must have non-null votes", message)

        // successfully use pool 1
        hasCardStyles = true
        cvr = cvrr.copy(poolId=1)
        var target = mvrsToAuditableCardsTestM(auditType, listOf(cvr), styles= listOf(batch))
        testOneTarget("** poll with pool", cvr, target.first(), auditType, hasCardStyles, batch.id(), expectBatch = batch)
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
        var target = mvrsToAuditableCardsTestM(auditType, cvrs, styles=listOf(cardPool), )
        testOneTarget("oa hasStyle", cvrc, target[0], auditType, hasCardStyles, CardStyle.fromCvrStyle.id)
        testOneTarget("oa hasStyle pooled", cvrp, target[1], auditType, hasCardStyles, cardPool.id(), expectBatch = cardPool)

        // noStyle means must supply the list of possibleContests for pooled data and cvrs
        hasCardStyles = true
        target = mvrsToAuditableCardsTestM(auditType, cvrs, styles=listOf(cardPool), )
        testOneTarget("oa noStyle", cvrc, target[0], auditType, hasCardStyles, CardStyle.fromCvrStyle.id)
        testOneTarget("oa noStyle pooled", cvrp, target[1], auditType, hasCardStyles, cardPool.id(), expectBatch = cardPool)
    }

    fun testOneTarget(what: String, cvr: Cvr, card: AuditableCard, auditType: AuditType, hasCardStyles: Boolean, expectStyleId:Int?, expectBatch:StyleIF?=null) {
        println("$what [$auditType hasCardStyles:$hasCardStyles]:")
        println("  ${cvr.show()}")
        println("  ${card.show()}")
        println()

        if (auditType.isClca()) {
            assertEquals(cvr, card.toCvr())
            assertNotNull(card.votes())
            assertTrue(testVotesEqual(cvr.votes, card.votes()))
            assertEquals(cvr.poolId(), card.poolId(), "card.poolId() should be null")

        } else if (auditType.isPolling()) {
            assertNull(card.votes())

        }  else if (auditType.isOA()) {
            assertEquals(cvr.poolId, card.poolId(), "poolId")
        }

        assertEquals(cvr.id, card.id)
        assertEquals(cvr.phantom, card.phantom)
        assertEquals(expectStyleId, card.styleId, "cardStyle")

        val styleContests = expectBatch?.possibleContests()?.toList()?.toSet() ?: emptySet()
        val expectContests = when (auditType) {
            AuditType.ONEAUDIT -> {
                if (card.votes() != null)
                    card.votes()!!.keys.toSet() // not correct; noStyles means cvrs are incomplete,
                                            // but then we need poolId to see what pool; interferes with pooled vs nonpooled cards
                                            // so cant do this case with Cvr as input
                else
                    styleContests
            }
            AuditType.CLCA -> if (hasCardStyles) styleContests else card.votes()!!.keys.toSet()
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
    if (votes() != null) {
        append(" votes={")
        votes()!!.toSortedMap().forEach { (id, cands) -> append("$id: ${cands.contentToString()}, ") }
        append("}")
    }
    append(" poolId=${poolId()}")
    append(" contests=${possibleContests().contentToString()}")
    append(" styleId=${styleId}")
}

///////////////////////////////////////////////////////////////////////////////////

// TODO only used in testing; remove and replace with mvrsToAuditableCardsList where needed
fun mvrsToAuditableCardsTestM(
    type: AuditType,
    mvrs: List<Cvr>,
    styles: List<StyleIF>?,
    seed: Long? = null,
): List<AuditableCard> {

    val styleMap = styles?.associateBy{ it.id() } ?: emptyMap()
    val prng = if (seed != null) Prng(seed) else null

    var cardIndex = 0 // 0 based index

    return mvrs.map { org ->
        val style = styleMap[org.poolId]  // hijack poolId
        val hasCvr = type.isClca() || (type.isOA() && org.poolId == null)
        val votes = if (hasCvr) org.votes else null  // removes votes for pooled data

        val useBatch = when {
            org.phantom() -> CardStyle.phantomStyle
            (style != null) -> style
            else -> CardStyle.fromCvrStyle
        }

        AuditableCard.fromVotes(
            org.id,
            null,
            cardIndex++,
            prng?.next() ?: 0,
            phantom = org.phantom,
            styleId=useBatch.id(),
            votes = votes,
            poolId = org.poolId,
        ).setStyle(useBatch)
    }
}
