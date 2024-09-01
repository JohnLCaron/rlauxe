package org.cryptobiotic.rlauxe.reader

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import nl.adaptivity.xmlutil.serialization.*
import org.cryptobiotic.rlauxe.util.Indent


import java.io.File
import java.io.FileOutputStream

fun readHartXmlFromFile(filename : String ) : HartXml {
    println("HartXml filename = ${filename}")

    //gulp the entire file to a string
    val file = File(filename)
    val text = file.readText(Charsets.UTF_8)

    val serializer = serializer<HartXml>() // use the default serializer

    // Create the configuration for (de)serialization
    val xml = XML { indent = 2 }

    val hartxml : HartXml = xml.decodeFromString(serializer, text)
    println("$hartxml")
    return hartxml
}

enum class ProofOfCorrectness { interactive, noninteractive}

@Serializable
@XmlSerialName(value = "Cvr")
data class HartXml(
    @XmlElement val contests: Contests,
    @XmlElement val BatchSequence: Int,
    @XmlElement val SheetNumber: Int,
    @XmlElement val PrecinctSplit: PrecinctSplit,
    @XmlElement val BatchNumber: Int,
    @XmlElement val CvrGuid: String,
) {

    @OptIn(InternalSerializationApi::class)
    fun writeToFile(filename : String ) {
        val xml = XML { indent = 2 }
        val serializer = this::class.serializer() as KSerializer<Any>
        val text = xml.encodeToString(serializer, this, null)
        FileOutputStream(filename).use { out ->
            out.write(text.toByteArray())
        }
    }

    override fun toString() = buildString {
        appendLine("Cvr")
        val indent = Indent(2)
        contests.contests.forEach{ append("${indent}${it.show(indent.incr())}") }
        appendLine("${indent}BatchSequence=$BatchSequence")
        appendLine("${indent}SheetNumber=$SheetNumber")
        appendLine("${indent}$PrecinctSplit")
        appendLine("${indent}BatchNumber=$BatchNumber")
        appendLine("${indent}CvrGuid='$CvrGuid'")
    }
}

@Serializable
@XmlSerialName(value = "Contests")
data class Contests(
    @XmlElement val contests: List<Contest>
)

@Serializable
@XmlSerialName(value = "Contest")
data class Contest(
    @XmlElement val Name: String,
    @XmlElement val Id: String,
    @XmlElement val Options: Options,
    @XmlElement val Undervotes: Int?,
) {
    fun show(indent: Indent) = buildString {
        appendLine("Contest")
        appendLine("${indent}Name='$Name'")
        appendLine("${indent}Id='$Id'")
        Options.options.forEach{ append("${indent}${it.show(indent.incr())}") }
        if (Undervotes != null) appendLine("${indent}Undervotes=$Undervotes")
    }
}

@Serializable
@XmlSerialName(value = "Options")
data class Options(
    @XmlElement val options: List<Option>
)

@Serializable
@XmlSerialName(value = "Option")
data class Option(
    @XmlElement val Name: String?,
    @XmlElement val Id: String,
    @XmlElement val Value: Int,
    @XmlElement val WriteInData: WriteInData?,
) {
    fun show(indent: Indent) = buildString {
        appendLine("Option")
        if (Name != null) appendLine("${indent}Name='$Name'")
        appendLine("${indent}Id='$Id'")
        appendLine("${indent}Value=$Value")
        if (WriteInData != null) appendLine("${indent}$WriteInData")
    }
}

@Serializable
@XmlSerialName(value = "WriteInData")
data class WriteInData(
    @XmlElement val Text: String,
    @XmlElement val ImageId: String,
    @XmlElement val WriteInDataStatus: String,
)


@Serializable
@XmlSerialName(value = "PrecinctSplit")
data class PrecinctSplit(
    @XmlElement val Name: String,
    @XmlElement val Id: String,
)