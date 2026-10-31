package org.cryptobiotic.rlauxe.util

object Locations {
    val home: String = System.getProperty("user.home")
    val user: String = System.getProperty("user.name")
    val startingDir: String = System.getProperty("user.dir")

    // TODO allow setting an environment variable for external developers
    val library = if (user == "joncifer") "$home/IdeaProjects/rlauxe/cases/build/libs" else "$home/dev/github/rla/rlauxe/cases/build/libs"
    val auditcenter = if (user == "joncifer") "$home/IdeaProjects/nealmcb/auditcenter" else "$home/datadrive/github/nealmcb/auditcenter"
    val testdataDir = if (user == "joncifer") "$home/rla/test" else "$home/rla"
    val cases       = if (user == "joncifer") "$home/rla/cases" else "$home/datadrive/rla/cases"
    val votedatabase = if (user == "joncifer") "$home/rla/votedatabase" else "$home/datadrive/votedatabase"
}

