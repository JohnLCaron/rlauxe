package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.CvrIF


// interface CardIF {
//    fun id(): String // enough info to find the card for a manual audit.
//    fun location(): String // enough info to find the card for a manual audit.
//    fun index(): Int  // index into the original, canonical list of cards
//    fun prn(): Long   // psuedo random number
//    fun isPhantom(): Boolean
//
//    fun votes(): Map<Int, IntArray>?   // CVRs and phantoms
//    fun poolId(): Int?                 // must be set if its from a CardPool
//    fun styleName(): String            // "fromCvr" if no cardStyle and its from a CVR (then votes is non null)
//}

// change to

// could break the rule and return ProtoCard for speed....
// HEY consistent sampling only wants hasContest, optimize for that...
// which might want the card styles to be joined

//     val votes: Map<Int, IntArray>?,   // contestId -> candidateIds voted for; CVRs and phantoms
//                                      // might be more efficient to have contestIds in an IntArray, and all candidates voted for in another IntArray.
//                                      // common case is only one candidate voted for. Otherwise you need another IntArray for the #starting index.
//                                      // hasContest: maybe a bitMap? and factor out into a StyleIF?


interface AuditableCardIF: CardIF, CvrIF, SamplingCardIF {
    fun style(): StyleIF?            // "fromCvr" if no cardStyle and its from a CVR (then votes is non null)
    fun possibleContests() : IntArray
    // TODO is hasStyle really card specific? contest? audit?
    //    is it the same as "consistentSampling" or something else ??
    fun hasStyle(): Boolean // TODO

    // fun show(): String
    fun toCvr(): Cvr  // TODO can we get rid of?
}

interface SamplingCardIF {
    fun hasContest(contestId: Int): Boolean
    fun prn(): Long
}

// lets us serialize either CardNoStyle or AuditableCard
interface CardIF {
    fun id(): String // enough info to find the card for a manual audit.
    fun location(): String // enough info to find the card for a manual audit.
    fun index(): Int  // index into the original, canonical list of cards
    fun prn(): Long   // psuedo random number
    fun phantom(): Boolean

    fun votes(): Map<Int, IntArray>?   // CVRs and phantoms
    fun poolId(): Int?                 // must be set if its from a CardPool
    fun styleName(): String            // "fromCvr" if no cardStyle and its from a CVR (then votes is non null)
}
