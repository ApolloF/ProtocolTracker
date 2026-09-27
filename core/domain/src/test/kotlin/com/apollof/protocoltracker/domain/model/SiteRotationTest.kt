package com.apollof.protocoltracker.domain.model

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SiteRotationTest {
    private val start = Instant.parse("2026-09-01T08:00:00Z")
    private val snapshot = DoseSnapshot("Test C (testosterone cypionate)", "testosterone", CompoundCategory.INJECTABLE_STEROID, BaseUnit.MG, null)

    /** Taken doses one day apart, oldest first, as [sites] (null = no site). */
    private fun history(vararg sites: String?, compound: String = "tc"): List<DoseLog> =
        sites.mapIndexed { i, site -> log("$compound$i", start.plus(Duration.ofDays(i.toLong())), site, compound = compound) }

    private fun log(
        id: String,
        takenAt: Instant,
        site: String?,
        status: LogStatus = LogStatus.TAKEN,
        createdAt: Instant = takenAt,
        compound: String = "tc",
    ) = DoseLog(id, "item-$compound", compound, null, null, takenAt, Amount(125.0, DoseUnit.MG), null, status, "", snapshot, createdAt, site = site)

    private fun suggestion(vararg sites: String?) = SiteRotation.state(history(*sites))?.suggestion

    @Test
    fun noHistoryOrNoSitesMeansNotTracked() {
        assertNull(SiteRotation.state(emptyList()))
        assertNull(SiteRotation.state(history(null, null, null)))
        assertNull(SiteRotation.state(history("", " ")))
    }

    @Test
    fun theFirstSiteSuggestsItsMirror() {
        assertEquals("delt_r", suggestion("delt_l"))
        assertEquals("vg_l", suggestion("vg_r"))
        assertEquals("thigh_l", suggestion("thigh_r"))
    }

    @Test
    fun aLearnedCycleIsFollowed() {
        assertEquals("vg_l", suggestion("vg_r", "vg_l", "delt_l", "delt_r", "vg_r"))
        assertEquals("delt_l", suggestion("vg_r", "vg_l", "delt_l", "delt_r", "vg_r", "vg_l"))
        assertEquals("delt_r", suggestion("vg_r", "vg_l", "delt_l", "delt_r", "vg_r", "vg_l", "delt_l"))
        assertEquals("vg_r", suggestion("vg_r", "vg_l", "delt_l", "delt_r", "vg_r", "vg_l", "delt_l", "delt_r"))
    }

    @Test
    fun anAbandonedSiteDropsAfterOneOverride() {
        // The cycle suggests delt_l after vg_l; he picks delt_r instead, once.
        val sites = arrayOf("vg_r", "vg_l", "delt_l", "delt_r", "vg_r", "vg_l", "delt_r")
        assertEquals("vg_r", suggestion(*sites))
        assertEquals("vg_l", suggestion(*sites, "vg_r"))
        assertEquals("delt_r", suggestion(*sites, "vg_r", "vg_l"))
    }

    @Test
    fun aNewestDoseWithoutASiteBreaksTheChain() {
        val state = SiteRotation.state(history("delt_l", "delt_r", null))!!
        assertNull(state.suggestion)
        assertEquals(SiteUse("delt_r", start.plus(Duration.ofDays(1))), state.last)
        assertEquals(listOf("delt_l", "delt_r"), state.personal)
        // Picking a site restarts it.
        assertEquals("delt_l", suggestion("delt_l", "delt_r", null, "delt_r"))
    }

    @Test
    fun skippedDosesAreIgnored() {
        val logs = history("delt_l", "delt_r") +
            log("skip", start.plus(Duration.ofDays(5)), null, status = LogStatus.SKIPPED) +
            log("skip-sited", start.plus(Duration.ofDays(6)), "pec_l", status = LogStatus.SKIPPED)
        val state = SiteRotation.state(logs)!!
        assertEquals("delt_r", state.last.site)
        assertEquals("delt_l", state.suggestion)
        assertEquals(listOf("delt_l", "delt_r"), state.personal)
        assertNull(SiteRotation.state(listOf(log("s", start, "delt_l", status = LogStatus.SKIPPED))))
    }

    @Test
    fun theSameSiteTwiceFallsBackToTheMirror() {
        assertEquals("glute_r", suggestion("glute_l", "glute_l"))
        assertEquals("glute_r", suggestion("glute_l", "delt_l", "glute_l", "glute_l"))
    }

    @Test
    fun anUnknownKeyIsKeptRawWithoutAMirror() {
        val state = SiteRotation.state(history("delt_l", "calf_l"))!!
        assertEquals("calf_l", state.last.site)
        assertNull(state.suggestion)
        assertEquals(listOf("delt_l", "calf_l"), state.personal)
        assertEquals("calf_l", InjectionSites.label("calf_l"))
        assertEquals("calf_l", InjectionSites.longLabel("calf_l"))
        assertNull(InjectionSites.mirror("calf_l"))
        // A learned successor still counts.
        assertEquals("delt_l", suggestion("calf_l", "delt_l", "calf_l"))
    }

    @Test
    fun onlyTheLast24TakenDosesCount() {
        // 25 doses: the oldest is the only other use of glute_l, so without the window it would suggest delt_l.
        val sites = listOf("glute_l", "delt_l") + List(22) { if (it % 2 == 0) "vg_l" else "vg_r" } + "glute_l"
        assertEquals(25, sites.size)
        assertEquals("glute_r", suggestion(*sites.toTypedArray()))
        // One dose fewer in between: the earlier glute_l is the 24th dose back and still teaches delt_l.
        val inWindow = listOf("glute_l", "delt_l") + List(21) { if (it % 2 == 0) "vg_l" else "vg_r" } + "glute_l"
        assertEquals(24, inWindow.size)
        assertEquals("delt_l", suggestion(*inWindow.toTypedArray()))
        // A site only in the 25th dose back is no longer in the personal set.
        val older = listOf("pec_l") + List(24) { "delt_r" }
        assertEquals(listOf("delt_r"), SiteRotation.state(history(*older.toTypedArray()))!!.personal)
    }

    @Test
    fun ordersByTimeTakenNotByTimeLogged() {
        // Logged in the order delt_l, vg_l, but vg_l was taken first (backfilled).
        val logs = listOf(
            log("a", start.plus(Duration.ofDays(2)), "delt_l", createdAt = start.plus(Duration.ofDays(2))),
            log("b", start.plus(Duration.ofDays(1)), "vg_l", createdAt = start.plus(Duration.ofDays(3))),
        )
        val state = SiteRotation.state(logs)!!
        assertEquals("delt_l", state.last.site)
        assertEquals("delt_r", state.suggestion)
        // The same when handed in any order.
        assertEquals(state, SiteRotation.state(logs.reversed()))
    }

    @Test
    fun thePersonalSetIsInCatalogOrderThenUnknownNewestFirst() {
        val state = SiteRotation.state(history("thigh_r", "x_one", "delt_l", "x_two", "vg_r", "delt_l"))!!
        assertEquals(listOf("delt_l", "vg_r", "thigh_r", "x_two", "x_one"), state.personal)
    }

    @Test
    fun eachCompoundHasItsOwnHistory() {
        val logs = history("delt_l", "delt_r", compound = "tc") + history("abdomen_l", compound = "bpc") + history(null, compound = "hcg")
        val states = SiteRotation.byCompound(logs)
        assertEquals(setOf("tc", "bpc"), states.keys)
        assertEquals("delt_l", states.getValue("tc").suggestion)
        assertEquals("abdomen_r", states.getValue("bpc").suggestion)
    }

    @Test
    fun theCatalogHas12SitesWithMirrorsAndLabels() {
        val keys = InjectionSites.all.map { it.key }
        assertEquals(
            listOf("delt_l", "delt_r", "pec_l", "pec_r", "abdomen_l", "abdomen_r", "vg_l", "vg_r", "glute_l", "glute_r", "thigh_l", "thigh_r"),
            keys,
        )
        keys.forEach { key ->
            val mirror = InjectionSites.mirror(key)!!
            assertEquals(key, InjectionSites.mirror(mirror))
            assertEquals(key.substringBefore('_'), mirror.substringBefore('_'))
        }
        assertEquals("L VG", InjectionSites.label("vg_l"))
        assertEquals("Right ventrogluteal", InjectionSites.longLabel("vg_r"))
        assertEquals("R delt", InjectionSites.label("delt_r"))
        assertEquals("Left deltoid", InjectionSites.longLabel("delt_l"))
        assertEquals(
            listOf("L delt", "R delt", "L pec", "R pec", "L abdomen", "R abdomen", "L VG", "R VG", "L glute", "R glute", "L thigh", "R thigh"),
            InjectionSites.all.map { it.short },
        )
        assertEquals(12, InjectionSites.all.map { it.long }.toSet().size)
    }
}
