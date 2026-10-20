package org.cryptobiotic.rlauxe.corlacvr

import org.apache.commons.csv.CSVRecord
import org.cryptobiotic.rlauxe.audit.CardStyle
import kotlin.collections.flatten
import kotlin.text.isEmpty


// scenario_ballot_type_present
// has two ballot styles with same contest set [0,1], these generate a single CardStyle
// and ballot style [1] has rows with different set [0], [0,1]
// row 19 could create a second CardStyle with name "1+1" meaning  balot type 1, variant1, with different contest set
//                    type votes
// 9,1,1,9,1-1-9,P1,    1, 1,0,1,0
// 12,1,1,12,1-1-12,P1, 2, 0,1,0,1
// 19,1,1,19,1-1-19,P1, 1, 1,0,,

data class CvrCardStyle(val cardType: String, val contestIds: Set<Int>) {
    var ncards: Int = 0 // not part of equals
    fun contains(contestId: Int) = contestIds.contains(contestId)
    fun setNcards(n:Int): CvrCardStyle {
        this.ncards = n
        return this
    }

    // data class CardStyle(
    //    val name: String,
    //    val id: Int,
    //    val possibleContests: IntArray,      // the list of possible contests.
    //    val hasExactContests: Boolean,       // aka hasStyle: if all cards have exactly the contests in possibleContests;
    //)
    fun toCardStyle(): CardStyle {
        return CardStyle(cardType, 0, contestIds.toList().toIntArray(), hasExactContests=true)
            .setNcards(ncards)
    }
}

data class CardStyleId(val ballotType: String, val contestIds: Set<Int>)

fun CSVRecord.isEmpty(): Boolean {
    val iter = this.iterator()
    while (iter.hasNext()) {
        val value = iter.next()
        if (!value.isEmpty()) return false
    }
    return true
}

class BallotStyles {
    val cardStyleMap = mutableMapOf<CardStyleId, CvrCardStyle>()
    val ballotTypes = mutableMapOf<String, List<CvrCardStyle>>() // all cardtypes for this ballotType
    var ballotStylesUnique = true

    fun cardStyles(): List<CvrCardStyle> {
        return cardStyleMap.values.sortedBy { it.ncards }.reversed()
    }

    fun add(cvr: CvrRow) {
        val cleanBallotType = cleanCsvString(cvr.ballotType) // no commas
        val cvrStyleId = CardStyleId(cleanBallotType, cvr.contests())
        val cardStyle = cardStyleMap.getOrPut(cvrStyleId) {
            // if already exists, then there are duplicate contestId sets for this ballotType
            val cardList = ballotTypes[cleanBallotType]
            if (cardList == null) {
                val newStyle = CvrCardStyle(cleanBallotType, cvr.contests())
                ballotTypes[cleanBallotType] = listOf(newStyle)
                newStyle
            } else {
                ballotStylesUnique = false
                val newStyle = CvrCardStyle(cleanBallotType, cvr.contests())
                val oldStyles = cardList + listOf(newStyle)
                val newStyles = createCardStylesAndUnion(cleanBallotType, oldStyles)
                //val newStyles = if (oldStyles.size == 2) renameSortedCardStyles(cleanBallotType, oldStyles)
                //    else renameCardStylesWithUnion(cleanBallotType, oldStyles)

                ballotTypes[cleanBallotType] = newStyles

                // replace the old ones in cardStyleMap
                newStyles.filter { it.contestIds != cvr.contests() }.forEach { replace ->
                    cardStyleMap[CardStyleId(cleanBallotType, replace.contestIds)] = replace
                }

                // return the new one to be added to cardStyleMap
                newStyles.find { it.contestIds == cvr.contests() }!!
            }
        }

        cardStyle.ncards++
    }
}

// could always create the union in case its needed ?? and just call it ballotType instead of ballotType-Union
// also maybe use -A,-B,-C only for disjoint sets, otherwise use -1, -2...
// Then use style name on county cards; later merge stage adds county-style?
// advantage is you can redo individual counties
fun createCardStylesAndUnion(cleanBallotType: String, allStyles: List<CvrCardStyle>): List<CvrCardStyle> {
    // look for disjoints
    val disjointStyles = getDisjointStyles(allStyles)
    val renamedDisjoints = renameSortedCardStyles(cleanBallotType, disjointStyles, 'A')

    // create the union style if we dont already have it
    val unionIds = allStyles.map { it.contestIds }.flatten().toSet().toSortedSet()
    val existingUnion = allStyles.find { it.contestIds == unionIds }
    val unionStyle = existingUnion ?: CvrCardStyle(cleanBallotType, unionIds)

    val otherStyles = allStyles - disjointStyles - unionStyle
    val renamedOther = renameSortedCardStyles(cleanBallotType, otherStyles, '1')

    return listOf(unionStyle) + renamedDisjoints + renamedOther
}

fun getDisjointStyles(styles: List<CvrCardStyle>): List<CvrCardStyle> {
    val disjointSets = mutableListOf<CvrCardStyle>()
    val inDisjointSet = mutableSetOf<Int>()

    // start with smallest
    val sortedStyles = styles.sortedBy { it.contestIds.size }

    for (style in sortedStyles) {
        // Check if the current set shares any Ints with already accepted sets
        val hasOverlap = style.contestIds.any { it in inDisjointSet }

        if (!hasOverlap) {
            disjointSets.add(style)
            inDisjointSet.addAll(style.contestIds) // Track these Ints so future sets can't reuse them
        }
    }

    return if (disjointSets.size > 1) disjointSets else emptyList()
}

// adjust the cardType names to be ballotType-A, ballotType-B, ...
fun renameSortedCardStyles(cleanBallotType: String, allStyles: List<CvrCardStyle>, firstChar: Char): List<CvrCardStyle> {
    val allStylesSorted : List<CvrCardStyle> = sortOnIntArray(allStyles)
    // val firstChar = 'A'
    val newStyles= allStylesSorted.mapIndexed { idx, it ->
        it.copy(cardType = cleanBallotType + "-${firstChar+idx}").setNcards(it.ncards)
    }
    return newStyles
}

fun sortOnIntArray(list: List<CvrCardStyle>): List<CvrCardStyle> {
    return list.sortedWith { a, b ->
        var result = 0

        // assumes contestIds are sorted
        val aiter = a.contestIds.iterator()
        val biter = b.contestIds.iterator()
        while (aiter.hasNext() && biter.hasNext()) {
            result = aiter.next().compareTo(biter.next())
            if (result != 0) break
        }

        // If all compared elements match, the shorter array comes first
        if (result == 0) a.contestIds.size.compareTo(b.contestIds.size) else result
    }
}

/*
    fun renameCardStylesWithUnion(cleanBallotType: String, allStyles: List<CvrCardStyle>): List<CvrCardStyle> {
        // look for unions and disjoints
        val largest = allStyles.maxBy { it.contestIds.size }
        val others = allStyles.filter { it != largest }
        val union = others.map { it.contestIds }.flatten().toSet()
        if (union == largest.contestIds) {
            val result = mutableListOf<CvrCardStyle>()
            result.add(largest.copy(cardType = cleanBallotType + "-Union").setNcards(largest.ncards))
            result.addAll(renameSortedCardStyles(cleanBallotType, others))
            return result
        }
        return renameSortedCardStyles(cleanBallotType, allStyles)
    } */