/*
  Copyright 2023 Democracy Developers
  This is a Java re-implementation of raire-rs https://github.com/DemocracyDevelopers/raire-rs
  It attempts to copy the design, API, and naming as much as possible subject to being idiomatic and efficient Java.

  This file is part of raire-java.
  raire-java is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
  raire-java is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Affero General Public License for more details.
  You should have received a copy of the GNU Affero General Public License along with ConcreteSTV.  If not, see <https://www.gnu.org/licenses/>.

 */
package org.cryptobiotic.rlauxe.shangrla

import au.org.democracydevelopers.raire.RaireError.*
import au.org.democracydevelopers.raire.RaireProblem
import au.org.democracydevelopers.raire.RaireSolution
import au.org.democracydevelopers.raire.RaireSolution.RaireResultOrError
import au.org.democracydevelopers.raire.assertions.Assertion
import au.org.democracydevelopers.raire.assertions.AssertionAndDifficulty
import au.org.democracydevelopers.raire.assertions.NotEliminatedBefore
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlin.test.assertNull

// Not really sure if this should be here. Its testing the raire-java code.
class TestRaireProblemSerialization {
    private val mapper = ObjectMapper()

    @Test
    @Throws(JsonProcessingException::class)
    fun testSerialization() {
        val demoJson = "{\n" +
                "  \"metadata\": {\n" +
                "    \"candidates\": [\"Alice\", \"Bob\", \"Chuan\",\"Diego\" ],\n" +
                "    \"note\" : \"Anything can go in the metadata section. Candidates names are used below if present. \"\n" +
                "  },\n" +
                "  \"num_candidates\": 4,\n" +
                "  \"votes\": [\n" +
                "    { \"n\": 5000, \"prefs\": [ 2, 1, 0 ] },\n" +
                "    { \"n\": 1000, \"prefs\": [ 1, 2, 3 ] },\n" +
                "    { \"n\": 1500, \"prefs\": [ 3, 0 ] },\n" +
                "    { \"n\": 4000, \"prefs\": [ 0, 3 ] },\n" +
                "    { \"n\": 2000, \"prefs\": [ 3 ]  }\n" +
                "  ],\n" +
                "  \"winner\": 2,\n" +
                "  \"trim_algorithm\": \"MinimizeTree\",\n" +
                "  \"audit\": { \"type\": \"OneOnMargin\", \"total_auditable_ballots\": 13500  }\n" +
                "}\n"
        val problem = mapper.readValue<RaireProblem>(demoJson, RaireProblem::class.java)
        // String serialized = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(problem);
        // System.out.println(serialized);
        val solution = problem.solve()
        assertNotNull(solution.solution.Ok)
        assertEquals(27.0, solution.solution.Ok.difficulty, 1e-6)

        println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(solution))
        val solution2 = mapper.readValue<RaireSolution>(mapper.writeValueAsString(solution), RaireSolution::class.java)
        assertNotNull(solution2.solution.Ok)
        assertEquals(27.0, solution2.solution.Ok.difficulty, 1e-6)
        assertEquals(solution.solution.Ok.assertions.size, solution2.solution.Ok.assertions.size)
    }

    @Test
    @Throws(JsonProcessingException::class)
    fun testErrorSerialization() {
        assertEquals(
            "{\"Err\":\"InvalidTimeout\"}",
            mapper.writeValueAsString(RaireResultOrError(InvalidTimeout()))
        )
        assertEquals(
            "{\"Err\":\"InvalidNumberOfCandidates\"}",
            mapper.writeValueAsString(RaireResultOrError(InvalidNumberOfCandidates()))
        )
        assertEquals(
            "{\"Err\":\"InvalidCandidateNumber\"}",
            mapper.writeValueAsString(RaireResultOrError(InvalidCandidateNumber()))
        )
        assertEquals(
            "{\"Err\":\"TimeoutCheckingWinner\"}",
            mapper.writeValueAsString(RaireResultOrError(TimeoutCheckingWinner()))
        )
        assertEquals(
            "{\"Err\":{\"TimeoutFindingAssertions\":3.0}}",
            mapper.writeValueAsString(RaireResultOrError(TimeoutFindingAssertions(3.0)))
        )
        assertEquals(
            "{\"Err\":\"TimeoutTrimmingAssertions\"}",
            mapper.writeValueAsString(RaireResultOrError(TimeoutTrimmingAssertions()))
        )
        assertEquals(
            "{\"Err\":{\"TiedWinners\":[2,3]}}",
            mapper.writeValueAsString(RaireResultOrError(TiedWinners(intArrayOf(2, 3))))
        )
        assertEquals(
            "{\"Err\":{\"WrongWinner\":[2,3]}}",
            mapper.writeValueAsString(RaireResultOrError(WrongWinner(intArrayOf(2, 3))))
        )
        assertEquals(
            "{\"Err\":{\"CouldNotRuleOut\":[2,3]}}",
            mapper.writeValueAsString(RaireResultOrError(CouldNotRuleOut(intArrayOf(2, 3))))
        )
        assertEquals(
            "{\"Err\":\"InternalErrorRuledOutWinner\"}",
            mapper.writeValueAsString(RaireResultOrError(InternalErrorRuledOutWinner()))
        )
        assertEquals(
            "{\"Err\":\"InternalErrorDidntRuleOutLoser\"}",
            mapper.writeValueAsString(RaireResultOrError(InternalErrorDidntRuleOutLoser()))
        )
        assertEquals(
            "{\"Err\":\"InternalErrorTrimming\"}",
            mapper.writeValueAsString(RaireResultOrError(InternalErrorTrimming()))
        )
    }

    @Throws(JsonProcessingException::class)
    fun checkIdempotentDeserializeAndSerializeRaireResultOrError(json: String?) {
        val deserialized = mapper.readValue<RaireResultOrError?>(json, RaireResultOrError::class.java)
        assertEquals(json, mapper.writeValueAsString(deserialized))
    }

    @Test
    @Throws(JsonProcessingException::class)
    fun testErrorDeserialization() {
        checkIdempotentDeserializeAndSerializeRaireResultOrError("{\"Err\":\"InvalidTimeout\"}")
        checkIdempotentDeserializeAndSerializeRaireResultOrError("{\"Err\":\"InvalidCandidateNumber\"}")
        checkIdempotentDeserializeAndSerializeRaireResultOrError("{\"Err\":\"TimeoutCheckingWinner\"}")
        checkIdempotentDeserializeAndSerializeRaireResultOrError("{\"Err\":{\"TimeoutFindingAssertions\":3.0}}")
        checkIdempotentDeserializeAndSerializeRaireResultOrError("{\"Err\":\"TimeoutTrimmingAssertions\"}")
        checkIdempotentDeserializeAndSerializeRaireResultOrError("{\"Err\":{\"TiedWinners\":[2,3]}}")
        checkIdempotentDeserializeAndSerializeRaireResultOrError("{\"Err\":{\"WrongWinner\":[2,3]}}")
        checkIdempotentDeserializeAndSerializeRaireResultOrError("{\"Err\":{\"CouldNotRuleOut\":[2,3]}}")
        checkIdempotentDeserializeAndSerializeRaireResultOrError("{\"Err\":\"InternalErrorRuledOutWinner\"}")
        checkIdempotentDeserializeAndSerializeRaireResultOrError("{\"Err\":\"InternalErrorDidntRuleOutLoser\"}")
        checkIdempotentDeserializeAndSerializeRaireResultOrError("{\"Err\":\"InternalErrorTrimming\"}")
    }


    /**
     * Test serialization/deserialization of AssertionAndDifficulty focussing on status by
     * serializing and then deserializing and checking is the same.
     */
    @Test
    @Throws(JsonProcessingException::class)
    fun testAssertionAndDifficultyStatus() {
        val a: Assertion = NotEliminatedBefore(0, 1)
        val noStatus = AssertionAndDifficulty(a, 2.0, 7)
        val noStatusS = mapper.readValue<AssertionAndDifficulty>(
            mapper.writeValueAsString(noStatus),
            AssertionAndDifficulty::class.java
        )
        assertTrue(noStatusS.assertion.isNEB())
        assertEquals(2.0, noStatusS.difficulty)
        assertEquals(7, noStatusS.margin)
        assertNull(noStatusS.status)
        val status = HashMap<String?, Any?>()
        status.put("name", "Rip Van Winkle")
        status.put("age", 956)
        val hasStatus = AssertionAndDifficulty(a, 3.0, 8, status)
        val hasStatusS = mapper.readValue<AssertionAndDifficulty>(
            mapper.writeValueAsString(hasStatus),
            AssertionAndDifficulty::class.java
        )
        assertTrue(hasStatusS.assertion.isNEB())
        assertEquals(3.0, hasStatusS.difficulty)
        assertEquals(8, hasStatusS.margin)
        assertNotNull(hasStatusS.status)
        assertEquals("Rip Van Winkle", hasStatusS.status.get("name"))
        assertEquals(956, hasStatusS.status.get("age"))
    }
}
