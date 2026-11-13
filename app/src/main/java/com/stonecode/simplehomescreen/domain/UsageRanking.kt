package com.stonecode.simplehomescreen.domain

import kotlin.math.ln
import kotlin.math.pow

data class AppUsage(
    val appId: AppId,
    val label: String,
    val lastUsedAtMillis: Long? = null,
    val launchCount: Int = 0,
) {
    init {
        require(label.isNotBlank()) { "label must not be blank" }
        require(lastUsedAtMillis == null || lastUsedAtMillis >= 0) {
            "lastUsedAtMillis must not be negative"
        }
        require(launchCount >= 0) { "launchCount must not be negative" }
    }
}
data class UsageRankingConfig(
    val recencyWeight: Double = 0.65,
    val frequencyWeight: Double = 0.35,
    val recencyHalfLifeMillis: Long = 3L * 24 * 60 * 60 * 1_000,
    val frequencySaturationCount: Int = 30,
) {
    init {
        require(recencyWeight >= 0.0 && frequencyWeight >= 0.0) { "Weights must not be negative" }
        require(recencyWeight + frequencyWeight > 0.0) { "At least one weight must be positive" }
        require(recencyHalfLifeMillis > 0) { "recencyHalfLifeMillis must be positive" }
        require(frequencySaturationCount > 0) { "frequencySaturationCount must be positive" }
    }
}

data class RankedApp(
    val app: AppUsage,
    val score: Double,
)

object UsageRanking {
    fun score(
        usage: AppUsage,
        nowMillis: Long,
        config: UsageRankingConfig = UsageRankingConfig(),
    ): Double {
        require(nowMillis >= 0) { "nowMillis must not be negative" }

        val weightTotal = config.recencyWeight + config.frequencyWeight
        val recency = usage.lastUsedAtMillis?.let { lastUsed ->
            val age = (nowMillis - lastUsed).coerceAtLeast(0)
            0.5.pow(age.toDouble() / config.recencyHalfLifeMillis)
        } ?: 0.0
        val frequency = (
            ln(usage.launchCount.toDouble() + 1.0) /
                ln(config.frequencySaturationCount.toDouble() + 1.0)
            ).coerceIn(0.0, 1.0)

        return (
            config.recencyWeight * recency + config.frequencyWeight * frequency
            ) / weightTotal
    }

    /**
     * Returns a stable order. When smart ranking is disabled, label and AppId are the
     * only sort keys, so callers can use the same function for the permission fallback.
     */
    fun rank(
        apps: List<AppUsage>,
        nowMillis: Long,
        enabled: Boolean = true,
        config: UsageRankingConfig = UsageRankingConfig(),
    ): List<RankedApp> = apps
        .map { app -> RankedApp(app, if (enabled) score(app, nowMillis, config) else 0.0) }
        .sortedWith(
            compareByDescending<RankedApp> { it.score }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.app.label }
                .thenBy { it.app.appId.componentName }
                .thenBy { it.app.appId.userSerial },
        )
}
