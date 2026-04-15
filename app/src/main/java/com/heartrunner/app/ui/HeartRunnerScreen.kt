package com.heartrunner.app.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.heartrunner.app.ble.ConnectionState
import com.heartrunner.app.export.ExportFormat
import com.heartrunner.app.tts.HeartRateAlertLogic

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HeartRunnerApp(viewModel: MainViewModel) {

    val permissions = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val permissionState = rememberMultiplePermissionsState(permissions)
    val connectionState by viewModel.bleManager.connectionState.collectAsStateWithLifecycle()
    val heartRate by viewModel.bleManager.heartRate.collectAsStateWithLifecycle()
    val scannedDevices by viewModel.bleManager.scannedDevices.collectAsStateWithLifecycle()
    val heartRateHistory by viewModel.heartRateHistory.collectAsStateWithLifecycle()
    val runDuration by viewModel.runDurationSec.collectAsStateWithLifecycle()
    val lastAlert by viewModel.lastAlert.collectAsStateWithLifecycle()
    val ttsEnabled by viewModel.ttsEnabled.collectAsStateWithLifecycle()
    val alertConfig by viewModel.alertConfig.collectAsStateWithLifecycle()
    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    val currentSpeed by viewModel.currentSpeed.collectAsStateWithLifecycle()
    val totalDistance by viewModel.totalDistance.collectAsStateWithLifecycle()
    val hasWorkoutToExport by viewModel.hasWorkoutToExport.collectAsStateWithLifecycle()
    val averageHeartRate by viewModel.averageHeartRate.collectAsStateWithLifecycle()
    val averagePace by viewModel.averagePace.collectAsStateWithLifecycle()
    val trackPoints by viewModel.trackPoints.collectAsStateWithLifecycle()

    var showSettings by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!permissionState.allPermissionsGranted) {
            permissionState.launchMultiplePermissionRequest()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("HeartRunner") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                actions = {
                    // TTS 开关
                    IconButton(onClick = { viewModel.toggleTts() }) {
                        Icon(
                            if (ttsEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                            contentDescription = "语音开关"
                        )
                    }
                    // 设置
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!permissionState.allPermissionsGranted) {
                PermissionCard(onRequest = { permissionState.launchMultiplePermissionRequest() })
            } else {
                when (connectionState) {
                    ConnectionState.DISCONNECTED,
                    ConnectionState.SCANNING -> {
                        ScanSection(
                            isScanning = connectionState == ConnectionState.SCANNING,
                            devices = scannedDevices,
                            onStartScan = { viewModel.startScan() },
                            onStopScan = { viewModel.stopScan() },
                            onConnect = { viewModel.connectDevice(it) }
                        )
                    }
                    ConnectionState.CONNECTING -> {
                        ConnectingSection()
                    }
                    ConnectionState.CONNECTED -> {
                        MonitorSection(
                            heartRate = heartRate,
                            heartRateHistory = heartRateHistory,
                            runDurationSec = runDuration,
                            lastAlert = lastAlert,
                            alertConfig = alertConfig,
                            isRecording = isRecording,
                            currentSpeed = currentSpeed,
                            totalDistance = totalDistance,
                            hasWorkoutToExport = hasWorkoutToExport,
                            averageHeartRate = averageHeartRate,
                            averagePace = averagePace,
                            trackPoints = trackPoints,
                            onStartRecording = { viewModel.startRecording() },
                            onStopRecording = { viewModel.stopRecording() },
                            onExport = { showExportDialog = true },
                            onDisconnect = { viewModel.disconnect() }
                        )
                    }
                }
            }
        }
    }

    if (showSettings) {
        SettingsDialog(
            config = alertConfig,
            onDismiss = { showSettings = false },
            onSave = { config ->
                viewModel.updateConfig(config)
                showSettings = false
            }
        )
    }

    if (showExportDialog) {
        ExportDialog(
            onDismiss = { showExportDialog = false },
            onExport = { format ->
                showExportDialog = false
                viewModel.exportWorkout(format)
            },
            onClear = {
                showExportDialog = false
                viewModel.clearWorkout()
            }
        )
    }
}

@Composable
fun PermissionCard(onRequest: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.BluetoothDisabled, contentDescription = null, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(16.dp))
            Text("需要蓝牙和位置权限才能使用", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRequest) {
                Text("授予权限")
            }
        }
    }
}

