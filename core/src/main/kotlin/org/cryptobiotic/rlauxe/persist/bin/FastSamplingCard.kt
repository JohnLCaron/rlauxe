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

class FastSamplingCardIterator(inputFile: String, styles: List<StyleIF>, bufferSize: Int): CloseableIterator<SamplingCardIF> {
    val styleMap = styles.associate { it.id() to it }
    val binputStream = BufferedInputStream( FileInputStream(inputFile), bufferSize)
    val dos = DataInputStream(binputStream)

    var nextCard: FastSamplingCard? = null
    var count = 0

    override fun next() = nextCard!!

    override fun hasNext(): Boolean {
        val prn = dos.readLong()
        val styleId = dos.readInt()
        if (styleId == Int.MIN_VALUE) {
            nextCard = null
            return false
        }
        val style = if (styleId < 0) PhantomStyle(-styleId) else styleMap[styleId]
        if (style == null)
            throw RuntimeException()
        nextCard = FastSamplingCard(prn, style)
        return true
    }

    override fun close() {
        dos.close()
        binputStream.close()
    }
}

data class PhantomStyle(val contestId: Int): StyleIF {
    override fun name() = "PhantomForContest$contestId"
    override fun id() = -contestId
    override fun possibleContests() = intArrayOf(contestId)
    override fun hasExactContests() = true
    override fun hasContest(contestId: Int) = (contestId == this.contestId)
    override fun ncards() = -1
}

// could use varint encoding
fun writeFastSamplingCards(cards: CloseableIterator<AuditableCard>, filenameOut: String, limit: Int? = null): Int {
    val outputStream: OutputStream = FileOutputStream(filenameOut)
    var count = 0

    DataOutputStream(outputStream).use { dos ->
        while (cards.hasNext() && (limit == null || count < limit)) {
            val card = cards.next()
            dos.writeLong(card.prn()) // 8 bytes; might switch to bytearray
            if (card.phantom && (card.styleId < 0)) {
                dos.writeInt(-card.contestIds[0]) // phantom has a single contest
            } else {
                dos.writeInt(card.styleId)  // 4 bytes
            }
            count++
        }
        // EOF
        dos.writeLong(0L)
        dos.writeInt(Int.MIN_VALUE)
    }
    outputStream.close() // probably dos closes it
    cards.close()

    return count
}