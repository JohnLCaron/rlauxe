package org.cryptobiotic.rlauxe.auditcenter

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.audit.CardPoolIF
import org.cryptobiotic.rlauxe.util.*
import kotlin.Int
import kotlin.String

private val logger = KotlinLogging.logger("CountyContestBuilder")

// Build the Contests from ColoradoInput
open class CountyContestBuilder(val coloradoInput: ColoradoInput) {
    val corlaContestBuilders: List<CorlaContestBuilder> = makeContestBuilders() // 181
    val infos: Map<Int, ContestInfo> = corlaContestBuilders.map { it.info }.associateBy{ it.id }
    val infosByName = infos.mapKeys { it.value.name }

    // TODO set Ncast ??
    fun contests(ncast: Map<Int, Int>): List<Contest> {
        return corlaContestBuilders.map { it.build( ncast[it.contestId] ?: it.Nc) }
    }

    private fun makeContestBuilders(): List<CorlaContestBuilder> {
        val mergedContestMap = coloradoInput.mergedContestMap
        val strataMap = coloradoInput.strataMap

        val contestBuilders = mutableListOf<CorlaContestBuilder>()

        // canonical drives the boat
        mergedContestMap.values.forEach{ mcontest ->
            val contestTabAllCounties = coloradoInput.contestTabAllCounties[mcontest.contestName]
            if (contestTabAllCounties == null) {
                logger.warn{"*** Cant find contestTab for '${mcontest.contestName}': remove from audit" }
                // throw RuntimeException()
            } else {
                val candidateNames = mcontest.choices.mapIndexed { idx, choice -> Pair(choice, idx) }.toMap()

                val info = ContestInfo(
                    mcontest.contestName,
                    contestBuilders.size + 1,
                    candidateNames,
                    SocialChoiceFunction.PLURALITY, // TODO
                    mcontest.voteForN
                )

                val strata = when {
                    (mcontest.counties.size == 1) -> strataMap[mcontest.counties.first()]!!
                    /* (mcontest.counties.size > 60) -> {
                    val contestsPlus = mcontest.counties // + listOf("Statewide")
                    computeStrataMinRate(mcontest.contestName, contestsPlus, strataMap)
                } */
                    else -> computeStrataMinRate(mcontest.contestName, mcontest.counties, strataMap)
                }
                info.metadata["CORLAhaveMvrs"] = strata.nmvrs.toString()
                info.metadata["CORLAstrataNcards"] = strata.ballotCardCount.toString()
                info.metadata["CORLAsample"] = mcontest.nsamples.toString()
                info.metadata["CORLAauditReason"] = mcontest.auditReason.toString()
                info.metadata["CORLAmarginInVotes"] = mcontest.marginInVotes.toString()
                info.metadata["CORLAcounties"] = mcontest.counties.toList().toString()
                info.metadata["CORLAcountyMvrs"] = mcontest.countyMvrs.toString()
                info.metadata["CORLAstatewideNmvrs"] = mcontest.statewideMvrs.toString()

                val contest = CorlaContestBuilder(
                    info,
                    mcontest,
                    strata,
                    contestTabAllCounties,
                )
                contestBuilders.add(contest)
            }
        }

        // println("number of contestBuilders = ${contestBuilders.size}")
        return contestBuilders
    }

    // Neals algorithm: use the minimum rate across strata
    // depends only on the set of counties, could make common one
    fun computeStrataMinRate(name: String, counties: Set<String>, strataMap: Map<String, StrataInfo>): StrataInfo {
        var orgSamples = 0
        val minRate = counties.map {
            val s = strataMap[it]
            if (s == null) 1.0 else {
                orgSamples += s.nmvrs
                s.nmvrs / s.ballotCardCount.toDouble()
            }
        }.min()

        var npop = 0
        var nmvrs = 0
        counties.forEach {
            val strata = strataMap[it]
            if (strata != null) {
                npop += strata.ballotCardCount
                val truncSamples = roundToClosest(strata.ballotCardCount * minRate)
                nmvrs += truncSamples
            }
        }
        return StrataInfo(name, nmvrs, npop)
    }
}

/////////////////////////////////////////////////////////////////////////////

class CorlaContestBuilder(val info: ContestInfo, val mcontest: MergedContestInfo, val strata: StrataInfo, val contestTabAllCounties: ContestTabAllCounties) {
    val contestId = info.id
    val Nc: Int     // taken from contestRound.contestBallotCardCount
    val minCardsNeeded: Int

    val candidateVoteCount: Map<String, Int> // canonicalCandidateName -> votes
    val counties = mcontest.counties

    var poolTotalCards: Int = 0
    var poolTotalVotes: Int = 0

