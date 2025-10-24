package com.stonecode.simplehomescreen.ui

import android.app.role.RoleManager
import android.appwidget.AppWidgetManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.stonecode.simplehomescreen.core.UsageRanker
import com.stonecode.simplehomescreen.widgets.WidgetController
import com.stonecode.simplehomescreen.widgets.WidgetHost

class HomeActivity : ComponentActivity() {

    private val viewModel: HomeViewModel by viewModels()

    private lateinit var widgetHost: WidgetHost
    private lateinit var widgetController: WidgetController
    private lateinit var usageRanker: UsageRanker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetHost = WidgetHost(this)
        widgetController = WidgetController(
            context = this,
            manager = AppWidgetManager.getInstance(this),
            host = widgetHost
        )
        usageRanker = UsageRanker(this)

        enableEdgeToEdge()
        maybeRequestHomeRole()

        setContent {
            HomeScreen(
                widgetController = widgetController,
                onRequestUsageAccess = { usageRanker.openUsageAccessSettings() },
                viewModel = viewModel
            )
        }
    }

    override fun onStart() {
        super.onStart()
        widgetHost.start()
    }

    override fun onStop() {
        super.onStop()
        widgetHost.stop()
    }

    override fun onResume() {
        super.onResume()
        viewModel.onUsageAccessChanged()
    }

    override fun onDestroy() {
        super.onDestroy()
        widgetHost.stop()
    }

    private fun maybeRequestHomeRole() {
        val roleManager = getSystemService(RoleManager::class.java) ?: return
        if (roleManager.isRoleAvailable(RoleManager.ROLE_HOME) &&
            !roleManager.isRoleHeld(RoleManager.ROLE_HOME)
        ) {
            startActivity(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME))
        }
    }
}
