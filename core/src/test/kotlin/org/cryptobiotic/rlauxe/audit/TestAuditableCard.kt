package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.util.CvrBuilder2
import org.junit.jupiter.api.Test
import kotlin.random.Random
import kotlin.test.assertEquals

class TestAuditableCard {

    @Test
    fun testCvr() {
        repeat(1000) {
            val cvr = makeCvr(it, 11, 15)
            val card = AuditableCard.fromCvr(cvr, it, it.toLong())
            assertEquals(cvr, card.cvr())
        }
    }
}

fun makeCvr(id: Int, ncontests: Int, ncandidates: Int): Cvr {
    val cvrb = CvrBuilder2(id.toString(),  false)
    repeat(ncontests) {
        val contestId = Random.nextInt(ncontests)
        val candidates = IntArray(ncandidates) { Random.nextInt(ncontests) }
        cvrb.addContest(contestId, candidates)
    }
    return cvrb.build()
}