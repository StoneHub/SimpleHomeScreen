package com.stonecode.simplehomescreen.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageRankingTest {
    private val now = 10L * 24 * 60 * 60 * 1_000

    @Test
    fun `recent and frequent usage both contribute to score`() {
        val unused = usage("unused")
        val recent = usage("recent", lastUsedAtMillis = now)
        val frequent = usage("frequent", launchCount = 30)

        assertEquals(0.0, UsageRanking.score(unused, now), 0.0)
        assertTrue(UsageRanking.score(recent, now) > UsageRanking.score(unused, now))
        assertTrue(UsageRanking.score(frequent, now) > UsageRanking.score(unused, now))
    }

    @Test
    fun `recency score halves after configured half life`() {
        val config = UsageRankingConfig(
            recencyWeight = 1.0,
            frequencyWeight = 0.0,
            recencyHalfLifeMillis = 1_000,
        )
        val app = usage("app", lastUsedAtMillis = now - 1_000)

        assertEquals(0.5, UsageRanking.score(app, now, config), 0.000001)
    }

    @Test
    fun `rank is deterministic when scores tie`() {
        val profileTwo = usage("Alpha", component = "com.example/.Alpha", userSerial = 2)
        val profileOne = usage("alpha", component = "com.example/.Alpha", userSerial = 1)
        val beta = usage("Beta")

        val ranked = UsageRanking.rank(listOf(beta, profileTwo, profileOne), now)

        assertEquals(listOf(profileOne.appId, profileTwo.appId, beta.appId), ranked.map { it.app.appId })
    }

    @Test
    fun `disabled ranking falls back to alphabetical ordering`() {
        val heavilyUsed = usage("Zulu", lastUsedAtMillis = now, launchCount = 100)
        val unused = usage("Alpha")

        val ranked = UsageRanking.rank(listOf(heavilyUsed, unused), now, enabled = false)

        assertEquals(listOf("Alpha", "Zulu"), ranked.map { it.app.label })
        assertTrue(ranked.all { it.score == 0.0 })
    }

    private fun usage(
        label: String,
        component: String = "com.example/.$label",
        userSerial: Long = 0,
        lastUsedAtMillis: Long? = null,
        launchCount: Int = 0,
    ) = AppUsage(
        appId = AppId(component, userSerial),
        label = label,
        lastUsedAtMillis = lastUsedAtMillis,
        launchCount = launchCount,
    )
}
