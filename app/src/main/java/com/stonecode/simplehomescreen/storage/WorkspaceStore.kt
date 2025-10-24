package com.stonecode.simplehomescreen.storage

import android.content.ComponentName
import android.content.Context
import com.stonecode.simplehomescreen.ui.WidgetTileState
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

data class WorkspaceSnapshot(
    val widgets: List<WidgetTileState> = emptyList()
)

class WorkspaceStore(context: Context) {

    private val file: File = File(context.filesDir, FILE_NAME)

    fun load(): WorkspaceSnapshot {
        if (!file.exists()) return WorkspaceSnapshot()
        val text = runCatching { file.readText() }.getOrElse { return WorkspaceSnapshot() }
        val json = runCatching { JSONObject(text) }.getOrElse { return WorkspaceSnapshot() }
        val widgetsArray = json.optJSONArray(KEY_WIDGETS) ?: JSONArray()
        val widgets = buildList {
            for (i in 0 until widgetsArray.length()) {
                val entry = widgetsArray.optJSONObject(i) ?: continue
                val id = entry.optInt(KEY_WIDGET_ID, -1)
                if (id == -1) continue
                val provider = entry.optString(KEY_WIDGET_PROVIDER, null)
                add(
                    WidgetTileState(
                        appWidgetId = id,
                        provider = provider?.takeIf { it.isNotEmpty() }?.let { flatten ->
                            ComponentName.unflattenFromString(flatten)
                        }
                    )
                )
            }
        }
        return WorkspaceSnapshot(widgets = widgets)
    }

    fun save(snapshot: WorkspaceSnapshot) {
        val json = JSONObject().apply {
            put(
                KEY_WIDGETS,
                JSONArray().apply {
                    snapshot.widgets.forEach { widget ->
                        put(
                            JSONObject().apply {
                                put(KEY_WIDGET_ID, widget.appWidgetId)
                                put(KEY_WIDGET_PROVIDER, widget.provider?.flattenToString() ?: "")
                            }
                        )
                    }
                }
            )
        }
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(json.toString())
        }
    }

    companion object {
        private const val FILE_NAME = "workspace.json"
        private const val KEY_WIDGETS = "widgets"
        private const val KEY_WIDGET_ID = "id"
        private const val KEY_WIDGET_PROVIDER = "provider"
    }
}
