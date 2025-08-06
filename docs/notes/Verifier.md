# Verifier for comparison audit with complete contest info on the CVRs

## 1. Committing to Information About Cards

The Prover must supply an ordered list of CVRs which constitutes the Find, CVR and Style functions: 

    CVR : 1..N → (V ∪ *bad* ∪ ⊥)
    If CVR(i) == ⊥ the Prover does not claim to be able to retrieve i.

* The CVRs have the ballot identifier.
* The CVRs may not be changed in any way after committment.
* The CVRS may have multiple contests.
* The CVR comittment must be made before the seed is drawn.

**Verification 1. The Verifier must verify the ballot identifiers are unique.**

## 2. Proof Parameters

The proof parameters define values and algorithms that are used by both the Prover and the
Verifier. These must be committed to before the election starts.

* A risk limit α ∈ [0, 1]
* A sampling function.
* A p-value calculator ProbVal (see Section 1.8).
* A stopping condition Stop (see Section 1.9).


## 3. Sampling function

A random seed is chosen. The process of creating the seed is publically visible, so all parties are ensured 
that it is both random and was created after the CVR committmwnt was made.

A pseudorandom generator PRG is provided to the Verifier(s). The PRG creates a deterministic sequence of pseudorandom numbers from the seed.
It uses this to create a random ordering of the CVRs, which is used to tell the Prover which ballots to audit.

All verifiers use the same PRG. The Verifier publishes the ordered list of ballot ids that were audited.

**Verification 2. The Verifier verifies the list of ballot ids are the ones created by the PRG with the given seed.**

Note: In this formulation, we verify another Verifier, not the Prover.


## 4. P-value calculator

A p-value calculator inputs a sequence of values calculated by an assorter from
votes that are assumed to derive from some sampling strategy. It assesses the
hypothesis that the mean of the population (of assorter values) is less than
or equal to 1/2.

A p-value function is sequentially valid if when it is evaluated repeatedly
over a telescoping sample, the chance that it is ever below q is at most q.


## 5. Ballot auditing workflow

### 5.1 Sequential, single contest

A sequential audit, or "one ballot at a time" makes things simple. Here we assume all ballots have
the same single contest, but there may be multiple assertions to test.

````
val sortedBallots = ballots.sortedBy { it.prn }

sortedBallots.forEach { ballot ->
    val mvr = Audit(ballot.desc) // do the audit on this ballot
    
    ballot.contest.assertions.filter { !assertion.done }.forEach { assertion ->
        val assortVal = Assort(assertion, ballot.cvr, mvr)
        val pval = Pvalue(assertion, assortVal)
        if (pval < risk) {
            assertion.done = true 
        }
    }
    if (contest.assertions.all { assertion.done } {
        contest.done = true
        break
    }
}
````

### 5.2 Sequential, multicontest

Here we allow multiple contests per ballot. To avoid sampling bias, its necessary that ballots are examined
in sorted order and only skip ballots that have no contests needing auditing.

````
val sortedBallots = ballots.sortedBy { it.prn }
val needsAudit = allContests

sortedBallots.forEach { ballot ->
    if (ballot.contests.any { contest.needsAudit() } {
        val mvr = Audit(ballot.id) // do the audit on this ballot
        
        ballot.contests.filter { contest.needsAudit() }.forEach { contest ->
            contest.assertions.filter { !assertion.done }.forEach { assertion ->
                val assortVal = Assort(assertion, ballot, mvr)
                val pval = Pvalue(assertion, assortVal)
                if (pval < risk) {
                    assertion.done = true
                }
            }
        }
        if (contest.assertions.all { assertion.done } {
            contest.done = true
            needsAudit.remove( contest) 
        }
    }
}
````

### 5.3 Batch, single contest

More realistic is a batch process. We estimate, based on the vote margin, how many ballots are needed to
prove the assertions, then audit all those in one batch. If the batchSize turns out to be too small, we do another round, up 
to some maximum batchLimit.

````
val sortedBallots = ballots.sortedBy { it.prn }
val wantIds = sortedBallots.take( batchSize) .map { ballot.id }

val mvrs = AuditAll(wantIds) // do the audit on all these ballots

for (idx : 0 until batchSize) {
    val cvr = sortedBallots[idx]
    val mvr = mvrs[idx]
    
    ballot.contest.assertions.filter { !assertion.done }.forEach { assertion ->
        val assortVal = Assort(assertion, cvr, mvr)
        val pval = Pvalue(assertion, assortVal)
        if (pval < risk) {
            assertion.done = true 
        }
    }
    if (contest.assertions.all { assertion.done } {
        contest.done = true
        break
    }
}
````

### 5.4 Batch, multicontest

Here we allow multiple contests per ballot using batches. Each contest has its own estimated batchSize.
We first run through the ballots, and select ballots that have contests needing auditing,
until all contest batchSizes are satisfied.

Again to avoid sampling bias, its important that the ballots are examined in sorted order, and only skip ballots 
that have no contests needing auditing. Do not try to choose ballots based on how many contests it has, or any other reason.

````
val sortedBallots = ballots.sortedBy { it.prn }
val needsAudit = allContests
val wantIds = mutableListOf<String>()
val contestCount = allContests.associateWith { 0 }.toMutableMap()

sortedBallots.forEach { ballot ->
    // any contests on this ballot needing more samples?
    if (ballot.contests.any { contest -> contestCount[contest.id] < contest.batchSize } {
        sortedBallots.add(ballot.id)
        
        ballot.contests.forEach { 
            val count = contestCount[contest.id]
            contestCount[contest.id] = count + 1
        }
    }    
    // are we done ?
    val done = contestCount.all { contest, count -> count >= contest.batchSize }
    if (done) break
}
    
val mvrs = AuditAll(wantIds) // do the audit on all these ballots

for (idx : 0 until mvrs.size) {
    val mvr = mvrs[idx]
    val cvr = sortedBallots.find { ballot.cvr.id == mvr.id } :? throw RuntimeExxception()
    
    ballot.contest.assertions.filter { !assertion.done }.forEach { assertion ->
        val assortVal = Assort(assertion, ballot, mvr)
        val pval = Pvalue(assertion, assortVal)
        if (pval < risk) {
            assertion.done = true 
        }
    }
    if (contest.assertions.all { assertion.done } {
        contest.done = true
        break
    }
}    

````







