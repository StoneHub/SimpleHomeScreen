package com.stonecode.simplehomescreen.ui

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stonecode.simplehomescreen.R
import com.stonecode.simplehomescreen.widgets.WidgetController
import com.stonecode.simplehomescreen.widgets.WidgetTile
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    widgetController: WidgetController,
    onRequestUsageAccess: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    val pendingWidgetId = remember { mutableIntStateOf(AppWidgetManager.INVALID_APPWIDGET_ID) }
    val pendingProvider = remember { mutableStateOf<ComponentName?>(null) }

    fun cleanupPending() {
        if (pendingWidgetId.intValue != AppWidgetManager.INVALID_APPWIDGET_ID) {
            widgetController.deleteId(pendingWidgetId.intValue)
        }
        pendingWidgetId.intValue = AppWidgetManager.INVALID_APPWIDGET_ID
        pendingProvider.value = null
    }

    fun placeWidget(id: Int) {
        val info = widgetController.getAppWidgetInfo(id)
        val provider = info?.provider ?: pendingProvider.value
        viewModel.onWidgetPlaced(id, provider)
        pendingWidgetId.intValue = AppWidgetManager.INVALID_APPWIDGET_ID
        pendingProvider.value = null
    }

    val bindLauncher = rememberLauncherForActivityResult(StartActivityForResult()) { result ->
        val id = pendingWidgetId.intValue
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return@rememberLauncherForActivityResult
        if (result.resultCode == Activity.RESULT_OK) {
            placeWidget(id)
        } else {
            widgetController.deleteId(id)
        }
        pendingWidgetId.intValue = AppWidgetManager.INVALID_APPWIDGET_ID
        pendingProvider.value = null
    }

    val pickLauncher = rememberLauncherForActivityResult(StartActivityForResult()) { result ->
        handlePickResult(
            result = result,
            pendingIdHolder = pendingWidgetId,
            pendingProviderHolder = pendingProvider,
            widgetController = widgetController,
            onWidgetReady = { id -> placeWidget(id) },
            onBindRequired = { id, provider, options ->
                pendingWidgetId.intValue = id
                pendingProvider.value = provider
                val bindIntent = widgetController.requestBindIntent(id, provider, options)
                bindLauncher.launch(bindIntent)
            },
            onFailure = {
                cleanupPending()
            }
        )
    }

    fun startWidgetPicker() {
        val id = widgetController.allocateId()
        pendingWidgetId.intValue = id
        val pickIntent = android.content.Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        }
        pickLauncher.launch(pickIntent)
    }

    var startY by remember { mutableStateOf(0f) }
    var currentY by remember { mutableStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        startY = offset.y
                        currentY = offset.y
                    },
                    onDragEnd = {
                        val dragDistance = startY - currentY
                        val startedInBottomThird = startY > size.height * 0.66f
                        val startedInTopThird = startY < size.height * 0.33f
                        val dragDown = currentY - startY

                        // Swipe up from bottom third → open drawer
                        if (dragDistance > 100f && startedInBottomThird) {
                            viewModel.onDrawerToggle(true)
                        }
                        // Swipe down from top third → expand notification shade
                        if (dragDown > 100f && startedInTopThird) {
                            runCatching {
                                @Suppress("WrongConstant")
                                val service = context.getSystemService("statusbar")
                                val clazz = service?.javaClass
                                clazz?.getMethod("expandNotificationsPanel")?.invoke(service)
                            }
                        }
                        startY = 0f
                        currentY = 0f
                    },
                    onDragCancel = {
                        startY = 0f
                        currentY = 0f
                    },
                    onVerticalDrag = { change, _ ->
                        currentY = change.position.y
                    }
                )
            }
    ) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            floatingActionButton = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Settings FAB
                    FloatingActionButton(
                        onClick = {
                            val intent = Intent(context, SettingsActivity::class.java)
                            context.startActivity(intent)
                        }
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                    // Widget FAB
                    FloatingActionButton(onClick = { startWidgetPicker() }) {
                        Icon(Icons.Default.Widgets, contentDescription = stringResource(R.string.home_add_widget))
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Usage access prompt
                if (state.showUsageAccessPrompt) {
                    UsageAccessCallout(
                        onOpenSettings = {
                            onRequestUsageAccess()
                            viewModel.onUsageAccessChanged()
                        },
                        modifier = Modifier.padding(16.dp)
                    )
                }

                // Widgets
                if (state.widgets.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        state.widgets.forEach { widget ->
                            WidgetHostCard(
                                widgetId = widget.appWidgetId,
                                controller = widgetController,
                                onRemove = {
                                    widgetController.deleteId(widget.appWidgetId)
                                    viewModel.onWidgetRemoved(widget.appWidgetId)
                                }
                            )
                        }
                    }
                }

                // Category tabs + pager
                if (state.activePages.isNotEmpty()) {
                    val pagerState = rememberPagerState(
                        initialPage = state.currentPage.coerceIn(0, (state.activePages.size - 1).coerceAtLeast(0)),
                        pageCount = { state.activePages.size }
                    )
                    val scope = rememberCoroutineScope()

                    // Sync pager -> viewModel
                    LaunchedEffect(pagerState) {
                        snapshotFlow { pagerState.currentPage }.collect { page ->
                            viewModel.onPageChanged(page)
                        }
                    }

                    ScrollableTabRow(
                        selectedTabIndex = pagerState.currentPage.coerceIn(0, (state.activePages.size - 1).coerceAtLeast(0)),
                        modifier = Modifier.fillMaxWidth(),
                        edgePadding = 16.dp
                    ) {
                        state.activePages.forEachIndexed { index, pageInfo ->
                            Tab(
                                selected = pagerState.currentPage == index,
                                onClick = {
                                    scope.launch { pagerState.animateScrollToPage(index) }
                                },
                                text = { Text(pageInfo.title) }
                            )
                        }
                    }

                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) { page ->
                        val pageInfo = state.activePages.getOrNull(page)
                        val appsForPage = when {
                            pageInfo == null -> emptyList()
                            pageInfo.isFavorites -> state.favorites
                            pageInfo.category != null -> state.categorizedApps[pageInfo.category] ?: emptyList()
                            else -> emptyList()
                        }
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(state.columns.coerceAtLeast(1)),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(
                                items = appsForPage,
                                key = { it.key }
                            ) { app ->
                                AppCell(app = app)
                            }
                        }
                    }

                    // Page indicator
                    PageIndicator(
                        pageCount = state.activePages.size,
                        currentPage = pagerState.currentPage.coerceIn(0, (state.activePages.size - 1).coerceAtLeast(0)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    )
                } else {
                    // Fallback: flat grid while loading
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(state.columns.coerceAtLeast(1)),
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(
                            items = state.apps,
                            key = { it.key }
                        ) { app ->
                            AppCell(app = app)
                        }
                    }
                }
            }
        }

        // App Drawer
        AppDrawer(
            visible = state.drawerOpen,
            apps = state.apps,
            searchQuery = state.searchQuery,
            searchResults = state.searchResults,
            onDismiss = { viewModel.onDrawerToggle(false) },
            onSearchQueryChange = { query -> viewModel.onSearchQueryChange(query) }
        )
    }
}

