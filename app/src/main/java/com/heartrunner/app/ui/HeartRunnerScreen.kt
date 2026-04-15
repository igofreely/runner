package com.heartrunner.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.MotionEvent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.heartrunner.app.ble.ConnectionState
import com.heartrunner.app.export.ExportFormat
import com.heartrunner.app.location.CoordinateConverter
import com.heartrunner.app.tts.BroadcastConfig
import com.heartrunner.app.tts.HeartRateAlertLogic
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File

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
    val broadcastConfig by viewModel.broadcastConfig.collectAsStateWithLifecycle()

    var showSettings by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showBroadcastSettings by remember { mutableStateOf(false) }

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
                    IconButton(onClick = { viewModel.testTts() }) {
                        Icon(Icons.Default.RecordVoiceOver, contentDescription = "测试语音")
                    }
                    // TTS 开关
                    IconButton(onClick = { viewModel.toggleTts() }) {
                        Icon(
                            if (ttsEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                            contentDescription = "语音开关"
                        )
                    }
                    // 播报设置
                    IconButton(onClick = { showBroadcastSettings = true }) {
                        Icon(Icons.Default.Campaign, contentDescription = "播报设置")
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
            } else if (connectionState == ConnectionState.CONNECTING) {
                ConnectingSection()
            } else {
                MonitorSection(
                    connectionState = connectionState,
                    heartRate = heartRate,
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
                    scannedDevices = scannedDevices,
                    onStartScan = { viewModel.startScan() },
                    onStopScan = { viewModel.stopScan() },
                    onConnect = { viewModel.connectDevice(it) },
                    onStartRecording = { viewModel.startRecording() },
                    onStopRecording = { viewModel.stopRecording() },
                    onExport = { showExportDialog = true },
                    onDisconnect = { viewModel.disconnect() }
                )
            }
        }
    }

    if (showBroadcastSettings) {
        BroadcastSettingsDialog(
            config = broadcastConfig,
            onDismiss = { showBroadcastSettings = false },
            onSave = { config ->
                viewModel.updateBroadcastConfig(config)
                showBroadcastSettings = false
            }
        )
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
    connectionState: ConnectionState,
    heartRate: Int,
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
    scannedDevices: List<com.heartrunner.app.ble.ScannedDevice>,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnect: (String) -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onExport: () -> Unit,
    onDisconnect: () -> Unit
) {
    val isConnected = connectionState == ConnectionState.CONNECTED
    val isScanning = connectionState == ConnectionState.SCANNING
    var isMapFullscreen by remember { mutableStateOf(false) }
    val onBleControlClick = {
        when {
            isConnected -> onDisconnect()
            isScanning -> onStopScan()
            else -> onStartScan()
        }
    }

    // 全屏地图模式
    if (isMapFullscreen) {
        Box(modifier = Modifier.fillMaxSize()) {
            TrackMapView(
                trackPoints = trackPoints,
                modifier = Modifier.fillMaxSize(),
                showFullscreenButton = true,
                onToggleFullscreen = { isMapFullscreen = false },
                overlayContent = {
                    MapActionOverlay(
                        isRecording = isRecording,
                        isConnected = isConnected,
                        isScanning = isScanning,
                        onStartRecording = onStartRecording,
                        onStopRecording = onStopRecording,
                        onBleControlClick = onBleControlClick
                    )
                }
            )
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
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
                        label = "运动时间",
                        value = formatDuration(runDurationSec),
                        unit = "",
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

        Spacer(Modifier.height(8.dp))

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

        if (hasWorkoutToExport && !isRecording) {
            Button(
                onClick = onExport,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary
                )
            ) {
                Icon(Icons.Default.FileDownload, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("导出")
            }

            Spacer(Modifier.height(12.dp))
        }

        if (!isConnected && isScanning && scannedDevices.isEmpty()) {
            Text(
                "正在搜索附近的BLE心率设备...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
        }

        if (!isConnected && scannedDevices.isNotEmpty()) {
            Text(
                "点击已发现的设备完成连接",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 180.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(scannedDevices, key = { it.address }) { device ->
                    DeviceCard(device = device, onClick = { onConnect(device.address) })
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // ── 地图轨迹 ──
        TrackMapView(
            trackPoints = trackPoints,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            showFullscreenButton = true,
            onToggleFullscreen = { isMapFullscreen = true },
            overlayContent = {
                MapActionOverlay(
                    isRecording = isRecording,
                    isConnected = isConnected,
                    isScanning = isScanning,
                    onStartRecording = onStartRecording,
                    onStopRecording = onStopRecording,
                    onBleControlClick = onBleControlClick
                )
            }
        )
    }
}

@Composable
private fun BoxScope.MapActionOverlay(
    isRecording: Boolean,
    isConnected: Boolean,
    isScanning: Boolean,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onBleControlClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(12.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        tonalElevation = 8.dp,
        shadowElevation = 12.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = if (isRecording) onStopRecording else onStartRecording,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRecording) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isRecording) "停止记录" else "开始记录",
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }

            FilledTonalButton(
                onClick = onBleControlClick,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (isConnected) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                    contentColor = if (isConnected) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    }
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                when {
                    isConnected -> {
                        Icon(
                            Icons.Default.BluetoothDisabled,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "断开心率带",
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    isScanning -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "停止扫描",
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    else -> {
                        Icon(
                            Icons.Default.BluetoothSearching,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "扫描心率带",
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
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
    modifier: Modifier = Modifier,
    showFullscreenButton: Boolean = false,
    onToggleFullscreen: (() -> Unit)? = null,
    overlayContent: @Composable BoxScope.() -> Unit = {}
) {
    val context = LocalContext.current

    // 高德地图瓦片源（GCJ-02 坐标系）
    val gaodeTileSource = remember {
        object : OnlineTileSourceBase(
            "GaodeMap", 0, 19, 256, ".png",
            arrayOf(
                "https://webrd01.is.autonavi.com",
                "https://webrd02.is.autonavi.com",
                "https://webrd03.is.autonavi.com",
                "https://webrd04.is.autonavi.com"
            )
        ) {
            override fun getTileURLString(pMapTileIndex: Long): String {
                val zoom = MapTileIndex.getZoom(pMapTileIndex)
                val x = MapTileIndex.getX(pMapTileIndex)
                val y = MapTileIndex.getY(pMapTileIndex)
                return "${baseUrl}/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x=$x&y=$y&z=$zoom"
            }
        }
    }

    // WGS-84 → GCJ-02 坐标转换
    val gcjPoints = remember(trackPoints) {
        trackPoints.map { (lat, lon) ->
            CoordinateConverter.wgs84ToGcj02(lat, lon)
        }
    }

    Box(modifier = modifier) {
        Card(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(16.dp)
        ) {
            AndroidView(
                factory = { ctx ->
                    Configuration.getInstance().load(ctx, ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
                    Configuration.getInstance().userAgentValue = ctx.packageName
                    Configuration.getInstance().osmdroidTileCache = File(ctx.cacheDir, "osmdroid")

                    MapView(ctx).apply {
                        setTileSource(gaodeTileSource)
                        setMultiTouchControls(true)
                        controller.setZoom(16.0)
                        controller.setCenter(GeoPoint(39.9, 116.4))

                        setOnTouchListener { v, event ->
                            when (event.action) {
                                MotionEvent.ACTION_DOWN -> v.parent?.requestDisallowInterceptTouchEvent(true)
                                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.parent?.requestDisallowInterceptTouchEvent(false)
                            }
                            false
                        }
                    }
                },
                update = { mapView ->
                    // 清除旧的覆盖物
                    mapView.overlays.removeAll { it is Polyline || it is Marker }

                    // 轨迹线
                    if (gcjPoints.size >= 2) {
                        val polyline = Polyline().apply {
                            gcjPoints.forEach { (lat, lon) ->
                                addPoint(GeoPoint(lat, lon))
                            }
                            outlinePaint.color = android.graphics.Color.rgb(25, 118, 210)
                            outlinePaint.strokeWidth = 10f
                            outlinePaint.isAntiAlias = true
                            outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                            outlinePaint.strokeJoin = android.graphics.Paint.Join.ROUND
                        }
                        mapView.overlays.add(polyline)
                    }

                    // 实时位置标记
                    if (gcjPoints.isNotEmpty()) {
                        val latest = gcjPoints.last()
                        val latestPoint = GeoPoint(latest.first, latest.second)

                        val marker = Marker(mapView).apply {
                            position = latestPoint
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            title = "当前位置"
                            icon = android.graphics.drawable.GradientDrawable().apply {
                                shape = android.graphics.drawable.GradientDrawable.OVAL
                                setSize(40, 40)
                                setColor(android.graphics.Color.rgb(25, 118, 210))
                                setStroke(6, android.graphics.Color.WHITE)
                            }
                        }
                        mapView.overlays.add(marker)

                        // 起点标记（绿色）
                        if (gcjPoints.size >= 2) {
                            val start = gcjPoints.first()
                            val startMarker = Marker(mapView).apply {
                                position = GeoPoint(start.first, start.second)
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                title = "起点"
                                icon = android.graphics.drawable.GradientDrawable().apply {
                                    shape = android.graphics.drawable.GradientDrawable.OVAL
                                    setSize(30, 30)
                                    setColor(android.graphics.Color.rgb(76, 175, 80))
                                    setStroke(4, android.graphics.Color.WHITE)
                                }
                            }
                            mapView.overlays.add(startMarker)
                        }

                        mapView.controller.animateTo(latestPoint)
                    }

                    mapView.invalidate()
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // 全屏切换按钮
        if (showFullscreenButton && onToggleFullscreen != null) {
            FilledIconButton(
                onClick = onToggleFullscreen,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(36.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                )
            ) {
                Icon(
                    Icons.Default.Fullscreen,
                    contentDescription = "全屏地图",
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        overlayContent()
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
fun BroadcastSettingsDialog(
    config: BroadcastConfig,
    onDismiss: () -> Unit,
    onSave: (BroadcastConfig) -> Unit
) {
    var enabled by remember { mutableStateOf(config.enabled) }
    var triggerByDistance by remember { mutableStateOf(config.triggerByDistance) }
    var triggerByTime by remember { mutableStateOf(config.triggerByTime) }
    var distanceInterval by remember { mutableStateOf(config.distanceIntervalKm.toString()) }
    var timeInterval by remember { mutableStateOf(config.timeIntervalMin.toString()) }
    var announceSpeed by remember { mutableStateOf(config.announceSpeed) }
    var announceDistance by remember { mutableStateOf(config.announceDistance) }
    var announceTime by remember { mutableStateOf(config.announceTime) }
    var announceHeartRate by remember { mutableStateOf(config.announceHeartRate) }
    var announceAvgHeartRate by remember { mutableStateOf(config.announceAvgHeartRate) }
    var announceAvgSpeed by remember { mutableStateOf(config.announceAvgSpeed) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("运动播报设置") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 总开关
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("启用运动播报", style = MaterialTheme.typography.titleSmall)
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }

                HorizontalDivider()

                // 触发方式（可同时启用）
                Text("播报触发方式", style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = triggerByDistance,
                        onClick = { triggerByDistance = !triggerByDistance },
                        label = { Text("按距离") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = triggerByTime,
                        onClick = { triggerByTime = !triggerByTime },
                        label = { Text("按时间") },
                        modifier = Modifier.weight(1f)
                    )
                }

                // 间隔设置
                if (triggerByDistance) {
                    OutlinedTextField(
                        value = distanceInterval,
                        onValueChange = { distanceInterval = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("距离间隔") },
                        suffix = { Text("公里") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (triggerByTime) {
                    OutlinedTextField(
                        value = timeInterval,
                        onValueChange = { timeInterval = it.filter { c -> c.isDigit() } },
                        label = { Text("时间间隔") },
                        suffix = { Text("分钟") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                HorizontalDivider()

                // 播报项目
                Text("播报内容", style = MaterialTheme.typography.titleSmall)
                BroadcastCheckbox("时间", announceTime) { announceTime = it }
                BroadcastCheckbox("里程", announceDistance) { announceDistance = it }
                BroadcastCheckbox("速度", announceSpeed) { announceSpeed = it }
                BroadcastCheckbox("平均速度", announceAvgSpeed) { announceAvgSpeed = it }
                BroadcastCheckbox("心率", announceHeartRate) { announceHeartRate = it }
                BroadcastCheckbox("平均心率", announceAvgHeartRate) { announceAvgHeartRate = it }

                Text(
                    "提示: 心率区间告警优先级更高，会打断播报",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val newConfig = BroadcastConfig(
                        enabled = enabled,
                        triggerByDistance = triggerByDistance,
                        triggerByTime = triggerByTime,
                        distanceIntervalKm = distanceInterval.toDoubleOrNull() ?: 1.0,
                        timeIntervalMin = timeInterval.toIntOrNull() ?: 5,
                        announceSpeed = announceSpeed,
                        announceDistance = announceDistance,
                        announceTime = announceTime,
                        announceHeartRate = announceHeartRate,
                        announceAvgHeartRate = announceAvgHeartRate,
                        announceAvgSpeed = announceAvgSpeed
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
private fun BroadcastCheckbox(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
    }
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
