package org.cryptobiotic.rlauxe.raire

data class RaireContest(
    val name: String,
    val candidates: List<String>,
    val winner: String,
    val tot_ballots: Int,
    val outcome: IntArray, // aka order
)

/*
fun load_contests_from_txt(path: String) {
    /*
        Format:
        First line is a comma separated list of candidate identifiers, either
        ending with the winner expressed as ",winner,winner identifier" or
        addditionally specifying the full outcome with ",order,sequence"

        Second line is party identifiers for each candidate
        Third line is a separator (eg. -----)

        Each subsequent line has the form:
        (Comma separated list of candidate identifiers) : Number of ballots

        Each line defines a ballot signature, a preference ordering over
        candidates, and the number of ballots that have been cast with that
        signature.

        Use default contest name of "1".
    */

    contests = []
    cvrs = {}

    var tot_auditable_ballots = 0

    with(open(path, "r") as data) {
        val lines = data.readlines()

        val toks = [line.strip() for line in lines[0].strip().split(',')]
        val windx = toks.index("winner")
        val winner = toks[windx + 1]
        val cands = toks[:windx]

        val order = []
        if ("order" in toks) {
            order = toks[windx + 2:]
        }

        val bcntr = 0

        for (l in range(3, len(lines))) {
            toks = [line.strip() for line in lines[l].strip().split(':')]
        }

        val num = int(toks[1])

        val prefs = [p.strip() for p in toks[0][1:-1].split(',')]

        tot_auditable_ballots += num

        if (prefs == []) {
            continue
        }

        for (i in range(num)) {
            val ballot = {}
            for (c in cands) {
                if (c in prefs) {
                    idx = prefs.index(c)
                    ballot[c] = idx
                }

                cvrs[bcntr] = { 1 : ballot }
                bcntr += 1
            }
        }

        return [Contest(
            1, cands, winner, this.tot_ballots,
            order = order
        )], cvrs
    }
}

// Data file in .raire format.
fun load_contests_from_raire(path: String) {
    val contests = []

//  A map between ballot id and the relevant CVR.
    val cvrs = {}
    with(open(path, "r") as data) {
        val lines = data.readlines()

//  Total number of contests described in data file
        val ncontests = int(lines[0])

//  Map between contest id and number of ballots involving that contest
        val num_ballots = {}

//  Map between contest id and the candidates & winner of that contest.
        val contest_info = {}

        for (i in range(ncontests)) {
            toks = [line.strip() for line in lines[1 + i].strip().split(',')]
        }

//  Get contest id and number of candidates in that contest
        val cid = toks[1]
        val ncands = int(toks[2])

//  Get list of candidate identifiers
        val cands = []

        for (j in range(ncands)) {
            cands.append(toks[3 + j])
        }

        val windx = toks.index("winner")
        val winner = toks[windx + 1]

        var informal = 0
        var inf_index = null
        if ("informal" in toks) {
            inf_index = toks.index("informal")
            informal = int(toks[inf_index + 1])
        }

        val order = []
        if ("order" in toks) {
            order = toks[windx + 2:inf_index] if inf_index != null  else
            toks[windx + 2:]
        }

        contest_info[cid] = (cands, winner, order)
        num_ballots[cid] = informal

        for (l in range(ncontests + 1, len(lines))) {
            toks = [line.strip() for line in lines[l].strip().split(',')]


            val cid = toks[0]
            val bid = toks[1]
            val prefs = toks[2:]

            val ballot = {}
            for (c in contest_info[cid][0]) {
                if (c in prefs) {
                    idx = prefs.index(c)
                    ballot[c] = idx
                }
            }
            num_ballots[cid] += 1

            if (!bid in cvrs) {
                cvrs[bid] = { cid: ballot }
            } else {
                cvrs[bid][cid] = ballot
            }
        }

        for (cid, (cands, winner, order) in contest_info.items()) {
        con = Contest(cid, cands, winner, num_ballots[cid], order = order)
        contests.append(con)
    }
    }

    return Pair(contests, cvrs)
}

 */