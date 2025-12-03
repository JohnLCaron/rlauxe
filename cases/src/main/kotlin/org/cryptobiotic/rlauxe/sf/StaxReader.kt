package org.cryptobiotic.rlauxe.sf

import java.io.FileInputStream
import javax.xml.namespace.QName
import javax.xml.stream.XMLEventReader
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.events.XMLEvent
import javax.xml.stream.events.StartElement

// read SF2024/summary.xml with Stax library

class StaxReader {
    val ballots2Q = QName("ballots2")
    val contestIdQ = QName("contestId")
    val candidateNameTextBox4Q = QName("candidateNameTextBox4")
    val undervotes3Q = QName("undervotes3")
    val overvotes2Q = QName("overvotes2")
    val totalBlanks = QName("totalBlanks")

    inner class StaxContest(val id: String) {
        val candidates = mutableListOf<StaxCandidate>()
        val attributes = mutableListOf<StartElement>()

        fun ncards(): Int? {
            attributes.forEach {
                val a = it.getAttributeByName(ballots2Q)
                if (a != null) {
                    val text = a.value!!
                    val tokens = text.split(' ')
                    return tokens[0].toInt()
                }
            }
            return null
        }

        fun undervotes(): Int? {
            attributes.forEach {
                val a = it.getAttributeByName(undervotes3Q)
                if (a != null) {
                    val text = a.value!!
                    return text.toInt()
                }
            }
            return null
        }

        fun overvotes(): Int? {
            attributes.forEach {
                val a = it.getAttributeByName(overvotes2Q)
                if (a != null) {
                    val text = a.value!!
                    return text.toInt()
                }
            }
            return null
        }

        // this differs from the cvr summary
        fun blanks(): Int? {
            attributes.forEach {
                val a = it.getAttributeByName(totalBlanks)
                if (a != null) {
                    val text = a.value!!
                    return text.toInt()
                }
            }
            return null
        }

        override fun toString() = buildString {
            appendLine("Contest '$id'")
            attributes.forEach { appendLine("    $it") }
            appendLine("Candidates")
            candidates.forEach { append("$it") }
        }
    }

    data class StaxCandidate(val id: String) {
        val attributes = mutableListOf<XMLEvent>()

        override fun toString() = buildString {
            appendLine("  Candidate '$id'")
            attributes.forEach { appendLine("    $it") }
            appendLine()
        }
    }

    fun read(xmlFile: String): List<StaxContest> {
        val contests = mutableListOf<StaxContest>()

        var currentContest = StaxContest("")
        var currentCandidate = StaxCandidate("")
        var insideCandidateResult: Boolean = false
        var insideCandidateNameTextBox4: Boolean = false
        var insidePartyGroup: Boolean = false
        var insideContestStatistics: Boolean = false

        // Use a factory to get an instance of XMLEventReader
        val inputFactory: XMLInputFactory = XMLInputFactory.newInstance()
        FileInputStream(xmlFile).use { fileInputStream ->
            val eventReader: XMLEventReader = inputFactory.createXMLEventReader(fileInputStream)

            while (eventReader.hasNext()) {
                val event: XMLEvent = eventReader.nextEvent()

                when {
                    // If the event is a starting element, check its name
                    event.isStartElement -> {
                        val startElement: StartElement = event.asStartElement()
                        when (startElement.name.localPart) {
                            "ContestIdGroup" -> {
                                currentContest = StaxContest(startElement.getAttributeByName(contestIdQ).value.trim() )
                            }

                            "ContestStatistics" -> {
                                insideContestStatistics = true
                            }

                            "Textbox9" -> {
                                if (insideContestStatistics) currentContest.attributes.add(startElement)
                            }

                            /* "cgId2" -> {
                                if (insideContestStatistics) currentContest.attributes.add(startElement)
                            } */

                            "CandidateResults" -> {
                                insideCandidateResult = true // needed ??
                            }

                            "candidateNameTextBox4" -> {
                                if (insideCandidateResult) {
                                    currentCandidate =
                                        StaxCandidate(startElement.getAttributeByName(candidateNameTextBox4Q).value.trim())
                                    currentContest.candidates.add(currentCandidate)
                                }
                            }

                            "partyGroup_Collection" -> {
                                insidePartyGroup = true
                            }

                            "cgGroup" -> {
                                if (insidePartyGroup) {
                                    currentCandidate.attributes.add(startElement)
                                }
                            }
                        }
                    }
                    // If the event is an ending element for a group, create and store the object
                    event.isEndElement -> {
                        val endElement = event.asEndElement()
                        when (endElement.name.localPart) {
                            "ContestIdGroup" -> {
                                contests.add(currentContest)
                            }

                            "ContestStatistics" -> {
                                insideContestStatistics = false
                            }

                            "CandidateResults" -> {
                                insideCandidateResult = false
                            }

                            "candidateNameTextBox4" -> {
                                insideCandidateNameTextBox4 = false
                            }

                            "partyGroup_Collection" -> {
                                insidePartyGroup = false
                            }
                        }
                    }
                }
            }
        }
        return contests
    }
}