    init {

        candidateVoteCount = contestTabAllCounties.canonicalChoices(mcontest.canonicalContest)

        // candidateVotes = contestTab.choices.values.mapIndexed { idx, choice -> Pair(idx, choice.totalVotes) }.toMap()
        minCardsNeeded = roundUp(candidateVoteCount.map { it.value }.sum() / info.voteForN.toDouble())

        var useNc = mcontest.nc
        if (useNc < minCardsNeeded) {
            println("*** Contest '${info.name}' has $minCardsNeeded total cards, but CorlaContestRoundCsv.contestBallotCardCount is ${mcontest.nc} - using totalVotes")
            useNc = minCardsNeeded
        }
        Nc = useNc
    }

    fun setTotalCardsFromPools(cardPools: List<CardPoolIF>){
        poolTotalCards = cardPools.filter{ it.hasContest(info.id) }.sumOf { it.ncards() }
        poolTotalVotes = cardPools.filter{ it.hasContest(info.id) }.sumOf { it.contestTab(info.id)!!.nvotes() }
    }

    fun expectedPoolNCards() = Nc

    fun build(ncast:Int): Contest {
        val candVotesById = candidateVoteCount.filter { info.candidateNames[it.key] != null } // get rid of writeins?
                                                .mapKeys { info.candidateNames[it.key]!! }
        var useNc = this.Nc
        if (useNc < ncast) {
            println("*** Contest '${info.name}' has Nc=${this.Nc}, but ncast is ${ncast} - using ncast")
            useNc = ncast
        }
        // we know cvrs are missing lots of cards, so maybe this is dubious
        var useNcast = ncast
        if (useNcast < minCardsNeeded) {
            println("*** Contest '${info.name}' has $minCardsNeeded total cards, but useNcast is ${useNcast}")
            useNcast = minCardsNeeded
        }
        info.metadata["PoolPct"] = (100.0 * poolTotalCards / useNc).toInt().toString()
        return Contest(info, candVotesById, useNc, useNcast)
    }

    override fun toString(): String {
        return "CorlaContestBuilder(info=$info, contestId=$contestId, Nc=$Nc, candidateVoteCount=$candidateVoteCount, poolTotalCards=$poolTotalCards)"
    }
}

