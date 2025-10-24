# SIMPLE_HOME_LAUNCHER.md

Goal: ship a smooth, modern Android launcher using Jetpack Compose for the app grid and View interop for widgets. Keep dependencies minimal, code clear, and performance solid.

---

## 0) Version matrix to confirm before coding
- Kotlin: confirm Kotlin plugin ↔ Compose matrix in Android Studio.
- Compose Compiler: set `composeOptions.kotlinCompilerExtensionVersion` to match your Compose BOM.
- AGP: use Studio’s suggested stable.
- Hilt: optional. If added later, verify no JavaPoet pin mismatch.
Document any overrides in `gradle/libs.versions.toml`.

---

## 1) Module structure
- `app/` – launcher UI, widget host, usage ranking, icon cache, settings.
- `baselineprofile/` (optional later) – macrobenchmark + baseline profiles.

Keep a renderer interface around the grid so a RecyclerView fallback stays trivial.

---

## 2) Gradle updates (app/build.gradle.kts)

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.stonecode.simplehomescreen"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.stonecode.simplehomescreen"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    buildFeatures { compose = true }

    composeOptions {
        // IMPORTANT: keep aligned to your Compose BOM
        kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions { jvmTarget = "11" }
}

dependencies {
    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Interop + system services
    implementation(libs.androidx.core.ktx)

    // Performance
    implementation(libs.androidx.profileinstaller)

    // Test
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
Top-level build.gradle.kts stays as you provided.

Example libs.versions.toml entries (add/merge as needed)
toml
Always show details

Copy code
[versions]
compose-bom = "2025.01.00"        # example; confirm in Studio
compose-compiler = "1.6.XX"       # match your Kotlin plugin
activity-compose = "1.9.2"
material3 = "1.3.0"
core-ktx = "1.13.1"
lifecycle-runtime-ktx = "2.8.5"
profileinstaller = "1.3.1"
junit = "4.13.2"
androidx-junit = "1.1.5"
espresso = "3.5.1"

[libraries]
androidx-compose-bom = { module = "androidx.compose:compose-bom", version.ref = "compose-bom" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activity-compose" }
androidx-compose-ui = { module = "androidx.compose.ui:ui" }
androidx-compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling" }
androidx-compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
androidx-compose-material3 = { module = "androidx.compose.material3:material3", version.ref = "material3" }
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "core-ktx" }
androidx-lifecycle-runtime-ktx = { module = "androidx.lifecycle:lifecycle-runtime-ktx", version.ref = "lifecycle-runtime-ktx" }
androidx-profileinstaller = { module = "androidx.profileinstaller:profileinstaller", version.ref = "profileinstaller" }
junit = { module = "junit:junit", version.ref = "junit" }
androidx-junit = { module = "androidx.test.ext:junit", version.ref = "androidx-junit" }
androidx-espresso-core = { module = "androidx.test.espresso:espresso-core", version.ref = "espresso" }

[plugins]
android-application = { id = "com.android.application", version = "8.6.0" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version = "2.0.0" }
3) Manifest (launcher + role + backups + theme)
xml
Always show details

Copy code
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.SimpleHomeScreen">

        <activity
            android:name=".ui.HomeActivity"
            android:exported="true"
            android:launchMode="singleTask"
            android:stateNotNeeded="true"
            android:excludeFromRecents="true"
            android:taskAffinity="">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.HOME" />
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>
        </activity>

    </application>
</manifest>
Notes:

ROLE_HOME is requested in code on first run.

Widgets bind via user confirmation. No special permission is required.

Optional backups (placeholders):

xml
Always show details

Copy code
<!-- res/xml/backup_rules.xml -->
<?xml version="1.0" encoding="utf-8"?>
<full-backup-content>
    <include domain="file" path="workspace.json"/>
</full-backup-content>
xml
Always show details

Copy code
<!-- res/xml/data_extraction_rules.xml -->
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
        <include domain="file" path="workspace.json"/>
    </cloud-backup>
</data-extraction-rules>
4) Core features and responsibilities
4.1 App discovery and shortcuts
Use LauncherApps for activities and deep shortcuts. Track package add/remove/changed via LauncherApps.Callback.

