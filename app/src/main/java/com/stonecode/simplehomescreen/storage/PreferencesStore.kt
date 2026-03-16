package com.stonecode.simplehomescreen.storage

import android.content.Context
import android.content.SharedPreferences
import com.stonecode.simplehomescreen.core.AppCategory

class PreferencesStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("simple_home_prefs", Context.MODE_PRIVATE)

    var gridColumns: Int
        get() = prefs.getInt(KEY_GRID_COLUMNS, DEFAULT_GRID_COLUMNS)
        set(value) = prefs.edit().putInt(KEY_GRID_COLUMNS, value.coerceIn(3, 6)).apply()

    var favoritesCount: Int
        get() = prefs.getInt(KEY_FAVORITES_COUNT, DEFAULT_FAVORITES_COUNT)
        set(value) = prefs.edit().putInt(KEY_FAVORITES_COUNT, value).apply()

    var hiddenCategories: Set<String>
        get() = prefs.getStringSet(KEY_HIDDEN_CATEGORIES, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_HIDDEN_CATEGORIES, value).apply()

    fun isCategoryVisible(category: AppCategory): Boolean {
        return category.name !in hiddenCategories
    }

    fun setCategoryVisible(category: AppCategory, visible: Boolean) {
        val current = hiddenCategories.toMutableSet()
        if (visible) {
            current.remove(category.name)
        } else {
            current.add(category.name)
        }
        hiddenCategories = current
    }

    companion object {
        private const val KEY_GRID_COLUMNS = "grid_columns"
        private const val KEY_FAVORITES_COUNT = "favorites_count"
        private const val KEY_HIDDEN_CATEGORIES = "hidden_categories"

        const val DEFAULT_GRID_COLUMNS = 5
        const val DEFAULT_FAVORITES_COUNT = 10
    }
}
