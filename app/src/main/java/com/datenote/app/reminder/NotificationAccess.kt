package com.datenote.app.reminder

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import android.Manifest

data class NotificationStatus(
    val permissionGranted: Boolean,
    val appNotificationsEnabled: Boolean,
    val channelEnabled: Boolean,
) {
    val canPost: Boolean
        get() = permissionGranted && appNotificationsEnabled && channelEnabled
}

data class ReminderReliability(
    val notificationStatus: NotificationStatus,
    val batteryOptimizationIgnored: Boolean,
)

/** Reads the real system state. It deliberately does not use a cached Boolean. */
object NotificationAccess {
    fun status(context: Context): NotificationStatus {
        ReminderNotifications.createChannel(context)
        val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        val appNotificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        val channelEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(android.app.NotificationManager::class.java)
                ?.getNotificationChannel(ReminderNotifications.CHANNEL_ID)
                ?.let { it.importance != android.app.NotificationManager.IMPORTANCE_NONE }
                ?: false
        } else {
            true
        }
        return NotificationStatus(permissionGranted, appNotificationsEnabled, channelEnabled)
    }

    fun reliability(context: Context): ReminderReliability {
        val powerManager = context.getSystemService(PowerManager::class.java)
        val batteryOptimizationIgnored = Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            runCatching { powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true }.getOrDefault(false)
        return ReminderReliability(status(context), batteryOptimizationIgnored)
    }
}

/** All system-setting navigation is kept here so Compose screens do not know vendor component names. */
object ReminderSettings {
    fun openNotificationSettings(context: Context): Boolean {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        } else {
            appDetailsIntent(context)
        }
        return startSafely(context, intent) || openAppDetails(context)
    }

    fun openAppDetails(context: Context): Boolean = startSafely(context, appDetailsIntent(context))

    fun openBatterySettings(context: Context): Boolean {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        return startSafely(context, intent) || openAppDetails(context)
    }

    fun vendorName(context: Context): String = when (Build.MANUFACTURER.lowercase()) {
        "xiaomi" -> "小米／红米"
        "huawei" -> "华为"
        "honor" -> "荣耀"
        "oppo" -> "OPPO"
        "vivo" -> "vivo"
        "meizu" -> "魅族"
        else -> "当前手机"
    }

    fun vendorGuide(context: Context): String = when (Build.MANUFACTURER.lowercase()) {
        "xiaomi" -> "设置 → 应用设置 → 应用管理 → 维来可期，开启自启动，并将电池策略设为不限制。"
        "huawei" -> "设置 → 应用和服务 → 应用启动管理 → 维来可期，允许自启动、后台活动和关联启动。"
        "honor" -> "设置 → 应用 → 应用启动管理 → 维来可期，允许自启动和后台活动，并关闭省电限制。"
        "oppo" -> "设置 → 应用 → 自启动 → 维来可期；再在电池设置中允许后台运行。"
        "vivo" -> "设置 → 应用与权限 → 自启动管理 → 维来可期；再将电池后台耗电设为不限制。"
        "meizu" -> "设置 → 应用管理 → 维来可期 → 权限管理，开启自启动并允许后台运行。"
        else -> "请在系统设置中打开维来可期的通知、后台运行和自启动，并将电池使用设为“不受限制”。"
    }

    private fun appDetailsIntent(context: Context): Intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:${context.packageName}"),
    )

    private fun startSafely(context: Context, intent: Intent): Boolean {
        if (intent.resolveActivity(context.packageManager) == null) return false
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }
}
