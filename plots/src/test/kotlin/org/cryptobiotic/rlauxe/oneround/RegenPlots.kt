package org.cryptobiotic.rlauxe.oneround

import kotlin.test.Test


class RegenPlots {

    @Test
    fun regenPlots() {
        val plotGen = GenVsMarginByStrategy2()
        plotGen.phantomPct = 0.0
        plotGen.dirName = "/home/stormy/temp/oneround/marginByStrategy0"
        plotGen.genSamplesVsMarginByStrategy()

        plotGen.phantomPct = 0.01
        plotGen.dirName = "/home/stormy/temp/oneround/marginByStrategy1"
        plotGen.genSamplesVsMarginByStrategy()

        plotGen.phantomPct = 0.02
        plotGen.dirName = "/home/stormy/temp/oneround/marginByStrategy2"
        plotGen.genSamplesVsMarginByStrategy()
    }

}