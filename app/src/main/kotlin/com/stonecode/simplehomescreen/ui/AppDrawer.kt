package com.stonecode.simplehomescreen.ui

import android.R.attr.onClick
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stonecode.simplehomescreen.R
import androidx.compose.ui.graphics.asImageBitmap


val EmptyBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).asImageBitmap()


/**
 * App drawer modal bottom sheet with search functionality
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDrawer(
    visible: Boolean,
    apps: List<AppTile>,
    searchQuery: String,
    searchResults: List<AppTile>,
    onDismiss: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!visible) return

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = false
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
        ) {
            // Header
            Text(
                text = stringResource(R.string.drawer_all_apps),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )

            // Search Bar
            SearchBar(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // App Count
            val displayApps = if (searchQuery.isBlank()) apps else searchResults
            Text(
                text = stringResource(R.string.search_results_count, displayApps.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // App List
            if (displayApps.isEmpty() && searchQuery.isNotBlank()) {
                // Empty state
                EmptySearchResults(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp)
                )
            } else {
                // Grouped app list with sections
                AppListWithSections(
                    apps = displayApps,
                    showSections = searchQuery.isBlank(),
                    onAppClick = { app ->
                        app.launch()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
fun AppDrawerPreview() {
    val apps = listOf(
        AppTile(key = "app1", label = "App 1", icon = EmptyBitmap, launch = {}),
        AppTile(key = "app2", label = "App 2", icon = EmptyBitmap, launch = {})
    )
    AppDrawer(
        visible = true,
        apps = apps,
        searchQuery = "",
        searchResults = emptyList(),
        onDismiss = {},
        onSearchQueryChange = {})
}

/**
 * Search bar component
 */
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = { Text(stringResource(R.string.search_hint)) },
        leadingIcon = {
            Text("🔍", style = MaterialTheme.typography.titleMedium)
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Text("✕")
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline
        )
    )
}

@Preview
@Composable
private fun SearchBarPreview() {
    var query by remember { mutableStateOf("Search query") }
    SearchBar(
        query = query,
        onQueryChange = { query = it })
}

/**
 * App list with optional alphabetical sections
 */
@Composable
private fun AppListWithSections(
    apps: List<AppTile>,
    showSections: Boolean,
    onAppClick: (AppTile) -> Unit,
    modifier: Modifier = Modifier
) {
    if (showSections) {
        // Group by first letter
        val grouped = apps.groupBy { it.label.firstOrNull()?.uppercaseChar() ?: '#' }
            .toSortedMap()

        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            grouped.forEach { (letter, appsInSection) ->
                // Section header
                item(key = "section_$letter") {
                    SectionHeader(letter = letter.toString())
                }

                // Apps in section
                items(
                    items = appsInSection,
                    key = { it.key }
                ) { app ->
                    DrawerAppItem(
                        app = app,
                        onClick = { onAppClick(app) }
                    )
                }
            }
        }
    } else {
        // Simple list without sections
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(
                items = apps,
                key = { it.key }
            ) { app ->
                DrawerAppItem(
                    app = app,
                    onClick = { onAppClick(app) }
                )
            }
        }
    }
}

@Preview
@Composable
private fun AppListWithSectionsPreview() {
    val apps = listOf(
        AppTile(key = "app1", label = "App One", icon = EmptyBitmap, launch = {}),
        AppTile(key = "app2", label = "Another App", icon = EmptyBitmap, launch = {}),
        AppTile(key = "app3", label = "Browser", icon = EmptyBitmap, launch = {})
    )
    AppListWithSections(
        apps = apps,
        showSections = true,
        onAppClick = {}
    )
}

/**
 * Section header for alphabetical grouping
 */
@Composable
private fun SectionHeader(letter: String) {
    Text(
        text = letter,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp)
    )
}

@Preview
@Composable
private fun SectionHeaderPreview() {
    SectionHeader(letter = "A")
}

/**
 * Individual app item in drawer
 */
@Composable
private fun DrawerAppItem(
    app: AppTile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Image(
            bitmap = app.icon,
            contentDescription = app.label,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
        )
        Text(
            text = app.label,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}


@Preview
@Composable
private fun DrawerAppItemPreview() {
    DrawerAppItem(
        app = AppTile(
            key = "preview_app",
            label = "Preview App",
            icon = EmptyBitmap,
            launch = {} // Replaced TODO() with an empty lambda
        ),
        // The 'launch' parameter here seems redundant, see next point.
        onClick = {}
    )
}

/**
 * Empty state when no search results found
 */
@Composable
private fun EmptySearchResults(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "🔍",
            style = MaterialTheme.typography.displayLarge,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Text(
            text = stringResource(R.string.search_no_results),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Preview
@Composable
private fun EmptySearchResultsPreview() {
    EmptySearchResults()
}
