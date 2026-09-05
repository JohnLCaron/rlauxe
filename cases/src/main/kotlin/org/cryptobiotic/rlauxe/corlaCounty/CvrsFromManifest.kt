package org.cryptobiotic.rlauxe.corlaCounty

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.CardPool
import org.cryptobiotic.rlauxe.audit.CardPoolBuilder
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.cvr.CorlaCvrConverter
import org.cryptobiotic.rlauxe.cvr.CvrRow
import org.cryptobiotic.rlauxe.cvr.RedactedGroup
import org.cryptobiotic.rlauxe.cvr.cleanCsvString
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.util.sumContestTabulations
import org.cryptobiotic.rlauxe.util.tabulateCards

data class ManifestId(val id: String) {
    var card : AuditableCard? = null
}

class CvrsFromManifest(val variant: ElectionVariant,
                       val input: CorlaCountyInput,
                       val converter: CorlaCvrConverter,
                       val convertedCvrs: List<AuditableCard>,
                       val infos: Map<Int, ContestInfo>,
                       val redactedGroups: List<RedactedGroup>) {

    var countMiss = 0 // count of Cvrs not in the manifest
    var countDup = 0  // count of duplicate ids in the Cvrs
    val countUnmatched : Int // count of Manifest not in the Cvrs; presumed to be == redacted CVRs

    val manifestSize : Int
    val cvrTabs : Map<Int, ContestTabulation>
    val redactedPools: List<CardPool>

    init {
        val manifestIds = mutableMapOf<String, ManifestId>()
        input.readCountyManifest().forEach { batch ->
            repeat(batch.nballotCards) { recordId ->
                val want = "${batch.tabulatorNum}-${batch.batchId}-${recordId + 1}"
                manifestIds[want] = ManifestId(want)
            }
        }
        manifestSize = manifestIds.size

        convertedCvrs.forEach { card ->
            val manifestMatch = manifestIds[card.id]
            if (manifestMatch != null) {
                if (manifestMatch.card != null) countDup++
                manifestMatch.card = card
            } else
                countMiss++
        }

        countUnmatched = manifestIds.values.count { it.card == null }
        println("countMiss=$countMiss countUnmatched=$countUnmatched countDup=$countDup")

        cvrTabs = tabulateCards(convertedCvrs.iterator(), infos)

        val redactedPoolBuilders = makeRedactedPools(variant)

        if (redactedPoolBuilders.size == 1) {
            redactedPoolBuilders.first().setNcards(countUnmatched)
        } else {
            setRedactedNCards(cvrTabs, redactedPoolBuilders)
        }

    }

    fun setRedactedNCards(cvrTabs: Map<Int, ContestTabulation>, poolBuilders: List<CardPoolBuilder>) {
        // assume that the undervote Pct in the redacted Groups is the same as in the unredacted CVRs

    }

    fun redactedTabulation(): Map<Int, ContestTabulation> {
        var sumTabs = mutableMapOf<Int, ContestTabulation>()
        redactedGroups.forEach { redacted: RedactedGroup ->
            val groupTab: Map<Int, ContestTabulation> = converter.convertToContestTabulation(redacted)
            sumTabs.sumContestTabulations(groupTab)
        }
        return sumTabs
    }

    fun makeRedactedPools(variant: ElectionVariant): List<CardPoolBuilder> {
        return if (variant.onePool) listOf(convertRedactedToOneCardPool())
               else convertRedactedToCardPool()
    }

    private fun convertRedactedToCardPool(): List<CardPoolBuilder> {
        var id = 1
        return redactedGroups.map { redacted: RedactedGroup ->
            //// the redacted groups dont have undervotes, so we should try to generate reasonable undervote counts
            // but... now we are just setting the vote totals, ignoring ncards and undervotes.

            // val contestTabs = redacted.contestVotes.mapValues{ ContestTabulation(infos[it.key]!!, it.value, ncards=0) }
            val contestTabs: Map<Int, ContestTabulation> = converter.convertToContestTabulation(redacted)

            val name = cleanCsvString(redacted.ballotType)
            // in this case, nlines == ncards
            val hasExactContests = !redacted.ballotType.contains("&") // has multiple card styles
            CardPoolBuilder.fromMinVotesNeeded(name, id++, hasExactContests=hasExactContests, infos, contestTabs)
                .setNcards(redacted.ncards())
        }
    }

    // note that ncards is not set here
    private fun convertRedactedToOneCardPool(): CardPoolBuilder {
        var sumTabs = mutableMapOf<Int, ContestTabulation>()
        redactedGroups.forEach { redacted: RedactedGroup ->
            // val groupTab = redacted.contestVotes.mapValues{ ContestTabulation(infos[it.key]!!, it.value, ncards=0) }
            val groupTab: Map<Int, ContestTabulation> = converter.convertToContestTabulation(redacted)
            sumTabs.sumContestTabulations(groupTab)
        }
        return CardPoolBuilder.fromMinVotesNeeded("RedactedPool", 1, hasExactContests=false, infos, sumTabs)
    }

    // originally
    fun check() {
        val corlaCvrs = input.readCorlaCvrs()

        val nCvrs = corlaCvrs.cvrs().size
        println("${input.cvrsSource}: nrows = ${corlaCvrs.nrows()} cvrs size = ${corlaCvrs.cvrs().size}")

        val redactedGroupSize = corlaCvrs.redactedGroups().size
        val redactedNCards = corlaCvrs.redactedGroups().sumOf { it.ncards() }
        println("\nRedacted groups (${redactedGroupSize})")
        corlaCvrs.redactedGroups().forEach { println("  $it") }

        println("redacted ncards = ${redactedNCards}")
        val totalCvrs = nCvrs + redactedNCards
        println("cvrs + redacted ncards = ${totalCvrs}")

        val manifestBatches = input.readCountyManifest()
        val manifestNCards = manifestBatches.sumOf { it.nballotCards }
        println("\n${input.manifestSource}")
        println("  manifestNCards = $manifestNCards")

        val estRedactedNcards = manifestNCards - corlaCvrs.cvrs().size
        println("  est redacted ncards= ${estRedactedNcards}")
        val missingNcards = manifestNCards - totalCvrs
        print("  sumManifest - totalCvrs = ${missingNcards}; ")
        if (missingNcards == 0) print(" AGREE!")
        else if (missingNcards < 0) print(" manifest not up to date ??")
        else if (redactedGroupSize == 1) print(" single group should be set to $estRedactedNcards, currently $redactedNCards ")
        else print(" need $missingNcards more redacted cards")
        println()

        /////////////////////////////////////////////////////////////////
        val cvrMap = corlaCvrs.cvrs().associate { it.imprintedId to CvrId(it) }
        println("\nmatch manifest cards to cvrs")

        var countMiss = 0
        var countDup = 0
        manifestBatches.forEach { batch ->
            repeat(batch.nballotCards) { recordId ->
                val want = "${batch.tabulatorNum}-${batch.batchId}-${recordId + 1}"
                val cvr = cvrMap[want]
                if (cvr != null) {
                    if (cvr.matched) countDup++
                    cvr.matched = true
                } else
                    countMiss++
                // println("didnt find cvr $want")
            }
        }
        println("  # manifest cards not found in cvrs == $countMiss countDup == $countDup")

        println("\nmatch cvrs to manifest")
        var count = 0
        cvrMap.forEach { (key, id) ->
            if (!id.matched) {
                // println("  $key")
                count++
            }
        }
        println("  # cvrs not found in manifest == $count")
        if (count > 0) println("   probably manifest file was updated but not in auditcenter")

        // println("  countMiss - missing from cvrs == ${countMiss - missing} redactedCards == $redactedCards")
    }
}

data class CvrId(val tabulatorNum: Int, val batchId: String, val recordId: Int) {
    var matched = false

    constructor(cvr: CvrRow) : this(cvr.tabulatorNum, cvr.batchId, cvr.recordId) {
        if (cvr.imprintedId != "${cvr.tabulatorNum}-${cvr.batchId}-${cvr.recordId}")
            print("")
        require(cvr.imprintedId == "${cvr.tabulatorNum}-${cvr.batchId}-${cvr.recordId}")
    }
}