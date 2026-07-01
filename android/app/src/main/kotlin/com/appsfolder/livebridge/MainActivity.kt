package com.appsfolder.livebridge

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager.MATCH_ALL
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutManagerCompat
import com.appsfolder.livebridge.liveupdate.AppPresentationOverridesCodec
import com.appsfolder.livebridge.liveupdate.AppPresentationOverridesLoader
import com.appsfolder.livebridge.liveupdate.ConverterPrefs
import com.appsfolder.livebridge.liveupdate.ConversionLogStore
import com.appsfolder.livebridge.liveupdate.KeepAliveForegroundService
import com.appsfolder.livebridge.liveupdate.LiveParserDictionaryLoader
import com.appsfolder.livebridge.liveupdate.LiveUpdateNotificationListenerService
import com.appsfolder.livebridge.liveupdate.LiveUpdateNotifier
import com.appsfolder.livebridge.liveupdate.LockStateManager
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.io.File
import java.util.Locale

class MainActivity : FlutterActivity() {
    private lateinit var prefs: ConverterPrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = ConverterPrefs(applicationContext)

        // Initialize lock state manager globally on app startup
        LockStateManager.init(applicationContext)
        LockStateManager.register()
        Log.i(TAG, "Initialized lock state manager")
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, METHOD_CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "getConverterEnabled" -> result.success(prefs.getConverterEnabled())
                "setConverterEnabled" -> {
                    prefs.setConverterEnabled(call.argument<Boolean>("value") ?: true)
                    result.success(true)
                }

                "getKeepAliveForegroundEnabled" -> result.success(prefs.getKeepAliveForegroundEnabled())
                "setKeepAliveForegroundEnabled" -> {
                    prefs.setKeepAliveForegroundEnabled(call.argument<Boolean>("value") ?: false)
                    result.success(true)
                }

                "getSpringTransitionsEnabled" -> result.success(prefs.getSpringTransitionsEnabled())
                "setSpringTransitionsEnabled" -> {
                    prefs.setSpringTransitionsEnabled(call.argument<Boolean>("value") ?: true)
                    result.success(true)
                }

                "getPreventMirrorDismissEnabled" -> result.success(prefs.getPreventMirrorDismissEnabled())
                "setPreventMirrorDismissEnabled" -> {
                    prefs.setPreventMirrorDismissEnabled(call.argument<Boolean>("value") ?: false)
                    result.success(true)
                }

                "getHideLockscreenContentEnabled" -> result.success(prefs.getHideLockscreenContentEnabled())
                "setHideLockscreenContentEnabled" -> {
                    prefs.setHideLockscreenContentEnabled(call.argument<Boolean>("value") ?: false)
                    LiveUpdateNotifier.ensureChannel(applicationContext)
                    // Force a refresh of all active Live Updates to apply new lock screen settings
                    LiveUpdateNotifier.refreshAllMirrorsAfterUnlock(applicationContext)
                    result.success(true)
                }

                "getHintsDisabled" -> result.success(prefs.getHintsDisabled())
                "setHintsDisabled" -> {
                    prefs.setHintsDisabled(call.argument<Boolean>("value") ?: false)
                    result.success(true)
                }

                "getConversionLogEnabled" -> result.success(prefs.getConversionLogEnabled())
                "setConversionLogEnabled" -> {
                    prefs.setConversionLogEnabled(call.argument<Boolean>("value") ?: false)
                    result.success(true)
                }

                "getBugReportAutoCopyEnabled" -> result.success(prefs.getBugReportAutoCopyEnabled())
                "setBugReportAutoCopyEnabled" -> {
                    prefs.setBugReportAutoCopyEnabled(call.argument<Boolean>("value") ?: false)
                    result.success(true)
                }

                "getAppLanguageTag" -> result.success(prefs.getAppLanguageTag())
                "setAppLanguageTag" -> {
                    prefs.setAppLanguageTag(call.argument<String>("value"))
                    result.success(true)
                }

