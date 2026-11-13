package com.stonecode.simplehomescreen.domain

data class GridSize(val columns: Int, val rows: Int) {
    init {
        require(columns > 0) { "columns must be positive" }
        require(rows > 0) { "rows must be positive" }
    }
}
data class GridPosition(val page: Int, val column: Int, val row: Int) {
    init {
        require(page >= 0) { "page must not be negative" }
        require(column >= 0) { "column must not be negative" }
        require(row >= 0) { "row must not be negative" }
    }
}

data class GridSpan(val columns: Int = 1, val rows: Int = 1) {
    init {
        require(columns > 0) { "columns must be positive" }
        require(rows > 0) { "rows must be positive" }
    }
}

sealed interface DashboardItem {
    val id: String
    val position: GridPosition
    val span: GridSpan

    fun withPlacement(position: GridPosition, span: GridSpan = this.span): DashboardItem

    data class AppShortcut(
        override val id: String,
        val appId: AppId,
        override val position: GridPosition,
        override val span: GridSpan = GridSpan(),
    ) : DashboardItem {
        init {
            require(id.isNotBlank()) { "Dashboard item id must not be blank" }
        }

        override fun withPlacement(position: GridPosition, span: GridSpan): DashboardItem =
            copy(position = position, span = span)
    }

    data class Widget(
        override val id: String,
        val appWidgetId: Int,
        val providerComponentName: String,
        override val position: GridPosition,
        override val span: GridSpan,
    ) : DashboardItem {
        init {
            require(id.isNotBlank()) { "Dashboard item id must not be blank" }
            require(appWidgetId >= 0) { "appWidgetId must not be negative" }
            require(providerComponentName.isNotBlank()) { "providerComponentName must not be blank" }
        }

        override fun withPlacement(position: GridPosition, span: GridSpan): DashboardItem =
            copy(position = position, span = span)
    }
}

data class DashboardLayout(
    val items: List<DashboardItem> = emptyList(),
)

enum class PlacementFailure {
    OUT_OF_BOUNDS,
    COLLISION,
    DUPLICATE_ID,
    ITEM_NOT_FOUND,
    NO_SPACE,
}

sealed interface PlacementResult {
    data class Success(val layout: DashboardLayout) : PlacementResult
    data class Rejected(val reason: PlacementFailure) : PlacementResult
}

/** Pure, deterministic placement rules shared by drag/drop, resize, and restoration. */
class DashboardPlacementEngine(
    private val gridSize: GridSize,
) {
    fun place(layout: DashboardLayout, item: DashboardItem): PlacementResult {
        if (layout.items.any { it.id == item.id }) {
            return PlacementResult.Rejected(PlacementFailure.DUPLICATE_ID)
        }
        validatePlacement(layout.items, item)?.let { return PlacementResult.Rejected(it) }
        return PlacementResult.Success(layout.copy(items = layout.items + item))
    }

    fun move(layout: DashboardLayout, itemId: String, position: GridPosition): PlacementResult {
        val item = layout.items.firstOrNull { it.id == itemId }
            ?: return PlacementResult.Rejected(PlacementFailure.ITEM_NOT_FOUND)
        return replace(layout, item.withPlacement(position))
    }

    fun resize(layout: DashboardLayout, itemId: String, span: GridSpan): PlacementResult {
        val item = layout.items.firstOrNull { it.id == itemId }
            ?: return PlacementResult.Rejected(PlacementFailure.ITEM_NOT_FOUND)
        return replace(layout, item.withPlacement(item.position, span))
    }

    fun remove(layout: DashboardLayout, itemId: String): PlacementResult {
        if (layout.items.none { it.id == itemId }) {
            return PlacementResult.Rejected(PlacementFailure.ITEM_NOT_FOUND)
        }
        return PlacementResult.Success(layout.copy(items = layout.items.filterNot { it.id == itemId }))
    }

    fun findFirstAvailable(
        layout: DashboardLayout,
        span: GridSpan,
        maxPages: Int,
    ): GridPosition? {
        require(maxPages > 0) { "maxPages must be positive" }
        for (page in 0 until maxPages) {
            for (row in 0 until gridSize.rows) {
                for (column in 0 until gridSize.columns) {
                    val position = GridPosition(page, column, row)
                    val probe = ProbeItem(position, span)
                    if (validatePlacement(layout.items, probe) == null) return position
                }
            }
        }
        return null
    }

    private fun replace(layout: DashboardLayout, replacement: DashboardItem): PlacementResult {
        val otherItems = layout.items.filterNot { it.id == replacement.id }
        validatePlacement(otherItems, replacement)?.let { return PlacementResult.Rejected(it) }
        return PlacementResult.Success(
            layout.copy(items = layout.items.map { if (it.id == replacement.id) replacement else it }),
        )
    }

    private fun validatePlacement(
        existingItems: List<DashboardItem>,
        candidate: DashboardItem,
    ): PlacementFailure? {
        if (candidate.position.column + candidate.span.columns > gridSize.columns ||
            candidate.position.row + candidate.span.rows > gridSize.rows
        ) {
            return PlacementFailure.OUT_OF_BOUNDS
        }
        if (existingItems.any { overlaps(it, candidate) }) {
            return PlacementFailure.COLLISION
        }
        return null
    }

    private fun overlaps(first: DashboardItem, second: DashboardItem): Boolean {
        if (first.position.page != second.position.page) return false
        return first.position.column < second.position.column + second.span.columns &&
            first.position.column + first.span.columns > second.position.column &&
            first.position.row < second.position.row + second.span.rows &&
            first.position.row + first.span.rows > second.position.row
    }

    private data class ProbeItem(
        override val position: GridPosition,
        override val span: GridSpan,
    ) : DashboardItem {
        override val id: String = "__placement_probe__"

        override fun withPlacement(position: GridPosition, span: GridSpan): DashboardItem =
            copy(position = position, span = span)
    }
}
