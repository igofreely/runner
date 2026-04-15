package com.heartrunner.app.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.heartrunner.app.ble.BleHeartRateManager
import com.heartrunner.app.ble.ConnectionState
import com.heartrunner.app.ble.ScannedDevice
import com.heartrunner.app.service.HeartRateService
import com.heartrunner.app.tts.HeartRateAlertLogic
import com.heartrunner.app.tts.TtsAlertManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val bleManager = BleHeartRateManager(application)
    private val ttsManager = TtsAlertManager(application)
    private val alertLogic = HeartRateAlertLogic()

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
        ttsManager.shutdown()
        bleManager.disconnect()
        stopForegroundService()
    }
}
