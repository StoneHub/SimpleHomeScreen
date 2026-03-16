package com.stonecode.simplehomescreen.ui

import android.app.Application
import android.content.ComponentName
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.stonecode.simplehomescreen.core.AppCategorizer
import com.stonecode.simplehomescreen.core.AppCategory
import com.stonecode.simplehomescreen.core.IconCache
import com.stonecode.simplehomescreen.core.LauncherAppSource
import com.stonecode.simplehomescreen.core.PackageEvent
import com.stonecode.simplehomescreen.core.SearchEngine
import com.stonecode.simplehomescreen.core.UsageRanker
import com.stonecode.simplehomescreen.storage.PreferencesStore
import com.stonecode.simplehomescreen.storage.WorkspaceSnapshot
import com.stonecode.simplehomescreen.storage.WorkspaceStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WidgetTileState(
    val appWidgetId: Int,
    val provider: ComponentName? = null
)

data class PageInfo(
    val title: String,
    val category: AppCategory? = null,
    val isFavorites: Boolean = false
)

data class HomeState(
    val apps: List<AppTile> = emptyList(),
    val categorizedApps: Map<AppCategory, List<AppTile>> = emptyMap(),
    val favorites: List<AppTile> = emptyList(),
    val activePages: List<PageInfo> = emptyList(),
    val currentPage: Int = 0,
    val widgets: List<WidgetTileState> = emptyList(),
    val columns: Int = PreferencesStore.DEFAULT_GRID_COLUMNS,
    val showUsageAccessPrompt: Boolean = false,
    val drawerOpen: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<AppTile> = emptyList()
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val iconCache = IconCache(application)
    private val launcherApps =
        application.getSystemService(LauncherApps::class.java) ?: throw IllegalStateException(
            "LauncherApps unavailable"
        )
    private val packageManager: PackageManager = application.packageManager
    private val appSource = LauncherAppSource(application, launcherApps)
    private val usageRanker = UsageRanker(application)
    private val workspaceStore = WorkspaceStore(application)
    private val searchEngine = SearchEngine()
    private val appCategorizer = AppCategorizer()
    val preferencesStore = PreferencesStore(application)
    private val initialWorkspace = workspaceStore.load()

    private val _state = MutableStateFlow(
        HomeState(
            widgets = initialWorkspace.widgets,
            columns = preferencesStore.gridColumns,
            showUsageAccessPrompt = !usageRanker.hasAccess()
        )
    )
    val state: StateFlow<HomeState> = _state

    private var refreshJob: Job? = null

    init {
        appSource.registerCallbacks { event ->
            when (event) {
                is PackageEvent.Added,
                is PackageEvent.Removed,
                is PackageEvent.Changed -> enqueueRefresh()
            }
        }
        enqueueRefresh()
    }

    override fun onCleared() {
        super.onCleared()
        appSource.unregisterCallbacks()
        iconCache.clear()
    }

    fun onUsageAccessChanged() {
        _state.update { it.copy(showUsageAccessPrompt = !usageRanker.hasAccess()) }
        enqueueRefresh()
    }

    fun onWidgetPlaced(id: Int, provider: ComponentName?) {
        var updated: HomeState? = null
        _state.update { current ->
            val widgets = if (current.widgets.any { it.appWidgetId == id }) {
                current.widgets
            } else {
                current.widgets + WidgetTileState(id, provider)
            }
            val newState = current.copy(widgets = widgets)
            updated = newState
            newState
        }
        updated?.let { workspaceStore.save(WorkspaceSnapshot(widgets = it.widgets)) }
    }

    fun onWidgetRemoved(id: Int) {
        var updated: HomeState? = null
        _state.update { current ->
            val newWidgets = current.widgets.filterNot { it.appWidgetId == id }
            val newState = current.copy(widgets = newWidgets)
            updated = newState
            newState
        }
        updated?.let { workspaceStore.save(WorkspaceSnapshot(widgets = it.widgets)) }
    }

    fun onDrawerToggle(open: Boolean) {
        _state.update { current ->
            current.copy(
                drawerOpen = open,
                searchQuery = if (!open) "" else current.searchQuery
            )
        }
    }

    fun onSearchQueryChange(query: String) {
        _state.update { current ->
            val results = if (query.isBlank()) {
                emptyList()
            } else {
                searchEngine.search(query, current.apps)
            }
            current.copy(
                searchQuery = query,
                searchResults = results
            )
        }
    }

    fun onPageChanged(page: Int) {
        _state.update { it.copy(currentPage = page) }
    }

    fun onPreferencesChanged() {
        _state.update { current ->
            val columns = preferencesStore.gridColumns
            val favCount = preferencesStore.favoritesCount
            val favorites = current.apps.take(favCount)
            val pages = buildPages(current.categorizedApps, favorites)
            current.copy(
                columns = columns,
                favorites = favorites,
                activePages = pages
            )
        }
    }

    private fun enqueueRefresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            val launchables = runCatching { appSource.loadLaunchables() }.getOrElse { emptyList() }

            val hasUsageAccess = usageRanker.hasAccess()
            val ranks = if (hasUsageAccess) usageRanker.ranks() else emptyMap()
            val tiles = launchables
                .sortedByDescending { ranks[it.component.packageName] ?: 0.0 }
                .mapNotNull { launchable ->
                    val icon = runCatching {
                        iconCache.get(launchable.component, launchable.userHandle)
                    }.getOrNull() ?: return@mapNotNull null
                    AppTile(
                        key = launchable.component.flattenToShortString(),
                        label = launchable.label,
                        icon = icon,
                        packageName = launchable.component.packageName,
                        launch = {
                            runCatching {
                                launcherApps.startMainActivity(
                                    launchable.component,
                                    launchable.userHandle,
                                    null,
                                    null
                                )
                            }
                        }
                    )
                }

            // Categorize apps
            val categorized = mutableMapOf<AppCategory, MutableList<AppTile>>()
            for (tile in tiles) {
                val appInfo = runCatching {
                    packageManager.getApplicationInfo(tile.packageName, 0)
                }.getOrNull()
                val category = if (appInfo != null) {
                    appCategorizer.categorize(tile.packageName, appInfo)
                } else {
                    AppCategory.OTHER
                }
                categorized.getOrPut(category) { mutableListOf() }.add(tile)
            }

            val favCount = preferencesStore.favoritesCount
            val favorites = tiles.take(favCount)
            val sortedCategorized = categorized
                .toSortedMap(compareBy { it.order })
                .mapValues { it.value.toList() }
            val pages = buildPages(sortedCategorized, favorites)

            _state.update { current ->
                current.copy(
                    apps = tiles,
                    categorizedApps = sortedCategorized,
                    favorites = favorites,
                    activePages = pages,
                    columns = preferencesStore.gridColumns,
                    showUsageAccessPrompt = !hasUsageAccess
                )
            }
        }
    }

    private fun buildPages(
        categorizedApps: Map<AppCategory, List<AppTile>>,
        favorites: List<AppTile>
    ): List<PageInfo> {
        val pages = mutableListOf<PageInfo>()
        if (favorites.isNotEmpty()) {
            pages.add(PageInfo(title = "Favorites", isFavorites = true))
        }
        for ((category, apps) in categorizedApps) {
            if (apps.isNotEmpty() && preferencesStore.isCategoryVisible(category)) {
                pages.add(PageInfo(title = category.displayName, category = category))
            }
        }
        return pages
    }
}
