package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.util.CloseableIterable
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.ZipReader
import org.cryptobiotic.rlauxe.util.emptyCloseableIterator
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.file.Files
import kotlin.io.path.Path

data class AuditableCardM (
    val id: String, // enough info to find the card for a manual audit.
    val location: String?, // enough info to find the card for a manual audit.
    val index: Int,  // index into the original, canonical list of cards
    val prn: Long,   // psuedo random number
    val phantom: Boolean,
    val styleName: String,
    val poolId: Int?, // must be set if its from a CardPool
    val contestIds: IntArray,   // these form the votes map. set style if different
    val contestStarts: IntArray,
    val candidates: IntArray,
): AuditableCardIF {
    // you can change the style but not null it; could also prevent changing altogether after its set
    private var style: StyleIF? = null
    fun setStyle(style: StyleIF): AuditableCardM {
        require(styleName == style.name())
        this.style = style
        return this
    }
    override fun style(): StyleIF = style!! // TODO

    // TODO could ignore useCvr
    private val useCvr = CardStyle.useVotes(styleName)
    private val votes: Map<Int, IntArray>? by lazy {
        if (contestIds.isEmpty()) null else {
            val lastIndex = contestIds.size - 1
            val makeVotes = mutableMapOf<Int, IntArray>()
            contestIds.forEachIndexed { index, contestId ->
                val start = contestStarts[index]
                val end = if (index < lastIndex) contestStarts[index + 1] else candidates.size
                if (start > end || end > candidates.size)
                    makeVotes[contestId] = candidates.sliceArray(start until end)
            }
            makeVotes.toMap()
        }
    }

    override fun id() = id
    override fun location() = location ?: id()
    override fun index() = index
    override fun prn() = prn
    override fun phantom() = phantom
    override fun poolId(): Int? = poolId
    override fun styleName() = styleName

    override fun votes(): Map<Int, IntArray>? = votes
    override fun votes(contestId: Int): IntArray? = votes?.get(contestId)

    override fun hasContest(contestId: Int): Boolean {
        return if (!useCvr && style != null) style!!.hasContest(contestId)
        else contestIds.contains(contestId)
    }

    override fun possibleContests() : IntArray {
        return when {
            (!useCvr && style != null) -> style!!.possibleContests()
            else -> votes!!.keys.toList().sorted().toIntArray() // assumes cvrsContainUndervotes, set style if not.
        }
    }

    override fun hasMarkFor(contestId: Int, candidateId: Int): Int {
        val contestVotes = votes(contestId)
        return if (contestVotes == null) 0
                else if (contestVotes.contains(candidateId)) 1 else 0
    }

    // TODO where is this used?
    override fun hasStyle(): Boolean {
        TODO("Not yet implemented")
    }

    // TODO where is this used?
    override fun toCvr(): Cvr {
        TODO("Not yet implemented")
    }

}

///////////////////////////////////////////////////////////////////////////////////////////

fun readCardCsvM(line: String): AuditableCardM {
    val tokens = line.split(",")
    val ttokens = tokens.map { it.trim() }

    var idx = 0
    val id = ttokens[idx++]
    val locationToken = ttokens[idx++]
    val location = locationToken.ifEmpty { null }
    val index = ttokens[idx++].toInt()
    val sampleNum = ttokens[idx++].toLong(radix=16)
    val phantom = ttokens[idx++] == "yes"
    val poolIdToken = ttokens[idx++]
    val poolId = if (poolIdToken.isEmpty()) null else poolIdToken.toInt()
    val styleName = ttokens[idx++].trim()

    // if clca, list of actual contests and their votes
    if (idx < ttokens.size-1) {
        val contestsStr = ttokens[idx++].trim()
        val contestsTokenTrimmed = contestsStr.split(" ").map { it.trim() }

        val contestIds = mutableListOf<Int>()
        contestsTokenTrimmed.forEach { tok ->
            if (tok.isNotEmpty()) contestIds.add(tok.toInt())
        }

        val candidates = mutableListOf<Int>()
        val contestStarts = mutableListOf<Int>()
        var start = 0

        contestIds.forEach {
            contestStarts.add(start)
            val vtokens = ttokens[idx++]
            val cands: List<Int> =
                if (vtokens.isEmpty()) emptyList()
                else vtokens.split(" ").map { it.trim().toInt() }
            candidates.addAll(cands)
            start += cands.size
        }
        return AuditableCardM(id, location, index, sampleNum, phantom, styleName, poolId,
            contestIds.toIntArray(), contestStarts.toIntArray(), candidates.toIntArray())
    }
    return AuditableCardM(id, location, index, sampleNum, phantom, styleName, poolId,
        intArrayOf(), intArrayOf(), intArrayOf())
}

class IteratorCardsCsvStreamM(input: InputStream, bufferSize: Int, val styles: List<StyleIF>?): CloseableIterator<AuditableCardM> {
    val styleMap = if (styles == null) null else styles.associateBy { it.name() }
    val reader = BufferedReader(InputStreamReader(input),bufferSize)
    var nextLine: String? = null
    var countLines  = 0

    init {
        reader.readLine() // get rid of header line
    }

    override fun hasNext() : Boolean {
        if (nextLine == null) {
            countLines++
            nextLine = reader.readLine()
        }
        return nextLine != null
    }

    override fun next(): AuditableCardM {
        if (!hasNext()) throw NoSuchElementException()
        val cardm: AuditableCardM =  readCardCsvM(nextLine!!)
        val style = styleMap?.get(cardm.styleName)
        if (style != null)
            cardm.setStyle(style)
        nextLine = null
        return cardm
    }

    override fun close() {
        reader.close()
    }
}

fun readCsvAndMergeCards(csvFile: String, styles: List<StyleIF>?): CloseableIterator<AuditableCardM> {
    val useFilename: String = if (Files.exists(Path(csvFile))) csvFile
        else if (Files.exists(Path("$csvFile.zip"))) "$csvFile.zip" // TODO unzip
        else {
            println("readAndMergeCards $csvFile or $csvFile.zip does not exist")
            return emptyCloseableIterator()
        }

    // TODO time with and without zip
    return if (useFilename.endsWith(".zip")) {
            val reader = ZipReader(useFilename)
            val input = reader.inputStream()
            IteratorCardsCsvStreamM(input, 8192, styles)
    } else {
            IteratorCardsCsvStreamM(File(useFilename).inputStream(), 8192, styles)
    }
}
