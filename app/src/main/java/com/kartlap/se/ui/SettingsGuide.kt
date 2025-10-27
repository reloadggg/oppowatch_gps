package com.kartlap.se.ui

import android.app.Activity
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.kartlap.se.util.BatteryOptimizationHelper

class SettingsGuide(private val activity: Activity) {

    private val helper = BatteryOptimizationHelper(activity)

    fun ensureBatteryOptimizationExemption() {
        if (!helper.isIgnoringOptimizations()) {
            val manufacturerIntent = helper.buildManufacturerIntent()
            if (manufacturerIntent != null && manufacturerIntent.resolveActivity(activity.packageManager) != null) {
                activity.startActivity(manufacturerIntent)
            } else {
                activity.startActivity(helper.buildRequestIntent())
            }
        }
    }

    fun openSystemSettings() {
        val intent = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        val uri = android.net.Uri.parse("package:${activity.packageName}")
        activity.startActivity(android.content.Intent(intent, uri))
    }
}
