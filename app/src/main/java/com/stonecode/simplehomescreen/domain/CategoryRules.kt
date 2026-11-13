package com.stonecode.simplehomescreen.domain

/** Platform metadata translated at the Android boundary into domain-safe values. */
enum class AppMetadataCategory {
    GAME,
    AUDIO,
    VIDEO,
    IMAGE,
    SOCIAL,
    NEWS,
    MAPS,
    PRODUCTIVITY,
    UNDEFINED,
}
enum class CategoryAssignmentSource {
    USER_OVERRIDE,
    PLATFORM_METADATA,
    EXACT_PACKAGE_RULE,
    PACKAGE_PREFIX_RULE,
    SYSTEM_APP_FALLBACK,
    DEFAULT,
}

data class CategoryAssignment(
    val categoryId: CategoryId,
    val source: CategoryAssignmentSource,
    val matchedRule: String? = null,
)

data class PackagePrefixRule(
    val packagePrefix: String,
    val categoryId: CategoryId,
) {
    private val normalizedPrefix = packagePrefix.trim().trimEnd('.').lowercase()

    init {
        require(normalizedPrefix.isNotBlank()) { "packagePrefix must not be blank" }
    }

    internal fun matches(normalizedPackageName: String): Boolean =
        normalizedPackageName == normalizedPrefix ||
            normalizedPackageName.startsWith("$normalizedPrefix.")

    internal fun description(): String = "$normalizedPrefix.*"
}

data class CategoryRuleSet(
    val exactPackageRules: Map<String, CategoryId> = emptyMap(),
    val packagePrefixRules: List<PackagePrefixRule> = emptyList(),
    val metadataRules: Map<AppMetadataCategory, CategoryId> = defaultMetadataRules,
    val systemAppCategory: CategoryId = DefaultCategories.Tools.id,
    val fallbackCategory: CategoryId = DefaultCategories.Other.id,
) {
    internal val normalizedExactRules: Map<String, CategoryId> = exactPackageRules
        .mapKeys { (packageName, _) -> packageName.trim().lowercase() }
        .also { rules -> require(rules.keys.none(String::isBlank)) { "Package rules must not be blank" } }

    companion object {
        val defaultMetadataRules = mapOf(
            AppMetadataCategory.GAME to DefaultCategories.Games.id,
            AppMetadataCategory.AUDIO to DefaultCategories.Media.id,
            AppMetadataCategory.VIDEO to DefaultCategories.Media.id,
            AppMetadataCategory.IMAGE to DefaultCategories.Media.id,
            AppMetadataCategory.SOCIAL to DefaultCategories.Communication.id,
            AppMetadataCategory.NEWS to DefaultCategories.Media.id,
            AppMetadataCategory.MAPS to DefaultCategories.Tools.id,
            AppMetadataCategory.PRODUCTIVITY to DefaultCategories.WorkAndFinance.id,
        )
    }
}

/** Deterministic categorization. User choices always win and every result explains why. */
class CategoryRuleEngine(
    private val rules: CategoryRuleSet,
) {
    fun categorize(
        packageName: String,
        metadataCategory: AppMetadataCategory = AppMetadataCategory.UNDEFINED,
        isSystemApp: Boolean = false,
        override: CategoryId? = null,
    ): CategoryAssignment {
        require(packageName.isNotBlank()) { "packageName must not be blank" }

        override?.let {
            return CategoryAssignment(it, CategoryAssignmentSource.USER_OVERRIDE)
        }

        rules.metadataRules[metadataCategory]?.let {
            return CategoryAssignment(
                categoryId = it,
                source = CategoryAssignmentSource.PLATFORM_METADATA,
                matchedRule = metadataCategory.name,
            )
        }

        val normalizedPackageName = packageName.trim().lowercase()
        rules.normalizedExactRules[normalizedPackageName]?.let {
            return CategoryAssignment(
                categoryId = it,
                source = CategoryAssignmentSource.EXACT_PACKAGE_RULE,
                matchedRule = normalizedPackageName,
            )
        }

        rules.packagePrefixRules.firstOrNull { it.matches(normalizedPackageName) }?.let {
            return CategoryAssignment(
                categoryId = it.categoryId,
                source = CategoryAssignmentSource.PACKAGE_PREFIX_RULE,
                matchedRule = it.description(),
            )
        }

        if (isSystemApp) {
            return CategoryAssignment(
                categoryId = rules.systemAppCategory,
                source = CategoryAssignmentSource.SYSTEM_APP_FALLBACK,
            )
        }

        return CategoryAssignment(
            categoryId = rules.fallbackCategory,
            source = CategoryAssignmentSource.DEFAULT,
        )
    }
}
