package com.stonecode.simplehomescreen.widgets

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle

class WidgetController(
    private val context: Context,
    private val manager: AppWidgetManager,
    private val host: WidgetHost
) {

    fun allocateId(): Int = host.allocateAppWidgetId()

    fun deleteId(id: Int) {
        host.deleteAppWidgetId(id)
    }

    fun createHostView(id: Int): AppWidgetHostView {
        val info = manager.getAppWidgetInfo(id)
        return host.createView(context, id, info)
    }

    fun bindIfAllowed(id: Int, provider: ComponentName?, options: Bundle? = null): Boolean {
        if (provider == null) return false
        return manager.bindAppWidgetIdIfAllowed(id, provider, options)
    }

    fun requestBindIntent(id: Int, provider: ComponentName?, options: Bundle? = null) =
        Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider)
            options?.let { putExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS, it) }
        }

    fun getAppWidgetInfo(id: Int) = manager.getAppWidgetInfo(id)
}
