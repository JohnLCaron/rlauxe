package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.CardStyle
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.estimate.makeFuzzedCardFromCard

private const val debug = false

/////////////////////////////////////////////////////////////////////////////////////////////////////////
// TODO this is pretty crude, just randomly changing shit.

fun makeFuzzedCvrsForClca(infoList: List<ContestInfo>, cvrs: List<Cvr>, fuzzPct: Double?) : List<Cvr> {
    if (fuzzPct == null || fuzzPct == 0.0) return cvrs
    val infos = infoList.associate{ it.id to it }
    val isIRV = infoList.associate { it.id to it.isIrv }
    var countChanged = 0
    val result =  cvrs.map { cvr ->
        val card = AuditableCard( cvr.id, null, 0, prn=0, cvr.phantom, null, cvr.votes, cardStyle = CardStyle.fromCvrBatch)
        val fuzzedCard = makeFuzzedCardFromCard(infos, isIRV, card, fuzzPct)
        val fuzzedCvr = Cvr( cvr.id, fuzzedCard.votes!!, cvr.phantom, cvr.poolId)
        if (fuzzedCvr != cvr)
            countChanged++
        fuzzedCvr
    }
    // println("changed $countChanged cards pct = ${countChanged/cvrs.size.toDouble()}")
    return result
}
