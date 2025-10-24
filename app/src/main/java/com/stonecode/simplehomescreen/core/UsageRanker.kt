package com.stonecode.simplehomescreen.core

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import kotlin.math.exp

class UsageRanker(private val context: Context) {

    private val usageStats: UsageStatsManager? =
        context.getSystemService(UsageStatsManager::class.java)

    fun hasAccess(): Boolean {
        val manager = usageStats ?: return false
        val end = System.currentTimeMillis()
        val start = end - THRESHOLD_WINDOW_MS
        val events = manager.queryEvents(start, end)
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                return true
            }
        }
        return false
    }

    fun openUsageAccessSettings() {
        context.startActivity(
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    fun ranks(days: Int = 30): Map<String, Double> {
        val manager = usageStats ?: return emptyMap()
        val end = System.currentTimeMillis()
        val start = end - days * DAY_MS
        val events = manager.queryEvents(start, end)
        val entries = mutableMapOf<String, Double>()
        val nowSeconds = end / 1000.0
        val decay = DECAY_FACTOR
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                val ageSeconds = nowSeconds - event.timeStamp / 1000.0
                val weight = exp(-decay * ageSeconds)
                val key = event.packageName ?: continue
                entries.merge(key, weight, Double::plus)
            }
        }
        return entries
    }

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
        const val THRESHOLD_WINDOW_MS = 60_000L
        const val DECAY_FACTOR = 1e-4
    }
}
