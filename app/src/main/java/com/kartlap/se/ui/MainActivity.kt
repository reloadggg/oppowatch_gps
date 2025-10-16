package com.kartlap.se.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.kartlap.se.R
import com.kartlap.se.databinding.ActivityMainBinding
import com.kartlap.se.export.ExportManager.ExportFormat
import com.kartlap.se.service.RecordService
import com.kartlap.se.util.BatteryOptimizationHelper
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var serviceBinder: RecordService.RecordBinder? = null
    private var bound = false
    private lateinit var batteryHelper: BatteryOptimizationHelper

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result.values.all { it }
        if (granted) {
            RecordService.start(this)
        } else {
            Toast.makeText(this, R.string.request_permissions, Toast.LENGTH_SHORT).show()
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            serviceBinder = service as? RecordService.RecordBinder
            bound = true
            observeService()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bound = false
            serviceBinder = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        batteryHelper = BatteryOptimizationHelper(this)
        binding.actionButton.setOnClickListener { toggleRecording() }
        binding.saveButton.setOnClickListener { exportSession() }
        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onStart() {
        super.onStart()
        maybePromptBatteryOptimization()
        Intent(this, RecordService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        if (bound) {
            unbindService(connection)
            bound = false
        }
    }

    private fun toggleRecording() {
        if (!hasLocationPermission()) {
            requestPermissions()
            return
        }
        val binder = serviceBinder ?: return
        when (binder.status().value) {
            RecordService.ServiceStatus.RECORDING -> binder.stopRecording()
            RecordService.ServiceStatus.IDLE, RecordService.ServiceStatus.STOPPED, RecordService.ServiceStatus.ERROR -> binder.startRecording()
        }
    }

    private fun exportSession() {
        val binder = serviceBinder ?: return
        binder.export(setOf(ExportFormat.GPX, ExportFormat.CSV))
        Toast.makeText(this, R.string.action_save, Toast.LENGTH_SHORT).show()
        binding.saveButton.isEnabled = false
    }

    private fun observeService() {
        val binder = serviceBinder ?: return
        lifecycleScope.launch {
            binder.status().collectLatest { status ->
                when (status) {
                    RecordService.ServiceStatus.RECORDING -> {
                        binding.actionButton.text = getString(R.string.stop_recording)
                        binding.statusText.text = getString(R.string.status_recording)
                        binding.saveButton.isEnabled = false
                    }
                    RecordService.ServiceStatus.STOPPED -> {
                        binding.actionButton.text = getString(R.string.start_recording)
                        binding.statusText.text = getString(R.string.action_save)
                        binding.saveButton.isEnabled = true
                    }
                    RecordService.ServiceStatus.ERROR -> {
                        binding.actionButton.text = getString(R.string.start_recording)
                        binding.statusText.text = getString(R.string.status_idle)
                        binding.saveButton.isEnabled = false
                    }
                    RecordService.ServiceStatus.IDLE -> {
                        binding.actionButton.text = getString(R.string.start_recording)
                        binding.statusText.text = getString(R.string.status_idle)
                        binding.saveButton.isEnabled = false
                    }
                }
            }
        }
        lifecycleScope.launch {
            binder.elapsed().collectLatest { elapsed ->
                if (binder.status().value == RecordService.ServiceStatus.RECORDING) {
                    val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsed)
                    val seconds = TimeUnit.MILLISECONDS.toSeconds(elapsed) % 60
                    binding.statusText.text = getString(
                        R.string.main_stats_format,
                        "%02d:%02d".format(minutes, seconds),
                        0,
                        "--"
                    )
                }
            }
        }
        lifecycleScope.launch {
            while (bound) {
                binding.trackView.update(binder.currentPoints())
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val background = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else true
        return fine && background
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            permissions += Manifest.permission.ACCESS_BACKGROUND_LOCATION
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun maybePromptBatteryOptimization() {
        if (!batteryHelper.isIgnoringOptimizations()) {
            val view = layoutInflater.inflate(R.layout.battery_prompt, null)
            AlertDialog.Builder(this)
                .setView(view)
                .setPositiveButton(R.string.battery_prompt_button) { _, _ ->
                    val manufacturerIntent = batteryHelper.buildManufacturerIntent()
                    if (manufacturerIntent != null && manufacturerIntent.resolveActivity(packageManager) != null) {
                        startActivity(manufacturerIntent)
                    } else {
                        startActivity(batteryHelper.buildRequestIntent())
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }
}
