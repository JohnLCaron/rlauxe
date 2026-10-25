package org.cryptobiotic.rlauxe.belgium

import org.cryptobiotic.rlauxe.persist.CountyAuditRecord
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.util.sfn
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter

data class BelgiumInput(val year: Int, val contests: Map<String, String>,
                        val resourceDir: String, val Royaume: String, val parties: String)

//// downloaded from https://resultatselection.belgium.be/ into resourceDir
fun belgiumInput(year: Int): BelgiumInput {
    val resourceDir = "/resources/data/cases/belgium/belgium$year"

    return BelgiumInput(year, mapOf(
        "Anvers" to "$resourceDir/${year}_chambre-des-représentants_Circonscription d'Anvers.json",
        "Bruxelles" to "$resourceDir/${year}_chambre-des-représentants_Circonscription de Bruxelles-Capitale.json",
        "FlandreWest" to "$resourceDir/${year}_chambre-des-représentants_Circonscription de Flandre occidentale.json",
        "FlandreEast" to "$resourceDir/${year}_chambre-des-représentants_Circonscription de Flandre orientale.json",
        "Hainaut" to "$resourceDir/${year}_chambre-des-représentants_Circonscription de Hainaut.json",
        "Liège" to "$resourceDir/${year}_chambre-des-représentants_Circonscription de Liège.json",
        "Limbourg" to "$resourceDir/${year}_chambre-des-représentants_Circonscription de Limbourg.json",
        "Luxembourg" to "$resourceDir/${year}_chambre-des-représentants_Circonscription de Luxembourg.json",
        "Namur" to "$resourceDir/${year}_chambre-des-représentants_Circonscription de Namur.json",
        "BrabantFlamant" to "$resourceDir/${year}_chambre-des-représentants_Circonscription du Brabant flamand.json",
        "BrabantWallon" to "$resourceDir/${year}_chambre-des-représentants_Circonscription du Brabant wallon.json",
    ), resourceDir, "$resourceDir/${year}_chambre-des-représentants_Royaume.csv", "$resourceDir/parties.txt")
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
            val name = ttokens[0].trim()
            val id = ttokens[1].trim().toInt()
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
        return "${sfn(name, 20)}:  seats=${nfn(seats, 3)}, votes=${nfn(votes, 7)}"
    }
}

fun readRoyaumeCsvResource(resourcePath: String): List<Royaume> {

    val inputStream = object {}.javaClass.getResourceAsStream(resourcePath) ?:
    throw RuntimeException("readPartyTxtResource $resourcePath does not exist")

    val parties = mutableListOf<Royaume>()
    inputStream.bufferedReader().use { reader ->
        reader.readLine() // get rid of header line
        while (true) {
            val line = reader.readLine() ?: break
            val tokens = line.split(";")
            val ttokens = tokens.map { it.trim() }
            val name = ttokens[0]
            val seats = ttokens[1].toInt()
            val votes = ttokens[2].toInt()
            parties.add(Royaume(name, seats, votes))
        }
    }

    return parties
}

/*
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
} */

fun writeCanonicalParties(year:Int, parties: Map<String, Int>, outputFilename: String): Map<Int, String> {
    val canon = mutableMapOf<Int, String>()
    parties.forEach {
        // the first parties entries are canonical, then come the aliases
        if (!canon.containsKey(it.value)) {
            canon[it.value] = it.key
        }
    }
    val writer: OutputStreamWriter = FileOutputStream(outputFilename).writer()
    println("Belgique $year – Chambre des représentants")

    canon.forEach { (id, name) ->
        writer.write("$id,$name\n")
    }
    writer.close()

    return canon
}