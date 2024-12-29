/*
  Copyright 2023 Democracy Developers
  This is a Java re-implementation of raire-rs https://github.com/DemocracyDevelopers/raire-rs
  It attempts to copy the design, API, and naming as much as possible subject to being idiomatic and efficient Java.

  This file is part of raire-java.
  raire-java is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
  raire-java is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Affero General Public License for more details.
  You should have received a copy of the GNU Affero General Public License along with ConcreteSTV.  If not, see <https://www.gnu.org/licenses/>.

 */

package org.cryptobiotic.rlauxe.raire

import au.org.democracydevelopers.raire.RaireException
import au.org.democracydevelopers.raire.algorithm.RaireResult
import au.org.democracydevelopers.raire.assertions.NotEliminatedBefore
import au.org.democracydevelopers.raire.audittype.BallotComparisonOneOnDilutedMargin
import au.org.democracydevelopers.raire.irv.IRVResult
import au.org.democracydevelopers.raire.irv.Vote
import au.org.democracydevelopers.raire.irv.Votes
import au.org.democracydevelopers.raire.pruning.TrimAlgorithm
import au.org.democracydevelopers.raire.time.TimeOut
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertContentEquals

class TestAGuideToRaireExamples {
    companion object {
        const val A = 0 // Alice
        const val B = 1 // Bob
        const val C = 2 // Chuan
        const val D = 3 // Diego

        /** Get the votes in Example 10 (at the time of writing), used in examples in chapter 6, "Using RAIRE to generate assertions". */
        val AUDIT = BallotComparisonOneOnDilutedMargin(13500)
    }

    @Throws(RaireException::class)
    fun getVotes(): Votes {
        val votes = arrayOf(
            Vote(5000, intArrayOf(C, B, A)),
            Vote(1000, intArrayOf(B, C, D)),
            Vote(1500, intArrayOf(D, A)),
            Vote(4000, intArrayOf(A, D)),
            Vote(2000, intArrayOf(D)),
        )
        return Votes(votes, 4)
    }

    // Test the get_votes() function and the methods on the Votes object.
    @Test
    @Throws(RaireException::class)
    fun testVotesStructure() {
        val votes = getVotes()
        assertEquals(AUDIT.total_auditable_ballots, votes.totalVotes())
        assertEquals(4000, votes.firstPreferenceOnlyTally(A))
        assertEquals(1000, votes.firstPreferenceOnlyTally(B))
        assertEquals(5000, votes.firstPreferenceOnlyTally(C))
        assertEquals(3500, votes.firstPreferenceOnlyTally(D))
        assertContentEquals(intArrayOf(4000, 6000, 3500), votes.restrictedTallies(intArrayOf(A, C, D)))
        assertContentEquals(intArrayOf(5500, 6000), votes.restrictedTallies(intArrayOf(A, C)))
        val result: IRVResult = votes.runElection(TimeOut.never())
        assertContentEquals(intArrayOf(C), result.possibleWinners)
        assertContentEquals(intArrayOf(B, D, A, C), result.eliminationOrder)
    }

    @Throws(RaireException::class)
    fun testNEB(winner: Int, loser: Int): Double {
        val assertion = NotEliminatedBefore(winner, loser)
        return assertion.difficulty(getVotes(), AUDIT).difficulty
    }

    /** Check NEB assertions in table 6.1 showing that A, B and C cannot be the last candidate standing. */
    @Test
    @Throws(RaireException::class)
    fun test_neb_assertions() {
        assertTrue(testNEB(B, A).isInfinite())
        assertTrue(testNEB(C, A).isInfinite())
        assertTrue(testNEB(D, A).isInfinite())
        assertTrue(testNEB(A, B).isInfinite())
        assertEquals(3.375, testNEB(C, B), 0.001)
        assertTrue(testNEB(D, B).isInfinite())
        assertTrue(testNEB(A, D).isInfinite())
        assertTrue(testNEB(B, D).isInfinite())
        assertTrue(testNEB(C, D).isInfinite())
    }

    /// Test RAIRE
    @Test
    @Throws(RaireException::class)
    fun test_raire() {
        val votes = getVotes()
        val minAssertions = RaireResult(votes, C, AUDIT, TrimAlgorithm.MinimizeAssertions, TimeOut.never())
        assertEquals(27.0, minAssertions.difficulty, 1e-6)
        assertEquals(5, minAssertions.assertions.size)
        val minTree = RaireResult(votes, C, AUDIT, TrimAlgorithm.MinimizeTree, TimeOut.never())
        assertEquals(27.0, minTree.difficulty, 1e-6)
        assertEquals(6, minTree.assertions.size)
    }
}