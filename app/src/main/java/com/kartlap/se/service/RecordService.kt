package com.kartlap.se.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Binder
import android.os.Build
import android.os.Looper
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.kartlap.se.R
import com.kartlap.se.export.ExportManager
import com.kartlap.se.export.ExportManager.ExportFormat
import com.kartlap.se.persistence.TrackWriter
import com.kartlap.se.sensor.MotionSensorManager
import com.kartlap.se.session.TrackPoint
import com.kartlap.se.session.TrackSessionManager
import com.kartlap.se.ui.MainActivity
import com.kartlap.se.util.AlarmManagerHelper
import com.kartlap.se.util.WakeLockManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

class RecordService : Service(), LocationListener {

    private val binder = RecordBinder()
    private val job = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Default + job)

    private lateinit var locationManager: LocationManager
    private lateinit var trackWriter: TrackWriter
    private lateinit var sessionManager: TrackSessionManager
    private lateinit var wakeLockManager: WakeLockManager
    private lateinit var alarmHelper: AlarmManagerHelper
    private lateinit var exportManager: ExportManager
    private lateinit var motionManager: MotionSensorManager

    private val trackPoints = CopyOnWriteArrayList<TrackPoint>()
    private val statusFlow = MutableStateFlow(ServiceStatus.IDLE)
    private val elapsedFlow = MutableStateFlow(0L)
    private val notificationUpdates = MutableSharedFlow<Notification>(replay = 1)

    private var sessionId: String? = null
    private var startTimestamp: Long = 0L
    private var highFrequencyUpdates: Boolean = true

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        trackWriter = TrackWriter(this)
        sessionManager = TrackSessionManager(this)
        wakeLockManager = WakeLockManager(this)
        alarmHelper = AlarmManagerHelper(this)
        exportManager = ExportManager(this, sessionManager)
        motionManager = MotionSensorManager(this)
        createNotificationChannel()
        observeMotion()
        serviceScope.launch {
            notificationUpdates.collect { notification ->
                NotificationManagerCompat.from(this@RecordService).notify(NOTIFICATION_ID, notification)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording()
            ACTION_STOP -> stopRecording()
            ACTION_HEARTBEAT -> ensureHeartbeat()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        stopTracking()
        if (statusFlow.value == ServiceStatus.RECORDING) {
            sessionId?.let { sessionManager.markIncomplete(it) }
            trackWriter.flush()
        }
        job.cancel()
        motionManager.stop()
    }

    private fun startRecording() {
        if (statusFlow.value == ServiceStatus.RECORDING) return
        val session = sessionManager.resumeIncomplete() ?: sessionManager.startNewSession()
        sessionId = session
        trackWriter.startNew(session)
        trackPoints.clear()
        trackPoints.addAll(trackWriter.persisted())
        startTimestamp = System.currentTimeMillis()
        statusFlow.value = ServiceStatus.RECORDING
        wakeLockManager.acquire(serviceScope)
        motionManager.start()
        highFrequencyUpdates = true
        requestLocationUpdates(highFrequency = true)
        alarmHelper.scheduleHeartbeat()
        startForeground(NOTIFICATION_ID, buildNotification(ServiceStatus.RECORDING))
        serviceScope.launch { updateElapsedLoop() }
    }

    private fun stopRecording() {
        if (statusFlow.value != ServiceStatus.RECORDING) return
        statusFlow.value = ServiceStatus.STOPPED
        stopTracking()
        sessionId?.let { sessionManager.markIncomplete(it) }
        trackWriter.flush()
        notificationUpdates.tryEmit(buildNotification(ServiceStatus.STOPPED))
        stopForeground(false)
    }

    private fun stopTracking() {
        locationManager.removeUpdates(this)
        wakeLockManager.release()
        alarmHelper.cancelHeartbeat()
        highFrequencyUpdates = false
    }

    private fun ensureHeartbeat() {
        if (statusFlow.value == ServiceStatus.RECORDING) {
            alarmHelper.scheduleHeartbeat()
        } else if (statusFlow.value == ServiceStatus.IDLE) {
            startRecording()
        }
    }

    private fun requestLocationUpdates(highFrequency: Boolean) {
        val interval = if (highFrequency) 1000L else TimeUnit.SECONDS.toMillis(5)
        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                interval,
                0f,
                this,
                android.os.Looper.getMainLooper()
            )
        } catch (_: SecurityException) {
            statusFlow.value = ServiceStatus.ERROR
        }
    }

    override fun onLocationChanged(location: Location) {
        val point = TrackPoint(
            timestamp = location.time,
            latitude = location.latitude,
            longitude = location.longitude,
            altitude = location.altitude,
            speed = location.speed,
            accuracy = location.accuracy,
            bearing = location.bearing
        )
        trackPoints += point
        trackWriter.append(point)
        notificationUpdates.tryEmit(buildNotification(statusFlow.value))
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
    override fun onProviderEnabled(provider: String) = Unit
    override fun onProviderDisabled(provider: String) = Unit
    override fun onLocationChanged(locations: MutableList<Location>) {
        locations.forEach { onLocationChanged(it) }
    }

    private fun observeMotion() {
        serviceScope.launch {
            motionManager.acceleration.collect { magnitude ->
                if (statusFlow.value == ServiceStatus.RECORDING) {
                    val moving = magnitude > 1.2f
                    if (moving != highFrequencyUpdates) {
                        highFrequencyUpdates = moving
                        locationManager.removeUpdates(this@RecordService)
                        requestLocationUpdates(highFrequency = moving)
                    }
                }
            }
        }
    }

    private suspend fun updateElapsedLoop() {
        while (statusFlow.value == ServiceStatus.RECORDING) {
            val elapsed = System.currentTimeMillis() - startTimestamp
            elapsedFlow.value = elapsed
            val notification = buildNotification(ServiceStatus.RECORDING)
            notificationUpdates.emit(notification)
            kotlinx.coroutines.delay(1000)
        }
    }

    private fun buildNotification(status: ServiceStatus): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_WORKOUT)
            .setOnlyAlertOnce(true)
            .setTicker(getString(R.string.notification_ticker))
            .setContentIntent(mainIntent())

        val elapsedSeconds = TimeUnit.MILLISECONDS.toSeconds(elapsedFlow.value)
        val minutes = elapsedSeconds / 60
        val seconds = elapsedSeconds % 60
        val statusText = when (status) {
            ServiceStatus.RECORDING -> getString(R.string.status_recording)
            ServiceStatus.STOPPED -> getString(R.string.action_save)
            ServiceStatus.ERROR -> "错误"
            ServiceStatus.IDLE -> getString(R.string.status_idle)
        }
        builder.setContentText("$statusText · %02d:%02d".format(minutes, seconds))

        val stopIntent = Intent(this, RecordService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(
            R.drawable.ic_notification,
            getString(R.string.notification_action_stop),
            stopPendingIntent
        )
        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.notification_channel_description)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun mainIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    inner class RecordBinder : Binder() {
        fun status(): StateFlow<ServiceStatus> = statusFlow.asStateFlow()
        fun elapsed(): StateFlow<Long> = elapsedFlow.asStateFlow()
        fun notificationFlow(): MutableSharedFlow<Notification> = notificationUpdates
        fun export(formats: Set<ExportFormat>) {
            val session = sessionId ?: return
            val snapshot = trackPoints.toList()
            trackWriter.complete(session)
            serviceScope.launch { exportManager.export(session, snapshot, formats) }
        }
        fun currentPoints(): List<TrackPoint> = trackPoints.toList()
        fun stopRecording() = this@RecordService.stopRecording()
        fun startRecording() = this@RecordService.startRecording()
    }

    enum class ServiceStatus {
        IDLE, RECORDING, STOPPED, ERROR
    }

    companion object {
        private const val CHANNEL_ID = "kartlap_record_channel"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_START = "com.kartlap.se.action.START"
        private const val ACTION_STOP = "com.kartlap.se.action.STOP"
        private const val ACTION_HEARTBEAT = "com.kartlap.se.action.HEARTBEAT"

        fun start(context: Context) {
            val intent = Intent(context, RecordService::class.java).apply { action = ACTION_START }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, RecordService::class.java).apply { action = ACTION_STOP }
            context.startService(intent)
        }

        fun enqueueHeartbeat(context: Context) {
            val intent = Intent(context, RecordService::class.java).apply { action = ACTION_HEARTBEAT }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
