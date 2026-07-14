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
import com.stonecode.simplehomescreen.storage.DashboardItem
import com.stonecode.simplehomescreen.storage.GridPosition
import com.stonecode.simplehomescreen.storage.GridSpan
import com.stonecode.simplehomescreen.storage.Workspace
import com.stonecode.simplehomescreen.storage.WorkspaceLoadResult
import com.stonecode.simplehomescreen.storage.WorkspaceSaveResult
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
    val searchResults: List<AppTile> = emptyList(),
    val loading: Boolean = true,
    val loadError: String? = null,
    val workspaceError: String? = null,
    val onboardingCompleted: Boolean = false
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
    private val appCategorizer = AppCategorizer(personalDevPackagePrefix = "com.stonecode")
    val preferencesStore = PreferencesStore(application)
    private val initialWorkspaceResult = workspaceStore.loadWorkspace()
    private var workspace = (initialWorkspaceResult as? WorkspaceLoadResult.Loaded)?.workspace
        ?: Workspace()
    private val initialWidgets = workspace.dashboardItems
        .filterIsInstance<DashboardItem.Widget>()
        .map { widget ->
            WidgetTileState(
                appWidgetId = widget.appWidgetId,
                provider = widget.provider?.let(ComponentName::unflattenFromString)
            )
        }
    private val initialWorkspaceError =
        (initialWorkspaceResult as? WorkspaceLoadResult.RecoveryRequired)?.problem?.toString()

    private val _state = MutableStateFlow(
        HomeState(
            widgets = initialWidgets,
            columns = preferencesStore.gridColumns,
            showUsageAccessPrompt = !usageRanker.hasAccess(),
            workspaceError = initialWorkspaceError,
            onboardingCompleted = preferencesStore.onboardingCompleted
        )
    )
    val state: StateFlow<HomeState> = _state

    private var refreshJob: Job? = null
    private var refreshRequested = false

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
        updated?.let { persistWidgets(it.widgets) }
    }

    fun onWidgetRemoved(id: Int) {
        var updated: HomeState? = null
        _state.update { current ->
            val newWidgets = current.widgets.filterNot { it.appWidgetId == id }
            val newState = current.copy(widgets = newWidgets)
            updated = newState
            newState
        }
        updated?.let { persistWidgets(it.widgets) }
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

    fun completeOnboarding() {
        preferencesStore.onboardingCompleted = true
        _state.update { it.copy(onboardingCompleted = true) }
    }

    private fun persistWidgets(widgets: List<WidgetTileState>) {
        val existingWidgets = workspace.dashboardItems
            .filterIsInstance<DashboardItem.Widget>()
            .associateBy { it.appWidgetId }
        val persistedWidgets = widgets.mapIndexed { index, widget ->
            existingWidgets[widget.appWidgetId] ?: DashboardItem.Widget(
                id = "widget:${widget.appWidgetId}",
                appWidgetId = widget.appWidgetId,
                provider = widget.provider?.flattenToString(),
                position = GridPosition(page = 0, column = 0, row = index * 2),
                span = GridSpan(columns = 2, rows = 2)
            )
        }
        val candidate = workspace.copy(
            dashboardItems = workspace.dashboardItems.filterNot { it is DashboardItem.Widget } +
                persistedWidgets
        )
        when (val result = workspaceStore.saveWorkspace(candidate)) {
            WorkspaceSaveResult.Saved -> {
                workspace = candidate
                _state.update { it.copy(workspaceError = null) }
            }
            is WorkspaceSaveResult.Failed -> {
                _state.update { it.copy(workspaceError = result.problem.toString()) }
            }
        }
    }

    private fun enqueueRefresh() {
        if (refreshJob?.isActive == true) {
            refreshRequested = true
            return
        }
        refreshJob = viewModelScope.launch {
            do {
                refreshRequested = false
                refreshOnce()
            } while (refreshRequested)
        }
    }

    private suspend fun refreshOnce() {
            _state.update { it.copy(loading = it.apps.isEmpty(), loadError = null) }
            val launchablesResult = runCatching { appSource.loadLaunchables() }
            val launchables = launchablesResult.getOrElse { failure ->
                _state.update {
                    it.copy(
                        loading = false,
                        loadError = failure.message ?: "Installed apps could not be loaded"
                    )
                }
                return
            }

            val hasUsageAccess = usageRanker.hasAccess()
            val ranks = if (hasUsageAccess) usageRanker.ranks() else emptyMap()
            val tiles = launchables
                .filterNot { it.component.packageName == getApplication<Application>().packageName }
                .sortedByDescending { ranks[it.component.packageName] ?: 0.0 }
                .map { launchable ->
                    val icon = iconCache.get(launchable.component, launchable.userHandle)
                    AppTile(
                        key = "${launchable.component.flattenToShortString()}@${launchable.userHandle.hashCode()}",
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
                    showUsageAccessPrompt = !hasUsageAccess,
                    loading = false,
                    loadError = null
                )
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
