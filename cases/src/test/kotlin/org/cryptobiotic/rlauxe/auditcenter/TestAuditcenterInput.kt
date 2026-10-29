package org.cryptobiotic.rlauxe.auditcenter

import kotlin.test.Test

class TestAuditcenterInput {

    @Test
    fun testAll() {
        testAuditcenterInput2024()
        testAuditcenterInput2025()
        testAuditcenterInput2026p()
        testAuditcenterInput2020()
    }

    fun test(input: AuditcenterInput) {
        println("---------------------------------------------------")
        println(input.topdir)
        val (manifests, cvrs) = input.checkStatus()
        val manifestz = manifests.map { it.replace(" ", "") }.toSet()
        val cvrz = cvrs.map { it.replace(" ", "") }.toSet()
        val cm = input.checkManifests()
        test(manifestz, cm, "manifests from status")
        test(cvrz, cm, "cvrs from status")
    }

    fun test(set1: Set<String>, set2: Set<String>, what: String) {
        if (set1 == set2) println("contests match $what")
        else {
            println("status has ${set1 - set2} not in manifest")
            println("manifest has ${set2 - set1} not in status upload")
        }
    }

    @Test
    fun testAuditcenterInput2024() {
        test(AuditcenterInput(
            topdir = "/home/stormy/datadrive/github/nealmcb/auditcenter/2024/general",
            manifestdir = "",
            manifestFileTemplate = "namezBallotManifest",
            uploadStatusFile = "uploadStatus.csv",
            batchCountComparison = "batchCountComparison.csv",
        ))
    }

    @Test
    fun testAuditcenterInput2025() {
        test(AuditcenterInput(
            topdir = "/home/stormy/datadrive/github/nealmcb/auditcenter/2025",
            manifestdir = "files",
            manifestFileTemplate = "namezManifest",
            uploadStatusFile = "uploadStatus.csv",
            batchCountComparison = "batchCountComparison.csv",
        ))
    }

    @Test
    fun testAuditcenterInput2026p() {
        test(AuditcenterInput(
            topdir = "/home/stormy/datadrive/github/nealmcb/auditcenter/2026/primary",
            manifestdir = "files",
            manifestFileTemplate = "namez",
            uploadStatusFile = "files/uploadStatus.csv",
            batchCountComparison = null,
        ))
    }

    @Test
    fun testAuditcenterInput2020() {
        test(AuditcenterInput(
            topdir = "/home/stormy/datadrive/github/nealmcb/auditcenter/2020/general",
            manifestdir = "round_1",
            manifestFileTemplate = "manifest-namez",
            uploadStatusFile = "upload_status.csv",
            batchCountComparison = "batch_count_comparison.csv",
        ))
    }
}