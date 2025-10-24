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

class IconCache(
    private val context: Context,
    maxEntries: Int = DEFAULT_MAX_ENTRIES
) {

    private val memory = object : LruCache<String, Bitmap>(maxEntries) {
        override fun sizeOf(key: String, value: Bitmap): Int = 1
    }

    suspend fun get(component: ComponentName, user: UserHandle): ImageBitmap =
        withContext(Dispatchers.IO) {
            val key = "${component.flattenToShortString()}@${user.hashCode()}"
            memory.get(key)?.let { return@withContext it.asImageBitmap() }

            val launcherApps = context.getSystemService(LauncherApps::class.java)
            val activities = launcherApps?.getActivityList(component.packageName, user).orEmpty()
            val activityInfo = activities.firstOrNull { it.componentName == component }
                ?: throw IllegalStateException("Component not found: $component")
            val drawable = activityInfo.getBadgedIcon(0)
            val sizePx = ICON_SIZE_PX
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).also { bmp ->
                val canvas = Canvas(bmp)
                drawable.setBounds(0, 0, sizePx, sizePx)
                drawable.draw(canvas)
            }
            memory.put(key, bitmap)
            bitmap.asImageBitmap()
        }

    fun clear() {
        memory.evictAll()
    }

    private companion object {
        const val DEFAULT_MAX_ENTRIES = 256
        const val ICON_SIZE_PX = 96
    }
}
