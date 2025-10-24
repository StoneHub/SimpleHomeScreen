package com.stonecode.simplehomescreen.ui

import android.app.Application
import android.content.ComponentName
import android.content.pm.LauncherApps
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.stonecode.simplehomescreen.core.IconCache
import com.stonecode.simplehomescreen.core.LauncherAppSource
import com.stonecode.simplehomescreen.core.PackageEvent
import com.stonecode.simplehomescreen.core.UsageRanker
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

data class HomeState(
    val apps: List<AppTile> = emptyList(),
    val widgets: List<WidgetTileState> = emptyList(),
    val columns: Int = DEFAULT_COLUMNS,
    val showUsageAccessPrompt: Boolean = false
) {
    companion object {
        const val DEFAULT_COLUMNS = 5
    }
}

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val iconCache = IconCache(application)
    private val launcherApps =
        application.getSystemService(LauncherApps::class.java) ?: throw IllegalStateException(
            "LauncherApps unavailable"
        )
    private val appSource = LauncherAppSource(application, launcherApps)
    private val usageRanker = UsageRanker(application)
    private val workspaceStore = WorkspaceStore(application)
    private val initialWorkspace = workspaceStore.load()

    private val _state = MutableStateFlow(
        HomeState(
            widgets = initialWorkspace.widgets,
            showUsageAccessPrompt = !usageRanker.hasAccess()
        )
    )
    val state: StateFlow<HomeState> = _state

    private var refreshJob: Job? = null

    init {
        appSource.registerCallbacks { event ->
            when (event) {
                is PackageEvent.Added,
                is PackageEvent.Removed -> enqueueRefresh()
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
                    }.getOrElse { return@mapNotNull null }
                    AppTile(
                        key = launchable.component.flattenToShortString(),
                        label = launchable.label,
                        icon = icon,
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
            _state.update { current ->
                current.copy(
                    apps = tiles,
                    showUsageAccessPrompt = !hasUsageAccess
                )
            }
        }
    }
}
