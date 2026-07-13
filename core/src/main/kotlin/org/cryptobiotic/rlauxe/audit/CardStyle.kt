package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.util.nfn
import java.util.BitSet
import kotlin.collections.forEach

/* From SamplePopulations.md
 * CardStyle = the full and exact list of contests on a card.
 * card.exactContests = list of contests that are on this card = CardStyle = "we know exactly what contests are on this card".
 * card.possibleContests = list of contests that might be on this card.
 * Batch = "population batch" = a distinct container of cards, from which we can retreive named cards (even if its just by an index into a sorted list).
 * batch.possibleContests = list of contests that are in this batch.
 * batch.hasExactContests = true if all cards in the batch have a single known CardStyle = "we know exactly what contests are on each card".
 */
interface StyleIF {
    fun name(): String
    fun id(): Int
    fun possibleContests(): IntArray // the set of contests that might be on any card in the batch
    fun hasExactContests(): Boolean // aka hasStyle: if all cards have exactly the contests in possibleContests()
    fun hasContest(contestId: Int): Boolean // "is in possibleContests()"
    fun contestIdSet(): Set<Int> = possibleContests().toList().toSet()
    fun ncards(): Int

    // if you have these, then you're a CardPool
    //   fun votesAndUndervotes(contestId: Int): Vunder
}

// the pool name is the first token of the style name
fun StyleIF.poolName(): String {
    val split = this.name().split("-",".")
    return split[0]
}

data class CardStyle(
    val name: String,
    val id: Int,
    val possibleContests: IntArray,      // the list of possible contests.
    val hasExactContests: Boolean,       // aka hasStyle: if all cards have exactly the contests in possibleContests;
) : StyleIF {
    val maxId = possibleContests.maxOrNull() ?: 1
    val bitset: BitSet
    var ncards = 0  // optional

    init {
        bitset = BitSet(maxId)
        possibleContests.forEach { bitset.set(it) }
    }

    constructor(id: Int, contestIds: Set<Int>, hasExactContests: Boolean): this("style$id", id, contestIds.toList().sorted().toIntArray(), hasExactContests)

    override fun ncards() = ncards
    fun setNcards(ncards:Int): CardStyle {
        this.ncards = ncards
        return this
    }

    override fun name() = name
    override fun id() = id
    override fun hasExactContests() = hasExactContests
    override fun hasContest(contestId: Int) = bitset.get(contestId)
    override fun possibleContests() = possibleContests

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CardStyle) return false

        if (id != other.id) return false
        if (hasExactContests != other.hasExactContests) return false
        if (name != other.name) return false
        if (!possibleContests.contentEquals(other.possibleContests)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + hasExactContests.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + possibleContests.contentHashCode()
        return result
    }

    fun show(): String {
        return "${nfn(id, 3)} ${nfn(ncards, 5)} ${possibleContests.contentToString()}"
    }

    override fun toString(): String {
        val sortedContests = possibleContests.toList().sorted()
        return "CardStyle(name=$name, id=$id, ncards= $ncards, contests=${sortedContests} exact=$hasExactContests"
    }

    companion object {
        // dont use these names for other batches
        val fromCvr = "_fromCvr"
        val phantoms = "_phantoms"
        // val unknown = CardStyle("unknown", -1, intArrayOf(), true) // kludge

        //  assumes cvrsContainUndervotes, use regular batch if not.
        val fromCvrStyle = CardStyle(fromCvr, -1, intArrayOf(), true)
        val phantomStyle = CardStyle(phantoms, -2, intArrayOf(), true)

        // card must contain votes if fromCvrStyle or phantomStyle
        fun useVotes(styleId: Int): Boolean = styleId == -1 ||  styleId == -2

        fun from(id: Int, contests: Set<Int>) = CardStyle(
            "cardStyle$id", id, contests.toList().sorted().toIntArray(), true)
    }
}

/*
fun makeCardStylesFromCvrs(cvrs: List<Cvr>, show: Boolean = false): Map<Set<Int>, CardStyle> {
    val cardStyleMap = mutableMapOf<Set<Int>, CardStyle>()
    cvrs.forEach { cvr ->
        val csc = cardStyleMap.getOrPut(cvr.votes.keys) { CardStyle(cardStyleMap.size + 1, cvr.votes.keys) }
        csc.ncards++
    }

    if (show) {
        println("\ncard styles  (${cardStyleMap.size})")
        println("id  count contests")
        val sortedCardStyles = cardStyleMap.toList().sortedBy { it.second.ncards }
        sortedCardStyles.forEach { (_, pv) ->
            println(pv)
        }
    }
    return cardStyleMap
} */

/*
timeConsistentSampling with CardStyle.hasContest using
	IntArray.contains:
		ncards = 4982786, included = 142470869 that took 106.1 s= 0.021296118276000614 ms/card
	BitSet.get:
		ncards = 4982786, included = 142470869 that took 46.50 s= 0.009329921052198509 ms/card
		ncards = 4982786, included = 142470869 that took 46.50 s= 0.009330523125014801 ms/card


why so much more than
	timeReadProto (100000):  ncards = 4982747, took 16.35 s = 0.003279917683960273 ms/card
	time to read all cards = 16.343 secs
?

remove everything but the card iterator:

2026-05-16 07:44:04.825 INFO  using cardsProtoFile at /home/stormy/rla/cases/corla/consistent/audit/cards.proto
	ncards = 4982786, included = 0 that took 20.36 s= 0.004084060603846924 ms/card

is it becazuse we have 723 contest, so need 723/64 = 12 longs
instead of fitting into one word ?? sems unlikely but...

use idxToId = Map<Int, Int>
	ncards = 4982786, included = 142470869 that took 96.04 s= 0.01927255154044344 ms/card
use Set<Id>
	ncards = 4982786, included = 142470869 that took 98.93 s= 0.019853752499103913 ms/card
use ByteArray[maxId]
	ncards = 4982786, included = 142470869 that took 43.04 s= 0.00863553040407515 ms/card (Bitset has 46)
	ncards = 4982786, included = 142470869 that took 46.38 s= 0.009306640903301888 ms/card

run actual sampling from viewer using BitSet:

2026-05-16 08:01:30,074 [    100360] INFO  ConsistentSampling -  consistentSampling read 3879127 and chose 22405 cards; took 52.23 s

(was 2026-05-16 06:27:56,359 [  47226432] INFO  ConsistentSampling -  consistentSampling read 3690306 and chose 22417 cards; took 83.60 s)

would like to find another factor of 2....
 */


