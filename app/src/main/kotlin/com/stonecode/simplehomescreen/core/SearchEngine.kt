package com.stonecode.simplehomescreen.core

import com.stonecode.simplehomescreen.ui.AppTile

/**
 * Search engine for filtering and ranking apps based on query
 */
class SearchEngine {

    /**
     * Search apps by label with fuzzy matching and ranking
     * Returns sorted list by relevance score (highest first)
     */
    fun search(query: String, apps: List<AppTile>): List<AppTile> {
        if (query.isBlank()) return apps

        val normalizedQuery = query.trim().lowercase()

        return apps
            .mapNotNull { app ->
                val score = scoreMatch(normalizedQuery, app.label)
                if (score > 0f) app to score else null
            }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    /**
     * Calculate match score for a query against an app label
     * Returns 0 if no match, higher values for better matches
     */
    private fun scoreMatch(query: String, appLabel: String): Float {
        val lowerLabel = appLabel.lowercase()

        return when {
            // Exact match - highest score
            lowerLabel == query -> 100f

            // Starts with query - very high score
            lowerLabel.startsWith(query) -> 80f

            // Contains query - high score
            lowerLabel.contains(query) -> 60f

            // Word boundary match - medium score
            // e.g., "gal" matches "Photo Gallery"
            lowerLabel.split(" ", "-", "_").any {
                it.startsWith(query)
            } -> 50f

            // Fuzzy match using Levenshtein distance - lower score
            else -> {
                val similarity = levenshteinSimilarity(query, lowerLabel)
                if (similarity > 0.6f) similarity * 40f else 0f
            }
        }
    }

    /**
     * Calculate Levenshtein similarity (0.0 to 1.0)
     * 1.0 means identical, 0.0 means completely different
     */
    private fun levenshteinSimilarity(s1: String, s2: String): Float {
        val distance = levenshteinDistance(s1, s2)
        val maxLen = maxOf(s1.length, s2.length)
        return if (maxLen == 0) 1f else 1f - (distance.toFloat() / maxLen)
    }

    /**
     * Calculate Levenshtein distance between two strings
     * Uses dynamic programming for efficiency
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val m = s1.length
        val n = s2.length

        // Create DP table
        val dp = Array(m + 1) { IntArray(n + 1) }

        // Initialize base cases
        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j

        // Fill DP table
        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,      // deletion
                    dp[i][j - 1] + 1,      // insertion
                    dp[i - 1][j - 1] + cost // substitution
                )
            }
        }

        return dp[m][n]
    }

    /**
     * Get alphabetical sections from app list
     * Returns map of section letter to starting index
     */
    fun getSections(apps: List<AppTile>): Map<Char, Int> {
        val sections = mutableMapOf<Char, Int>()
        apps.forEachIndexed { index, app ->
            val firstChar = app.label.firstOrNull()?.uppercaseChar() ?: '#'
            if (!sections.containsKey(firstChar)) {
                sections[firstChar] = index
            }
        }
        return sections
    }

    /**
     * Group apps by first letter for sectioned display
     */
    fun groupByFirstLetter(apps: List<AppTile>): Map<Char, List<AppTile>> {
        return apps.groupBy {
            it.label.firstOrNull()?.uppercaseChar() ?: '#'
        }.toSortedMap()
    }
}
