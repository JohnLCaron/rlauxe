package org.cryptobiotic.rlauxe.belgium


import org.cryptobiotic.rlauxe.util.sfn
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream

fun readPartyTxtFile(filename: String): Map<String, Int> {
    val reader: BufferedReader = File(filename).bufferedReader()
    reader.readLine() // get rid of header line

    val parties = mutableListOf<Pair<String, Int>>()
    while (true) {
        val line = reader.readLine() ?: break
        val tokens = line.split(",")
        val ttokens = tokens.map { it.trim() }
        val id = ttokens[0].toInt()
        val name = ttokens[1]
        parties.add(Pair(name, id))
    }
    reader.close()
    return parties.toMap()
}

fun readPartyTxtResource(resourcePath: String): Map<String, Int> {
    val parties = mutableListOf<Pair<String, Int>>()

    val inputStream = object {}.javaClass.getResourceAsStream(resourcePath) ?:
        throw RuntimeException("readPartyTxtResource $resourcePath does not exist")

    inputStream.bufferedReader().use { reader ->
        reader.readLine() // get rid of header line
        while (true) {
            val line = reader.readLine() ?: break
            val tokens = line.split(",")
            val ttokens = tokens.map { it.trim() }
            val id = ttokens[0].toInt()
            val name = ttokens[1]
            parties.add(Pair(name, id))
        }
    }

    return parties.toMap()
}

fun copyResourceFile(resourcePath: String, outputFile: String) {
    val inputStream = object {}.javaClass.getResourceAsStream(resourcePath) ?:
    throw RuntimeException("readPartyTxtResource $resourcePath does not exist")

    val outputFile = File(outputFile)
    outputFile.outputStream().use { fileOut ->
        inputStream.copyTo(fileOut)
    }
}

data class Royaume(val name: String, val seats: Int, val votes: Int) {
    override fun toString(): String {
        return "${sfn(name, 20)}:  seats=$seats, votes=$votes"
    }
}

// used ??
fun readRoyaumeTxtFile(filename: String): List<Royaume> {
    val reader: BufferedReader = File(filename).bufferedReader()
    reader.readLine() // get rid of header line

    val parties = mutableListOf<Royaume>()
    while (true) {
        val line = reader.readLine() ?: break
        val tokens = line.split(";")
        val ttokens = tokens.map { it.trim() }
        val name = ttokens[0]
        val seats = ttokens[1].toInt()
        val votes = ttokens[2].toInt()
        parties.add(Royaume(name, seats, votes))
    }
    reader.close()
    return parties
}