                "getConversionLogMaxBytes" -> result.success(prefs.getConversionLogMaxBytes())
                "setConversionLogMaxBytes" -> {
                    prefs.setConversionLogMaxBytes(call.argument<Int>("value") ?: 1024 * 1024)
                    result.success(true)
                }

                "getNetworkSpeedEnabled" -> result.success(prefs.getNetworkSpeedEnabled())
                "setNetworkSpeedEnabled" -> {
                    prefs.setNetworkSpeedEnabled(call.argument<Boolean>("value") ?: false)
                    result.success(true)
                }

                "getNetworkSpeedMinThresholdBytesPerSecond" -> result.success(prefs.getNetworkSpeedMinThresholdBytesPerSecond())
                "setNetworkSpeedMinThresholdBytesPerSecond" -> {
                    prefs.setNetworkSpeedMinThresholdBytesPerSecond(call.argument<Long>("value") ?: 0L)
                    result.success(true)
                }

                "getSyncDndEnabled" -> result.success(prefs.getSyncDndEnabled())
                "setSyncDndEnabled" -> {
                    prefs.setSyncDndEnabled(call.argument<Boolean>("value") ?: true)
                    result.success(true)
                }

                "getUpdateChecksEnabled" -> result.success(prefs.getUpdateChecksEnabled())
                "setUpdateChecksEnabled" -> {
                    prefs.setUpdateChecksEnabled(call.argument<Boolean>("value") ?: true)
                    result.success(true)
                }

                "getUpdateCachedAvailable" -> result.success(prefs.getUpdateCachedAvailable())
                "getUpdateCachedLatestVersion" -> result.success(prefs.getUpdateCachedLatestVersion())

                "getOnlyWithProgress" -> result.success(prefs.getOnlyWithProgress())
                "setOnlyWithProgress" -> {
                    prefs.setOnlyWithProgress(call.argument<Boolean>("value") ?: true)
                    result.success(true)
                }

                "getTextProgressEnabled" -> result.success(prefs.getTextProgressEnabled())
                "setTextProgressEnabled" -> {
                    prefs.setTextProgressEnabled(call.argument<Boolean>("value") ?: true)
                    result.success(true)
                }

                "isNotificationListenerEnabled" -> result.success(isNotificationListenerEnabled())
                "isNotificationPermissionGranted" -> result.success(isNotificationPermissionGranted())
                "canPostPromotedNotifications" -> result.success(canPostPromotedNotifications())

                "requestNotificationPermission" -> requestNotificationPermission(result)
                "openNotificationListenerSettings" -> {
                    launchSettingsIntent(
                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    )
                    result.success(true)
                }

