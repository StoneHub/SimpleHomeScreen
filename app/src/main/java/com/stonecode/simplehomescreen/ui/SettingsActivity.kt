package com.stonecode.simplehomescreen.ui

import android.app.role.RoleManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stonecode.simplehomescreen.BuildConfig
import com.stonecode.simplehomescreen.R
import com.stonecode.simplehomescreen.core.AppCategory
import com.stonecode.simplehomescreen.storage.PreferencesStore

class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val preferencesStore = PreferencesStore(this)
        setContent {
            MaterialTheme {
                SettingsScreen(
                    preferencesStore = preferencesStore,
                    onSetAsDefaultLauncher = { requestDefaultLauncher() },
                    onOpenAppInfo = { openAppInfo() },
                    onNavigateBack = { finish() }
                )
            }
        }
    }

    private fun requestDefaultLauncher() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
                startActivity(intent)
            }
        } else {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        }
    }

    private fun openAppInfo() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    preferencesStore: PreferencesStore,
    onSetAsDefaultLauncher: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // Appearance Section
            SettingsSection(title = stringResource(R.string.settings_appearance_section)) {
                // Grid columns slider
                GridColumnsSlider(preferencesStore)

                // Favorites count selector
                FavoritesCountSelector(preferencesStore)
            }

            HorizontalDivider()

            // Category visibility
            SettingsSection(title = stringResource(R.string.settings_categories_section)) {
                CategoryVisibilityToggles(preferencesStore)
            }

            HorizontalDivider()

            // Launcher Settings Section
            SettingsSection(title = stringResource(R.string.settings_launcher_section)) {
                SettingsItem(
                    title = stringResource(R.string.settings_set_default_launcher),
                    subtitle = stringResource(R.string.settings_set_default_launcher_desc),
                    onClick = onSetAsDefaultLauncher
                )
            }

            HorizontalDivider()

            // About Section
            SettingsSection(title = stringResource(R.string.settings_about_section)) {
                SettingsItem(
                    title = stringResource(R.string.settings_app_info),
                    subtitle = stringResource(R.string.settings_app_info_desc),
                    onClick = onOpenAppInfo
                )

                SettingsItem(
                    title = stringResource(R.string.settings_version),
                    subtitle = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    onClick = null
                )
            }
        }
    }
}

@Composable
private fun GridColumnsSlider(preferencesStore: PreferencesStore) {
    var sliderValue by remember { mutableFloatStateOf(preferencesStore.gridColumns.toFloat()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_grid_columns),
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${sliderValue.toInt()} columns",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = {
                preferencesStore.gridColumns = sliderValue.toInt()
            },
            valueRange = 3f..6f,
            steps = 2
        )
    }
}

@Composable
private fun FavoritesCountSelector(preferencesStore: PreferencesStore) {
    val options = listOf(4, 8, 12, 16)
    var selected by remember { mutableStateOf(preferencesStore.favoritesCount) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_favorites_count),
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.settings_favorites_count_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { count ->
                FilterChip(
                    selected = selected == count,
                    onClick = {
                        selected = count
                        preferencesStore.favoritesCount = count
                    },
                    label = { Text("$count") }
                )
            }
        }
    }
}

@Composable
private fun CategoryVisibilityToggles(preferencesStore: PreferencesStore) {
    val categories = AppCategory.entries
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        categories.forEach { category ->
            var checked by remember { mutableStateOf(preferencesStore.isCategoryVisible(category)) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = category.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = checked,
                    onCheckedChange = { value ->
                        checked = value
                        preferencesStore.setCategoryVisible(category, value)
                    }
                )
            }
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        content()
    }
}

@Composable
fun SettingsItem(
    title: String,
    subtitle: String,
    onClick: (() -> Unit)?
) {
    Surface(
        onClick = onClick ?: {},
        enabled = onClick != null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
