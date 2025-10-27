package com.kartlap.se.util

import android.content.Context
import android.os.PowerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class WakeLockManager(private val context: Context) {

    private var wakeLock: PowerManager.WakeLock? = null
    private var refresher: Job? = null

    fun acquire(scope: CoroutineScope) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (wakeLock?.isHeld != true) {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "KartLap:Track").apply {
                setReferenceCounted(false)
                acquire(TimeUnit.MINUTES.toMillis(10))
            }
        }
        refresher?.cancel()
        refresher = scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(TimeUnit.MINUTES.toMillis(5))
                wakeLock?.let {
                    if (!it.isHeld) {
                        it.acquire(TimeUnit.MINUTES.toMillis(10))
                    } else {
                        it.acquire(TimeUnit.MINUTES.toMillis(10))
                    }
                }
            }
        }
    }

    fun release() {
        refresher?.cancel()
        refresher = null
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
    }
}
