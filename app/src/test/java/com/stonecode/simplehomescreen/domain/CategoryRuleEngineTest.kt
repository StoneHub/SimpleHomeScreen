package com.stonecode.simplehomescreen.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryRuleEngineTest {
    private val exactCategory = CategoryId("exact")
    private val prefixCategory = CategoryId("prefix")
    private val overrideCategory = CategoryId("override")
    private val engine = CategoryRuleEngine(
        CategoryRuleSet(
            exactPackageRules = mapOf("com.example.exact" to exactCategory),
            packagePrefixRules = listOf(
                PackagePrefixRule("com.monroe.apps", prefixCategory),
            ),
        ),
    )

    @Test
    fun `manual override wins over every inferred source`() {
        val assignment = engine.categorize(
            packageName = "com.example.exact",
            metadataCategory = AppMetadataCategory.GAME,
            isSystemApp = true,
            override = overrideCategory,
        )

        assertEquals(overrideCategory, assignment.categoryId)
        assertEquals(CategoryAssignmentSource.USER_OVERRIDE, assignment.source)
    }

    @Test
    fun `metadata wins before package rules`() {
        val assignment = engine.categorize(
            packageName = "com.example.exact",
            metadataCategory = AppMetadataCategory.GAME,
        )

        assertEquals(DefaultCategories.Games.id, assignment.categoryId)
        assertEquals(CategoryAssignmentSource.PLATFORM_METADATA, assignment.source)
        assertEquals("GAME", assignment.matchedRule)
    }

    @Test
    fun `exact package match is case insensitive but not a substring match`() {
        val exact = engine.categorize("COM.EXAMPLE.EXACT")
        val substring = engine.categorize("com.example.exact.clone")

        assertEquals(exactCategory, exact.categoryId)
        assertEquals(CategoryAssignmentSource.EXACT_PACKAGE_RULE, exact.source)
        assertEquals(DefaultCategories.Other.id, substring.categoryId)
    }

    @Test
    fun `prefix rule respects package segment boundaries`() {
        val matching = engine.categorize("com.monroe.apps.weather")
        val prefixItself = engine.categorize("com.monroe.apps")
        val lookalike = engine.categorize("com.monroe.appstore")

        assertEquals(prefixCategory, matching.categoryId)
        assertEquals(prefixCategory, prefixItself.categoryId)
        assertEquals(DefaultCategories.Other.id, lookalike.categoryId)
        assertEquals("com.monroe.apps.*", matching.matchedRule)
    }

    @Test
    fun `system fallback precedes default category`() {
        val system = engine.categorize("com.vendor.unknown", isSystemApp = true)
        val ordinary = engine.categorize("com.vendor.unknown")

        assertEquals(DefaultCategories.Tools.id, system.categoryId)
        assertEquals(CategoryAssignmentSource.SYSTEM_APP_FALLBACK, system.source)
        assertEquals(DefaultCategories.Other.id, ordinary.categoryId)
        assertEquals(CategoryAssignmentSource.DEFAULT, ordinary.source)
    }
}
