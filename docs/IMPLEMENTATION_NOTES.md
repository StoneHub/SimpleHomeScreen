# Implementation Notes - Simple Home Launcher

This document tracks implementation decisions, troubleshooting steps, and lessons learned during development.

---

## Recent Updates (2025)

### 1. BuildConfig Generation Fix

**Problem**: `BuildConfig` class not found during compilation, causing build errors in `SettingsActivity`.

**Solution**:
```kotlin
// app/build.gradle.kts
android {
    buildFeatures {
        compose = true
        buildConfig = true  // ← Added this
    }
}
```

**Why**: AGP 8+ doesn't generate BuildConfig by default. Must explicitly enable it.

**Import required**:
```kotlin
import com.stonecode.simplehomescreen.BuildConfig
```

---

### 2. Material Library Integration

**Problem**: Theme resources not found during resource linking:
- `Theme.Material3.DayNight.NoActionBar` not found
- `colorPrimaryVariant` attribute not found

**Solution**: Added Material library alongside Compose Material3:

```toml
# gradle/libs.versions.toml
[versions]
material = "1.12.0"

[libraries]
androidx-material = { module = "com.google.android.material:material", version.ref = "material" }
```

```kotlin
// app/build.gradle.kts
dependencies {
    implementation(libs.androidx.compose.material3)  // For Compose UI
    implementation(libs.androidx.material)           // For XML themes
}
```

**Why**: Compose Material3 provides Composables, but XML theme files need the View-based Material library for theme parent definitions.

---

### 3. Settings Activity for App Visibility

**Problem**: Launcher app not visible in Samsung app drawer, making testing difficult.

**Solution**: Created separate Settings activity with LAUNCHER intent:

```xml
<!-- AndroidManifest.xml -->
<activity
    android:name=".ui.SettingsActivity"
    android:exported="true"
    android:label="@string/settings_name">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity>
```

**Result**: "Simple Home Settings" appears in app drawer for easy access.

**Features**:
- Set as default launcher button
- Open app info
- Version display

---

### 4. Back Gesture Handling

**Problem**: Swiping back on home screen caused unwanted refresh behavior.

**Solution**: Disable back gesture for home screen:

```kotlin
// HomeActivity.kt onCreate()
onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
    override fun handleOnBackPressed() {
        // Do nothing - home screen is bottom of navigation stack
    }
})
```

**Why**: Home screen should be the bottom of the navigation stack. Back gesture should have no effect.

---

### 5. App Drawer with Search

**Architecture**: ModalBottomSheet + SearchEngine + Gesture Detection

#### 5.1 SearchEngine Implementation

**File**: `core/SearchEngine.kt`

**Algorithm**: Multi-tier ranking system
1. **Exact match** (score: 100) - `"gmail" == "gmail"`
2. **Starts with** (score: 80) - `"gm"` matches `"gmail"`
3. **Contains** (score: 60) - `"mail"` matches `"gmail"`
4. **Word boundary** (score: 50) - `"gal"` matches `"Photo Gallery"`
5. **Fuzzy (Levenshtein)** (score: 0-40) - `"gmial"` matches `"gmail"`

**Performance**:
- Levenshtein distance uses dynamic programming
- Searches are synchronous (no debouncing needed for small app lists)
- Results sorted by descending score

#### 5.2 AppDrawer UI Component

**File**: `ui/AppDrawer.kt`

**Features**:
- **ModalBottomSheet** for native swipe dismiss behavior
- **Search bar** with clear button at top
- **Alphabetical sections** when not searching
- **Empty state** when no results found
- **App count** display
- **Auto-close** on app launch

**Layout**:
```
┌─────────────────────┐
│ All Apps            │ ← Title
│ [🔍 Search...]  [✕] │ ← Search bar
│ 42 apps             │ ← Count
├─────────────────────┤
│ A                   │ ← Section
│ ⬜ Amazon           │
│ ⬜ Assistant        │
│ B                   │
│ ⬜ Browser          │
│ ...                 │
└─────────────────────┘
```

#### 5.3 Swipe-Up Gesture Detection

**File**: `HomeScreen.kt`

**Implementation**:
```kotlin
Box(
    modifier = Modifier
        .pointerInput(Unit) {
            detectVerticalDragGestures(
                onVerticalDrag = { change, dragAmount ->
                    // Trigger from bottom third, upward swipe
                    if (dragAmount < -20 && change.position.y > size.height * 0.66f) {
                        viewModel.onDrawerToggle(true)
                    }
                }
            )
        }
)
```

**Trigger conditions**:
- Upward drag (dragAmount < 0)
- Minimum velocity: 20px
- Start position: bottom third of screen (66%+)

#### 5.4 State Management

**HomeState additions**:
```kotlin
data class HomeState(
    val apps: List<AppTile> = emptyList(),
    val widgets: List<WidgetTileState> = emptyList(),
    val columns: Int = DEFAULT_COLUMNS,
    val showUsageAccessPrompt: Boolean = false,
    val drawerOpen: Boolean = false,           // NEW
    val searchQuery: String = "",              // NEW
    val searchResults: List<AppTile> = emptyList()  // NEW
)
```

**ViewModel methods**:
- `onDrawerToggle(open: Boolean)` - Open/close drawer, clear search on close
- `onSearchQueryChange(query: String)` - Update search query and compute results

