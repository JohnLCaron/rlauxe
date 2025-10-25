package org.cryptobiotic.rlauxe.raire

import au.org.democracydevelopers.raire.RaireError
import au.org.democracydevelopers.raire.RaireProblem
import au.org.democracydevelopers.raire.RaireSolution
import au.org.democracydevelopers.raire.assertions.Assertion
import au.org.democracydevelopers.raire.assertions.AssertionAndDifficulty
import au.org.democracydevelopers.raire.assertions.NotEliminatedBefore
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
            mapper.writeValueAsString(RaireSolution.RaireResultOrError(RaireError.InvalidTimeout()))
        )
        assertEquals(
            "{\"Err\":\"InvalidNumberOfCandidates\"}",
            mapper.writeValueAsString(RaireSolution.RaireResultOrError(RaireError.InvalidNumberOfCandidates()))
        )
        assertEquals(
            "{\"Err\":\"InvalidCandidateNumber\"}",
            mapper.writeValueAsString(RaireSolution.RaireResultOrError(RaireError.InvalidCandidateNumber()))
        )
        assertEquals(
            "{\"Err\":\"TimeoutCheckingWinner\"}",
            mapper.writeValueAsString(RaireSolution.RaireResultOrError(RaireError.TimeoutCheckingWinner()))
        )
        assertEquals(
            "{\"Err\":{\"TimeoutFindingAssertions\":3.0}}",
            mapper.writeValueAsString(RaireSolution.RaireResultOrError(RaireError.TimeoutFindingAssertions(3.0)))
        )
        assertEquals(
            "{\"Err\":\"TimeoutTrimmingAssertions\"}",
            mapper.writeValueAsString(RaireSolution.RaireResultOrError(RaireError.TimeoutTrimmingAssertions()))
        )
        assertEquals(
            "{\"Err\":{\"TiedWinners\":[2,3]}}",
            mapper.writeValueAsString(RaireSolution.RaireResultOrError(RaireError.TiedWinners(intArrayOf(2, 3))))
        )
        assertEquals(
            "{\"Err\":{\"WrongWinner\":[2,3]}}",
            mapper.writeValueAsString(RaireSolution.RaireResultOrError(RaireError.WrongWinner(intArrayOf(2, 3))))
        )
        assertEquals(
            "{\"Err\":{\"CouldNotRuleOut\":[2,3]}}",
            mapper.writeValueAsString(RaireSolution.RaireResultOrError(RaireError.CouldNotRuleOut(intArrayOf(2, 3))))
        )
        assertEquals(
            "{\"Err\":\"InternalErrorRuledOutWinner\"}",
            mapper.writeValueAsString(RaireSolution.RaireResultOrError(RaireError.InternalErrorRuledOutWinner()))
        )
        assertEquals(
            "{\"Err\":\"InternalErrorDidntRuleOutLoser\"}",
            mapper.writeValueAsString(RaireSolution.RaireResultOrError(RaireError.InternalErrorDidntRuleOutLoser()))
        )
        assertEquals(
            "{\"Err\":\"InternalErrorTrimming\"}",
            mapper.writeValueAsString(RaireSolution.RaireResultOrError(RaireError.InternalErrorTrimming()))
        )
    }

    @Throws(JsonProcessingException::class)
    fun checkIdempotentDeserializeAndSerializeRaireResultOrError(json: String?) {
        val deserialized = mapper.readValue<RaireSolution.RaireResultOrError?>(json, RaireSolution.RaireResultOrError::class.java)
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
        assertTrue(noStatusS.assertion.isNEB)
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
        assertTrue(hasStatusS.assertion.isNEB)
        assertEquals(3.0, hasStatusS.difficulty)
        assertEquals(8, hasStatusS.margin)
        assertNotNull(hasStatusS.status)
        assertEquals("Rip Van Winkle", hasStatusS.status.get("name"))
        assertEquals(956, hasStatusS.status.get("age"))
    }
}