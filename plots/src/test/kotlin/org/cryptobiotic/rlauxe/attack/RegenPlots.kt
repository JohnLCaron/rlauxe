package org.cryptobiotic.rlauxe.attack

import kotlin.test.Test


class RegenPlots {

    @Test
    fun regenPlots() {
        val plotGen = OaPhantomAttack()
        plotGen.phantomPct = 0.0
        plotGen.name = "marginWithPhantoms0"
        plotGen.dirName = "/home/stormy/temp/attack/marginWithPhantoms0"
        plotGen.genSamplesVsMarginWithPhantoms()

        plotGen.phantomPct = 0.02
        plotGen.name = "marginWithPhantoms2"
        plotGen.dirName = "/home/stormy/temp/attack/marginWithPhantoms2"
        plotGen.genSamplesVsMarginWithPhantoms()

        plotGen.phantomPct = 0.05
        plotGen.name = "marginWithPhantoms5"
        plotGen.dirName = "/home/stormy/temp/attack/marginWithPhantoms5"
        plotGen.genSamplesVsMarginWithPhantoms()
    }

}