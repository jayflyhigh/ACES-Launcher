package app.olauncher.helper

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.pm.LauncherApps
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import app.olauncher.R
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import app.olauncher.data.WidgetStore

class PinItemActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        window.setBackgroundDrawable(null)

        val launcherApps = getSystemService(LauncherApps::class.java)
        val pinItemRequest = launcherApps.getPinItemRequest(intent)

        when (pinItemRequest != null) {
            true -> handleRequestType(pinItemRequest)
            false -> showToast(R.string.invalid_pin_request)
        }

        finish()
    }

    private fun handleRequestType(pinItemRequest: LauncherApps.PinItemRequest) {
        when (pinItemRequest.requestType) {
            LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT ->
                handleShortcutRequest(pinItemRequest)

            LauncherApps.PinItemRequest.REQUEST_TYPE_APPWIDGET ->
                handleWidgetRequest(pinItemRequest)

            else -> showToast(R.string.pin_action_not_supported)
        }
    }

    private fun handleWidgetRequest(pinItemRequest: LauncherApps.PinItemRequest) {
        val host = AppWidgetHost(applicationContext, Constants.APPWIDGET_HOST_ID)
        val widgetId = host.allocateAppWidgetId()
        val accepted = runCatching {
            pinItemRequest.accept(bundleOf(AppWidgetManager.EXTRA_APPWIDGET_ID to widgetId))
        }.getOrDefault(false)
        if (accepted) WidgetStore.add(Prefs(this), widgetId)
        else {
            host.deleteAppWidgetId(widgetId)
            showToast(R.string.widget_add_failed)
        }
    }

    private fun handleShortcutRequest(pinItemRequest: LauncherApps.PinItemRequest) {
        val shortcutInfo = pinItemRequest.shortcutInfo
        if (shortcutInfo != null) {
            val success = runCatching { pinItemRequest.accept() }.getOrDefault(false)
            val message = when (success) {
                true -> R.string.shortcut_pinned
                false -> R.string.shortcut_pin_failed
            }
            showToast(message)
        } else {
            showToast(R.string.invalid_shortcut)
        }
    }
}
