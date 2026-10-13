/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.gradle.plugin

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.gradle.api.GradleException

class KmpTargetSelectionTest {
    private val linux = KmpHost("Linux", "amd64")
    private val windows = KmpHost("Windows 11", "amd64")
    private val mac = KmpHost("Mac OS X", "aarch64")

    @Test
    fun noPropertyMeansJvmOnly() {
        val selection = KmpTargetSelection.parse(null)
        assertEquals(setOf(KmpTarget.JVM), selection.named)
        assertFalse(selection.all)
        KmpTarget.entries.filter { target -> target != KmpTarget.JVM }.forEach { target ->
            assertFalse(selection.requested(target), "$target requested without a property")
        }
    }

    @Test
    fun tokensAreCaseInsensitiveAndAliasesFold() {
        val selection = KmpTargetSelection.parse(" WASM, LinuxX64 ,js")
        assertEquals(setOf(KmpTarget.JVM, KmpTarget.WASM_JS, KmpTarget.NATIVE, KmpTarget.JS), selection.named)
        assertEquals(KmpTarget.WASM_JS, KmpTargetSelection.target("wasmJs"))
        assertEquals(KmpTarget.NATIVE, KmpTargetSelection.target(" linuxx64 "))
    }

    @Test
    fun unknownTokenFailsWithTheTokenAndTheAllowedValues() {
        val error = assertFailsWith<GradleException> { KmpTargetSelection.parse("jvm,andriod") }
        assertContains(error.message.orEmpty(), "'andriod'")
        assertContains(error.message.orEmpty(), "wasmJs")
        assertFailsWith<GradleException> { KmpTargetSelection.parse("wasmsj") }
        assertFailsWith<GradleException> { KmpTargetSelection.target("all") }
    }

    @Test
    fun blankValueIsAnError() {
        assertFailsWith<GradleException> { KmpTargetSelection.parse("") }
        assertFailsWith<GradleException> { KmpTargetSelection.parse(" , ") }
    }

    @Test
    fun allRequestsEverythingButAndroid() {
        val selection = KmpTargetSelection.parse("all")
        assertTrue(selection.requested(KmpTarget.JS))
        assertTrue(selection.requested(KmpTarget.WASM_JS))
        assertTrue(selection.requested(KmpTarget.NATIVE))
        assertTrue(selection.requested(KmpTarget.IOS))
        assertFalse(selection.requested(KmpTarget.ANDROID))
        assertTrue(KmpTargetSelection.parse("all,android").requested(KmpTarget.ANDROID))
    }

    @Test
    fun namedIosFailsOffMacOsAndNamesTheHost() {
        val selection = KmpTargetSelection.parse("jvm,ios")
        val error = assertFailsWith<GradleException> { selection.enabledOn(windows, KmpTarget.IOS) }
        assertContains(error.message.orEmpty(), "Windows 11 amd64")
        assertTrue(selection.enabledOn(mac, KmpTarget.IOS))
    }

    @Test
    fun allDropsIosOffMacOsAndReportsIt() {
        val dropped = mutableListOf<String>()
        assertFalse(KmpTargetSelection.parse("all").enabledOn(linux, KmpTarget.IOS) { message -> dropped += message })
        assertEquals(1, dropped.size)
        assertContains(dropped.single(), "Linux amd64")
        assertTrue(KmpTargetSelection.parse("all").enabledOn(mac, KmpTarget.IOS))
    }

    @Test
    fun nativeIsEnabledOnEveryHost() {
        val selection = KmpTargetSelection.parse("native")
        listOf(linux, windows, mac).forEach { host -> assertTrue(selection.enabledOn(host, KmpTarget.NATIVE), host.description) }
    }

    @Test
    fun hostClassification() {
        assertTrue(mac.isMacOs)
        assertFalse(KmpHost("Linux-mac80211", "amd64").isMacOs)
        assertTrue(linux.isLinuxX64)
        assertTrue(KmpHost("Linux", "x86_64").isLinuxX64)
        assertFalse(KmpHost("Linux", "aarch64").isLinuxX64)
        assertFalse(windows.isLinuxX64)
    }
}
