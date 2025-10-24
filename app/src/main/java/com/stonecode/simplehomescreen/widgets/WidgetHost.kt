package com.stonecode.simplehomescreen.widgets

import android.appwidget.AppWidgetHost
import android.content.Context

class WidgetHost(context: Context) : AppWidgetHost(context, HOST_ID) {
    fun start() = startListening()
    fun stop() = stopListening()

    companion object {
        const val HOST_ID = 0x5348 // "SH"
    }
}