// *** Contest 'Baseline Water District Ballot Issue 6B' has 283 total cards, but useNcast is 281
//*** Contest 'City of Colorado Springs Ballot Issue 2A' has Nc=254774, but ncast is 255171 - using ncast
//*** Contest 'City of Colorado Springs Ballot Question 2B' has Nc=254774, but ncast is 255171 - using ncast
//*** Contest 'City of Colorado Springs Ballot Question 2C' has Nc=254774, but ncast is 255171 - using ncast
//*** Contest 'City of Dacono Council Members' has Nc=2998, but ncast is 3000 - using ncast
//*** Contest 'City of Greeley Ballot Issue 2I' has Nc=46363, but ncast is 46370 - using ncast
//*** Contest 'Colorado Springs School District 11 Ballot Issue 4A' has Nc=129326, but ncast is 129516 - using ncast
//*** Contest 'District Attorney - 15th Judicial District' has 7895 total cards, but useNcast is 7656
//*** Contest 'District Attorney - 3rd Judicial District' has 7273 total cards, but useNcast is 4459
//*** Contest 'District Attorney - 4th Judicial District' has Nc=399633, but ncast is 400219 - using ncast
//*** Contest 'District Attorney - 7th Judicial District' has Nc=54591, but ncast is 54592 - using ncast
//*** Contest 'District Court Judge - 4th Judicial District - Bain' has Nc=399633, but ncast is 400219 - using ncast
//*** Contest 'District Court Judge - 4th Judicial District - DuBois' has Nc=399633, but ncast is 400219 - using ncast
//*** Contest 'District Court Judge - 4th Judicial District - Kane' has Nc=399633, but ncast is 400219 - using ncast
//*** Contest 'District Court Judge - 4th Judicial District - McHenry' has Nc=399633, but ncast is 400219 - using ncast
//*** Contest 'District Court Judge - 4th Judicial District - Prince' has Nc=399633, but ncast is 400219 - using ncast
//*** Contest 'District Court Judge - 4th Judicial District - Sokol' has Nc=399633, but ncast is 400219 - using ncast
//*** Contest 'District Court Judge - 4th Judicial District - Werner' has Nc=399633, but ncast is 400219 - using ncast
//*** Contest 'District Court Judge - 7th Judicial District - Deganhart' has Nc=54591, but ncast is 54592 - using ncast
//*** Contest 'District Court Judge - 7th Judicial District - Jackson' has Nc=54591, but ncast is 54592 - using ncast
//*** Contest 'District Court Judge - 7th Judicial District - Patrick' has Nc=54591, but ncast is 54592 - using ncast
//*** Contest 'Donald Wescott Fire Protection District Ballot Issue 6B' has Nc=6486, but ncast is 6493 - using ncast
//*** Contest 'Donald Wescott Fire Protection District Northern Subdistrict Ballot Issue 6C' has Nc=6292, but ncast is 6301 - using ncast
//*** Contest 'El Paso County Commissioner - District 2' has Nc=82083, but ncast is 82209 - using ncast
//*** Contest 'El Paso County Commissioner - District 3' has Nc=85384, but ncast is 85473 - using ncast
//*** Contest 'El Paso County Commissioner - District 4' has Nc=55363, but ncast is 55499 - using ncast
//*** Contest 'El Paso County Court Judge - Findorff' has Nc=382546, but ncast is 383132 - using ncast
//*** Contest 'El Paso County Court Judge - Gerhart' has Nc=382546, but ncast is 383132 - using ncast
//*** Contest 'Fort Lupton Fire Protection District Ballot Issue 6D' has Nc=5698, but ncast is 5699 - using ncast
//*** Contest 'Gilpin County Ballot Issue 1A' has Nc=4239, but ncast is 4240 - using ncast
//*** Contest 'Gilpin County Ballot Issue 1B' has Nc=4239, but ncast is 4240 - using ncast
//*** Contest 'Gilpin County Commissioner - District 1' has Nc=4239, but ncast is 4240 - using ncast
//*** Contest 'Gilpin County Commissioner - District 3' has Nc=4239, but ncast is 4240 - using ncast
//*** Contest 'Gilpin County Library District Ballot Issue 6A' has Nc=4239, but ncast is 4240 - using ncast
//*** Contest 'Kit Carson County Commissioner - District 1' has Nc=3892, but ncast is 3893 - using ncast
//*** Contest 'Kit Carson County Commissioner - District 3' has Nc=3892, but ncast is 3893 - using ncast
//*** Contest 'Miami Yoder School District JT 60 Ballot Question 5A' has Nc=1344, but ncast is 1345 - using ncast
//*** Contest 'Representative to the 117th United States Congress - District 5' has Nc=445654, but ncast is 446240 - using ncast
//*** Contest 'San Miguel County Commissioner - District 1' has Nc=5189, but ncast is 5190 - using ncast
//*** Contest 'San Miguel County Commissioner - District 3' has Nc=5189, but ncast is 5190 - using ncast
//*** Contest 'San Miguel County Referred Question 1A' has Nc=5189, but ncast is 5190 - using ncast
//*** Contest 'State Representative - District 14' has Nc=58822, but ncast is 58912 - using ncast
//*** Contest 'State Representative - District 15' has Nc=51883, but ncast is 51974 - using ncast
//*** Contest 'State Representative - District 16' has Nc=46234, but ncast is 46308 - using ncast
//*** Contest 'State Representative - District 17' has Nc=29004, but ncast is 29049 - using ncast
//*** Contest 'State Representative - District 18' has Nc=46846, but ncast is 46915 - using ncast
//*** Contest 'State Representative - District 19' has Nc=67306, but ncast is 67390 - using ncast
//*** Contest 'State Representative - District 20' has Nc=48686, but ncast is 48735 - using ncast
//*** Contest 'State Representative - District 21' has Nc=33765, but ncast is 33849 - using ncast
//*** Contest 'State Representative - District 23' has Nc=52299, but ncast is 52301 - using ncast
//*** Contest 'State Representative - District 27' has Nc=63531, but ncast is 63532 - using ncast
//*** Contest 'State Representative - District 29' has Nc=49653, but ncast is 49654 - using ncast
//*** Contest 'State Representative - District 48' has Nc=59303, but ncast is 59308 - using ncast
//*** Contest 'State Representative - District 50' has Nc=30267, but ncast is 30269 - using ncast
//*** Contest 'State Representative - District 53' has Nc=48053, but ncast is 48056 - using ncast
//*** Contest 'State Representative - District 58' has Nc=47442, but ncast is 47443 - using ncast
//*** Contest 'State Representative - District 64' has 43783 total cards, but useNcast is 35398
//*** Contest 'State Senator - District 10' has Nc=88174, but ncast is 88321 - using ncast
//*** Contest 'State Senator - District 12' has Nc=81805, but ncast is 81968 - using ncast
//*** Contest 'State Senator - District 14' has Nc=101580, but ncast is 101583 - using ncast
//*** Contest 'State Senator - District 19' has Nc=97274, but ncast is 97275 - using ncast
//*** Contest 'State Senator - District 35' has 73129 total cards, but useNcast is 65318
//*** Contest 'Sunshine Fire Protection District Ballot Issue 6A' has 237 total cards, but useNcast is 235
//*** Contest 'Town of Monument Ballot Issue 2E' has Nc=6542, but ncast is 6551 - using ncast
//*** Contest 'Town of Monument Ballot Question 2F' has Nc=6542, but ncast is 6551 - using ncast