@Composable
fun ScanSection(
    isScanning: Boolean,
    devices: List<com.heartrunner.app.ble.ScannedDevice>,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnect: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 扫描按钮
        Button(
            onClick = { if (isScanning) onStopScan() else onStartScan() },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            if (isScanning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(12.dp))
                Text("扫描中... 点击停止")
            } else {
                Icon(Icons.Default.BluetoothSearching, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text("扫描心率带")
            }
        }

        Spacer(Modifier.height(16.dp))

        if (devices.isEmpty() && isScanning) {
            Text(
                "正在搜索附近的BLE心率设备...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 设备列表
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(devices, key = { it.address }) { device ->
                DeviceCard(device = device, onClick = { onConnect(device.address) })
            }
        }
    }
}

@Composable
fun DeviceCard(
    device: com.heartrunner.app.ble.ScannedDevice,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.FavoriteBorder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    device.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    device.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "${device.rssi} dBm",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ConnectingSection() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(24.dp))
        Text("正在连接心率带...", style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
fun MonitorSection(
    heartRate: Int,
    heartRateHistory: List<Int>,
    runDurationSec: Long,
    lastAlert: String,
    alertConfig: HeartRateAlertLogic.AlertConfig,
    isRecording: Boolean,
    currentSpeed: Float,
    totalDistance: Double,
    hasWorkoutToExport: Boolean,
    averageHeartRate: Int,
    averagePace: String,
    trackPoints: List<Pair<Double, Double>>,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onExport: () -> Unit,
    onDisconnect: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── 顶部：心率 + 时间 ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 心率显示
            CompactHeartRateDisplay(heartRate = heartRate, config = alertConfig)
            Spacer(Modifier.width(16.dp))
            // 运动时间
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "运动时间",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    formatDuration(runDurationSec),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── 核心数据面板：2x3 网格 ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    DataCell(
                        label = "里程",
                        value = if (totalDistance >= 1000) {
                            String.format("%.2f", totalDistance / 1000)
                        } else {
                            String.format("%.0f", totalDistance)
                        },
                        unit = if (totalDistance >= 1000) "km" else "m",
                        modifier = Modifier.weight(1f)
                    )
                    DataCell(
                        label = "当前速度",
                        value = String.format("%.1f", currentSpeed),
                        unit = "km/h",
                        modifier = Modifier.weight(1f)
                    )
                    DataCell(
                        label = "平均配速",
                        value = averagePace.ifEmpty { "--'--\"" },
                        unit = "/km",
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    DataCell(
                        label = "当前心率",
                        value = if (heartRate > 0) "$heartRate" else "--",
                        unit = "bpm",
                        valueColor = heartRateColor(heartRate, alertConfig),
                        modifier = Modifier.weight(1f)
                    )
                    DataCell(
                        label = "平均心率",
                        value = if (averageHeartRate > 0) "$averageHeartRate" else "--",
                        unit = "bpm",
                        modifier = Modifier.weight(1f)
                    )
                    DataCell(
                        label = "心率区间",
                        value = "${alertConfig.zoneLowBpm}-${alertConfig.zoneHighBpm}",
                        unit = "bpm",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── 地图轨迹 ──
        TrackMapView(
            trackPoints = trackPoints,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        )

        Spacer(Modifier.height(12.dp))

        // ── 心率曲线 ──
        if (heartRateHistory.size > 1) {
            HeartRateChart(
                data = heartRateHistory,
                config = alertConfig,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            )
            Spacer(Modifier.height(12.dp))
        }

        // ── 最近播报 ──
        if (lastAlert.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(lastAlert, style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // ── 录制控制按钮 ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isRecording) {
                Button(
                    onClick = onStopRecording,
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("停止记录")
                }
            } else {
                Button(
                    onClick = onStartRecording,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.FiberManualRecord, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("开始记录")
                }
            }

            if (hasWorkoutToExport && !isRecording) {
                Button(
                    onClick = onExport,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary
                    )
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("导出")
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── 断开连接 ──
        OutlinedButton(
            onClick = onDisconnect,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Icon(Icons.Default.BluetoothDisabled, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("断开连接")
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun CompactHeartRateDisplay(heartRate: Int, config: HeartRateAlertLogic.AlertConfig) {
    val color = heartRateColor(heartRate, config)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(100.dp)
            .background(color = color.copy(alpha = 0.1f), shape = CircleShape)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Favorite,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = if (heartRate > 0) "$heartRate" else "--",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                "BPM",
                style = MaterialTheme.typography.labelSmall,
                color = color.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun DataCell(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
        Text(
            unit,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun TrackMapView(
    trackPoints: List<Pair<Double, Double>>,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = surfaceVariantColor.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        if (trackPoints.size < 2) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Map,
                        contentDescription = null,
                        tint = onSurfaceVariantColor.copy(alpha = 0.4f),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (trackPoints.isEmpty()) "开始记录后显示轨迹" else "等待更多GPS数据...",
                        style = MaterialTheme.typography.bodySmall,
                        color = onSurfaceVariantColor.copy(alpha = 0.5f)
                    )
                }
            }
        } else {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                val padding = 8.dp.toPx()

                val lats = trackPoints.map { it.first }
                val lons = trackPoints.map { it.second }
                val minLat = lats.min()
                val maxLat = lats.max()
                val minLon = lons.min()
                val maxLon = lons.max()

                val latRange = (maxLat - minLat).coerceAtLeast(0.0001)
                val lonRange = (maxLon - minLon).coerceAtLeast(0.0001)

                val drawWidth = size.width - padding * 2
                val drawHeight = size.height - padding * 2

                // 保持比例
                val scaleX = drawWidth / lonRange
                val scaleY = drawHeight / latRange
                val scale = minOf(scaleX, scaleY)
                val offsetX = padding + (drawWidth - lonRange * scale).toFloat() / 2
                val offsetY = padding + (drawHeight - latRange * scale).toFloat() / 2

                fun toScreen(lat: Double, lon: Double): Offset {
                    val x = offsetX + ((lon - minLon) * scale).toFloat()
                    val y = offsetY + ((maxLat - lat) * scale).toFloat() // 翻转Y轴
                    return Offset(x, y)
                }

                // 绘制网格
                for (i in 0..4) {
                    val y = padding + drawHeight * i / 4
                    drawLine(outlineColor.copy(alpha = 0.3f), Offset(padding, y), Offset(size.width - padding, y), 0.5.dp.toPx())
                    val x = padding + drawWidth * i / 4
                    drawLine(outlineColor.copy(alpha = 0.3f), Offset(x, padding), Offset(x, size.height - padding), 0.5.dp.toPx())
                }

                // 绘制轨迹
                val path = Path()
                trackPoints.forEachIndexed { index, (lat, lon) ->
                    val pos = toScreen(lat, lon)
                    if (index == 0) path.moveTo(pos.x, pos.y) else path.lineTo(pos.x, pos.y)
                }

                drawPath(
                    path = path,
                    color = primaryColor,
                    style = Stroke(
                        width = 3.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                // 起点（绿色）
                val startPos = toScreen(trackPoints.first().first, trackPoints.first().second)
                drawCircle(Color(0xFF4CAF50), radius = 6.dp.toPx(), center = startPos)
                drawCircle(Color.White, radius = 3.dp.toPx(), center = startPos)

                // 当前位置（蓝色）
                val endPos = toScreen(trackPoints.last().first, trackPoints.last().second)
                drawCircle(primaryColor, radius = 6.dp.toPx(), center = endPos)
                drawCircle(Color.White, radius = 3.dp.toPx(), center = endPos)
            }
        }
    }
}

private fun heartRateColor(heartRate: Int, config: HeartRateAlertLogic.AlertConfig): Color {
    return when {
        heartRate <= 0 -> Color.Gray
        heartRate < config.zoneLowBpm -> Color(0xFF2196F3)
        heartRate > config.zoneHighBpm -> Color(0xFFF44336)
        else -> Color(0xFF4CAF50)
    }
}

@Composable
fun HeartRateChart(
    data: List<Int>,
    config: HeartRateAlertLogic.AlertConfig,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error
    val zoneLow = config.zoneLowBpm.toFloat()
    val zoneHigh = config.zoneHighBpm.toFloat()

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            if (data.size < 2) return@Canvas

            val minHr = (data.minOrNull() ?: 50).coerceAtMost(50).toFloat()
            val maxHr = (data.maxOrNull() ?: 200).coerceAtLeast(200).toFloat()
            val range = maxHr - minHr

            val stepX = size.width / (data.size - 1).coerceAtLeast(1)

            // 画区间线
            val lowY = size.height - ((zoneLow - minHr) / range) * size.height
            val highY = size.height - ((zoneHigh - minHr) / range) * size.height

            drawLine(
                color = Color.Green.copy(alpha = 0.3f),
                start = Offset(0f, lowY),
                end = Offset(size.width, lowY),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = Color.Red.copy(alpha = 0.3f),
                start = Offset(0f, highY),
                end = Offset(size.width, highY),
                strokeWidth = 1.dp.toPx()
            )

            // 画心率曲线
            val path = Path()
            data.forEachIndexed { index, hr ->
                val x = index * stepX
                val y = size.height - ((hr.toFloat() - minHr) / range) * size.height
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }

            drawPath(
                path = path,
                color = primaryColor,
                style = Stroke(width = 2.dp.toPx())
            )

            // 画当前点
            if (data.isNotEmpty()) {
                val lastX = (data.size - 1) * stepX
                val lastY = size.height - ((data.last().toFloat() - minHr) / range) * size.height
                drawCircle(
                    color = if (data.last() > config.zoneHighBpm) errorColor else primaryColor,
                    radius = 4.dp.toPx(),
                    center = Offset(lastX, lastY)
                )
            }
        }
    }
}

@Composable
fun SettingsDialog(
    config: HeartRateAlertLogic.AlertConfig,
    onDismiss: () -> Unit,
    onSave: (HeartRateAlertLogic.AlertConfig) -> Unit
) {
    var maxHr by remember { mutableStateOf(config.maxHeartRate.toString()) }
    var zoneLow by remember { mutableStateOf(config.zoneLowPercent.toString()) }
    var zoneHigh by remember { mutableStateOf(config.zoneHighPercent.toString()) }
    var reportInterval by remember { mutableStateOf(config.heartRateReportInterval.toString()) }
    var alertInterval by remember { mutableStateOf(config.zoneAlertInterval.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("心率区间设置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = maxHr,
                    onValueChange = { maxHr = it.filter { c -> c.isDigit() } },
                    label = { Text("最大心率") },
                    suffix = { Text("BPM") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = zoneLow,
                        onValueChange = { zoneLow = it.filter { c -> c.isDigit() } },
                        label = { Text("区间下限") },
                        suffix = { Text("%") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = zoneHigh,
                        onValueChange = { zoneHigh = it.filter { c -> c.isDigit() } },
                        label = { Text("区间上限") },
                        suffix = { Text("%") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = reportInterval,
                        onValueChange = { reportInterval = it.filter { c -> c.isDigit() } },
                        label = { Text("播报间隔") },
                        suffix = { Text("秒") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = alertInterval,
                        onValueChange = { alertInterval = it.filter { c -> c.isDigit() } },
                        label = { Text("告警间隔") },
                        suffix = { Text("秒") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                // 预览计算值
                val previewMax = maxHr.toIntOrNull() ?: 0
                val previewLow = zoneLow.toIntOrNull() ?: 0
                val previewHigh = zoneHigh.toIntOrNull() ?: 0
                if (previewMax > 0 && previewLow > 0 && previewHigh > 0) {
                    Text(
                        "心率范围: ${previewMax * previewLow / 100} - ${previewMax * previewHigh / 100} BPM",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val newConfig = HeartRateAlertLogic.AlertConfig(
                        maxHeartRate = maxHr.toIntOrNull() ?: 190,
                        zoneLowPercent = zoneLow.toIntOrNull() ?: 70,
                        zoneHighPercent = zoneHigh.toIntOrNull() ?: 80,
                        heartRateReportInterval = reportInterval.toIntOrNull() ?: 30,
                        zoneAlertInterval = alertInterval.toIntOrNull() ?: 5
                    )
                    onSave(newConfig)
                }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
fun ExportDialog(
    onDismiss: () -> Unit,
    onExport: (ExportFormat) -> android.net.Uri?,
    onClear: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导出运动数据") },
        text = {
            Text("选择导出格式。GPX 格式兼容性更广，TCX 格式包含更多训练数据。")
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    val uri = onExport(ExportFormat.GPX)
                    if (uri != null) shareFile(context, uri, "application/gpx+xml")
                }) {
                    Text("GPX")
                }
                TextButton(onClick = {
                    val uri = onExport(ExportFormat.TCX)
                    if (uri != null) shareFile(context, uri, "application/xml")
                }) {
                    Text("TCX")
                }
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onClear) {
                    Text("清除数据", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
            }
        }
    )
}

private fun shareFile(context: android.content.Context, uri: android.net.Uri, mimeType: String) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, "导出运动数据"))
}

private fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) {
        String.format("%d:%02d:%02d", h, m, s)
    } else {
        String.format("%02d:%02d", m, s)
    }
}
