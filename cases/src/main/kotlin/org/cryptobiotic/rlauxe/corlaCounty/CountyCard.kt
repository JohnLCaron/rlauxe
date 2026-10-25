package org.cryptobiotic.rlauxe.corlaCounty

import org.cryptobiotic.rlauxe.corlacvr.ContestVotes

data class CountyCard (
   val imprintedId: String,
   val location: String,
   val style: String, // must have a style or "missing"
   val votes: List<ContestVotes>, // contest -> votedFor
   val poolName: String?, // must be set if its from a OneAudit CardPool
)