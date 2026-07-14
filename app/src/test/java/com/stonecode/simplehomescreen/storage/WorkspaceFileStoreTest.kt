package com.stonecode.simplehomescreen.storage

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WorkspaceFileStoreTest {
    private lateinit var directory: File
    private lateinit var file: File
    private lateinit var store: WorkspaceFileStore

    @Before
    fun setUp() {
        directory = Files.createTempDirectory("workspace-store-test").toFile()
        file = File(directory, WorkspaceFileStore.FILE_NAME)
        store = WorkspaceFileStore(file)
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun missingFileIsExplicit() {
        assertEquals(WorkspaceLoadResult.Missing, store.load())
    }

    @Test
    fun currentWorkspaceRoundTripsAllPersistentFields() {
        val appId = StoredAppId("com.example/.MainActivity", 42)
        val expected = Workspace(
            dashboardItems = listOf(
                DashboardItem.AppShortcut(
                    id = "app:42:com.example/.MainActivity",
                    appId = appId,
                    position = GridPosition(0, 1, 2)
                ),
                DashboardItem.Widget(
                    id = "widget:17",
                    appWidgetId = 17,
                    provider = "com.widgets/.WeatherProvider",
                    position = GridPosition(1, 0, 0),
                    span = GridSpan(3, 2)
                )
            ),
            categoryOverrides = mapOf(appId to "work-and-finance")
        )

        assertEquals(WorkspaceSaveResult.Saved, store.save(expected))

        val loaded = store.load() as WorkspaceLoadResult.Loaded
        assertEquals(expected, loaded.workspace)
        assertEquals(null, loaded.migratedFromVersion)
        assertFalse(File(directory, ".workspace.json.tmp").exists())
    }

    @Test
    fun v0WidgetOnlyFileMigratesAndKeepsOriginalBackup() {
        val legacy = """
            {"widgets":[
              {"id":7,"provider":"com.clock/.ClockProvider"},
              {"id":8,"provider":""},
              {"id":9,"provider":"com.notes/.NotesProvider"}
            ]}
        """.trimIndent()
        file.writeText(legacy)

        val result = store.load() as WorkspaceLoadResult.Loaded

        assertEquals(0, result.migratedFromVersion)
        assertEquals(legacy, result.migrationBackup?.readText())
        assertEquals(
            listOf(
                GridPosition(0, 0, 0),
                GridPosition(0, 2, 0),
                GridPosition(0, 0, 2)
            ),
            result.workspace.dashboardItems.map { it.position }
        )
        val widgets = result.workspace.dashboardItems.filterIsInstance<DashboardItem.Widget>()
        assertEquals(listOf(7, 8, 9), widgets.map { it.appWidgetId })
        assertEquals(null, widgets[1].provider)

        val secondLoad = store.load() as WorkspaceLoadResult.Loaded
        assertEquals(null, secondLoad.migratedFromVersion)
        assertEquals(result.workspace, secondLoad.workspace)
    }

    @Test
    fun corruptFileReturnsRecoverySignalAndDoesNotModifySource() {
        val corrupt = "{ definitely-not-json"
        file.writeText(corrupt)

        val result = store.load() as WorkspaceLoadResult.RecoveryRequired

        assertTrue(result.problem is WorkspaceProblem.InvalidData)
        assertEquals(file, result.sourceFile)
        assertEquals(corrupt, file.readText())
        assertFalse(File(directory, ".workspace.json.tmp").exists())
    }

    @Test
    fun newerSchemaReturnsUnsupportedVersionWithoutOverwrite() {
        val future = """{"schemaVersion":99,"dashboardItems":[],"categoryOverrides":[]}"""
        file.writeText(future)

        val result = store.load() as WorkspaceLoadResult.RecoveryRequired

        assertEquals(WorkspaceProblem.UnsupportedVersion(99), result.problem)
        assertEquals(future, file.readText())
    }

    @Test
    fun invalidGridFieldsReturnRecoverySignal() {
        file.writeText(
            """{"schemaVersion":1,"dashboardItems":[{"type":"widget","id":"widget:1","appWidgetId":1,"provider":null,"position":{"page":0,"column":-1,"row":0},"span":{"columns":2,"rows":2}}],"categoryOverrides":[]}"""
        )

        val result = store.load() as WorkspaceLoadResult.RecoveryRequired

        assertTrue(result.problem is WorkspaceProblem.InvalidData)
    }
}
