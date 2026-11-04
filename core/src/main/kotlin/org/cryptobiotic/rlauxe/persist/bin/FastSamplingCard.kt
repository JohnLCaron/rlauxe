package org.cryptobiotic.rlauxe.persist.bin

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.SamplingCardIF
import org.cryptobiotic.rlauxe.audit.StyleIF
import org.cryptobiotic.rlauxe.util.CloseableIterator
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream

data class FastSamplingCard(val prn : Long, val style: StyleIF) : SamplingCardIF {
    override fun prn() = prn
    override fun hasContest(contestId: Int) = style.hasContest( contestId)
}

class FastSamplingCardIterator(inputFile: String, val styles: List<StyleIF>, bufferSize: Int): CloseableIterator<SamplingCardIF> {
    val binputStream = BufferedInputStream( FileInputStream(inputFile), bufferSize)
    val dos = DataInputStream(binputStream)

    var nextCard: FastSamplingCard? = null
    var count = 0

    override fun next() = nextCard!!

    override fun hasNext(): Boolean {
        val prn = dos.readLong()
        val styleIdx = dos.readShort()
        if (styleIdx == Short.MIN_VALUE) {
            nextCard = null
            return false
        }
        // TODO
        val style = if (styleIdx < 0) PhantomStyle(-styleIdx) else styles[styleIdx.toInt()]
        nextCard = FastSamplingCard(prn, style)
        return true
    }

    override fun close() {
        dos.close()
        binputStream.close()
    }
}

data class PhantomStyle(val contestId: Int): StyleIF {
    override fun county() = ""
    override fun name() = "PhantomForContest$contestId"
    override fun id() = -contestId
    override fun possibleContests() = intArrayOf(contestId)
    override fun hasExactContests() = true
    override fun hasContest(contestId: Int) = (contestId == this.contestId)
    override fun ncards() = -1
}

// could use varint encoding
fun writeFastSamplingCards(cards: CloseableIterator<AuditableCard>, styles: List<StyleIF>, filenameOut: String, limit: Int? = null): Int {
    val stylePairs: List<Pair<String, Int>> = styles.map { Pair(it.county(), it.id()) }
    val styleMap: Map<Pair<String, Int>, Int> = stylePairs.mapIndexed { idx, pair -> Pair(pair, idx) }.toMap()

    val outputStream: OutputStream = FileOutputStream(filenameOut)
    var count = 0

    DataOutputStream(outputStream).use { dos ->
        while (cards.hasNext() && (limit == null || count < limit)) {
            val card = cards.next()
            dos.writeLong(card.prn()) // 8 bytes; might switch to normalized bytearray, see electionguard
            if (card.phantom && (card.styleId < 0)) {
                dos.writeShort(-card.contestIds[0]) // phantom has a single contest // TODO ??
            } else {
                if (styleMap[Pair(card.county, card.styleId)] == null)
                    print("")
                dos.writeShort(styleMap[Pair(card.county, card.styleId)]!! )  // 2 bytes
            }
            count++
        }
        // EOF
        dos.writeLong(0L)
        dos.writeShort(Short.MIN_VALUE.toInt())
    }
    outputStream.close() // probably dos closes it
    cards.close()

    return count
}