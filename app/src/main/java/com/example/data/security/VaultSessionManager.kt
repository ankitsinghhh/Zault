package com.example.data.security

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AutoLockTimeout(val displayName: String, val millis: Long) {
    IMMEDIATELY("Immediately", 0L),
    SECONDS_15("After 15 seconds", 15_000L),
    MINUTE_1("After 1 minute", 60_000L),
    MINUTE_5("After 5 minutes", 300_000L);

    companion object {
        fun fromOrdinal(ord: Int): AutoLockTimeout = values().getOrElse(ord) { IMMEDIATELY }
    }
}

class VaultSessionManager(
    private val storageManager: VaultStorageManager
) : Application.ActivityLifecycleCallbacks {

    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private var autoLockTimeout: AutoLockTimeout = AutoLockTimeout.IMMEDIATELY
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lockRunnable: Runnable? = null
    private var lastBackgroundTimestamp: Long = 0L
    private var activeActivityCount: Int = 0
    private val lockListeners = mutableListOf<() -> Unit>()

    init {
        // Vault starts strictly locked
        _isUnlocked.value = false
    }

    fun setAutoLockTimeout(timeout: AutoLockTimeout) {
        this.autoLockTimeout = timeout
    }

    fun unlock() {
        cancelLockTimer()
        _isUnlocked.value = true
    }

    fun addOnLockListener(listener: () -> Unit) {
        lockListeners += listener
    }

    fun lock() {
        cancelLockTimer()
        _isUnlocked.value = false
        // Wipe all temporary viewer decrypted files immediately on lock
        storageManager.cleanViewerTemp()
        lockListeners.forEach { listener -> runCatching { listener() } }
    }

    private fun cancelLockTimer() {
        lockRunnable?.let { mainHandler.removeCallbacks(it) }
        lockRunnable = null
    }

    override fun onActivityStarted(activity: Activity) {
        activeActivityCount++
        if (activeActivityCount == 1) {
            // Returned to foreground
            if (_isUnlocked.value && autoLockTimeout != AutoLockTimeout.IMMEDIATELY) {
                val elapsed = System.currentTimeMillis() - lastBackgroundTimestamp
                if (elapsed >= autoLockTimeout.millis) {
                    lock()
                } else {
                    cancelLockTimer()
                }
            }
        }
    }

    override fun onActivityStopped(activity: Activity) {
        activeActivityCount--
        if (activeActivityCount <= 0) {
            activeActivityCount = 0
            // App went to background
            if (!_isUnlocked.value) return

            lastBackgroundTimestamp = System.currentTimeMillis()
            when (autoLockTimeout) {
                AutoLockTimeout.IMMEDIATELY -> {
                    lock()
                }
                else -> {
                    cancelLockTimer()
                    val runnable = Runnable { lock() }
                    lockRunnable = runnable
                    mainHandler.postDelayed(runnable, autoLockTimeout.millis)
                }
            }
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