kotlin
Always show details

Copy code
// core/AppSource.kt
package com.stonecode.simplehomescreen.core

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.os.UserHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface AppSource {
    suspend fun loadLaunchables(): List<Launchable>
    fun registerCallbacks(callback: (PackageEvent) -> Unit)
}

data class Launchable(
    val component: ComponentName,
    val label: String,
    val userHandle: UserHandle
)

sealed interface PackageEvent {
    data class Added(val pkg: String, val user: UserHandle): PackageEvent
    data class Removed(val pkg: String, val user: UserHandle): PackageEvent
}
kotlin
Always show details

Copy code
// core/LauncherAppSource.kt
package com.stonecode.simplehomescreen.core

import android.content.Context
import android.content.pm.LauncherApps
import android.os.UserHandle

class LauncherAppSource(
    private val context: Context,
    private val launcherApps: LauncherApps
) : AppSource {
    override suspend fun loadLaunchables(): List<Launchable> = withContext(Dispatchers.IO) {
        launcherApps.profiles.flatMap { user ->
            launcherApps.getActivityList(null, user).map {
                Launchable(it.componentName, it.label.toString(), user)
            }
        }
    }

    override fun registerCallbacks(callback: (PackageEvent) -> Unit) {
        launcherApps.registerCallback(object : LauncherApps.Callback() {
            override fun onPackageAdded(packageName: String, user: UserHandle) {
                callback(PackageEvent.Added(packageName, user))
            }
            override fun onPackageRemoved(packageName: String, user: UserHandle) {
                callback(PackageEvent.Removed(packageName, user))
            }
        })
    }
}
4.2 Usage-based ranking
Use UsageStatsManager. Provide a deep link to the Usage Access settings. Apply exponential decay so recent launches outrank stale ones.

kotlin
Always show details

Copy code
// core/UsageRanker.kt
package com.stonecode.simplehomescreen.core

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import kotlin.math.exp

class UsageRanker(private val ctx: Context) {
    fun hasAccess(): Boolean {
        val usm = ctx.getSystemService(UsageStatsManager::class.java)
        val end = System.currentTimeMillis()
        val start = end - 60_000
        return usm.queryEvents(start, end).hasNextEvent()
    }

    fun openUsageAccessSettings() {
        ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun ranks(days: Int = 30): Map<String, Double> {
        val usm = ctx.getSystemService(UsageStatsManager::class.java)
        val end = System.currentTimeMillis()
        val start = end - days * 24L * 60 * 60 * 1000
        val ue = usm.queryEvents(start, end)
        val launches = mutableMapOf<String, Double>()
        val now = end.toDouble()
        val decay = 1e-6 // tune by testing

        val e = UsageEvents.Event()
        while (ue.hasNextEvent()) {
            ue.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                val age = (now - e.timeStamp) / 1000.0
                val weight = exp(-decay * age)
                launches.merge(e.packageName, weight, Double::plus)
            }
        }
        return launches
    }
}
4.3 Icon cache
Decode once off main thread. Memory LRU.

kotlin
Always show details

Copy code
// core/IconCache.kt
package com.stonecode.simplehomescreen.core

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.UserHandle
import androidx.collection.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class IconCache(private val ctx: Context, maxEntries: Int = 256) {
    private val mem = object : LruCache<String, Bitmap>(maxEntries) {
        override fun sizeOf(key: String, value: Bitmap) = 1
    }

    suspend fun get(component: ComponentName, user: UserHandle): ImageBitmap = withContext(Dispatchers.IO) {
        val key = component.flattenToString() + "@" + user.hashCode()
        mem.get(key)?.let { return@withContext it.asImageBitmap() }
        val la = ctx.getSystemService(LauncherApps::class.java)
        val info = la.getActivityList(component.packageName, user).first { it.componentName == component }
        val dr = info.getBadgedIcon(0)
        val size = 96
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also {
            val c = Canvas(it); dr.setBounds(0, 0, size, size); dr.draw(c)
        }
        mem.put(key, bmp)
        bmp.asImageBitmap()
    }
}
4.4 Widgets host
Use AppWidgetHost and AppWidgetManager. Flow: pick → allocate ID → bind → host view.

