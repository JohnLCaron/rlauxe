# Verification
_last changed 10/19/2025_

## ContestInfo verification

````
enum class SocialChoiceFunction { PLURALITY, APPROVAL, SUPERMAJORITY, IRV }
class ContestInfo(
    val name: String,
    val id: Int,
    val candidateNames: Map<String, Int>,       // candidate name -> candidate id
    val choiceFunction: SocialChoiceFunction,   // electionguard has "VoteVariationType"
    val nwinners: Int = 1,                      // aka "numberElected"
    val voteForN: Int = nwinners,               // aka "contestSelectionLimit" or "optionSelectionLimit"
    val minFraction: Double? = null,            // supermajority only.
)
````

1. for each contest, verify that candidate names and candidate ids are unique.
2. over all contests, verify that the names and ids are unique.

## Contest verification

````
class Contest(
        val info: ContestInfo,
        val votes: Map<Int, Int>,   // candidateId -> nvotes
        val Nc: Int,                // trusted maximum ballots/cards that contain this contest
        val Ncast: Int,             // number of cast ballots containing this Contest, including undervotes
        val winners: List<Int>,
    )
    
class RaireContest(
    val info: ContestInfo,
    val winners: List<Int>,
    val Nc: Int,
    val Ncast: Int,
    val undervotes: Int,
)

````

1. verify that the candidateIds match whats in the ContestInfo 
2. verify that the winners are unique
3. verify that the winners have more votes than the losers (margins > 0 for all assertions)
4. check that the top nwinners are in the list of winners

## AuditableCard verification

````
data class AuditableCard (
    val location: String, // info to find the card for a manual audit. Aka ballot identifier.
    val index: Int,  // index into the original, canonical list of cards
    val prn: Long,   // psuedo random number
    val phantom: Boolean,
    val contests: IntArray, // list of contests on this ballot. optional when !hasStyles 
    val votes: List<IntArray>?, // for each contest, an array of the candidate ids voted for; for IRV, ranked first to last; missing for pooled data
    val poolId: Int?, // for OneAudit
)
````

1. Check that all locations, indices, and PRNs are unique.
2. If hasStyle, check that the count of non-phantom cards containing a contest = Contest.Ncast.
3. If hasStyle, check that the count of phantom cards containing a contest = Contest.Nc - Contest.Ncast.
4. Given the seed and the PRG, check that the PRNs are correct and are assigned sequentially by index.

## Cvr verification

````
data class Cvr(
    val id: String, // ballot identifier
    val votes: Map<Int, IntArray>, // contest -> list of candidates voted for; for IRV, ranked first to last
    val phantom: Boolean = false,
    val poolId: Int? = null,
)
````

1. Check that each CVR has a corresponding AuditableCard, where id = location

For non-IRV contests:

2. CLCA: tabulate the cvrs and check totals agree with Contest votes
3. OneAudit: tabulate the cvrs and the pool votes and check totals agree with Contest votes

For IRV contests:

4. CLCA: tabulate the cvrs and check VoteConsolidations agree with RaireContest
5. Polling and OneAudit not currently possible

## AuditRound verification

For each audit round, for each contest, verify that the cards selected for auditing have the smallest PRN.

1. if !hasStyles the cards are selected using UniformSampling.
2. if hasStyles the cards are selected using ConsistentSampling.







