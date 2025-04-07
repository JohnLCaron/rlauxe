package org.cryptobiotic.rlauxe.corla

import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import java.io.File

// Detail XLS (295 contests, 92k zipped, 2.6M unzipped )
//has a separate sheet for every contest with vote count, by county
//https://results.enr.clarityelections.com//CO//122598/355977/reports/detailxls.zip
//can also get it as an XML (56k zipped, 780k unzipped ):
//https://results.enr.clarityelections.com//CO//122598/355977/reports/detailxml.zip

// corla/src/test/data/2024election/detail.xml

// <ElectionResult>
//    <Timestamp>12/6/2024 1:20:51 PM MST</Timestamp>
//    <ElectionName>2024 General</ElectionName>
//    <ElectionDate>11/5/2024</ElectionDate>
//    <Region>CO</Region>
//    <ElectionVoterTurnout totalVoters="4058938" ballotsCast="3241120" voterTurnout="79.85">
//        <Counties>
//            <County name="Adams" totalVoters="320225" ballotsCast="236899" voterTurnout="73.98" precinctsParticipating="283" precinctsReported="283" precinctsReportingPercent="100.00" />
//            <County name="Alamosa" totalVoters="10321" ballotsCast="7671" voterTurnout="74.32" precinctsParticipating="8" precinctsReported="8" precinctsReportingPercent="100.00" />
// ...

@Serializable
@XmlSerialName(value = "ElectionResult")
data class ElectionDetailXml(
    @XmlElement val Timestamp: String,
    @XmlElement val ElectionName: String,
    @XmlElement val ElectionDate: String,
    @XmlElement val Region: String,
    @XmlElement val ElectionVoterTurnout: ElectionVoterTurnout,
    @XmlElement val contests: List<ElectionDetailContest>,
) {
    override fun toString() = buildString {
        appendLine("ElectionResult(Timestamp='$Timestamp', ElectionName='$ElectionName', ElectionDate='$ElectionDate', Region='$Region'")
        appendLine("   $ElectionVoterTurnout")
        contests.forEach { appendLine("   $it") }
    }
}

@Serializable
@XmlSerialName(value = "ElectionVoterTurnout")
data class ElectionVoterTurnout(
    val totalVoters: Int,
    val ballotsCast: Int,
    val voterTurnout: Double,
    @XmlElement val Counties: Counties,
) {
    override fun toString() = buildString {
        appendLine("ElectionVoterTurnout(totalVoters=$totalVoters, ballotsCast=$ballotsCast, voterTurnout=$voterTurnout)")
        Counties.counties.forEach { appendLine("      $it") }
    }
}

@Serializable
@XmlSerialName(value = "Counties")
data class Counties(
    val counties: List<County>
)

// <County name="Adams" totalVoters="320225" ballotsCast="236899" voterTurnout="73.98" precinctsParticipating="283" precinctsReported="283" precinctsReportingPercent="100.00" />
@Serializable
@XmlSerialName(value = "County")
data class County(
    val name: String,
    val totalVoters: Int,
    val ballotsCast: Int,
    val voterTurnout: Double,
    val precinctsParticipating: Int,
    val precinctsReported: Int,
    val precinctsReportingPercent: Double,
)

//     <Contest key="10" text="Presidential Electors" voteFor="1" isQuestion="false" countiesParticipating="64" countiesReported="64" precinctsParticipating="3202" precinctsReported="3202" precinctsReportingPercent="100.00">
//        <ParticipatingCounties>
//            <County name="Adams" precinctsParticipating="278" precinctsReported="278" precinctsReportingPercent="100.00" />
//            <County name="Alamosa" precinctsParticipating="8" precinctsReported="8" precinctsReportingPercent="100.00" />
//        ...
//        </ParticipatingCounties>

@Serializable
@XmlSerialName(value = "Contest")
data class ElectionDetailContest(
    val key: Int,
    val text: String,
    val voteFor: Int,
    val isQuestion: Boolean,
    val countiesParticipating: Int,
    val countiesReported: Int,
    val precinctsParticipating: Int,
    val precinctsReported: Int,
    val precinctsReportingPercent: Double,
    @XmlElement val pcounties: ParticipatingCounties,
    val choices: List<Choice>,
) {
    override fun toString() = buildString {
        appendLine("Contest(key=$key, text='$text', voteFor=$voteFor, isQuestion=$isQuestion, countiesParticipating=$countiesParticipating, countiesReported=$countiesReported, precinctsParticipating=$precinctsParticipating, precinctsReported=$precinctsReported, precinctsReportingPercent=$precinctsReportingPercent")
        pcounties.participatingCounties.forEach { appendLine("      $it")}
        appendLine()
        choices.forEach { appendLine("      $it")}
    }
}

@Serializable
@XmlSerialName(value = "ParticipatingCounties")
data class ParticipatingCounties(
    val participatingCounties: List<ParticipatingCounty>
)

@Serializable
@XmlSerialName(value = "County")
data class ParticipatingCounty(
    val name: String,
    val precinctsParticipating: Int,
    val precinctsReported: Int,
    val precinctsReportingPercent: Double,
)

//        <Choice key="1" text="Kamala D. Harris / Tim Walz" party="DEM" totalVotes="1728159">
//            <VoteType name="Total Votes" votes="1728159">
//                <County name="Adams" votes="124056" />
//                <County name="Alamosa" votes="3244" />
//                <County name="Arapahoe" votes="190725" />
//  ...
//             </VoteType>
//        </Choice>
//        <Choice key="2" text="Donald J. Trump / JD Vance" party="REP" totalVotes="1377441">

@Serializable
@XmlSerialName(value = "Choice")
data class Choice(
    val key: String, // may have "Yes" / "No"
    val text: String,
    val party: String,
    val totalVotes: Int,
    val voteTypes: List<VoteType>,
) {
    override fun toString() = buildString {
        return "Choice(key=$key, text='$text', party='$party', totalVotes=$totalVotes, voteTypes=$voteTypes)"
    }
}

@Serializable
@XmlSerialName(value = "VoteType")
data class VoteType(
    val name: String,
    val votes: Int,
    val byCounty: List<CountyVote>,
) {
    override fun toString() = buildString {
        appendLine("VoteType(name='$name', votes=$votes")
        byCounty.forEach { appendLine("         $it")}
    }
}

@Serializable
@XmlSerialName(value = "County")
data class CountyVote(
    val name: String,
    val votes: Int,
)

fun readColoradoElectionDetail(filename : String ) : ElectionDetailXml {
    println("readColoradoElectionDetail filename = ${filename}")

    //gulp the entire file to a string
    val file = File(filename)
    val text = file.readText(Charsets.UTF_8)

    val serializer = serializer<ElectionDetailXml>() // use the default serializer

    // Create the configuration for (de)serialization
    val xml = XML { indent = 2 }

    val result : ElectionDetailXml = xml.decodeFromString(serializer, text)
    // println("$result")
    return result
}