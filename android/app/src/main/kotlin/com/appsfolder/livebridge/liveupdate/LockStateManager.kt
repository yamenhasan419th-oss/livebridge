package com.appsfolder.livebridge.liveupdate

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService

/**
 * Manages device lock state detection and automatic Live Update refresh on unlock.
 *
 * This manager detects when the device transitions from locked to unlocked state
 * and triggers a refresh of all active Live Updates to restore real notification
 * content (instead of redacted/hidden content).
 *
 * Architecture:
 * - Registers a BroadcastReceiver for ACTION_USER_PRESENT (device unlock)
 * - Tracks lock state changes using KeyguardManager
 * - Coordinates with LiveUpdateNotifier to refresh active notifications
 * - Handles proper cleanup to prevent memory leaks
 */
object LockStateManager {
    private const val TAG = "LockStateManager"
    private const val REFRESH_DELAY_MS = 100L

    private var context: Context? = null
    private var receiver: LockStateReceiver? = null
    private var keyguardManager: KeyguardManager? = null
    private var mainHandler: Handler? = null
    private val stateLock = Any()
    private var isLocked = false
    private var isRegistered = false
    private var refreshScheduled = false
    private val refreshRunnable = Runnable {
        refreshScheduled = false
        performRefresh()
    }

    /**
     * Initialize the lock state manager.
     * Must be called once on application startup.
     */
    fun init(appContext: Context) {
        synchronized(stateLock) {
            if (context != null) {
                Log.w(TAG, "Already initialized")
                return
            }
            context = appContext
            keyguardManager = appContext.getSystemService()
            mainHandler = Handler(Looper.getMainLooper())
            updateLockState()
            Log.i(TAG, "Initialized with lock state: locked=$isLocked")
        }
    }

    /**
     * Register the lock state receiver to listen for unlock events.
     * Safe to call multiple times.
     */
    fun register() {
        synchronized(stateLock) {
            val ctx = context ?: run {
                Log.w(TAG, "Not initialized, skipping register")
                return
            }
            if (isRegistered) {
                return
            }

            receiver = LockStateReceiver()
            val intentFilter = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                IntentFilter(Intent.ACTION_USER_PRESENT).apply {
                    addAction(Intent.ACTION_SCREEN_OFF)
                    addAction(Intent.ACTION_SCREEN_ON)
                }
            } else {
                IntentFilter().apply {
                    addAction(Intent.ACTION_USER_PRESENT)
                    addAction(Intent.ACTION_SCREEN_OFF)
                    addAction(Intent.ACTION_SCREEN_ON)
                }
            }

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ContextCompat.registerReceiver(
                        ctx,
                        receiver,
                        intentFilter,
                        ContextCompat.RECEIVER_EXPORTED
                    )
                } else {
                    @Suppress("DEPRECATION")
                    ctx.registerReceiver(receiver, intentFilter)
                }
                isRegistered = true
                Log.i(TAG, "Receiver registered")
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to register receiver", e)
                receiver = null
            }
        }
    }

    /**
     * Unregister the lock state receiver.
     * Safe to call multiple times.
     */
    fun unregister() {
        synchronized(stateLock) {
            val ctx = context ?: return
            val recv = receiver ?: return

            if (!isRegistered) {
                return
            }

            try {
                ctx.unregisterReceiver(recv)
                isRegistered = false
                Log.i(TAG, "Receiver unregistered")
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to unregister receiver", e)
            }
            receiver = null
        }
    }

    /**
     * Clear all state and resources.
     * Call when shutting down the application.
     */
    fun shutdown() {
        synchronized(stateLock) {
            unregister()
            mainHandler?.removeCallbacksAndMessages(null)
            context = null
            keyguardManager = null
            mainHandler = null
            isLocked = false
            refreshScheduled = false
            Log.i(TAG, "Shutdown complete")
        }
    }

    /**
     * Get current lock state (thread-safe).
     */
    fun isDeviceLocked(): Boolean = synchronized(stateLock) {
        isLocked
    }

    /**
     * Internal: Update lock state from KeyguardManager
     */
    private fun updateLockState() {
        val km = keyguardManager ?: return
        val wasLocked = isLocked
        isLocked = try {
            km.isDeviceLocked
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to check lock state", e)
            wasLocked
        }

        if (!wasLocked && isLocked) {
            Log.d(TAG, "Device locked")
        } else if (wasLocked && !isLocked) {
            Log.d(TAG, "Device unlocked - scheduling refresh")
            scheduleRefresh()
        }
    }

    /**
     * Internal: Schedule a refresh of active notifications with a small delay.
     * This allows lock state to stabilize before rebuilding notifications.
     */
    private fun scheduleRefresh() {
        synchronized(stateLock) {
            val handler = mainHandler ?: return
            if (refreshScheduled) {
                return
            }
            refreshScheduled = true
            handler.postDelayed(refreshRunnable, REFRESH_DELAY_MS)
        }
    }

    /**
     * Internal: Perform the actual refresh of all active Live Updates.
     * This rebuilds notifications with real content instead of redacted content.
     */
    private fun performRefresh() {
        synchronized(stateLock) {
            val ctx = context ?: return
            if (isLocked) {
                Log.d(TAG, "Device locked again, skipping refresh")
                return
            }
        }

        Log.i(TAG, "Refreshing all active Live Updates")
        LiveUpdateNotifier.refreshAllMirrorsAfterUnlock(context!!)
    }

    /**
     * BroadcastReceiver for lock state events.
     * Only registered when needed.
     */
    private class LockStateReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return

            when (intent.action) {
                Intent.ACTION_USER_PRESENT -> {
                    // Device was unlocked
                    Log.d(TAG, "Received ACTION_USER_PRESENT")
                    synchronized(stateLock) {
                        updateLockState()
                    }
                }
                Intent.ACTION_SCREEN_OFF -> {
                    // Screen turned off (might lock)
                    Log.d(TAG, "Received ACTION_SCREEN_OFF")
                    synchronized(stateLock) {
                        updateLockState()
                    }
                }
                Intent.ACTION_SCREEN_ON -> {
                    // Screen turned on (verify lock state)
                    Log.d(TAG, "Received ACTION_SCREEN_ON")
                    synchronized(stateLock) {
                        updateLockState()
                    }
                }
            }
        }
    }
}