                "openNotificationSettings" -> {
                    launchSettingsIntent(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                            putExtra(Settings.EXTRA_CHANNEL_ID, "livebridge_promoted_updates")
                        }
                    )
                    result.success(true)
                }

                "getSmartStatusDetectionEnabled" -> result.success(prefs.getSmartStatusDetectionEnabled())
                "setSmartStatusDetectionEnabled" -> {
                    prefs.setSmartStatusDetectionEnabled(call.argument<Boolean>("value") ?: true)
                    result.success(true)
                }

                "getSmartTaxiEnabled" -> result.success(prefs.getSmartTaxiEnabled())
                "setSmartTaxiEnabled" -> {
                    prefs.setSmartTaxiEnabled(call.argument<Boolean>("value") ?: true)
                    result.success(true)
                }

                "getSmartDeliveryEnabled" -> result.success(prefs.getSmartDeliveryEnabled())
                "setSmartDeliveryEnabled" -> {
                    prefs.setSmartDeliveryEnabled(call.argument<Boolean>("value") ?: true)
                    result.success(true)
                }

                "getSmartCallsEnabled" -> result.success(prefs.getSmartCallsEnabled())
                "setSmartCallsEnabled" -> {
                    prefs.setSmartCallsEnabled(call.argument<Boolean>("value") ?: true)
                    result.success(true)
                }

                "getSmartMediaPlaybackEnabled" -> result.success(prefs.getSmartMediaPlaybackEnabled())
                "setSmartMediaPlaybackEnabled" -> {
                    prefs.setSmartMediaPlaybackEnabled(call.argument<Boolean>("value") ?: false)
                    result.success(true)
                }

                "getSmartMediaPlaybackShowOnLockScreen" -> result.success(prefs.getSmartMediaPlaybackShowOnLockScreen())
                "setSmartMediaPlaybackShowOnLockScreen" -> {
                    prefs.setSmartMediaPlaybackShowOnLockScreen(call.argument<Boolean>("value") ?: false)
                    result.success(true)
                }

                "getSmartMediaPlaybackUseSymbolsInPlayer" -> result.success(prefs.getSmartMediaPlaybackUseSymbolsInPlayer())
                "setSmartMediaPlaybackUseSymbolsInPlayer" -> {
                    prefs.setSmartMediaPlaybackUseSymbolsInPlayer(call.argument<Boolean>("value") ?: false)
                    result.success(true)
                }

                "getSmartNavigationEnabled" -> result.success(prefs.getSmartNavigationEnabled())
                "setSmartNavigationEnabled" -> {
                    prefs.setSmartNavigationEnabled(call.argument<Boolean>("value") ?: true)
                    result.success(true)
                }

                "getSmartWeatherEnabled" -> result.success(prefs.getSmartWeatherEnabled())
                "setSmartWeatherEnabled" -> {
                    prefs.setSmartWeatherEnabled(call.argument<Boolean>("value") ?: true)
                    result.success(true)
                }

                "getSmartExternalDevicesEnabled" -> result.success(prefs.getSmartExternalDevicesEnabled())
                "setSmartExternalDevicesEnabled" -> {
                    prefs.setSmartExternalDevicesEnabled(call.argument<Boolean>("value") ?: true)
                    result.success(true)
                }

                "getSmartExternalDevicesIgnoreDebugging" -> result.success(prefs.getSmartExternalDevicesIgnoreDebugging())
                "setSmartExternalDevicesIgnoreDebugging" -> {
                    prefs.setSmartExternalDevicesIgnoreDebugging(call.argument<Boolean>("value") ?: true)
                    result.success(true)
                }

                "getSmartVpnEnabled" -> result.success(prefs.getSmartVpnEnabled())
                "setSmartVpnEnabled" -> {
                    prefs.setSmartVpnEnabled(call.argument<Boolean>("value") ?: true)
                    result.success(true)
                }

                "getOtpDetectionEnabled" -> result.success(prefs.getOtpDetectionEnabled())
                "setOtpDetectionEnabled" -> {
                    prefs.setOtpDetectionEnabled(call.argument<Boolean>("value") ?: true)
                    result.success(true)
                }

                "getOtpAutoCopyEnabled" -> result.success(prefs.getOtpAutoCopyEnabled())
                "setOtpAutoCopyEnabled" -> {
                    prefs.setOtpAutoCopyEnabled(call.argument<Boolean>("value") ?: false)
                    result.success(true)
                }

                "getAospCuttingEnabled" -> result.success(prefs.getAospCuttingEnabled())
                "setAospCuttingEnabled" -> {
                    prefs.setAospCuttingEnabled(call.argument<Boolean>("value") ?: false)
                    result.success(true)
                }

                "getAospCuttingLength" -> result.success(prefs.getAospCuttingLength())
                "setAospCuttingLength" -> {
                    prefs.setAospCuttingLength(call.argument<Int>("value") ?: 20)
                    result.success(true)
                }

                "getAnimatedIslandEnabled" -> result.success(prefs.getAnimatedIslandEnabled())
                "setAnimatedIslandEnabled" -> {
                    prefs.setAnimatedIslandEnabled(call.argument<Boolean>("value") ?: false)
                    result.success(true)
                }

                "getAnimatedIslandUpdateFrequencyMs" -> result.success(prefs.getAnimatedIslandUpdateFrequencyMs())
                "setAnimatedIslandUpdateFrequencyMs" -> {
                    prefs.setAnimatedIslandUpdateFrequencyMs(call.argument<Int>("value") ?: 5000)
                    result.success(true)
                }

                "getHyperBridgeEnabled" -> result.success(prefs.getHyperBridgeEnabled())
                "setHyperBridgeEnabled" -> {
                    prefs.setHyperBridgeEnabled(call.argument<Boolean>("value") ?: false)
                    result.success(true)
                }

                "getOtpRemoveOriginalMessageEnabled" -> result.success(prefs.getOtpRemoveOriginalMessageEnabled())
                "setOtpRemoveOriginalMessageEnabled" -> {
                    prefs.setOtpRemoveOriginalMessageEnabled(call.argument<Boolean>("value") ?: false)
                    result.success(true)
                }

                "getSmartRemoveOriginalMessageEnabled" -> result.success(prefs.getSmartRemoveOriginalMessageEnabled())
                "setSmartRemoveOriginalMessageEnabled" -> {
                    prefs.setSmartRemoveOriginalMessageEnabled(call.argument<Boolean>("value") ?: false)
                    result.success(true)
                }

                "getNotificationDedupEnabled" -> result.success(prefs.getNotificationDedupEnabled())
                "setNotificationDedupEnabled" -> {
                    prefs.setNotificationDedupEnabled(call.argument<Boolean>("value") ?: true)
                    result.success(true)
                }

                "getAppVersionName" -> result.success(getAppVersionName())
                "getDeviceInfo" -> result.success(getDeviceInfo())
                "getSettingsSnapshot" -> result.success(prefs.getSettingsSnapshot())
                "applySettingsSnapshot" -> {
                    val snapshot = call.argument<String>("snapshot") ?: "{}"
                    prefs.applySettingsSnapshot(snapshot)
                    result.success(true)
                }

                "exportConversionLog" -> {
                    val exported = ConversionLogStore.exportAndClearLog(applicationContext)
                    result.success(exported)
                }

                "setKeepAliveForegroundService" -> {
                    val enabled = call.argument<Boolean>("enabled") ?: false
                    if (enabled) {
                        KeepAliveForegroundService.start(applicationContext)
                    } else {
                        KeepAliveForegroundService.stop(applicationContext)
                    }
                    result.success(true)
                }

                else -> result.notImplemented()
            }
        }
    }

    override fun onDestroy() {
        // Cleanup lock state manager on app destroy
        LockStateManager.shutdown()
        Log.i(TAG, "Shutdown lock state manager")
        super.onDestroy()
    }

    private fun getAppVersionName(): String {
        return try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        } catch (e: PackageManager.NameNotFoundException) {
            ""
        }
    }

    private fun getDeviceInfo(): Map<String, Any?> {
        val isPixel = Build.FINGERPRINT.contains("google") || Build.MANUFACTURER.equals("Google")
        val isSamsung = Build.MANUFACTURER.equals("samsung", true) || Build.BRAND.equals("samsung", true)
        val isAospDevice = !isPixel && !isSamsung && !isLikelyChineseDevice()

        return mapOf(
            "label" to Build.MODEL,
            "manufacturer" to Build.MANUFACTURER,
            "brand" to Build.BRAND,
            "market_name" to "",
            "model" to Build.MODEL,
            "raw_model" to Build.DEVICE,
            "product" to Build.PRODUCT,
            "display" to Build.DISPLAY,
            "fingerprint" to Build.FINGERPRINT,
            "is_pixel" to isPixel,
            "is_samsung" to isSamsung,
            "is_aosp_device" to isAospDevice,
            "should_hide_live_updates_promotion" to (Build.VERSION.SDK_INT < Build.VERSION_CODES.S),
            "build_version" to Build.VERSION.SDK_INT,
            "build_release" to Build.VERSION.RELEASE,
            "build_display" to (Build.DISPLAY ?: "")
        )
    }

    private fun isRussianLocale(): Boolean {
        val locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            resources.configuration.locales.get(0)
        } else {
            @Suppress("DEPRECATION")
            resources.configuration.locale
        }
        return locale?.language?.startsWith("ru", ignoreCase = true) == true
    }

    private fun isLikelyChineseDevice(): Boolean {
        val manufacturer = (Build.MANUFACTURER ?: "").lowercase(Locale.ROOT)
        val brand = (Build.BRAND ?: "").lowercase(Locale.ROOT)
        val fingerprint = (Build.FINGERPRINT ?: "").lowercase(Locale.ROOT)
        val display = (Build.DISPLAY ?: "").lowercase(Locale.ROOT)
        val product = (Build.PRODUCT ?: "").lowercase(Locale.ROOT)
        val combined = "$manufacturer $brand $fingerprint $display $product"

        if (CHINESE_DEVICE_MARKERS.any(combined::contains)) {
            return true
        }
        return CHINESE_ROM_MARKERS.any(combined::contains)
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
            ?: return false
        val service = ComponentName(this, LiveUpdateNotificationListenerService::class.java)
        return enabled.split(":")
            .mapNotNull(ComponentName::unflattenFromString)
            .any { it == service }
    }

    private fun requestNotificationListenerRebind(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return false
        }
        if (!isNotificationListenerEnabled()) {
            return false
        }

        return try {
            NotificationListenerService.requestRebind(
                ComponentName(this, LiveUpdateNotificationListenerService::class.java)
            )
            true
        } catch (error: Throwable) {
            Log.e(TAG, "Failed to request listener rebind", error)
            false
        }
    }

    private fun isNotificationPermissionGranted(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true
        }

        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestNotificationPermission(res: MethodChannel.Result) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            res.success(true)
            return
        }

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            res.success(true)
            return
        }

        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            REQUEST_POST_NOTIFICATIONS
        )
        res.success(true)
    }

    private fun canPostPromotedNotifications(): Boolean {
        if (!isNotificationListenerEnabled()) {
            return false
        }
        if (!isNotificationPermissionGranted()) {
            return false
        }
        return NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()
    }

    private fun launchSettingsIntent(intent: Intent): Boolean {
        return try {
            if (intent.resolveActivity(packageManager) == null) {
                false
            } else {
                startActivity(intent)
                true
            }
        } catch (_: ActivityNotFoundException) {
            false
        } catch (error: SecurityException) {
            Log.e(TAG, "Unable to open settings with intent: ${intent.action}", error)
            false
        }
    }

    companion object {
        private const val METHOD_CHANNEL = "livebridge/platform"
        private const val REQUEST_POST_NOTIFICATIONS = 2406
        private const val TAG = "MainActivity"
        private const val INSTALLED_APPS_CACHE_TTL_MS = 10 * 60 * 1000L
        private const val MAX_ICON_CACHE_SIZE = 512
        private const val UPDATE_CHANNEL_ID = "livebridge_update_checks"
        private const val UPDATE_CHANNEL_NAME = "LiveBridge Updates"
        private const val UPDATE_NOTIFICATION_ID = 32001

        private val CHINESE_DEVICE_MARKERS = setOf(
            "xiaomi", "redmi", "poco", "realme", "oppo", "vivo", "oneplus",
            "honor", "huawei", "asus", "zenfone", "lenovo", "moto", "nokia"
        )
        private val CHINESE_ROM_MARKERS = setOf(
            "miui", "redmi", "funtouchOS", "coloros", "originos", "oxygenos",
            "magic UI", "emui", "harmony", "zipao", "one ui"
        )
    }
}
