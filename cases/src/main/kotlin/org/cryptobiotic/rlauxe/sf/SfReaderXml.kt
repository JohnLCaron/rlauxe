@file:OptIn(ExperimentalXmlUtilApi::class)

package org.cryptobiotic.rlauxe.sf

import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import nl.adaptivity.xmlutil.ExperimentalXmlUtilApi
import nl.adaptivity.xmlutil.serialization.DefaultXmlSerializationPolicy
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.serialization.XmlConfig.Companion.IGNORING_UNKNOWN_CHILD_HANDLER
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import java.io.File

// SF2024 summary xml
// https://www.sfelections.org/results/20241105/data/20241203/summary.xml
// cases/src/test/data/SF2024/summary.xml

@Serializable
@XmlSerialName(value = "Report")
data class ElectionSummaryXml(
    @XmlElement val tabBatchIdList: tabBatchIdList,
)

@Serializable
@XmlSerialName(value = "tabBatchIdList")
data class tabBatchIdList(
    @XmlElement val TabBatchGroup_Collection: TabBatchGroup_Collection,
)

@Serializable
@XmlSerialName(value = "TabBatchGroup_Collection")
data class TabBatchGroup_Collection(
    @XmlElement val TabBatchGroup: TabBatchGroup,
)

@Serializable
@XmlSerialName(value = "TabBatchGroup")
data class TabBatchGroup(
    @XmlElement val ElectionSummarySubReport: ElectionSummarySubReport,
)

@Serializable
@XmlSerialName(value = "ElectionSummarySubReport")
data class ElectionSummarySubReport(
    @XmlElement val Report: ElectionSummarySubReportRPT,
)

@Serializable
@XmlSerialName(value = "Report")
data class ElectionSummarySubReportRPT(
    @XmlElement val contestList: contestList,
)

@Serializable
@XmlSerialName(value = "contestList")
data class contestList(
    @XmlElement val ContestIdGroup_Collection: ContestIdGroup_Collection,
)

@Serializable
@XmlSerialName(value = "ContestIdGroup_Collection")
data class ContestIdGroup_Collection(
    @XmlElement val ContestIdGroup: ContestIdGroup,
)

@Serializable
@XmlSerialName(value = "ContestIdGroup")
data class ContestIdGroup(
    val contestId : String,
    // @XmlElement val ContestStatistics: ContestStatistics,
    @XmlElement val CandidateResults: CandidateResults,
)

@Serializable
@XmlSerialName(value = "ContestStatistics")
data class ContestStatistics(
    // @XmlElement val Report: ContestStatisticsReport,
    @XmlElement val CandidateResults: CandidateResults,
)

@Serializable
@XmlSerialName(value = "Report")
data class ContestStatisticsReport(
    @XmlElement val ContestStatistics: ContestStatistics,
)

@Serializable
@XmlSerialName(value = "CandidateResults")
data class CandidateResults(
    @XmlElement val CandidateResultsReport: CandidateResultsReport,
)

@Serializable
@XmlSerialName(value = "Report")
data class CandidateResultsReport(
    @XmlElement val Tablix1: Tablix1,
)

@Serializable
@XmlSerialName(value = "Tablix1")
data class Tablix1(
    @XmlElement val chGroup_Collection: chGroup_Collection,
)

@Serializable
@XmlSerialName(value = "TextBox7")
data class TextBox7(
    @XmlElement val chGroup_Collection: chGroup_Collection,
)

@Serializable
@XmlSerialName(value = "TextBox33")
data class TextBox33(
    @XmlElement val chGroup_Collection: chGroup_Collection,
)

@Serializable
@XmlSerialName(value = "chGroup_Collection")
data class chGroup_Collection(
    @XmlElement val chGroup: List<chGroup>,
)

@Serializable
@XmlSerialName(value = "chGroup")
data class chGroup(
    @XmlElement val candidateNameTextBox4: candidateNameTextBox4,
    @XmlElement val partyGroup_Collection: partyGroup_Collection,
)

@Serializable
@XmlSerialName(value = "candidateNameTextBox4")
data class candidateNameTextBox4(
    val candidateNameTextBox4: String,
    @XmlElement val cgGroup_Collection: cgGroup_Collection,
    @XmlElement val Textbox13: Textbox13,
)

@Serializable
@XmlSerialName(value = "cgGroup_Collection")
data class cgGroup_Collection(
    val countingGroupName: String,
    val vot4: String
)

@Serializable
@XmlSerialName(value = "cgGroup")
data class cgGroup(
    @XmlElement val cgGroup: cgGroup,
)

@Serializable
@XmlSerialName(value = "Textbox13")
data class Textbox13(
    val vot8: String,
    val Textbox17: String
)

@Serializable
@XmlSerialName(value = "partyGroup_Collection")
data class partyGroup_Collection(
    @XmlElement val partyGroup: partyGroup,
)

@Serializable
@XmlSerialName(value = "partyGroup")
data class partyGroup(
    val candidateNameTextBox4: String,
    @XmlElement val cgGroup_Collection: cgGroup_Collection,
    @XmlElement val Textbox13p: Textbox13p,
)

@Serializable
@XmlSerialName(value = "Textbox13")
data class Textbox13p(
    val vot9: String,
    val Textbox18: String
)

fun readSf2024electionSummaryXML(filename : String ) : ElectionSummaryXml {
    println("readColoradoElectionDetail filename = ${filename}")

    //gulp the entire file to a string
    val file = File(filename)
    val text = file.readText(Charsets.UTF_8)

    val serializer = serializer<ElectionSummaryXml>() // use the default serializer

    // Create the configuration for (de)serialization
    val xml = XML{ policy = CustomPolicy }

    val result : ElectionSummaryXml = xml.decodeFromString(serializer, text)
    // println("$result")
    return result
}

object CustomPolicy : DefaultXmlSerializationPolicy(
    Builder().apply {
        unknownChildHandler = IGNORING_UNKNOWN_CHILD_HANDLER
    }
)