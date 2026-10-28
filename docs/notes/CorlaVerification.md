# CORLA Verification notes

_last changed 06/02/2026_

See   https://docs.google.com/document/d/1bAZCWKFRmo6W3K2zC604IKoMrVNvETx3muyo_U_ENuI/edit?tab=t.0#heading=h.1lgfbw97qzhb

# County Support for Public Election Verification

Pilot the involvement of audit verifiers for the RLA of the June 2026 primary.
La Plata, Weld, Boulder and El Paso.

* Before the random seed is drawn, a redacted version of the CVR file that was submitted to the state must be produced and a SHA256 hash of that file made publicly available.
* By day 13 (after the election), the random seed is selected.
* At least a day before the RLA, which could be the same date as the selection of the random seed, the county will make the following files available on the county web site:
    * the redacted CVR file
    * the list of ballot sheets to be audited, identified by Imprinted Id.
    * the number of audit boards that the county will use to audit the ballot sheets.
    * the ballot manifest
    * the summary results file that was uploaded to the SoS at the same time that the unredacted CVR file and the ballot manifest was uploaded.

Questions:
* I think "ballot sheet" = card in SHANGRLA terminology? YES
* Does each CVR = 1 ballot sheet ? Even when there are multiple "sheets" per ballot? YES
* What does the redaction of the CVR file do? Are they aggregating ballots in some way (like Boulder County) or is there one line per CVR ?
* What does the ballot manifest look like? Does each sheet have their own entry?
* Is there a seperate "trusted maximum" number of cards for each contest? Or do we just use the CVR count?

# Corla observer-support

See: https://github.com/nealmcb/auditcenter_analyze-private/blob/main/docs/observer-support/PLAN.md

The tool will:

1. Audit center data: pull the latest data from the Secretary of State's audit center. 
   _** Can you list what specific files we need to have? County manifests, upload_status, batch_count_comparison?_
   _** what do they already have at this point? CanonicalListOfContestsAndChoices? ContestsListRound1?_
2. County files: accept the files the project team gets from each county. The redacted cast vote record (CVR) arrives before the drawing, and the county's pull list (CORLA's "List of ballots to audit") arrives afterwards. 
3. Redaction check: confirm each redacted CVR is adequately redacted as provided, or report rare-style names and counts only if there are issues. _*** Process for reporting??_
4. Consistency checks: check ballot manifests, CVRs and counts against each other. _*** Process for reporting??_
5. Timestamping: commit to all of this evidence before the random seed is rolled, and commit to the derived extract before the audit starts.
   *** Detail how commit works. timestamped?
6. Seed: accept the seed by hand once it is announced. 
7. Selections: predict each county's selected ballots, and check them against an independent implementation and the county's pull list. 
8. Extract: produce the observers' extract for printouts, spreadsheets and the online entry site. 
9. Live observation: support up to several observers per audit board as they follow the read-out ballot by ballot, each on the medium they choose (paper, spreadsheet or online), with online progress pushed live to a public status dashboard that needs no login. The dashboard is shared at first only within a small circle.

**The reference**

Committed inputs: the redacted CVRs, manifests and audit center files are hashed and timestamped before the seed is rolled.

Deterministic derivation: the predicted selections and the extract are a deterministic function of the committed inputs plus the public seed and the county's pull list, computed by published, versioned code.

Committed before the audit: the printed extract and its per-page hashes are committed and timestamped before the audit starts, so they can't be changed afterwards to fit.

Three-way agreement: before the audit, the tool compares three independent results:

* its own prediction
* an independent implementation, the RLAAuditHelper at https://github.com/loriinboulder/AuditVerificationAssistant
* the county's CORLA pull list

Any disagreement is reported before the first ballot is read.

Seed: observers note the seed announced at the public ceremony and compare it with the seed shown on their screen or printout.

Printed and on-screen identification: every page or screen of the extract shows:

* the seed
* the hashes of the inputs
* the extract's own hash
* its position ("page 3 of 12", or "ballot 41 of 316")
* a QR code carrying the page's own hash

_This info makes the page crowded, as Lori noted_

Photo check: tooling is available to take a photograph of a printed extract page, read its QR code and printed text, and confirm that every imprinted ID and expected vote on it matches the committed extract. A coordinator can then confirm a printout is genuine before observers rely on it.

**The record**

Paper: the observer's marked printout, initialed and dated, is a record outside the software. It can be photographed at the end of the day, and the photographs hashed and committed.
Screen: each entry made on a phone or laptop produces a receipt (next subsection). The observer's own device keeps every entry and every receipt, independently of the server.

**The summary**

The hash-chained event log is published, so anyone can recompute the dashboard's figures from it. An observer can confirm that their own entries are in it, unchanged.

_Append-only logger?_

-----------------------
What it is: a website that follows the audit for observers. 

1. It shows each ballot the audit board will read, with its imprinted ID large and the votes the county's CVR recorded for it. As the board reads each ballot, the observer taps to record a match or types what they heard instead. 
2. Every entry is signed by the observer's own phone or laptop and gets a receipt from the server, and each observer can download their own record at any time, so the record doesn't depend on trusting our server. Paper and spreadsheets remain fully supported alternatives.

Behind the scenes it also:
- pulls the Secretary of State's audit center files
- checks each county's redacted CVR for rare ballot styles that could identify voters, by precinct and ballot type
- predicts the random selections from the seed (it reproduced every July 2026 selection)
- builds the observer extract with its own hash on every page
- timestamps the evidence before the dice roll
- flags any manifest or CVR that changes after the roll, as La Plata's did in July

The practice data is the real, public July 2026 primary data: La Plata (127 ballots), Morgan (87) and Weld (316).
