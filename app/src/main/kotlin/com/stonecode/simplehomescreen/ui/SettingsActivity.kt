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
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stonecode.simplehomescreen.BuildConfig
import com.stonecode.simplehomescreen.R

/**
 * Settings activity for Simple Home launcher.
 * Provides configuration options and launcher management.
 */
class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                SettingsScreen(
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
                // Always show the dialog, even if already set as default
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
                startActivity(intent)
            }
        } else {
            // For older Android versions, open home settings
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        }
    }

    private fun openAppInfo() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }

    companion object {
        private const val REQUEST_CODE_SET_DEFAULT_LAUNCHER = 100
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
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
                        Text("←") // Simple back arrow
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
            // Launcher Settings Section
            SettingsSection(title = stringResource(R.string.settings_launcher_section)) {
                SettingsItem(
                    title = stringResource(R.string.settings_set_default_launcher),
                    subtitle = stringResource(R.string.settings_set_default_launcher_desc),
                    onClick = onSetAsDefaultLauncher
                )
            }

            HorizontalDivider()

            // App Info Section
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
