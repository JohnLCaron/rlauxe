package org.cryptobiotic.rlauxe.corlaCounty

data class CountyCard (
   val imprintedId: String, 
   val location: String, 
   val style: String, // must have a style or "missing"
   val votes: Map<Int, IntArray>, // contest -> votedFor
   val poolName: String?, // must be set if its from a OneAudit CardPool
)