**Flow**:
1. User swipes up → `onDrawerToggle(true)`
2. User types query → `onSearchQueryChange(query)`
3. SearchEngine filters apps → UI updates with results
4. User taps app → App launches → `onDrawerToggle(false)`

---

## Dependency Versions (Current)

```toml
[versions]
agp = "8.13.0"              # Android Gradle Plugin
kotlin = "2.0.0"
compose-bom = "2024.09.03"
compose-compiler = "1.6.0"
material3 = "1.3.0"
material = "1.12.0"         # For XML themes
coreKtx = "1.13.1"
lifecycle-runtime-ktx = "2.8.4"
```

**Compatibility notes**:
- AGP 8.13.0 requires Gradle 8.6+
- Kotlin 2.0.0 compatible with Compose Compiler 1.6.0
- minSdk = 26 (Android 8.0) for modern API usage

---

## Performance Considerations

### Icon Caching
- LRU cache with 256 max entries
- Icons loaded at 96x96px
- Async loading on IO dispatcher
- Cache cleared on ViewModel destruction

### Search Performance
- No debouncing needed (list size typically < 200 apps)
- Synchronous search execution
- Results limited by natural app count
- Levenshtein algorithm O(n*m) but acceptable for short strings

### Gesture Detection
- Single `pointerInput` modifier on root Box
- No gesture conflicts with scrolling (triggers from specific region)
- Minimal overhead when not dragging

---

## Known Limitations

1. **Search scope**: Currently only searches app labels, not package names or categories
2. **Gesture sensitivity**: May trigger accidentally on fast scrolls near bottom
3. **No recent searches**: Search history not persisted
4. **No app shortcuts**: Long-press shortcuts not implemented yet
5. **Widget limitations**: No resize support, no configuration UI

---

## Testing Checklist

### App Drawer
- [ ] Swipe up from bottom third opens drawer
- [ ] Swipe down or tap outside closes drawer
- [ ] Search filters apps correctly
- [ ] Clear button resets search
- [ ] Tapping app launches and closes drawer
- [ ] Alphabetical sections display correctly
- [ ] Empty state shows when no results

### Back Gesture
- [ ] Back gesture does nothing on home screen
- [ ] App drawer closes on back gesture
- [ ] No screen refresh on back gesture

### Settings
- [ ] "Simple Home Settings" visible in app drawer
- [ ] "Set as default launcher" button works
- [ ] "App info" button opens system settings
- [ ] Version displays correctly

---

## Troubleshooting

### Build Issues

**"BuildConfig not found"**
- Enable `buildFeatures.buildConfig = true`
- Add import: `com.stonecode.simplehomescreen.BuildConfig`

**"Theme.Material3.DayNight.NoActionBar not found"**
- Add Material library: `implementation(libs.androidx.material)`
- Sync gradle and rebuild

**"Unresolved reference: AppDrawer"**
- Check file location: `app/src/main/kotlin/...`
- Ensure package name matches: `com.stonecode.simplehomescreen.ui`
- Rebuild project

### Runtime Issues

**Drawer not opening on swipe**
- Check trigger zone (bottom 33% of screen)
- Verify gesture direction (upward = negative dragAmount)
- Check ViewModel state update

**Search not filtering**
- Verify SearchEngine instance created
- Check search query state flow
- Ensure apps list is populated

**Back gesture still works**
- Verify OnBackPressedCallback added in onCreate
- Check callback is enabled: `OnBackPressedCallback(true)`
- Ensure callback not removed prematurely

---

## Future Enhancements

### Planned Features
1. ✅ App drawer with search (COMPLETED)
2. ⬜ Category-based pages (Games, Professional, Personal, Utilities)
3. ⬜ Wallpaper integration
4. ⬜ App usage ranking improvements
5. ⬜ Widget resize functionality
6. ⬜ Long-press app shortcuts
7. ⬜ Hidden apps feature
8. ⬜ Grid customization in settings

### Architecture Improvements
1. ⬜ Baseline profiles for startup optimization
2. ⬜ Macrobenchmark module for performance testing
3. ⬜ RecyclerView fallback for low-end devices
4. ⬜ Implement remaining AppSource callbacks
5. ⬜ Advanced widget configuration UI

---

## Code References

### Key Files
- **HomeActivity.kt**: Main activity, back gesture handler
- **HomeScreen.kt**: Main UI, swipe gesture detection, app drawer integration
- **HomeViewModel.kt**: State management, search state
- **AppDrawer.kt**: Drawer UI, search bar, app list
- **SearchEngine.kt**: Fuzzy search algorithm
- **SettingsActivity.kt**: Settings UI for launcher configuration

### Key Functions
- `HomeViewModel.onDrawerToggle()` - Open/close drawer
- `HomeViewModel.onSearchQueryChange()` - Handle search input
- `SearchEngine.search()` - Filter and rank apps
- `detectVerticalDragGestures()` - Swipe detection
- `handleOnBackPressed()` - Disable back gesture

---

## References

- [Compose Gestures Documentation](https://developer.android.com/jetpack/compose/gestures)
- [ModalBottomSheet Guide](https://developer.android.com/jetpack/compose/components/bottom-sheets)
- [LauncherApps API](https://developer.android.com/reference/android/content/pm/LauncherApps)
- [OnBackPressedCallback](https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture)
