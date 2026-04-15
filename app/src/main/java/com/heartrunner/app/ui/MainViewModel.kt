package com.heartrunner.app.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.location.Location
import android.net.Uri
import android.os.IBinder
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.heartrunner.app.ble.BleHeartRateManager
import com.heartrunner.app.ble.ConnectionState
import com.heartrunner.app.ble.ScannedDevice
import com.heartrunner.app.data.WorkoutPoint
import com.heartrunner.app.export.ExportFormat
import com.heartrunner.app.export.WorkoutExporter
import com.heartrunner.app.location.LocationTracker
import com.heartrunner.app.service.HeartRateService
import com.heartrunner.app.tts.HeartRateAlertLogic
import com.heartrunner.app.tts.TtsAlertManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val bleManager = BleHeartRateManager(application)
    private val ttsManager = TtsAlertManager(application)
    private val alertLogic = HeartRateAlertLogic()
    private val locationTracker = LocationTracker(application)

    private val _alertConfig = MutableStateFlow(HeartRateAlertLogic.AlertConfig())
    val alertConfig: StateFlow<HeartRateAlertLogic.AlertConfig> = _alertConfig.asStateFlow()

    // 心率历史记录（最近 60 个数据点用于图表）
    private val _heartRateHistory = MutableStateFlow<List<Int>>(emptyList())
    val heartRateHistory: StateFlow<List<Int>> = _heartRateHistory.asStateFlow()

    // 运行时长（秒）
    private val _runDurationSec = MutableStateFlow(0L)
    val runDurationSec: StateFlow<Long> = _runDurationSec.asStateFlow()

    // 最近播报信息
    private val _lastAlert = MutableStateFlow("")
    val lastAlert: StateFlow<String> = _lastAlert.asStateFlow()

    // TTS 开关
    private val _ttsEnabled = MutableStateFlow(true)
    val ttsEnabled: StateFlow<Boolean> = _ttsEnabled.asStateFlow()

    // 运动记录状态
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _currentSpeed = MutableStateFlow(0f)
    val currentSpeed: StateFlow<Float> = _currentSpeed.asStateFlow()

    private val _totalDistance = MutableStateFlow(0.0)
    val totalDistance: StateFlow<Double> = _totalDistance.asStateFlow()

    private val _hasWorkoutToExport = MutableStateFlow(false)
    val hasWorkoutToExport: StateFlow<Boolean> = _hasWorkoutToExport.asStateFlow()

    // 平均心率
    private val _averageHeartRate = MutableStateFlow(0)
    val averageHeartRate: StateFlow<Int> = _averageHeartRate.asStateFlow()

    // 平均配速 (min/km)
    private val _averagePace = MutableStateFlow("")
    val averagePace: StateFlow<String> = _averagePace.asStateFlow()

    // GPS 轨迹点列表 (lat, lon)
    private val _trackPoints = MutableStateFlow<List<Pair<Double, Double>>>(emptyList())
    val trackPoints: StateFlow<List<Pair<Double, Double>>> = _trackPoints.asStateFlow()

    private val workoutPoints = mutableListOf<WorkoutPoint>()
    private val heartRateSum = mutableListOf<Int>()
    private var lastLocation: Location? = null
    private var recordingJob: Job? = null

    private var serviceConnection: ServiceConnection? = null
    private var heartRateService: HeartRateService? = null
    private var connectedStartTime = 0L

    init {
        // 监听心率变化，执行告警逻辑
        viewModelScope.launch {
            bleManager.heartRate.collect { bpm ->
                if (bpm > 0) {
                    // 更新历史
                    val history = _heartRateHistory.value.toMutableList()
                    history.add(bpm)
                    if (history.size > 60) history.removeAt(0)
                    _heartRateHistory.value = history

                    // 更新平均心率
                    if (_isRecording.value) {
                        heartRateSum.add(bpm)
                        _averageHeartRate.value = heartRateSum.average().toInt()
                    }

                    // 更新运行时长
                    if (connectedStartTime > 0) {
                        _runDurationSec.value = (System.currentTimeMillis() - connectedStartTime) / 1000
                    }

                    // 告警判断
                    if (_ttsEnabled.value) {
                        val results = alertLogic.evaluate(bpm, _alertConfig.value, System.currentTimeMillis())
                        for (result in results) {
                            val msg = when (result) {
                                is HeartRateAlertLogic.AlertResult.HeartRateReport -> "心率${result.bpm}"
                                is HeartRateAlertLogic.AlertResult.ZoneTooLow -> "心率过低"
                                is HeartRateAlertLogic.AlertResult.ZoneTooHigh -> "心率过高"
                                is HeartRateAlertLogic.AlertResult.ZoneChanged -> "心率区间${result.zone}"
                                HeartRateAlertLogic.AlertResult.None -> null
                            }
                            if (msg != null) {
                                ttsManager.speak(msg)
                                _lastAlert.value = msg
                            }
                        }
                    }
                }
            }
        }

        // 监听连接状态变化
        viewModelScope.launch {
            bleManager.connectionState.collect { state ->
                when (state) {
                    ConnectionState.CONNECTED -> {
                        connectedStartTime = System.currentTimeMillis()
                        ttsManager.speak("心率带已连接")
                        _lastAlert.value = "心率带已连接"
                        startForegroundService()
                    }
                    ConnectionState.DISCONNECTED -> {
                        if (connectedStartTime > 0) {
                            ttsManager.speak("心率带已断开")
                            _lastAlert.value = "心率带已断开"
                        }
                        connectedStartTime = 0
                        _heartRateHistory.value = emptyList()
                        stopForegroundService()
                    }
                    else -> {}
                }
            }
        }
    }

    fun startScan() {
        bleManager.startScan()
    }

    fun stopScan() {
        bleManager.stopScan()
    }

    fun connectDevice(address: String) {
        bleManager.connect(address)
    }

    fun disconnect() {
        if (_isRecording.value) {
            stopRecording()
        }
        alertLogic.reset()
        bleManager.disconnect()
    }

    fun updateConfig(config: HeartRateAlertLogic.AlertConfig) {
        _alertConfig.value = config
        alertLogic.reset()
    }

    fun toggleTts() {
        _ttsEnabled.value = !_ttsEnabled.value
        if (!_ttsEnabled.value) {
            ttsManager.stop()
        }
    }

    fun startRecording() {
        workoutPoints.clear()
        heartRateSum.clear()
        _totalDistance.value = 0.0
        _currentSpeed.value = 0f
        _averageHeartRate.value = 0
        _averagePace.value = "--'--\""
        _trackPoints.value = emptyList()
        lastLocation = null
        _isRecording.value = true
        _hasWorkoutToExport.value = false
        locationTracker.startTracking()

        recordingJob = viewModelScope.launch {
            locationTracker.currentLocation.collect { location ->
                if (location != null) {
                    val hr = bleManager.heartRate.value
                    val point = WorkoutPoint(
                        timestamp = System.currentTimeMillis(),
                        latitude = location.latitude,
                        longitude = location.longitude,
                        altitude = location.altitude,
                        speed = location.speed,
                        heartRate = hr
                    )
                    workoutPoints.add(point)
                    _currentSpeed.value = location.speed * 3.6f // m/s -> km/h

                    // 更新轨迹
                    _trackPoints.value = _trackPoints.value + Pair(location.latitude, location.longitude)

                    lastLocation?.let { last ->
                        val results = FloatArray(1)
                        Location.distanceBetween(
                            last.latitude, last.longitude,
                            location.latitude, location.longitude,
                            results
                        )
                        _totalDistance.value += results[0].toDouble()

                        // 计算平均配速 (min/km)
                        val distKm = _totalDistance.value / 1000.0
                        if (distKm > 0.01 && workoutPoints.size >= 2) {
                            val elapsedMin = (workoutPoints.last().timestamp - workoutPoints.first().timestamp) / 60000.0
                            val paceMinPerKm = elapsedMin / distKm
                            val paceMin = paceMinPerKm.toInt()
                            val paceSec = ((paceMinPerKm - paceMin) * 60).toInt()
                            _averagePace.value = "${paceMin}'${String.format("%02d", paceSec)}\""
                        }
                    }
                    lastLocation = location
                }
            }
        }
    }

    fun stopRecording() {
        _isRecording.value = false
        recordingJob?.cancel()
        recordingJob = null
        locationTracker.stopTracking()
        _hasWorkoutToExport.value = workoutPoints.isNotEmpty()
    }

    fun exportWorkout(format: ExportFormat): Uri? {
        val points = workoutPoints.toList()
        if (points.isEmpty()) return null

        val content = when (format) {
            ExportFormat.GPX -> WorkoutExporter.exportGpx(points)
            ExportFormat.TCX -> WorkoutExporter.exportTcx(points)
        }

        val extension = format.name.lowercase()
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
            .format(Date(points.first().timestamp))
        val fileName = "heartrunner_${timestamp}.$extension"

        val app = getApplication<Application>()
        val dir = File(app.cacheDir, "workouts")
        dir.mkdirs()
        val file = File(dir, fileName)
        file.writeText(content)

        return FileProvider.getUriForFile(
            app, "${app.packageName}.fileprovider", file
        )
    }

    fun clearWorkout() {
        workoutPoints.clear()
        heartRateSum.clear()
        _hasWorkoutToExport.value = false
        _totalDistance.value = 0.0
        _currentSpeed.value = 0f
        _averageHeartRate.value = 0
        _averagePace.value = ""
        _trackPoints.value = emptyList()
    }

    private fun startForegroundService() {
        val ctx = getApplication<Application>()
        val intent = Intent(ctx, HeartRateService::class.java)
        ctx.startForegroundService(intent)

        serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                heartRateService = (binder as? HeartRateService.LocalBinder)?.getService()
                heartRateService?.setBleManager(bleManager)
            }
            override fun onServiceDisconnected(name: ComponentName?) {
                heartRateService = null
            }
        }
        ctx.bindService(intent, serviceConnection!!, Context.BIND_AUTO_CREATE)
    }

    private fun stopForegroundService() {
        val ctx = getApplication<Application>()
        serviceConnection?.let { ctx.unbindService(it) }
        serviceConnection = null
        heartRateService = null
        ctx.stopService(Intent(ctx, HeartRateService::class.java))
    }

    override fun onCleared() {
        super.onCleared()
        if (_isRecording.value) {
            stopRecording()
        }
        ttsManager.shutdown()
        bleManager.disconnect()
        stopForegroundService()
    }
}