private fun handlePickResult(
    result: ActivityResult,
    pendingIdHolder: androidx.compose.runtime.MutableIntState,
    pendingProviderHolder: androidx.compose.runtime.MutableState<ComponentName?>,
    widgetController: WidgetController,
    onWidgetReady: (Int) -> Unit,
    onBindRequired: (Int, ComponentName?, android.os.Bundle?) -> Unit,
    onFailure: () -> Unit
) {
    val data = result.data
    val currentId = pendingIdHolder.intValue
    if (currentId == AppWidgetManager.INVALID_APPWIDGET_ID) {
        onFailure()
        return
    }
    if (result.resultCode != Activity.RESULT_OK || data == null) {
        widgetController.deleteId(currentId)
        onFailure()
        return
    }
    val returnedId =
        data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, currentId).takeIf {
            it != AppWidgetManager.INVALID_APPWIDGET_ID
        } ?: run {
            widgetController.deleteId(currentId)
            onFailure()
            return
        }

    pendingIdHolder.intValue = returnedId
    val info = widgetController.getAppWidgetInfo(returnedId)
    if (info != null) {
        onWidgetReady(returnedId)
        return
    }
    val provider = data.getParcelableExtra(
        AppWidgetManager.EXTRA_APPWIDGET_PROVIDER,
        ComponentName::class.java
    ) ?: pendingProviderHolder.value

    pendingProviderHolder.value = provider
    if (widgetController.bindIfAllowed(returnedId, provider)) {
        onWidgetReady(returnedId)
    } else {
        val options = data.getParcelableExtra(
            AppWidgetManager.EXTRA_APPWIDGET_OPTIONS,
            android.os.Bundle::class.java
        )
        onBindRequired(returnedId, provider, options)
    }
}

@Composable
private fun UsageAccessCallout(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(id = R.string.usage_access_prompt_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(id = R.string.usage_access_prompt_message),
                style = MaterialTheme.typography.bodyMedium
            )
            TextButton(onClick = onOpenSettings) {
                Text(text = stringResource(id = R.string.usage_access_open_settings))
            }
        }
    }
}

@Composable
private fun WidgetHostCard(
    widgetId: Int,
    controller: WidgetController,
    onRemove: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            WidgetTile(
                controller = controller,
                appWidgetId = widgetId,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedButton(
                onClick = onRemove,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(end = 16.dp, bottom = 12.dp)
            ) {
                Text(text = stringResource(R.string.remove_widget))
            }
        }
    }
}
