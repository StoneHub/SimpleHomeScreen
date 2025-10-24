package com.stonecode.simplehomescreen.core

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface AppSource {
    suspend fun loadLaunchables(): List<Launchable>
    fun registerCallbacks(callback: (PackageEvent) -> Unit)
    fun unregisterCallbacks()
}

data class Launchable(
    val component: ComponentName,
    val label: String,
    val userHandle: UserHandle
)

sealed interface PackageEvent {
    data class Added(val pkg: String, val user: UserHandle) : PackageEvent
    data class Removed(val pkg: String, val user: UserHandle) : PackageEvent
}

class LauncherAppSource(
    private val context: Context,
    private val launcherApps: LauncherApps
) : AppSource {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var callback: LauncherApps.Callback? = null

    override suspend fun loadLaunchables(): List<Launchable> = withContext(Dispatchers.IO) {
        launcherApps.profiles.flatMap { user ->
            launcherApps.getActivityList(null, user).map {
                Launchable(
                    component = it.componentName,
                    label = it.label?.toString() ?: it.componentName.className,
                    userHandle = user
                )
            }
        }.sortedBy { it.label.lowercase() }
    }

    override fun registerCallbacks(callback: (PackageEvent) -> Unit) {
        if (this.callback != null) return
        val cb = object : LauncherApps.Callback() {
            override fun onPackageAdded(packageName: String, user: UserHandle) {
                callback(PackageEvent.Added(packageName, user))
            }

            override fun onPackageRemoved(packageName: String, user: UserHandle) {
                callback(PackageEvent.Removed(packageName, user))
            }
        }
        launcherApps.registerCallback(cb, mainHandler)
        this.callback = cb
    }

    override fun unregisterCallbacks() {
        callback?.let { launcherApps.unregisterCallback(it) }
        callback = null
    }
}
