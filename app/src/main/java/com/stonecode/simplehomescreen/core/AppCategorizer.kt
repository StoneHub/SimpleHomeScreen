package com.stonecode.simplehomescreen.core

import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps.ShortcutQuery
import android.os.UserHandle

/**
 * Categories for organizing apps into pages
 */
enum class AppCategory(val displayName: String, val order: Int) {
    GAMES("Games", 0),
    PROFESSIONAL("Professional & Banking", 1),
    PERSONAL_DEV("My Apps", 2),
    UTILITIES("Utilities & Tools", 3),
    OTHER("Other", 4);

    companion object {
        fun fromOrdinal(ordinal: Int): AppCategory =
            entries.find { it.order == ordinal } ?: OTHER
    }
}

/**
 * App tile data with category information
 */
data class CategorizedAppTile(
    val packageName: String,
    val activityName: String,
    val label: String,
    val user: UserHandle,
    val category: AppCategory,
    val usageRank: Float = 0f,
    val isSystemApp: Boolean = false
)

/**
 * Categorizes apps based on various heuristics
 */
class AppCategorizer(
    private val personalDevPackagePrefix: String = ""
) {

    // Known banking and professional app package patterns
    private val professionalPackages = setOf(
        // Banking
        "com.bankofamerica",
        "com.chase",
        "com.wellsfargo",
        "com.usaa",
        "com.capitalone",
        "com.citibank",
        "com.usbank",
        "com.ally",
        "com.discover",
        "com.pnc",
        "com.td",
        "com.suntrust",
        "com.regions",
        "com.fidelity",
        "com.schwab",
        "com.vanguard",
        "com.etrade",
        "com.paypal",
        "com.venmo",
        "com.square.cash", // Cash App
        "com.coinbase",
        // Professional/Business
        "com.microsoft.office",
        "com.google.android.apps.docs", // Google Docs
        "com.google.android.apps.sheets", // Sheets
        "com.google.android.apps.slides", // Slides
        "com.microsoft.teams",
        "com.slack",
        "com.zoom",
        "com.cisco.webex",
        "com.dropbox",
        "com.box",
        "com.evernote",
        "com.notion",
        "com.trello",
        "com.asana",
        "com.linkedin",
        "com.adobe.reader",
        "com.adobe.scan",
        "com.scanner",
        "com.camscanner"
    )

    // Known utility and tool package patterns
    private val utilityPackages = setOf(
        "com.android.settings",
        "com.android.calculator2",
        "com.google.android.calculator",
        "com.android.calendar",
        "com.google.android.calendar",
        "com.android.deskclock",
        "com.google.android.deskclock",
        "com.android.contacts",
        "com.google.android.contacts",
        "com.android.dialer",
        "com.google.android.dialer",
        "com.android.camera",
        "com.google.android.camera",
        "com.android.gallery3d",
        "com.google.android.apps.photos",
        "com.android.providers.downloads.ui",
        "com.google.android.apps.messaging", // Messages
        "com.android.mms",
        "com.google.android.gm", // Gmail
        "com.android.email",
        "com.google.android.apps.maps",
        "com.google.android.youtube",
        "com.android.chrome",
        "com.google.android.googlequicksearchbox", // Google app
        "com.google.android.apps.translate",
        "com.google.android.keep",
        "com.google.android.apps.recorder",
        "com.android.systemui",
        "com.samsung.android.app.settings", // Samsung Settings
        "com.samsung.android.calendar",
        "com.samsung.android.contacts",
        "com.samsung.android.dialer",
        "com.samsung.android.messaging",
        "com.samsung.android.email",
        "com.sec.android.app.launcher" // Samsung Launcher
    )

    /**
     * Categorize an app based on package name, application info, and custom rules
     */
    fun categorize(
        packageName: String,
        applicationInfo: ApplicationInfo,
        manualCategory: AppCategory? = null
    ): AppCategory {
        // Manual override takes precedence
        if (manualCategory != null) {
            return manualCategory
        }

        // Check if it's a personal dev app
        if (personalDevPackagePrefix.isNotEmpty() &&
            packageName.startsWith(personalDevPackagePrefix)) {
            return AppCategory.PERSONAL_DEV
        }

        // Check if it's a game (Android 8.0+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            if (applicationInfo.category == ApplicationInfo.CATEGORY_GAME) {
                return AppCategory.GAMES
            }
        }

        // Check against professional packages
        if (professionalPackages.any { packageName.contains(it, ignoreCase = true) }) {
            return AppCategory.PROFESSIONAL
        }

        // Check against utility packages
        if (utilityPackages.any { packageName.contains(it, ignoreCase = true) }) {
            return AppCategory.UTILITIES
        }

        // Check if it's a system app (likely utility)
        if ((applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0) {
            return AppCategory.UTILITIES
        }

        // Default to OTHER
        return AppCategory.OTHER
    }

    /**
     * Check if a package name pattern matches professional apps
     */
    fun isProfessionalPackage(packageName: String): Boolean {
        return professionalPackages.any { packageName.contains(it, ignoreCase = true) } ||
               packageName.contains("bank", ignoreCase = true) ||
               packageName.contains("finance", ignoreCase = true) ||
               packageName.contains("trading", ignoreCase = true)
    }

    /**
     * Check if a package name pattern matches utility apps
     */
    fun isUtilityPackage(packageName: String): Boolean {
        return utilityPackages.any { packageName.contains(it, ignoreCase = true) }
    }

    /**
     * Get categorized apps grouped by category
     */
    fun groupByCategory(apps: List<CategorizedAppTile>): Map<AppCategory, List<CategorizedAppTile>> {
        return apps.groupBy { it.category }
            .toSortedMap(compareBy { it.order })
    }

    /**
     * Sort apps within a category by usage rank (descending)
     */
    fun sortByUsageRank(apps: List<CategorizedAppTile>): List<CategorizedAppTile> {
        return apps.sortedByDescending { it.usageRank }
    }
}
