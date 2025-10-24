package com.stonecode.simplehomescreen.widgets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun WidgetTile(
    controller: WidgetController,
    appWidgetId: Int,
    modifier: Modifier = Modifier
) {
    val hostView = remember(appWidgetId) { controller.createHostView(appWidgetId) }
    AndroidView(factory = { hostView }, modifier = modifier)
}