kotlin
Always show details

Copy code
// widgets/WidgetHost.kt
package com.stonecode.simplehomescreen.widgets

import android.appwidget.AppWidgetHost
import android.content.Context

class WidgetHost(ctx: Context) : AppWidgetHost(ctx, HOST_ID) {
    fun start() = startListening()
    fun stop() = stopListening()
    companion object { const val HOST_ID = 0x53484 } // "SH"
}
kotlin
Always show details

Copy code
// widgets/WidgetController.kt
package com.stonecode.simplehomescreen.widgets

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.Context

class WidgetController(
    private val context: Context,
    private val appWidgetManager: AppWidgetManager,
    private val host: WidgetHost
) {
    fun allocateId() = host.allocateAppWidgetId()
    fun deleteId(id: Int) = host.deleteAppWidgetId(id)
    fun createHostView(id: Int): AppWidgetHostView =
        host.createView(context, id, appWidgetManager.getAppWidgetInfo(id))
}
Compose interop:

kotlin
Always show details

Copy code
// widgets/WidgetViews.kt
package com.stonecode.simplehomescreen.widgets

import android.appwidget.AppWidgetHostView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun WidgetTile(hostView: AppWidgetHostView, modifier: Modifier = Modifier) {
    AndroidView(factory = { hostView }, modifier = modifier)
}
4.5 Grid UI in Compose
LazyVerticalGrid for apps. Stable keys. Light item.

kotlin
Always show details

Copy code
// ui/AppGrid.kt
package com.stonecode.simplehomescreen.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Immutable

@Immutable
data class AppTile(
    val key: String,
    val label: String,
    val icon: ImageBitmap,
    val launch: () -> Unit
)

@Composable
fun AppGrid(apps: List<AppTile>, columns: Int, modifier: Modifier = Modifier) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp)
    ) {
        items(apps, key = { it.key }) { app ->
            AppCell(app)
        }
    }
}

@Composable
private fun AppCell(app: AppTile) {
    Column(
        modifier = Modifier
            .size(88.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { app.launch() }
            .padding(6.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(app.icon, contentDescription = app.label, modifier = Modifier.size(48.dp))
        Text(app.label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
5) Home activity, role request, and state pipeline
kotlin
Always show details

Copy code
// ui/HomeActivity.kt
package com.stonecode.simplehomescreen.ui

import android.app.role.RoleManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme

class HomeActivity : ComponentActivity() {
    private val vm: HomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        maybeRequestHomeRole()
        setContent {
            MaterialTheme {
                HomeScreen()
            }
        }
    }

    private fun maybeRequestHomeRole() {
        val rm = getSystemService(RoleManager::class.java)
        if (rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
            startActivity(rm.createRequestRoleIntent(RoleManager.ROLE_HOME))
        }
    }
}
kotlin
Always show details

Copy code
// ui/HomeScreen.kt
package com.stonecode.simplehomescreen.ui

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun HomeScreen(vm: HomeViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    AppGrid(apps = state.apps, columns = state.columns,
        modifier = Modifier.background(MaterialTheme.colorScheme.background))
}
kotlin
Always show details

Copy code
// ui/HomeViewModel.kt
package com.stonecode.simplehomescreen.ui

import android.app.Application
import android.content.pm.LauncherApps
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.stonecode.simplehomescreen.core.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class HomeState(
    val apps: List<AppTile> = emptyList(),
    val columns: Int = 5
)

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val iconCache = IconCache(app)
    private val appSource = LauncherAppSource(app, app.getSystemService(LauncherApps::class.java))
    private val usage = UsageRanker(app)

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state

    init {
        viewModelScope.launch {
            val launchables = appSource.loadLaunchables()
            val ranks = if (usage.hasAccess()) usage.ranks() else emptyMap()
            val tiles = launchables
                .sortedByDescending { ranks[it.component.packageName] ?: 0.0 }
                .map { l ->
                    val icon = iconCache.get(l.component, l.userHandle)
                    AppTile(
                        key = l.component.flattenToString(),
                        label = l.label,
                        icon = icon,
                        launch = { launch(l) }
                    )
                }
            _state.value = HomeState(apps = tiles, columns = 5)
        }
    }

    private fun launch(l: Launchable) {
        val la = getApplication<Application>().getSystemService(LauncherApps::class.java)
        la.startMainActivity(l.component, l.userHandle, null, null)
    }
}
6) Widgets flow UI
“Add widget” button → ACTION_APPWIDGET_PICK → allocate ID → ACTION_APPWIDGET_BIND if needed → host view. Persist positions by appWidgetId and cell spans.

Pseudocode:

kotlin
Always show details

Copy code
// In an Activity or a dedicated screen
val pick = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
    val appWidgetId = res.data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        ?: return@registerForActivityResult
    // If binding required, launch ACTION_APPWIDGET_BIND
    // else create host view and add to workspace model
}
7) Settings
Grid size (rows, columns).

