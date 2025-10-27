package com.kartlap.se.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kartlap.se.service.RecordService

class HeartbeatReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        RecordService.enqueueHeartbeat(context)
    }
}
