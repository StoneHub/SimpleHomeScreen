package com.stonecode.simplehomescreen.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Immutable
data class AppTile(
    val key: String,
    val label: String,
    val icon: ImageBitmap,
    val launch: () -> Unit
)

@Composable
fun AppGrid(
    apps: List<AppTile>,
    columns: Int,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(apps, key = { it.key }) { tile ->
            AppCell(tile)
        }
    }
}

@Preview
@Composable
fun AppGridPreview() {
    val apps = listOf(
        AppTile("1", "App 1", ImageBitmap(1, 1), {}),
        AppTile("2", "App 2", ImageBitmap(1, 1), {}),
        AppTile("3", "App 3", ImageBitmap(1, 1), {}),
        AppTile("4", "App 4", ImageBitmap(1, 1), {}),
    )
    AppGrid(apps = apps, columns = 4)
}

@Composable
internal fun AppCell(app: AppTile) {
    Column(
        modifier = Modifier
            .size(96.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = app.launch)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            bitmap = app.icon,
            contentDescription = app.label,
            modifier = Modifier.size(54.dp)
        )
        Text(
            text = app.label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Preview
@Composable
internal fun AppCellPreview() {
    val app = AppTile(
        key = "preview",
        label = "Sample App",
        icon = ImageBitmap(54, 54),
        launch = {})
    AppCell(app = app)
}