Icon size and label toggle.

Wallpaper: launch system wallpaper picker; or draw a custom dynamic backdrop inside the launcher root.

8) Persistence
Serialize workspace (apps, folders, widgets with spans/positions) to a JSON file:

Path suggestion: filesDir/workspace.json

Include migration field version for future changes.

9) Performance checklist
Include androidx.profileinstaller to ship precompiled profiles.

Warm icon cache off main thread.

Stable keys, immutable models, remember for handlers to avoid extra recomposition.

Disable heavy item animations.

Validate:

adb shell cmd gfxinfo com.stonecode.simplehomescreen framestats

adb shell dumpsys meminfo com.stonecode.simplehomescreen

Add Macrobenchmark + BaselineProfile module later if needed.

10) Rollback plan
Abstract AppGrid behind GridRenderer:

ComposeGridRenderer (default)

RecyclerViewGridRenderer (fallback)
Swap implementations without touching higher logic if performance targets miss.

11) Minimal TODO list for the agent
Apply Gradle updates and add Compose + ProfileInstaller deps.

Replace manifest with launcher activity declaration from §3.

Implement LauncherAppSource, UsageRanker, IconCache, and HomeViewModel from §4–5.

Build HomeActivity + HomeScreen from §5.

Add settings screen for grid columns and label toggle.

Implement widget pick/bind/host shell per §6.

Add simple JSON persistence for app and widget layout.

Show a “Grant Usage Access” CTA if UsageRanker.hasAccess() is false.

Smoke test on Android 13–15. Record framestats. Adjust icon cache size if needed.

Optional: add baselineprofile module and macrobench to remove cold-start jank.

12) ProGuard (keep rules)
pro
Always show details

Copy code
# Keep composables and metadata
-keep class **\$ComposableSingletons** { *; }
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}
# Keep classes used by reflection in widgets/shortcuts if any future libs added
13) Strings and theme placeholders
xml
Always show details

Copy code
<!-- res/values/strings.xml -->
<resources>
    <string name="app_name">Simple Home</string>
</resources>
xml
Always show details

Copy code
<!-- res/values/themes.xml -->
<resources xmlns:tools="http://schemas.android.com/tools">
    <style name="Theme.SimpleHomeScreen" parent="Theme.Material3.DayNight.NoActionBar">
        <item name="android:windowTranslucentStatus">true</item>
        <item name="android:windowDrawsSystemBarBackgrounds">true</item>
        <item name="android:statusBarColor">@android:color/transparent</item>
    </style>
</resources>
14) Known constraints
You cannot replace system Recents/Quickstep animations. Third‑party launchers are limited here.

Widget binding needs user consent. No silent binding.

Usage access requires user grant. Provide a deep link.

15) Validation script ideas (manual run)
Cold start time: measure via logcat markers or Macrobenchmark.

Scroll the grid for 10 seconds and confirm zero jank on a mid‑tier device.

Add and resize the following widgets: Clock, Calendar, Weather, a 3rd‑party notes widget.

Install/uninstall an app and verify grid updates without full reload.

bash
Always show details

Copy code

# End of file
