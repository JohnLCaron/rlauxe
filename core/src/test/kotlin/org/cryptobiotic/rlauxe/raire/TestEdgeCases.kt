/*
  Copyright 2024 Democracy Developers
  This is a Java re-implementation of raire-rs https://github.com/DemocracyDevelopers/raire-rs
  It attempts to copy the design, API, and naming as much as possible subject to being idiomatic and efficient Java.

  This file is part of raire-java.
  raire-java is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
  raire-java is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Affero General Public License for more details.
  You should have received a copy of the GNU Affero General Public License along with ConcreteSTV.  If not, see <https://www.gnu.org/licenses/>.

 */
package org.cryptobiotic.rlauxe.raire

import au.org.democracydevelopers.raire.RaireError.InvalidNumberOfCandidates
import au.org.democracydevelopers.raire.RaireProblem
import au.org.democracydevelopers.raire.audittype.BallotComparisonOneOnDilutedMargin
import au.org.democracydevelopers.raire.irv.Vote

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

class TestEdgeCases {
    @Test
    fun test_zero_candidates() {
        val problem = RaireProblem(
            HashMap<String?, Any?>(),
            arrayOfNulls<Vote>(0),
            0,
            null,
            BallotComparisonOneOnDilutedMargin(13500),
            null,
            null,
            null
        )
        val error = problem.solve().solution.Err
        assertNotNull(error)
        assertTrue(error is InvalidNumberOfCandidates)
    }

    @Test
    fun test_one_candidate() {
        val problem = RaireProblem(
            HashMap<String?, Any?>(),
            arrayOfNulls<Vote>(0),
            1,
            null,
            BallotComparisonOneOnDilutedMargin(13500),
            null,
            null,
            null
        )
        val result = problem.solve().solution.Ok
        assertNotNull(result)
        assertEquals(0, result.winner)
    }
}
