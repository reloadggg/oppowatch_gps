package com.kartlap.se.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

class BatteryOptimizationHelper(private val context: Context) {

    fun isIgnoringOptimizations(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun buildRequestIntent(): Intent {
        return Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    fun buildManufacturerIntent(): Intent? {
        val intent = when (Build.MANUFACTURER.lowercase()) {
            "oppo", "oneplus" -> Intent().apply {
                setClassName("com.coloros.oppoguardelf", "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity")
            }
            "xiaomi" -> Intent("miui.intent.action.POWER_HIDE_MODE_APP_LIST")
            "huawei" -> Intent().apply {
                setClassName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")
            }
            else -> null
        }
        return intent?.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
    }
}
