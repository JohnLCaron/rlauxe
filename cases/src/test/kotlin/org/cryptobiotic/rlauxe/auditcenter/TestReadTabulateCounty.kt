package org.cryptobiotic.rlauxe.auditcenter

import org.cryptobiotic.rlauxe.corla.CorlaStateContestInfoBuilder
import org.cryptobiotic.rlauxe.corlaCounty.CountyCardConverter
import org.cryptobiotic.rlauxe.corlaCounty.CountyCardsFromManifest
import org.cryptobiotic.rlauxe.corlaCounty.makeContestInfo
import org.cryptobiotic.rlauxe.corlaInput.Statewide2020General
import org.cryptobiotic.rlauxe.corlaInput.Statewide2026Primary
import org.cryptobiotic.rlauxe.corlaInput.StatewideInput
import org.cryptobiotic.rlauxe.corlacvr.CorlaCountyCvrsIF
import org.cryptobiotic.rlauxe.corlacvr.CvrTabulations
import org.cryptobiotic.rlauxe.corlacvr.tabulate
import org.cryptobiotic.rlauxe.util.ContestTabulation
import kotlin.collections.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestReadTabulateCounty {
    val input = Statewide2020General()

    @Test
    fun writeGunnisonAddendum() {
        val county = "Gunnison"
        // Gunnison not in round file; hack in from the cvrs
        val countyInput = input.corlaCountyInput("Gunnison")!!
        val gunnisonCvrs = countyInput.readCorlaCvrs()
        val infos = gunnisonCvrs.makeContestInfo()
        val cvrTabulation: CvrTabulations = tabulate(gunnisonCvrs, infos)
        // CvrTabulations(val infos: Map<Int, CorlaContestInfo>,
        //                          val cvrTabs: Map<Int, ContestTabulation>,
        //                          val redactedTabs: Map<Int, ContestTabulation>)

        // data class CorlaContestInfo(
        //    val name: String,
        //    val id: Int,
        //    val candidateNames: Map<String, Int>,
        //    val isIrv: Boolean,
        //    val voteForN: Int) {
        //        val candidateIdToName: Map<Int, String> = candidateNames.entries.associate {(k,v) -> v to k }
        //}

        println("county_name,contest_name,choice,votes")
        val sums = cvrTabulation.sum().toSortedMap()
        infos.forEach { id, info ->
            val sum: ContestTabulation = sums[id]!!
            // todo canonNames
            sum.votes.toSortedMap().forEach { candId, candVote ->
                println("$county, ${info.name}, ${info.candidateIdToName[candId]}, $candVote")
            }
        }
        // county_name,contest_name,choice,votes
        //Adams,Presidential Electors,Joseph R. Biden / Kamala D. Harris,134202
        //Adams,Presidential Electors,Donald J. Trump / Michael R. Pence,95657
        //Adams,Presidential Electors,"Jo Jorgensen / Jeremy ""Spike"" Cohen",3731
        //Adams,Presidential Electors,Howie Hawkins / Angela Nicole Walker,744
    }

    @Test
    fun readGunnisonCvrs() {
        val countyInput = input.corlaCountyInput("Gunnison")!!
        val gunnisonCvrs = countyInput.readCorlaCvrs()
        val infos = gunnisonCvrs.makeContestInfo()
        val gunnisonTabulate: CvrTabulations = tabulate(gunnisonCvrs, infos)

        // now convert to canonical
        val stateContestBuilder = CorlaStateContestInfoBuilder(input)
        val countyCards = CountyCardsFromManifest(countyInput)
        val converter = CountyCardConverter(countyInput.countyName, countyCards, input, stateContestBuilder.infosByName)
        val convertedTab = converter.convertTabs(gunnisonTabulate.sum())

        val gunnisonCountyTab = CountyTabAllContests("Gunnison")
        gunnisonCountyTab.addContestTabulations(convertedTab, converter.infos)
        println(gunnisonCountyTab)

        val result = input.countyTabsAllContests() + mapOf("Gunnison" to gunnisonCountyTab)
    }

    @Test
    fun readTabulateCounties() {
        val countyTabs: Map<String, CountyTabAllContests> = readCountyTabulateCsvFromFile(input.tabulateCountyFile)
        val countyTabs2: Map<String, CountyTabAllContests> = input.countyTabsAllContests()
        countyTabs.forEach {
            val tab2 = countyTabs2[it.key]
            assertEquals(it.value, tab2)
            println(it.value)
        }
    }

    @Test
    fun contestTabAllCounties() {
        val contestTabAllCounties: Map<String, ContestTabAllCounties> = input.contestTabsAllCounties()
        contestTabAllCounties.forEach {
            assertTrue(!input.skipCounties(it.key))
            println(it)
        }
    }
}