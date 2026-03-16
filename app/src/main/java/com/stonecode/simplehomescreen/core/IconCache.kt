package com.stonecode.simplehomescreen.core

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.UserHandle
import androidx.collection.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class IconCache(
    private val context: Context,
    maxEntries: Int = DEFAULT_MAX_ENTRIES
) {
    private val packageManager = context.packageManager

    private val memory = object : LruCache<String, Bitmap>(maxEntries) {
        override fun sizeOf(key: String, value: Bitmap): Int = 1
    }

    suspend fun get(component: ComponentName, user: UserHandle): ImageBitmap =
        withContext(Dispatchers.IO) {
            val key = "${component.flattenToShortString()}@${user.hashCode()}"
            memory.get(key)?.let { return@withContext it.asImageBitmap() }

            val bitmap = runCatching {
                loadDrawable(component, user).renderBitmap()
            }.recoverCatching {
                packageManager.defaultActivityIcon.renderBitmap()
            }.getOrElse {
                Bitmap.createBitmap(ICON_SIZE_PX, ICON_SIZE_PX, Bitmap.Config.ARGB_8888)
            }
            memory.put(key, bitmap)
            bitmap.asImageBitmap()
        }

    fun clear() {
        memory.evictAll()
    }

    private fun loadDrawable(component: ComponentName, user: UserHandle): Drawable {
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        val activities = launcherApps?.getActivityList(component.packageName, user).orEmpty()
        val activityInfo = activities.firstOrNull { it.componentName == component }

        return runCatching { activityInfo?.getBadgedIcon(0) }.getOrNull()
            ?: runCatching { packageManager.getActivityIcon(component) }.getOrNull()
            ?: runCatching { packageManager.getApplicationIcon(component.packageName) }.getOrNull()
            ?: packageManager.defaultActivityIcon
    }

    private fun Drawable.renderBitmap(): Bitmap {
        val sizePx = ICON_SIZE_PX
        return Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).also { bmp ->
            val canvas = Canvas(bmp)
            setBounds(0, 0, sizePx, sizePx)
            draw(canvas)
        }
    }

    private companion object {
        const val DEFAULT_MAX_ENTRIES = 256
        const val ICON_SIZE_PX = 96
    }
}
