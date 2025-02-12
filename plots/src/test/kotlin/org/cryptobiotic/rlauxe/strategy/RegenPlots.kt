package org.cryptobiotic.rlauxe.strategy

import org.junit.jupiter.api.Test

class RegenPlots {

    @Test
    fun regenPlots() {
        val plotGen = GenVsMarginByStrategy2()
        plotGen.phantomPct = 0.0
        plotGen.name = "clcaVsMarginByStrategy0"
        plotGen.genSamplesVsFuzzByStrategy()

        plotGen.phantomPct = 0.01
        plotGen.name = "clcaVsMarginByStrategy1"
        plotGen.genSamplesVsFuzzByStrategy()

        plotGen.phantomPct = 0.02
        plotGen.name = "clcaVsMarginByStrategy2"
        plotGen.genSamplesVsFuzzByStrategy()

        val plotGen2 = GenVsMarginByStrategy()
        plotGen2.fuzzPct = 0.005
        plotGen2.name = "clcaVsMarginByStrategy05"
        plotGen2.genSamplesVsFuzzByStrategy()

        plotGen2.fuzzPct = 0.01
        plotGen2.name = "clcaVsMarginByStrategy1"
        plotGen2.genSamplesVsFuzzByStrategy()

        plotGen2.fuzzPct = 0.04
        plotGen2.name = "clcaVsMarginByStrategy4"
        plotGen2.genSamplesVsFuzzByStrategy()
    }
}