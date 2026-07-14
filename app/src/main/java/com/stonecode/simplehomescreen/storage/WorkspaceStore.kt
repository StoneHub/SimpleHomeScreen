package com.stonecode.simplehomescreen.storage

import android.content.ComponentName
import android.content.Context
import com.stonecode.simplehomescreen.ui.WidgetTileState
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Stable identity for an app, including the Android user/profile that owns it. */
data class StoredAppId(
    val componentName: String,
    val userSerial: Long
)

data class GridPosition(
    val page: Int,
    val column: Int,
    val row: Int
)

data class GridSpan(
    val columns: Int,
    val rows: Int
)

sealed interface DashboardItem {
    val id: String
    val position: GridPosition
    val span: GridSpan

    data class AppShortcut(
        override val id: String,
        val appId: StoredAppId,
        override val position: GridPosition,
        override val span: GridSpan = GridSpan(1, 1)
    ) : DashboardItem

    data class Widget(
        override val id: String,
        val appWidgetId: Int,
        val provider: String?,
        override val position: GridPosition,
        override val span: GridSpan
    ) : DashboardItem
}

data class Workspace(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val dashboardItems: List<DashboardItem> = emptyList(),
    val categoryOverrides: Map<StoredAppId, String> = emptyMap()
) {
    init {
        require(schemaVersion == CURRENT_SCHEMA_VERSION) {
            "Unsupported in-memory workspace schema: $schemaVersion"
        }
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

sealed interface WorkspaceLoadResult {
    data class Loaded(
        val workspace: Workspace,
        val migratedFromVersion: Int? = null,
        val migrationBackup: File? = null
    ) : WorkspaceLoadResult

    data object Missing : WorkspaceLoadResult

    /** The source file is deliberately left untouched for diagnosis or manual recovery. */
    data class RecoveryRequired(
        val sourceFile: File,
        val problem: WorkspaceProblem
    ) : WorkspaceLoadResult
}

sealed interface WorkspaceSaveResult {
    data object Saved : WorkspaceSaveResult
    data class Failed(val problem: WorkspaceProblem) : WorkspaceSaveResult
}

sealed interface WorkspaceProblem {
    data class InvalidData(val message: String) : WorkspaceProblem
    data class UnsupportedVersion(val version: Int) : WorkspaceProblem
    data class IoFailure(val operation: String, val message: String) : WorkspaceProblem
}

/**
 * Versioned file store. Writes are fsynced to a sibling temporary file and atomically moved into
 * place. V0 is the prototype's widget-only JSON shape; its original bytes are retained as a backup.
 */
class WorkspaceFileStore internal constructor(
    private val file: File,
    private val codec: WorkspaceJsonCodec = WorkspaceJsonCodec()
) {
    fun load(): WorkspaceLoadResult {
        if (!file.exists()) return WorkspaceLoadResult.Missing
        val source = try {
            file.readText()
        } catch (error: Exception) {
            return WorkspaceLoadResult.RecoveryRequired(
                file,
                WorkspaceProblem.IoFailure("read", error.message ?: error.javaClass.simpleName)
            )
        }

        return when (val decoded = codec.decode(source)) {
            is WorkspaceJsonCodec.DecodeResult.Current ->
                WorkspaceLoadResult.Loaded(decoded.workspace)

            is WorkspaceJsonCodec.DecodeResult.LegacyV0 -> migrateV0(source, decoded.widgets)
            is WorkspaceJsonCodec.DecodeResult.Invalid -> WorkspaceLoadResult.RecoveryRequired(
                file,
                WorkspaceProblem.InvalidData(decoded.message)
            )

            is WorkspaceJsonCodec.DecodeResult.Unsupported -> WorkspaceLoadResult.RecoveryRequired(
                file,
                WorkspaceProblem.UnsupportedVersion(decoded.version)
            )
        }
    }

    fun save(workspace: Workspace): WorkspaceSaveResult {
        return try {
            atomicWrite(codec.encode(workspace))
            WorkspaceSaveResult.Saved
        } catch (error: Exception) {
            WorkspaceSaveResult.Failed(
                WorkspaceProblem.IoFailure("write", error.message ?: error.javaClass.simpleName)
            )
        }
    }

    private fun migrateV0(
        source: String,
        widgets: List<WorkspaceJsonCodec.LegacyWidget>
    ): WorkspaceLoadResult {
        val workspace = Workspace(
            dashboardItems = widgets.mapIndexed { index, widget ->
                val widgetsPerRow = DEFAULT_GRID_COLUMNS / LEGACY_WIDGET_SPAN
                val column = (index % widgetsPerRow) * LEGACY_WIDGET_SPAN
                val row = (index / widgetsPerRow) * LEGACY_WIDGET_SPAN
                DashboardItem.Widget(
                    id = "widget:${widget.id}",
                    appWidgetId = widget.id,
                    provider = widget.provider,
                    position = GridPosition(page = 0, column = column, row = row),
                    span = GridSpan(LEGACY_WIDGET_SPAN, LEGACY_WIDGET_SPAN)
                )
            }
        )
        val backup = File(file.parentFile, "$FILE_NAME.v0.bak")
        return try {
            if (!backup.exists()) writeFileDurably(backup, source)
            atomicWrite(codec.encode(workspace))
            WorkspaceLoadResult.Loaded(workspace, migratedFromVersion = 0, migrationBackup = backup)
        } catch (error: Exception) {
            WorkspaceLoadResult.RecoveryRequired(
                file,
                WorkspaceProblem.IoFailure("migrate", error.message ?: error.javaClass.simpleName)
            )
        }
    }

    private fun atomicWrite(text: String) {
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, ".${file.name}.tmp")
        try {
            writeFileDurably(temporary, text)
            try {
                Files.move(
                    temporary.toPath(),
                    file.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    temporary.toPath(),
                    file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }
        } finally {
            temporary.delete()
        }
    }

    private fun writeFileDurably(destination: File, text: String) {
        destination.parentFile?.mkdirs()
        FileOutputStream(destination).use { stream ->
            stream.write(text.toByteArray(Charsets.UTF_8))
            stream.fd.sync()
        }
    }

    companion object {
        const val FILE_NAME = "workspace.json"
        private const val DEFAULT_GRID_COLUMNS = 5
        private const val LEGACY_WIDGET_SPAN = 2
    }
}

/** Compatibility shape for the prototype UI while it migrates to [Workspace]. */
data class WorkspaceSnapshot(
    val widgets: List<WidgetTileState> = emptyList()
)

class WorkspaceStore(context: Context) {
    private val store = WorkspaceFileStore(File(context.filesDir, WorkspaceFileStore.FILE_NAME))

    fun loadWorkspace(): WorkspaceLoadResult = store.load()

    fun saveWorkspace(workspace: Workspace): WorkspaceSaveResult = store.save(workspace)

    @Deprecated("Use loadWorkspace() and handle recovery states explicitly")
    fun load(): WorkspaceSnapshot {
        val workspace = (store.load() as? WorkspaceLoadResult.Loaded)?.workspace
            ?: return WorkspaceSnapshot()
        return WorkspaceSnapshot(
            widgets = workspace.dashboardItems.filterIsInstance<DashboardItem.Widget>().map { widget ->
                WidgetTileState(
                    appWidgetId = widget.appWidgetId,
                    provider = widget.provider?.let(ComponentName::unflattenFromString)
                )
            }
        )
    }

    @Deprecated("Use saveWorkspace() and inspect its result")
    fun save(snapshot: WorkspaceSnapshot) {
        val existing = (store.load() as? WorkspaceLoadResult.Loaded)?.workspace ?: Workspace()
        val nonWidgetItems = existing.dashboardItems.filterNot { it is DashboardItem.Widget }
        val widgets = snapshot.widgets.mapIndexed { index, widget ->
            DashboardItem.Widget(
                id = "widget:${widget.appWidgetId}",
                appWidgetId = widget.appWidgetId,
                provider = widget.provider?.flattenToString(),
                position = GridPosition(page = 0, column = 0, row = index * 2),
                span = GridSpan(columns = 2, rows = 2)
            )
        }
        store.save(existing.copy(dashboardItems = nonWidgetItems + widgets))
    }
}
