package com.stonecode.simplehomescreen.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardPlacementEngineTest {
    private val engine = DashboardPlacementEngine(GridSize(columns = 4, rows = 5))
    private val appId = AppId("com.example/.MainActivity", 0)

    @Test
    fun `placing an item rejects duplicate ids collisions and out of bounds spans`() {
        val existing = shortcut("one", GridPosition(0, 0, 0))
        val layout = DashboardLayout(listOf(existing))

        assertRejected(engine.place(layout, shortcut("one", GridPosition(0, 2, 2))), PlacementFailure.DUPLICATE_ID)
        assertRejected(engine.place(layout, shortcut("two", GridPosition(0, 0, 0))), PlacementFailure.COLLISION)
        assertRejected(
            engine.place(
                layout,
                widget("widget", GridPosition(0, 3, 4), GridSpan(2, 1)),
            ),
            PlacementFailure.OUT_OF_BOUNDS,
        )
    }

    @Test
    fun `items on different pages do not collide`() {
        val layout = DashboardLayout(listOf(shortcut("one", GridPosition(0, 0, 0))))

        val result = engine.place(layout, shortcut("two", GridPosition(1, 0, 0)))

        assertTrue(result is PlacementResult.Success)
    }

    @Test
    fun `invalid move and resize preserve the original layout`() {
        val one = shortcut("one", GridPosition(0, 0, 0))
        val two = shortcut("two", GridPosition(0, 1, 0))
        val layout = DashboardLayout(listOf(one, two))

        assertRejected(engine.move(layout, "two", GridPosition(0, 0, 0)), PlacementFailure.COLLISION)
        assertRejected(engine.resize(layout, "one", GridSpan(5, 1)), PlacementFailure.OUT_OF_BOUNDS)
        assertEquals(GridPosition(0, 0, 0), one.position)
        assertEquals(GridSpan(1, 1), one.span)
    }

    @Test
    fun `valid move resize and remove replace only the target item`() {
        val widget = widget("widget", GridPosition(0, 0, 0), GridSpan(2, 1))
        val app = shortcut("app", GridPosition(0, 3, 0))
        val layout = DashboardLayout(listOf(widget, app))

        val moved = success(engine.move(layout, "app", GridPosition(0, 2, 2)))
        val resized = success(engine.resize(moved, "widget", GridSpan(2, 2)))
        val removed = success(engine.remove(resized, "app"))

        assertEquals(GridPosition(0, 2, 2), moved.items.first { it.id == "app" }.position)
        assertEquals(GridSpan(2, 2), resized.items.first { it.id == "widget" }.span)
        assertEquals(listOf("widget"), removed.items.map { it.id })
    }

    @Test
    fun `first available placement scans rows then pages`() {
        val pageZeroItems = (0 until 4).map { column ->
            widget("widget-$column", GridPosition(0, column, 0), GridSpan(1, 5))
        }
        val layout = DashboardLayout(pageZeroItems)

        assertEquals(GridPosition(1, 0, 0), engine.findFirstAvailable(layout, GridSpan(2, 2), maxPages = 2))
        assertNull(engine.findFirstAvailable(layout, GridSpan(2, 2), maxPages = 1))
    }

    private fun shortcut(id: String, position: GridPosition) = DashboardItem.AppShortcut(
        id = id,
        appId = appId,
        position = position,
    )

    private fun widget(id: String, position: GridPosition, span: GridSpan) = DashboardItem.Widget(
        id = id,
        appWidgetId = id.hashCode().let { if (it == Int.MIN_VALUE) 0 else kotlin.math.abs(it) },
        providerComponentName = "com.example/.WidgetProvider",
        position = position,
        span = span,
    )

    private fun assertRejected(result: PlacementResult, reason: PlacementFailure) {
        assertEquals(PlacementResult.Rejected(reason), result)
    }

    private fun success(result: PlacementResult): DashboardLayout {
        assertTrue(result is PlacementResult.Success)
        return (result as PlacementResult.Success).layout
    }
}
